"""Expiry-SMS blast — lives on the campaigns page (SMS tab), separate form:
the audience/channel is entirely different (Subscription expiry window + SMS,
not a segment + push), so it gets its own send/preview endpoints.

Manual counterpart to accounting.SubscriptionReminderSettings' cron-run
reminder: same "owner's subscription ending soon" audience, but the operator
writes the wording fresh each time and sends on demand.
"""

from django.contrib import messages
from django.http import HttpResponseRedirect, JsonResponse

from api.sms import SEND_WINDOW_CLOSED

from ._common import require_perm, require_post_perm
from .campaigns import campaigns_url

PERM = 'core.send_expiry_sms'
_ERR_EMPTY_MESSAGE = 'متن پیامک نمی‌تواند خالی باشد.'
_ERR_DAYS = 'تعداد روز باید عددی مثبت باشد.'


def _within_send_window():
    # Resolved on core.admin_site at call time so tests can patch
    # `core.admin_site.within_send_window`.
    from core import admin_site

    return admin_site.within_send_window()


def _parse_days(raw):
    try:
        return int(raw)
    except ValueError:
        return 0


def expiry_audience_params(data):
    """(days_before, plan_ids, skip_reminded) from a GET/POST of the
    manual-send form; days_before is 0 when missing or not a number."""
    days_before = _parse_days(data.get('expiry_days_before') or data.get('days_before') or '')
    plan_ids = [int(p) for p in data.getlist('plan') if str(p).isdigit()]
    skip_reminded = data.get('skip_reminded', '1') != '0'
    return days_before, plan_ids, skip_reminded


def expiry_mode(data):
    """'expired' (lapsed and not renewed) or the default 'expiring'."""
    return 'expired' if data.get('expiry_mode') == 'expired' else 'expiring'


def expiry_sms_count_view(site, request):
    """Live audience for the manual send: the count and the first rows of
    who exactly it reaches (name, phone, plan, days left).

    Names and phones are shown here because the same permission already
    shows them in the send report — the preview is that list, a moment
    earlier, so nobody sends blind.
    """
    from core.messaging import days_for_sms, expiry_sms_audience

    require_perm(request, PERM)

    days_before, plan_ids, skip_reminded = expiry_audience_params(request.GET)
    mode = expiry_mode(request.GET)
    if days_before <= 0:
        return JsonResponse({'error': _ERR_DAYS}, status=400)

    subs = expiry_sms_audience(
        days_before, plan_ids=plan_ids, skip_reminded=skip_reminded, mode=mode,
    )
    preview_limit = 50
    rows = [
        {
            'name': sub.user.name or '',
            'phone': getattr(sub.user, 'phone', '') or '',
            'plan': sub.plan.name,
            'days_left': days_for_sms(sub, mode),
        }
        for sub in subs[:preview_limit]
    ]
    return JsonResponse({
        'matched': subs.count(),
        'rows': rows,
        'preview_limit': preview_limit,
    })


def expiry_sms_send_view(site, request):
    """POST-only: send the manual renewal reminder, then show its report."""
    from core.messaging import send_expiry_sms

    require_post_perm(request, PERM)

    sms_tab = campaigns_url(site, 'sms')
    message = (request.POST.get('expiry_message') or '').strip()
    days_before, plan_ids, skip_reminded = expiry_audience_params(request.POST)

    if not message:
        messages.error(request, _ERR_EMPTY_MESSAGE)
        return HttpResponseRedirect(sms_tab)
    if days_before <= 0:
        messages.error(request, _ERR_DAYS)
        return HttpResponseRedirect(sms_tab)
    if not _within_send_window():
        # The line would take every message and deliver none of them; say
        # so now instead of logging a whole audience as failed.
        messages.error(request, SEND_WINDOW_CLOSED)
        return HttpResponseRedirect(sms_tab)

    log = send_expiry_sms(
        message, days_before, actor=request.user,
        plan_ids=plan_ids, skip_reminded=skip_reminded,
        mode=expiry_mode(request.POST),
    )
    if log.matched_count == 0:
        messages.warning(request, 'با این فیلترها کسی پیدا نشد؛ پیامکی ارسال نشد.')
        return HttpResponseRedirect(sms_tab)
    messages.success(
        request,
        f'برای {log.matched_count} صاحب اشتراک واجد شرایط، ارسال پیامک در صف قرار گرفت؛ '
        f'نتیجه در گزارش ارسال ثبت می‌شود.',
    )
    return HttpResponseRedirect(campaigns_url(site, 'reports'))


def expiry_sms_test_view(site, request):
    """POST-only: one copy of the manual message to the operator's own
    phone, filled with sample values, to read before sending to everyone."""
    from core.messaging import send_expiry_sms_test

    require_post_perm(request, PERM)

    message = (request.POST.get('expiry_message') or '').strip()
    days_before, _, _ = expiry_audience_params(request.POST)
    if not message:
        messages.error(request, _ERR_EMPTY_MESSAGE)
    elif not _within_send_window():
        messages.error(request, SEND_WINDOW_CLOSED)
    else:
        log = send_expiry_sms_test(message, max(days_before, 1), actor=request.user)
        if log.sent_count:
            messages.success(request, f'پیامک آزمایشی به شماره‌ی {log.recipients[0]["phone"]} ارسال شد.')
        else:
            messages.error(request, f'ارسال آزمایشی ناموفق بود: {log.recipients[0].get("error") or "نامشخص"}')
    return HttpResponseRedirect(campaigns_url(site, 'sms'))


def expiry_sms_autosend_view(site, request):
    """POST-only: save the automatic reminder's own form — on/off, lead
    time and template — onto ``accounting.SubscriptionReminderSettings``,
    which the cron-run ``run_subscription_lifecycle`` reads.

    Separate from the manual send's fields on purpose: saving this no
    longer copies whatever happened to be typed into the manual box.
    It does not schedule anything itself; the page shows the job's last
    run so an operator can see whether something is running it.
    """
    from accounting.models import SubscriptionReminderSettings

    require_post_perm(request, PERM)

    message = (request.POST.get('auto_message') or '').strip()
    days_before = _parse_days(request.POST.get('auto_days_before', ''))
    is_enabled = request.POST.get('auto_enabled') == '1'

    if not message:
        messages.error(request, 'متن پیامک خودکار نمی‌تواند خالی باشد.')
    elif days_before <= 0:
        messages.error(request, _ERR_DAYS)
    else:
        settings_row = SubscriptionReminderSettings.load()
        settings_row.is_enabled = is_enabled
        settings_row.days_before = days_before
        settings_row.message_template = message
        settings_row.save()
        messages.success(
            request,
            'ارسال خودکار روشن و ذخیره شد.' if is_enabled
            else 'تنظیمات ذخیره شد؛ ارسال خودکار خاموش است.',
        )

    return HttpResponseRedirect(campaigns_url(site, 'sms'))
