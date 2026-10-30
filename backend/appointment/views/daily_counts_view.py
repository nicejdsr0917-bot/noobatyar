"""
appointment/views/daily_counts_view.py

Per-day appointment counts for the owner's home chart.

GET /api/appointment/daily-counts/?business_id=<id>&days=7&days_ahead=0
→ [{ "date": "YYYY-MM-DD", "count": <int> }, ...]  (oldest → newest, gap-filled)

`days` counts backwards from today (inclusive); `days_ahead` extends the window
into the future. The home screen's month card needs both sides — an owner
looking at "next month" is asking about bookings that have not happened yet —
while the 7-day trend chart keeps calling this with `days` alone.
"""

from asgiref.sync import sync_to_async
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response
from adrf.views import APIView

from appointment import selectors_stats
from common.params import business_id_param
from common.responses import APIResponse

# Per side, so a full window can reach ~6 months. Raised from 60 because the
# home month card asks for a whole Jalali month either side of today, and on
# the last day of a 31-day month that alone is 62 days back.
MAX_DAYS = 93


class DailyCountsView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request: Request) -> Response:
        business_id, error = business_id_param(request)
        if error:
            return error

        try:
            days = int(request.query_params.get('days', 7))
        except ValueError:
            days = 7
        days = max(1, min(days, MAX_DAYS))

        try:
            days_ahead = int(request.query_params.get('days_ahead', 0))
        except ValueError:
            days_ahead = 0
        days_ahead = max(0, min(days_ahead, MAX_DAYS))

        data = await self._daily_counts(request.user, business_id, days, days_ahead)
        return APIResponse.success(data=data, message='آمار روزانه نوبت‌ها دریافت شد')

    @sync_to_async
    def _daily_counts(self, user, business_id: int, days: int, days_ahead: int = 0):
        return selectors_stats.daily_counts(user, business_id, days, days_ahead)
