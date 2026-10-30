"""پرونده مشتری: the global visitor list (or one business' customers) shaped
like the demo — file code, tabs with counts, search by phone/name/file number,
appointment source filter and chip — and a detail page per customer."""

from django.db.models import Count, Max, Q
from django.shortcuts import get_object_or_404

from appointment.models import Appointment
from business.models import Business
from reviews.models import Review
from visitor.models import SmsLog, Visitor, VisitorActivity

from .. import selectors
from ..access import panel_view
from ._common import file_code_id, paginate, render_panel, search_term

DETAIL_ROWS = 50
SOURCE_LABELS = dict(Appointment.SOURCE_CHOICES)
STATUS_LABELS = dict(Appointment.STATUS_CHOICES)


def _scope(request):
    raw = request.GET.get('business')
    if raw and raw.isdigit():
        return get_object_or_404(Business.objects.only('id', 'title', 'category'), pk=raw)
    return None


@panel_view('visitor.view_visitor')
def customers(request):
    business = _scope(request)
    bid = business.pk if business else None
    tab = request.GET.get('tab') if request.GET.get('tab') in dict(selectors.TABS) else 'all'
    source = request.GET.get('source') if request.GET.get('source') in selectors.SOURCES else ''
    q = search_term(request)

    qs = selectors.customers(bid)
    qs = selectors.filter_tab(qs, tab)
    qs = selectors.filter_source(qs, source, bid)
    if q:
        qs = selectors.search(qs, q, file_code_id(q))
    page = paginate(request, qs.order_by('-last_at', '-id'))
    counts = selectors.tab_counts(bid)

    return render_panel(request, 'staffpanel/customers.html', 'customers', {
        'business': business, 'page_obj': page, 'q': q, 'tab': tab, 'source': source,
        'tabs': [(k, label, counts[k]) for k, label in selectors.TABS],
        'sources': [(code, SOURCE_LABELS[code]) for code in selectors.SOURCES],
        'source_labels': SOURCE_LABELS, 'status_labels': STATUS_LABELS,
    }, title=f'مشتریان {business.title}' if business else 'پرونده مشتریان')


@panel_view('visitor.view_visitor')
def customer_detail(request, pk):
    visitor = get_object_or_404(Visitor.objects.select_related('user'), pk=pk)
    business = _scope(request)
    user = request.user
    can = {
        'appointments': user.has_perm('appointment.view_appointment'),
        'reviews': user.has_perm('reviews.view_review'),
        'sms': user.has_perm('visitor.view_smslog'),
        'activity': user.has_perm('visitor.view_visitoractivity'),
    }
    ctx = {'visitor': visitor, 'business': business, 'can': can, 'source_labels': SOURCE_LABELS,
           'status_labels': STATUS_LABELS}
    scoped = {'business': business} if business else {}

    if can['appointments']:
        appts = Appointment.objects.filter(visitor=visitor, **scoped)
        ctx['stats'] = appts.aggregate(
            total=Count('id'),
            completed=Count('id', filter=Q(status='COMPLETED')),
            no_show=Count('id', filter=Q(status='NO_SHOW')),
            cancelled=Count('id', filter=Q(status='CANCELLED')),
            web=Count('id', filter=Q(source='WEB')),
            app=Count('id', filter=Q(source='APP')),
            owner=Count('id', filter=Q(source='OWNER')),
        )
        ctx['appointments'] = list(
            appts.select_related('business').order_by('-appointment_date', '-id')[:DETAIL_ROWS])
        ctx['payments'] = list(
            # Anything that shows money moved: a verified deposit, a gateway
            # reference or a declared deposit method (`__gt=''` skips NULL and '';
            # the method defaults to 'NONE').
            appts.filter(Q(deposit_verified_at__isnull=False) | Q(payment_reference__gt='')
                         | (Q(deposit_payment_method__gt='') & ~Q(deposit_payment_method='NONE')))
            .select_related('business').order_by('-appointment_date', '-id')[:DETAIL_ROWS])
        ctx['by_business'] = list(
            Appointment.objects.filter(visitor=visitor).values('business_id', 'business__title')
            .annotate(n=Count('id'), last=Max('appointment_date')).order_by('-last')[:20])
    if can['reviews']:
        ctx['reviews'] = list(Review.objects.filter(visitor=visitor, **scoped)
                              .select_related('business').order_by('-created_at')[:DETAIL_ROWS])
    if can['sms']:
        ctx['sms'] = list(SmsLog.objects.filter(visitor=visitor, **scoped)
                          .select_related('business').order_by('-sent_at')[:DETAIL_ROWS])
    if can['activity']:
        ctx['activity'] = list(VisitorActivity.objects.filter(visitor=visitor, **scoped)
                               .select_related('business').order_by('-created_at')[:20])
    return render_panel(request, 'staffpanel/customer_detail.html', 'customers', ctx,
                        title=visitor.full_name or 'مشتری')
