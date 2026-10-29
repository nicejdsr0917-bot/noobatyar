"""Promotional push campaigns page: compose once, send to customers and/or
business owners, filtered by business category.

Deliberately a separate section from the segment builder: that page is
one-audience-at-a-time by design (its whole querystring is scoped to a single
`kind`), and "this announcement, to doctors' customers *and* doctor owners"
cannot be said in that model.

The page is three tabs (compose a push, the renewal-reminder SMS, reports of
both) on one URL picked by ?tab=, so every send can land back on the tab that
shows its result. The SMS tab's endpoints live in :mod:`.expiry_sms`.
"""

import uuid

from django.contrib import messages
from django.core.exceptions import PermissionDenied
from django.http import HttpResponseRedirect, JsonResponse
from django.template.response import TemplateResponse
from django.urls import reverse

from ._common import exclude_opted_out, require_perm, require_post_perm

TEMPLATE = 'admin/core/push_campaigns.html'

#: Audience checkbox key → the segment `kind` its filters are parsed as.
CAMPAIGN_AUDIENCES = {
    'visitor': 'visitor',
    'owner': 'owner',
}

CAMPAIGN_TABS = ('push', 'sms', 'reports')


def campaigns_url(site, tab):
    return reverse('admin:core_campaigns', current_app=site.name) + f'?tab={tab}'


def campaign_filters(audience, category):
    """Build a segment filter dict for one audience from the campaign form.

    The campaign form is deliberately far simpler than the segment builder
    (audience + category, not fifteen filters), so rather than reusing
    `segments.parse_filters` on a querystring the form doesn't produce,
    this constructs the one filter shape it needs.
    """
    from core import segments

    params = {'business_category': category} if category else {}
    return segments.parse_filters(audience, params)


def _selected_audiences(data):
    return [a for a in CAMPAIGN_AUDIENCES if data.get(f'audience_{a}') == '1']


def _recent_campaigns(limit=40):
    """Recent push logs grouped so a two-audience campaign reads as one entry
    with two rows, matching how it was actually sent."""
    from api.jalali import format_datetime as jalali_datetime
    from core.models import MarketingPushLog

    recent = list(MarketingPushLog.objects.select_related('sent_by').order_by('-sent_at')[:limit])
    grouped = {}
    for row in recent:
        grouped.setdefault(row.group_id, []).append(row)
    campaigns = []
    for rows in grouped.values():
        first = rows[0]
        reachable_total = sum(r.reachable_count for r in rows)
        delivered_total = sum(r.delivered_count for r in rows)
        campaigns.append({
            'rows': rows,
            'sent_at': first.sent_at,
            'title': first.title,
            'body': first.body,
            'image_url': first.image_url,
            'link': first.link,
            'deep_link': first.deep_link,
            'sent_by': first.sent_by,
            'recipient_total': sum(r.recipient_count for r in rows),
            'reachable_total': reachable_total,
            'delivered_total': delivered_total,
            'failed_total': sum(r.failed_count for r in rows),
            'sent_at_display': jalali_datetime(first.sent_at),
            'delivery_rate': round(100 * delivered_total / reachable_total) if reachable_total else 0,
        })
    campaigns.sort(key=lambda c: c['sent_at'], reverse=True)
    return campaigns


def _resolve_tab(request, tab):
    if tab not in CAMPAIGN_TABS:
        tab = request.GET.get('tab')
    if tab not in CAMPAIGN_TABS:
        tab = 'push'
    can_push = request.user.has_perm('core.send_marketing_push')
    can_sms = request.user.has_perm('core.send_expiry_sms')
    if (tab == 'push' and not can_push) or (tab == 'sms' and not can_sms):
        tab = 'push' if can_push else 'sms'
    return tab, can_push, can_sms


def campaign_context(site, request, form=None, error=None, tab=None):
    from api.jalali import format_datetime as jalali_datetime
    from api.services import push
    from api.sms import SMS_FOOTER, SMS_OPT_OUT
    from accounting.models import REMINDER_TOKENS, Plan, SubscriptionReminderSettings
    from core import segments
    from core.campaigns import BODY_MAX, DEEP_LINK_PAGES, TITLE_MAX
    from core.models import ExpirySmsLog

    campaigns = _recent_campaigns()

    expiry_logs = list(ExpirySmsLog.objects.select_related('sent_by').order_by('-sent_at')[:30])
    for log in expiry_logs:
        log.sent_at_display = jalali_datetime(log.sent_at)

    reminder_settings = SubscriptionReminderSettings.load()
    last_run_display = (
        jalali_datetime(reminder_settings.last_run_at) if reminder_settings.last_run_at else ''
    )

    # What api.sms.signed() appends after whatever staff types — footer
    # + the mandatory لغو11 opt-out — so the compose box can count real
    # SMS segments (and therefore real cost) instead of just the typed
    # text, which understates both.
    sms_suffix = f'\n\n{SMS_FOOTER}\n{SMS_OPT_OUT}'

    tab, can_push, can_sms = _resolve_tab(request, tab)

    return {
        **site.each_context(request),
        'title': 'اعلان تبلیغاتی',
        'tab': tab,
        'business_categories': segments.BUSINESS_CATEGORY_CHOICES,
        'deep_link_pages': DEEP_LINK_PAGES,
        'title_max': TITLE_MAX,
        'body_max': BODY_MAX,
        'campaigns': campaigns,
        'form': form or {},
        'error': error,
        'push_configured': push.is_configured(),
        'can_send_push': can_push,
        'can_send_sms': can_sms,
        'expiry_logs': expiry_logs,
        'reminder_settings': reminder_settings,
        'reminder_last_run': last_run_display,
        'reminder_tokens': REMINDER_TOKENS,
        'plans': Plan.objects.order_by('-is_active', 'id'),
        'operator_phone': getattr(request.user, 'phone', '') or '',
        'sms_suffix_len': len(sms_suffix),
    }


def campaign_view(site, request):
    """The campaigns page: push compose, renewal-reminder SMS, reports.

    Open to anyone holding either send permission; each sees only the
    tabs their permission covers (see campaign_context).
    """
    if not (request.user.has_perm('core.send_marketing_push')
            or request.user.has_perm('core.send_expiry_sms')):
        raise PermissionDenied
    return TemplateResponse(request, TEMPLATE, campaign_context(site, request))


def campaign_count_view(site, request):
    """Live "how many will this reach" preview, as JSON.

    Counts only — no names, no phone numbers — so this needs no
    `export_pii` gate: it is the same information the send confirmation
    would show a moment later.
    """
    from core import segments
    from core.messaging.push_campaigns import audience_recipients

    require_perm(request, 'core.send_marketing_push')

    category = request.GET.get('business_category') or ''
    exclude = exclude_opted_out(request.GET)

    out = {}
    try:
        for audience in _selected_audiences(request.GET):
            filters = campaign_filters(audience, category)
            matched, reachable = audience_recipients(audience, filters, exclude)
            out[audience] = {'matched': matched, 'reachable': len(reachable)}
    except segments.SegmentFilterError as exc:
        return JsonResponse({'error': str(exc)}, status=400)

    return JsonResponse({'audiences': out})


def campaign_send_view(site, request):
    """POST-only: build one PushCampaign and send it to each ticked audience.

    Gate: `core.send_marketing_push` — the same permission the visitor-only
    Phase 3 send used; see core.models.MarketingPushLog's Meta.permissions
    for why it is standalone rather than folded into a view permission.
    """
    from core import segments
    from core.campaigns import CampaignError, PushCampaign
    from core.messaging import send_push_campaign

    require_post_perm(request, 'core.send_marketing_push')

    category = request.POST.get('business_category') or ''
    exclude = exclude_opted_out(request.POST)
    audiences = _selected_audiences(request.POST)

    # Re-rendered rather than redirected on error, so a long composed body
    # survives a validation failure instead of being thrown away.
    form = {
        'title': request.POST.get('title', ''),
        'body': request.POST.get('body', ''),
        'image_url': request.POST.get('image_url', ''),
        'link': request.POST.get('link', ''),
        'deep_link': request.POST.get('deep_link', ''),
        'business_category': category,
        'exclude_opted_out': exclude,
        'audience_visitor': 'visitor' in audiences,
        'audience_owner': 'owner' in audiences,
    }

    def fail(msg):
        return TemplateResponse(
            request, TEMPLATE,
            campaign_context(site, request, form=form, error=msg, tab='push'),
        )

    if not audiences:
        return fail('حداقل یک گروه مخاطب (مشتریان یا صاحبان کسب‌وکار) را انتخاب کنید.')

    try:
        campaign = PushCampaign.create(
            title=form['title'], body=form['body'],
            image_url=form['image_url'], link=form['link'],
            deep_link=form['deep_link'],
        )
    except CampaignError as exc:
        return fail(str(exc))

    group_id = uuid.uuid4()
    logs = []
    try:
        for audience in audiences:
            filters = campaign_filters(audience, category)
            definition = {'business_category': category, 'exclude_opted_out': exclude}
            logs.append(send_push_campaign(
                campaign, audience, filters, definition,
                group_id=group_id, exclude_opted_out=exclude,
                actor=request.user,
            ))
    except segments.SegmentFilterError as exc:
        return fail(str(exc))

    for log in logs:
        label = log.get_audience_display()
        if log.reachable_count == 0:
            messages.warning(
                request,
                f'{label}: {log.recipient_count} نفر با فیلتر مطابقت داشتند، '
                f'اما هیچ‌کدام اعلان فعال ندارند.',
            )
        else:
            messages.success(
                request,
                f'{label}: از {log.recipient_count} نفر واجد شرایط، '
                f'{log.reachable_count} نفر اعلان فعال داشتند؛ '
                f'ارسال در صف قرار گرفت و نتیجه در گزارش‌ها ثبت می‌شود.',
            )

    return HttpResponseRedirect(campaigns_url(site, 'reports'))
