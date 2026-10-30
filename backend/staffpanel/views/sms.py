"""پیامک: owner SMS logs plus the history of staff campaigns (marketing push,
subscription-expiry SMS, direct messages to businesses). Sending stays on the
existing admin pages (campaigns, segment builder) — linked, not duplicated."""

from django.db.models import Count, Q

from core.models import AdminMessageLog, ExpirySmsLog, MarketingPushLog
from visitor.models import SmsLog

from ..access import panel_view, require
from ._common import paginate, render_panel, search_term

VIEWS = (
    ('logs', 'پیامک مالک‌ها', 'visitor.view_smslog'),
    ('push', 'کمپین اعلان', 'core.view_marketingpushlog'),
    ('expiry', 'پیامک انقضا', 'core.view_expirysmslog'),
    ('messages', 'پیام به کسب‌وکار', 'core.view_adminmessagelog'),
)
SMS_STATUSES = (('', 'همه'),) + tuple((code, label) for code, label in (
    ('SENT', 'ارسال‌شده'), ('FAILED', 'ناموفق'), ('SKIPPED_QUOTA', 'بدون اعتبار')))


@panel_view('visitor.view_smslog')
def sms(request):
    user = request.user
    allowed = [(k, label) for k, label, perm in VIEWS if user.is_superuser or user.has_perm(perm)]
    view = request.GET.get('view') if request.GET.get('view') in dict(allowed) else 'logs'
    require(user, dict((k, p) for k, _l, p in VIEWS)[view])
    q = search_term(request)
    status = request.GET.get('status') or ''
    counts = None

    if view == 'logs':
        counts = SmsLog.objects.aggregate(
            all=Count('id'), sent=Count('id', filter=Q(status='SENT')),
            failed=Count('id', filter=Q(status='FAILED')), skipped=Count('id', filter=Q(status='SKIPPED_QUOTA')))
        qs = SmsLog.objects.select_related('business', 'visitor')
        if status in dict(SMS_STATUSES) and status:
            qs = qs.filter(status=status)
        business_id = request.GET.get('business')
        if business_id and business_id.isdigit():
            qs = qs.filter(business_id=business_id)
        if q:
            qs = qs.filter(Q(visitor__phone_number__contains=q) | Q(business__title__icontains=q)
                           | Q(message_text__icontains=q))
        qs = qs.order_by('-sent_at', '-id')
    elif view == 'push':
        qs = MarketingPushLog.objects.select_related('sent_by').order_by('-sent_at', '-id')
        if q:
            qs = qs.filter(Q(title__icontains=q) | Q(body__icontains=q))
    elif view == 'expiry':
        qs = ExpirySmsLog.objects.select_related('sent_by').order_by('-sent_at', '-id')
        if q:
            qs = qs.filter(message__icontains=q)
    else:
        qs = AdminMessageLog.objects.select_related('business', 'sent_by').order_by('-sent_at', '-id')
        if q:
            qs = qs.filter(Q(business__title__icontains=q) | Q(body__icontains=q))

    return render_panel(request, 'staffpanel/sms.html', 'sms', {
        'view': view, 'views': allowed, 'page_obj': paginate(request, qs), 'q': q,
        'status': status, 'statuses': SMS_STATUSES, 'counts': counts,
        'can_campaign': user.is_superuser or user.has_perm('core.view_marketingpushlog'),
        'can_segment': user.is_superuser or user.has_perm('core.view_audiencesegment'),
    }, title='پیامک')
