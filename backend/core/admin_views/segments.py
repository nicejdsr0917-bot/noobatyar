"""Audience segment builder: filter form + live count, save, re-run, CSV
export and the visitor-only "notify" push.

core.segments is the query layer (core/segments.py); these views own the
request/permission/render side. Every one is `admin_view()`-wrapped by
NobatyarAdminSite.get_urls(), so is_staff/login is already enforced.
"""

import csv
import io

from django.contrib import messages
from django.core.exceptions import PermissionDenied
from django.http import HttpResponse, HttpResponseRedirect
from django.shortcuts import get_object_or_404
from django.template.response import TemplateResponse
from django.urls import reverse
from django.utils.http import urlencode

from ._common import exclude_opted_out, require_perm, require_post_perm


def segment_kind(request):
    # segment_builder_view/segment_run_view/segment_export_view arrive as
    # GET; segment_save_view is the one POST here, and its form
    # (templates/admin/core/segment_builder.html) submits `kind` as a
    # hidden POST field, not a querystring param — the action URL carries
    # no querystring at all. Reading GET only here meant saving an
    # "owner" segment silently fell through to the 'visitor' default,
    # producing a wrong kind and an empty filter definition (POST's
    # low_wallet_below etc. were parsed as visitor filters and dropped).
    # POST takes priority so a GET param can't shadow an explicit POST.
    kind = request.POST.get('kind') or request.GET.get('kind') or 'visitor'
    return kind if kind in ('visitor', 'owner') else 'visitor'


def _redirect_to_builder(site, request):
    base = reverse('admin:core_segment_builder', current_app=site.name)
    return HttpResponseRedirect(f'{base}?{request.POST.get("return_qs", "")}')


def segment_builder_view(site, request):
    """Filter form + live count preview, no export.

    Gate: holding `visitor.view_visitor` unlocks the visitor tab, holding
    `api.view_user` unlocks the owner tab — same "degrade per panel, never
    blanket-403 the page" idiom as everywhere else in this admin, applied
    to *tabs* instead of panels since the two kinds are otherwise
    unrelated pages sharing one template. A viewer with neither gets a
    real 403: there is nothing left on this page for them.
    """
    from core import segments

    can_visitor = request.user.has_perm('visitor.view_visitor')
    can_owner = request.user.has_perm('api.view_user')
    if not (can_visitor or can_owner):
        raise PermissionDenied

    kind = segment_kind(request)
    if kind == 'visitor' and not can_visitor:
        raise PermissionDenied
    if kind == 'owner' and not can_owner:
        raise PermissionDenied

    can_export = request.user.has_perm('core.export_pii')
    can_save = request.user.has_perm('core.add_audiencesegment')
    can_notify = kind == 'visitor' and request.user.has_perm('core.send_marketing_push')

    error = None
    counts = None
    preview_rows = []
    exclude = exclude_opted_out(request.GET)
    try:
        filters = segments.parse_filters(kind, request.GET)
        if request.GET:
            # The live COUNT is safe for anyone who can see this tab at
            # all — a number identifies nobody. Individual name+phone rows
            # are exactly the thing core.export_pii exists to gate, so
            # they must never render for a viewer who lacks it. Before
            # this check, holding only visitor.view_visitor/api.view_user
            # (which Support already has, per setup_admin_roles.py) was
            # enough to page through 20-row previews and reconstruct an
            # arbitrarily large phone-number list one filter tweak at a
            # time — entirely outside the export_pii gate and the
            # AudienceSegmentExport audit trail this whole feature is
            # built around. The query for preview_rows isn't even run
            # when the viewer can't see the result, not just hidden in
            # the template.
            counts = segments.count_segment(kind, filters, exclude_opted_out=exclude)
            if can_export:
                if kind == 'visitor':
                    preview_qs = segments.visitor_queryset(filters, exclude_opted_out=exclude)
                    preview_rows = list(preview_qs.order_by('id')[:20].values('id', 'full_name', 'phone_number'))
                else:
                    preview_rows = list(
                        segments.owner_queryset(filters).order_by('id')[:20].values('id', 'name', 'phone')
                    )
    except segments.SegmentFilterError as exc:
        error = str(exc)
        filters = {}

    from core.models import AudienceSegment
    saved_segments = AudienceSegment.objects.filter(kind=kind).select_related('created_by')[:25]

    query_for_export = request.GET.urlencode()
    context = {
        **site.each_context(request),
        'title': 'ساخت گروه مخاطب',
        'kind': kind,
        'can_visitor': can_visitor,
        'can_owner': can_owner,
        'can_export': can_export,
        'can_save': can_save,
        'can_notify': can_notify,
        'error': error,
        'counts': counts,
        'preview_rows': preview_rows,
        'exclude_opted_out': exclude,
        'saved_segments': saved_segments,
        'business_categories': segments.BUSINESS_CATEGORY_CHOICES,
        'appointment_statuses': segments.APPOINTMENT_STATUS_CHOICES,
        'plans': segments.plan_choices(),
        'query_for_export': query_for_export,
        'export_url': (
            reverse('admin:core_segment_export', current_app=site.name) + f'?{query_for_export}'
            if query_for_export else None
        ),
        'params': request.GET,
    }
    return TemplateResponse(request, 'admin/core/segment_builder.html', context)


def segment_save_view(site, request):
    """Save the current builder filters as a named AudienceSegment.

    POST-only (mutating), gated on the model's own `add_audiencesegment`
    permission — no bespoke permission for "may save a segment definition"
    since a saved filter, unlike an export, carries no PII by itself.
    """
    from core import segments
    from core.models import AudienceSegment

    require_post_perm(request, 'core.add_audiencesegment')

    kind = segment_kind(request)
    name = (request.POST.get('name') or '').strip()
    if not name:
        messages.error(request, 'نام گروه مخاطب الزامی است.')
    else:
        try:
            segments.parse_filters(kind, request.POST)  # validate only; see raw_params() below
        except segments.SegmentFilterError as exc:
            messages.error(request, str(exc))
        else:
            definition = segments.raw_params(kind, request.POST)
            if kind == 'visitor':
                definition['exclude_opted_out'] = exclude_opted_out(request.POST)
            AudienceSegment.objects.create(
                name=name, kind=kind, definition=definition, created_by=request.user,
            )
            messages.success(request, f'گروه مخاطب «{name}» ذخیره شد.')

    return _redirect_to_builder(site, request)


def segment_run_view(site, request, segment_id):
    """Re-run a saved segment: bounce to the builder pre-filled with its
    stored definition. Updates `last_run_at` — the segment itself is never
    mutated otherwise, this is the one field that records "somebody looked
    at this again", not what they saw (the filters always re-evaluate live;
    see core/segments.py's module docstring)."""
    from django.utils import timezone as dj_timezone

    from core.models import AudienceSegment

    segment = get_object_or_404(AudienceSegment, pk=segment_id)
    require_perm(
        request,
        'visitor.view_visitor' if segment.kind == 'visitor' else 'api.view_user',
    )

    segment.last_run_at = dj_timezone.now()
    segment.save(update_fields=['last_run_at'])

    # `definition` already holds the raw, stringified param values
    # (core.segments.raw_params) — the same shape a form submission
    # produces — so this is just handing them back to the same query
    # string the builder always reads, not a separate deserialize path.
    params = dict(segment.definition or {})
    excluded = params.pop('exclude_opted_out', True)
    params['kind'] = segment.kind
    params['exclude_opted_out'] = '1' if excluded else '0'
    base = reverse('admin:core_segment_builder', current_app=site.name)
    return HttpResponseRedirect(f'{base}?{urlencode(params)}')


def segment_export_view(site, request):
    """CSV export of the current filter — the one action in this whole
    phase that hands out raw phone numbers.

    Gate: a dedicated `core.export_pii` permission, not `api.view_user` /
    `visitor.view_visitor`. Those two only mean "may browse this data one
    record at a time in the admin" (Support holds `api.view_user` per
    setup_admin_roles.py, for exactly that reason); none of the four
    existing roles (Superadmin/Moderator/Support/Finance) is actually the
    right fit for "may bulk-export a marketing contact list" — see
    core/models.py's Meta.permissions comment on AudienceSegment. Until a
    human decides who else should hold it, only Superadmin can export
    here, which is intentionally conservative rather than picking the
    closest existing role.

    The audit row (core.models.AudienceSegmentExport) is written *before*
    the response is returned, using a row count computed the same way the
    live-count preview computed it — never from `len()` on a materialised
    CSV — so the count in the log is trustworthy even if the client drops
    the connection while the file streams.
    """
    from core import segments
    from core.models import AudienceSegmentExport

    require_perm(request, 'core.export_pii')

    kind = segment_kind(request)
    exclude = exclude_opted_out(request.GET)
    try:
        filters = segments.parse_filters(kind, request.GET)
        # export_rows (not just parse_filters) has to be inside this try:
        # the low-wallet filter's cache-reachability check
        # (segments._apply_low_wallet_filter) raises the same
        # SegmentFilterError, and it can only be known once the query
        # actually runs — it must fail this export closed with a clean
        # 400, not fall through to an unhandled 500.
        header, rows, row_count = segments.export_rows(kind, filters, exclude_opted_out=exclude)
    except segments.SegmentFilterError as exc:
        return HttpResponse(str(exc), status=400, content_type='text/plain; charset=utf-8')

    export_definition = segments.raw_params(kind, request.GET)
    export_definition['exclude_opted_out'] = exclude
    AudienceSegmentExport.objects.create(
        kind=kind,
        definition=export_definition,
        exported_by=request.user,
        row_count=row_count,
    )

    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(header)
    for row in rows:
        writer.writerow(row)

    # utf-8-sig — same reason accounting/admin.py's CSV export uses it:
    # without the BOM, Excel opens Persian columns as mojibake.
    content = buffer.getvalue().encode('utf-8-sig')
    response = HttpResponse(content, content_type='text/csv; charset=utf-8')
    response['Content-Disposition'] = f'attachment; filename="audience-segment-{kind}-{row_count}.csv"'
    return response


def segment_notify_view(site, request):
    """POST-only: send a promotional push to every visitor matching the
    current filter who has an active device token. Gate: core.send_
    marketing_push — see core.models.MarketingPushLog's Meta.permissions
    comment for why this is its own permission rather than folded into
    visitor.view_visitor.

    Kept alongside the richer campaign_send_view: this one reaches only
    visitors but can target the segment builder's *full* filter set (last
    booking date, appointment status, plan…), whereas the campaign page
    trades those for multi-audience sends and media. Both go through
    core.messaging.send_push_campaign so a campaign sent from either place
    is logged and counted identically."""
    from core import segments
    from core.campaigns import CampaignError, PushCampaign
    from core.messaging import send_push_campaign
    from core.models import MarketingPushLog

    require_post_perm(request, 'core.send_marketing_push')

    exclude = exclude_opted_out(request.POST)

    try:
        campaign = PushCampaign.create(
            title=request.POST.get('notify_title', ''),
            body=request.POST.get('notify_body', ''),
        )
        filters = segments.parse_filters('visitor', request.POST)
        definition = segments.raw_params('visitor', request.POST)
        definition['exclude_opted_out'] = exclude
        log = send_push_campaign(
            campaign, MarketingPushLog.AUDIENCE_VISITOR, filters, definition,
            exclude_opted_out=exclude, actor=request.user,
        )
    except (CampaignError, segments.SegmentFilterError) as exc:
        messages.error(request, str(exc))
    else:
        if log.reachable_count == 0:
            messages.warning(request, 'هیچ مخاطبی با اعلان فعال در این فیلتر یافت نشد.')
        else:
            messages.success(
                request,
                f'از {log.recipient_count} مخاطب واجد شرایط، {log.reachable_count} نفر اعلان '
                f'فعال داشتند؛ ارسال در صف قرار گرفت و نتیجه در گزارش‌ها ثبت می‌شود.',
            )

    return _redirect_to_builder(site, request)
