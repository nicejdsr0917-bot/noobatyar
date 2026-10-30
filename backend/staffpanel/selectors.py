"""Read queries for the customer file («پرونده مشتری»).

Visitor is global (one row per phone number) and reaches a business only
through Appointment, so "customers of business X" is an Exists() over
appointments — same reasoning as core.detail_views.business_customers_queryset,
but with the per-row facts the demo-style list needs (last source, last
business, counts) computed in the same query, never per row.
"""

from datetime import timedelta

from django.db.models import Count, Exists, OuterRef, Q, Subquery
from django.utils import timezone

from appointment.models import Appointment
from visitor.models import Visitor

#: Statuses that mean "this appointment is still going to happen".
ACTIVE_STATUSES = ('PENDING_VERIFICATION', 'PENDING_APPROVAL', 'CONFIRMED', 'WAITING', 'IN_PROGRESS')
NEW_DAYS = 30

#: The demo's three tabs (ورودی‌ها / در دست / انجام‌شده) mapped onto visitors.
TABS = (
    ('all', 'همه'),
    ('new', 'ورودی‌ها'),
    ('active', 'در دست'),
    ('done', 'انجام‌شده'),
)
SOURCES = tuple(code for code, _ in Appointment.SOURCE_CHOICES if code != Appointment.SOURCE_UNKNOWN)


def _appointments(business_id=None):
    qs = Appointment.objects.filter(visitor=OuterRef('pk'))
    return qs.filter(business_id=business_id) if business_id else qs


def customers(business_id=None, now=None):
    """Annotated visitor queryset, optionally scoped to one business."""
    now = now or timezone.now()
    appts = _appointments(business_id)
    last = appts.order_by('-appointment_date', '-id')
    scope = Q(appointments__business_id=business_id) if business_id else Q()

    qs = Visitor.objects.all()
    if business_id:
        qs = qs.filter(Exists(appts))
    return qs.annotate(
        appointment_count=Count('appointments', filter=scope),
        completed_count=Count('appointments', filter=scope & Q(appointments__status='COMPLETED')),
        last_at=Subquery(last.values('appointment_date')[:1]),
        last_source=Subquery(last.values('source')[:1]),
        last_status=Subquery(last.values('status')[:1]),
        last_business=Subquery(last.values('business__title')[:1]),
        has_active=Exists(appts.filter(appointment_date__gte=now, status__in=ACTIVE_STATUSES)),
        has_done=Exists(appts.filter(status='COMPLETED')),
    )


def filter_tab(qs, tab, now=None):
    now = now or timezone.now()
    if tab == 'new':
        return qs.filter(created_at__gte=now - timedelta(days=NEW_DAYS))
    if tab == 'active':
        return qs.filter(has_active=True)
    if tab == 'done':
        return qs.filter(has_done=True, has_active=False)
    return qs


def tab_counts(business_id=None, now=None):
    """All four tab counts in one query."""
    now = now or timezone.now()
    appts = _appointments(business_id)
    qs = Visitor.objects.all()
    if business_id:
        qs = qs.filter(Exists(appts))
    qs = qs.annotate(
        _active=Exists(appts.filter(appointment_date__gte=now, status__in=ACTIVE_STATUSES)),
        _done=Exists(appts.filter(status='COMPLETED')),
    )
    return qs.aggregate(
        all=Count('id'),
        new=Count('id', filter=Q(created_at__gte=now - timedelta(days=NEW_DAYS))),
        active=Count('id', filter=Q(_active=True)),
        done=Count('id', filter=Q(_done=True, _active=False)),
    )


def filter_source(qs, source, business_id=None):
    if source in SOURCES:
        return qs.filter(Exists(_appointments(business_id).filter(source=source)))
    return qs


def search(qs, term, file_id=None):
    if file_id:
        return qs.filter(pk=file_id)
    if term.isdigit():
        return qs.filter(phone_number__contains=term)
    return qs.filter(Q(full_name__icontains=term) | Q(phone_number__contains=term))
