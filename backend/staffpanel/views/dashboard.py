"""داشبورد: the cached admin KPI payload (core.dashboard) drawn with Nava
charts, plus today's live queues."""

import logging

from django.db.models import Count, Q
from django.utils import timezone

from ..access import panel_view
from ._common import render_panel

logger = logging.getLogger('staffpanel')


def _series(labels, *series):
    return {'labels': labels, 'series': [
        {'name': name, 'color': color, 'values': values} for name, color, values in series
    ]}


def _queues(user):
    """Live, uncached counts — these are what staff act on right now."""
    from appointment.models import Appointment
    from business.models import Business, ContentReport
    from marketing.models import MarketingLead
    from showcase.models import Certificate, ModerationStatus, PortfolioItem

    today = timezone.localdate()
    out = []
    if user.has_perm('business.view_business'):
        out.append(('صف تأیید کسب‌وکار', 'check-circle', 'staffpanel:approvals',
                    Business.objects.filter(moderation_status=Business.MODERATION_PENDING).count(), 'accent'))
    if user.has_perm('showcase.view_portfolioitem'):
        out.append(('نمونه‌کار و مدرک', 'image', 'staffpanel:showcase',
                    PortfolioItem.objects.filter(status=ModerationStatus.PENDING).count()
                    + Certificate.objects.filter(status=ModerationStatus.PENDING).count(), 'info'))
    if user.has_perm('business.view_contentreport'):
        out.append(('گزارش تخلف جدید', 'alert', 'staffpanel:reports',
                    ContentReport.objects.filter(status=ContentReport.STATUS_NEW).count(), 'danger'))
    if user.has_perm('marketing.view_marketinglead'):
        out.append(('تماس امروز', 'phone', 'staffpanel:marketing',
                    MarketingLead.objects.filter(next_follow_up__lte=today)
                    .exclude(status__in=MarketingLead.CLOSED_STATUSES).count(), 'warning'))
    appointments = None
    if user.has_perm('appointment.view_appointment'):
        appointments = Appointment.objects.filter(appointment_date__date=today).aggregate(
            total=Count('id'),
            done=Count('id', filter=Q(status='COMPLETED')),
            cancelled=Count('id', filter=Q(status__in=('CANCELLED', 'NO_SHOW'))),
            web=Count('id', filter=Q(source='WEB')),
            app=Count('id', filter=Q(source='APP')),
            owner=Count('id', filter=Q(source='OWNER')),
        )
    return out, appointments


@panel_view()
def dashboard(request):
    from core.dashboard import cache as dashboard_cache, panels

    user = request.user
    can = panels.permissions(user)
    payload = None
    try:
        payload, _cached = dashboard_cache.get_payload(force_refresh=request.GET.get('refresh') == '1')
    except Exception:
        # Same fallback as the admin index: a metrics failure must not cost
        # staff the panel's front door.
        logger.exception('panel dashboard metrics failed')

    queues, today_appts = _queues(user)
    kpis, charts = [], {}
    if payload:
        counts = payload['counts']
        if can['users']:
            kpis.append(('کاربران جدید', 'user-plus', counts['users'], 'chart-1'))
        if can['businesses']:
            kpis.append(('کسب‌وکار جدید', 'building', counts['businesses'], 'chart-2'))
        if can['appointments']:
            kpis.append(('نوبت ثبت‌شده', 'calendar', counts['appointments'], 'chart-4'))
        if can['sms']:
            kpis.append(('پیامک ارسالی', 'message', counts['sms'], 'chart-5'))
        c = payload['charts']
        growth = c['growth']
        charts['growth'] = {'month': _series(
            c['labels'],
            ('کاربر', 'chart-1', growth['users']),
            ('کسب‌وکار', 'chart-2', growth['businesses']),
        )}
        charts['growth']['week'] = _series(
            c['labels'][-7:],
            ('کاربر', 'chart-1', growth['users'][-7:]),
            ('کسب‌وکار', 'chart-2', growth['businesses'][-7:]),
        )
        if can['finance']:
            rev = c['revenue']
            charts['revenue'] = {
                'month': _series(c['labels'], ('اشتراک', 'chart-1', rev['subscription']), ('بسته', 'chart-2', rev['addon'])),
                'week': _series(c['labels'][-7:], ('اشتراک', 'chart-1', rev['subscription'][-7:]), ('بسته', 'chart-2', rev['addon'][-7:])),
            }
    if today_appts and today_appts['total']:
        charts['sources'] = {'segments': [
            {'label': 'سایت', 'value': today_appts['web'], 'color': 'chart-1'},
            {'label': 'اپ', 'value': today_appts['app'], 'color': 'chart-2'},
            {'label': 'مالک', 'value': today_appts['owner'], 'color': 'chart-4'},
        ], 'centerLabel': 'نوبت امروز'}

    return render_panel(request, 'staffpanel/dashboard.html', 'dashboard', {
        'payload': payload,
        'can': can,
        'kpis': kpis,
        'queues': queues,
        'today_appts': today_appts,
        'charts': charts,
        'standing': payload and {
            'active_subscriptions': payload.get('active_subscriptions'),
            'pending_moderation': payload.get('pending_moderation'),
        },
    }, title='داشبورد')
