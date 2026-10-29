"""
appointment/views/range_visitors_view.py

Distinct customers who had an appointment in a date window, for the owner
app's "مشتری‌ها" tab (the third tab on the نوبت‌ها screen; formerly
"گزارش بازه‌ای").

GET /api/appointment/range-visitors/?business_id=<id>&date_from=<ms>&date_to=<ms>[&ordering][&page][&page_size]
→ {
      "date_from": <ms>, "date_to": <ms>,   // the range actually measured
      "clamped": <bool>,
      "count": <int>, "total_pages": <int>, "current_page": <int>,
      "next": <int|null>, "previous": <int|null>,
      "results": [
          {"visitor_id": <int>, "full_name": <str>, "phone_number": <str>,
           "appointment_count": <int>, "last_visit": <ms>}, ...
      ]
  }

Unlike the appointment list, this answers "who came", not "what happened" —
one row per person with how many times they came. Paging
through appointment_list and grouping client-side would only ever group the
page in hand, the same trap service_breakdown_view.py was written to avoid.

── date_from / date_to ───────────────────────────────────────────────────
Unix epoch milliseconds, with the same day semantics as appointment_list
(appointment/views/appointment_query_view.py): date_from floored to the start
of its day, date_to pushed to the start of the next day so the end date is
inclusive. Both are **required** here, unlike the other range endpoints where
they are independently optional — a customer list with no window is the
unbounded scan this endpoint exists to prevent.

── ordering ─────────────────────────────────────────────────────────────
`visits` (default): most appointments first — the regulars.
`recent`: latest appointment first — who was in most recently.
Anything else falls back to `visits`. Sorted here, not in the app, because
the list is paged: re-sorting the rows in hand would only order one page.

── the 31-day cap ────────────────────────────────────────────────────────
A window longer than [MAX_RANGE_DAYS] is clamped by moving date_from up to
`date_to - (MAX_RANGE_DAYS - 1) days`, and the response echoes back the range
it actually measured along with `clamped: true`. Clamped rather than 400 so a
caller with a stale or hand-edited range still gets usable data, and echoed
so the client can correct its own picker instead of labelling the result with
a range that was never queried.

The app caps its picker too. This is the second layer: the owner panel also
runs in a browser, where the query string is whatever the caller types.
"""

from datetime import timedelta

from asgiref.sync import sync_to_async
from django.core.paginator import Paginator
from django.db.models import Count, Max
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response
from adrf.views import APIView

from appointment.models import Appointment
from common.params import business_id_param, positive_int
from common.responses import APIResponse
from common.timeutils import local_date_midnight

MAX_RANGE_DAYS = 31
MAX_PAGE_SIZE = 100
ORDERINGS = {
    'visits': ('-appointment_count', '-last_visit'),
    'recent': ('-last_visit', '-appointment_count'),
}


def _parse_ms(value: str):
    """Epoch-ms string → aware datetime at the start of that local day.

    The day is read in the server's zone (Asia/Tehran), not UTC. The app
    sends local midnight, which in UTC is still the previous date — reading
    it as a UTC date moved both ends of the window back a day, so the
    customer list never included the last day asked for (with the default
    windows, that was always today).
    """
    return local_date_midnight(value)


class RangeVisitorsView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request: Request) -> Response:
        business_id, error = business_id_param(request)
        if error:
            return error

        raw_from = request.query_params.get('date_from')
        raw_to = request.query_params.get('date_to')
        if not raw_from or not raw_to:
            return APIResponse.error(
                'پارامترهای date_from و date_to الزامی هستند',
                code=status.HTTP_400_BAD_REQUEST,
            )

        try:
            start = _parse_ms(raw_from)
        except (ValueError, OSError, OverflowError):
            return APIResponse.error('تاریخ شروع (date_from) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد', code=status.HTTP_400_BAD_REQUEST)
        try:
            end_day = _parse_ms(raw_to)
        except (ValueError, OSError, OverflowError):
            return APIResponse.error('تاریخ پایان (date_to) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد', code=status.HTTP_400_BAD_REQUEST)

        if end_day < start:
            return APIResponse.error(
                'تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد',
                code=status.HTTP_400_BAD_REQUEST,
            )

        # Clamp from the *end* of the window: the recent side is the one the
        # owner is looking at, so a too-wide range loses its oldest days.
        clamped = False
        earliest_allowed = end_day - timedelta(days=MAX_RANGE_DAYS - 1)
        if start < earliest_allowed:
            start = earliest_allowed
            clamped = True

        page = positive_int(request.query_params.get('page'), default=1)
        page_size = min(positive_int(request.query_params.get('page_size'), default=20), MAX_PAGE_SIZE)
        ordering = ORDERINGS.get(request.query_params.get('ordering'), ORDERINGS['visits'])

        data = await self._range_visitors(
            request.user, business_id, start, end_day, page, page_size, ordering
        )
        data['date_from'] = int(start.timestamp() * 1000)
        data['date_to'] = int(end_day.timestamp() * 1000)
        data['clamped'] = clamped
        return APIResponse.success(data=data, message='لیست مشتریان بازه دریافت شد')

    @sync_to_async
    def _range_visitors(self, user, business_id: int, start, end_day, page: int, page_size: int,
                        ordering: tuple) -> dict:
        # Scoped to business__user=user, same as daily_counts_view.py and
        # service_breakdown_view.py — a business_id the caller does not own
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
