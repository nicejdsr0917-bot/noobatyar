"""
appointment/selectors_stats.py

The read-only aggregate queries behind the owner app's stats endpoints. They
are plain synchronous functions (views wrap them in ``sync_to_async``) and take
already-parsed arguments -- query-string parsing and the response envelope stay
in ``appointment/views``.

Every query is scoped to ``business__user=user``: a business id the caller does
not own yields an all-zero / empty result and reveals nothing about whether the
id exists.
"""

from datetime import timedelta

from django.core.paginator import Paginator
from django.db.models import Count, Max, Q
from django.db.models.functions import TruncDate
from django.utils import timezone

from appointment.models import Appointment
from business.models import Business


def split_selected_services(raw: str) -> list:
    """Parse a stored Appointment.selected_services value into ordered,
    de-duplicated names.

    Deliberately not business.serializers.normalize_service_names: that
    function *validates* (raises on a >100-char name or a non-string/list
    input) because it runs on fresh client input before it is stored. This
    runs on data already in the database — AppointmentView.post
    (appointment/views/views.py) writes selected_services straight from
    request.data without ever calling normalize_service_names, so a raising
    parser here would let one odd historical row 500 the whole dashboard
    instead of just quietly landing in no_service_count/removed_service_count.
    """
    if not raw:
        return []
    names = []
    for part in raw.split(','):
        name = part.strip()
        if name and name not in names:
            names.append(name)
    return names


def business_stats(user, business_id: int, date_filter: dict) -> dict:
    """Totals for one business over ``date_filter`` (``appointment_date__gte/lt``)."""
    stats = Appointment.objects.filter(
        business_id=business_id,
        # Scoped to the caller's own businesses, same as daily_counts.
        # Without it any authenticated caller could read another owner's
        # numbers by guessing a business_id.
        business__user=user,
        **date_filter
    ).aggregate(
        total=Count('id'),
        completed=Count('id', filter=Q(status='COMPLETED')),
        no_show=Count('id', filter=Q(status='NO_SHOW')),
        unique_visitors=Count('visitor_id', distinct=True)
    )

    source_counts = {code: 0 for code, _ in Appointment.SOURCE_CHOICES}
    for row in (Appointment.objects.filter(
        business_id=business_id, business__user=user, **date_filter,
    ).values('source').annotate(n=Count('id'))):
        source_counts[row['source']] = row['n']

    return {
        'total_appointments': stats['total'] or 0,
        'completed_appointments': stats['completed'] or 0,
        'no_show_appointments': stats['no_show'] or 0,
        'total_visitors': stats['unique_visitors'] or 0,
        'source_counts': source_counts,
    }


def daily_counts(user, business_id: int, days: int, days_ahead: int = 0):
    """One ``{'date', 'count'}`` row per day, gap-filled, ``days`` back + ``days_ahead`` forward."""
    tz = timezone.get_current_timezone()
    today = timezone.localdate()
    start_date = today - timedelta(days=days - 1)
    # Exclusive upper bound on the query so a forward window does not pull
    # in every future booking the business has ever taken.
    end_date = today + timedelta(days=days_ahead)
    start_dt = timezone.make_aware(
        timezone.datetime(start_date.year, start_date.month, start_date.day, 0, 0), tz
    )
    end_dt = timezone.make_aware(
        timezone.datetime(end_date.year, end_date.month, end_date.day, 0, 0), tz
    ) + timedelta(days=1)

    rows = (
        Appointment.objects
        .filter(
            business_id=business_id, business__user=user,
            appointment_date__gte=start_dt, appointment_date__lt=end_dt,
        )
        .annotate(day=TruncDate('appointment_date'))
        .values('day')
        .annotate(count=Count('id'))
    )
    counts = {row['day'].isoformat(): row['count'] for row in rows if row['day']}

    # Gap-fill every day in the window so the chart always has a point per day.
    result = []
    for i in range(days + days_ahead):
        d = (start_date + timedelta(days=i)).isoformat()
        result.append({'date': d, 'count': counts.get(d, 0)})
    return result


def service_breakdown(user, business_id: int, date_filter: dict) -> dict:
    """Appointment counts per primary service, plus unnamed/removed-service tallies."""
    # Scoped to business__user=user -- a business_id the caller does not own
    # resolves to an all-zero response rather than a 403/404, so this endpoint
    # gives no signal about whether the id exists at all.
    appointments = Appointment.objects.filter(
        business_id=business_id, business__user=user, **date_filter
    ).values_list('selected_services', flat=True)

    catalog = set(
        Business.objects.filter(id=business_id, user=user)
        .values_list('services', flat=True)
        .first() or []
    )

    counts: dict[str, int] = {}
    no_service_count = 0
    removed_service_count = 0
    total = 0

    for raw in appointments:
        total += 1
        names = split_selected_services(raw)
        if not names:
            no_service_count += 1
            continue
        primary = names[0]
        if primary in catalog:
            counts[primary] = counts.get(primary, 0) + 1
        else:
            removed_service_count += 1

    services = [
        {'name': name, 'count': count}
        for name, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    ]

    return {
        'business_id': business_id,
        'total_appointments': total,
        'services': services,
        'no_service_count': no_service_count,
        'removed_service_count': removed_service_count,
    }


def range_visitors(user, business_id: int, start, end_day, page: int, page_size: int,
                   ordering: tuple) -> dict:
    """Distinct customers with an appointment in ``[start, end_day + 1 day)``, one page."""
    # Scoped to business__user=user: a business_id the caller does not own
    # comes back empty rather than 403/404, so the endpoint leaks nothing
    # about whether that id exists.
    rows = (
        Appointment.objects
        .filter(
            business_id=business_id,
            business__user=user,
            appointment_date__gte=start,
            appointment_date__lt=end_day + timedelta(days=1),
        )
        .values('visitor_id', 'visitor__full_name', 'visitor__phone_number')
        .annotate(appointment_count=Count('id'), last_visit=Max('appointment_date'))
        # visitor_id last so rows that tie on both keys still page
        # deterministically — without it the same person can show up on
        # two pages, or on none.
        .order_by(*ordering, 'visitor_id')
    )

    # Paginator over the queryset, not a materialised list: the grouping
    # runs in the database and only one page is fetched, unlike
    # visitor/views.py's list endpoint which pulls every row into Python
    # before slicing.
    paginator = Paginator(rows, page_size)
    page_obj = paginator.get_page(page)

    return {
        'count': paginator.count,
        'total_pages': paginator.num_pages,
        'current_page': page_obj.number,
        'next': page_obj.next_page_number() if page_obj.has_next() else None,
        'previous': page_obj.previous_page_number() if page_obj.has_previous() else None,
        'results': [
            {
                'visitor_id': row['visitor_id'],
                'full_name': row['visitor__full_name'],
                'phone_number': row['visitor__phone_number'],
                'appointment_count': row['appointment_count'],
                'last_visit': int(row['last_visit'].timestamp() * 1000) if row['last_visit'] else None,
            }
            for row in page_obj.object_list
        ],
    }
