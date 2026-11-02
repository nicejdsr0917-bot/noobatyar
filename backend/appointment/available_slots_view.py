from adrf.views import APIView
from asgiref.sync import sync_to_async
from django.core.cache import cache
from rest_framework.permissions import AllowAny
from rest_framework.throttling import ScopedRateThrottle
from common.responses import APIResponse
from business.models import Business
from .cache_utils import available_slots_key, SLOT_CACHE_TTL
from .slots import build_day_slots, busy_intervals
from datetime import datetime, timezone
import logging

logger = logging.getLogger(__name__)


class AvailableSlotsView(APIView):
    """
    Returns available time slots for a business on a given date.
    GET /api/client/appointments/{business_id}/available-slots/?date=YYYY-MM-DD
    """
    permission_classes = [AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = 'public_slots'

    async def get(self, request, business_id):
        date_str = request.query_params.get('date', '')
        if not date_str:
            return APIResponse.error(message="پارامتر date الزامی است (فرمت: YYYY-MM-DD)", code=400)

        try:
            target_date = datetime.strptime(date_str, '%Y-%m-%d').date()
        except ValueError:
            return APIResponse.error(message="فرمت تاریخ نامعتبر است. از YYYY-MM-DD استفاده کنید", code=400)

        try:
            # Same gate as the public business detail endpoint: availability is
            # discovery, so a business that is locked or not editorially
            # approved reads as missing rather than as "exists but hidden".
            #
            # Resolved *before* the cache read on purpose. The cache is keyed on
            # business+date and knows nothing about moderation, so serving a hit
            # first would keep publishing a business's availability for the rest
            # of the TTL after a moderator suspended it. One indexed PK lookup on
            # a cache hit is a cheap price for the gate never going stale; the
            # expensive part (the appointment scan) is still cached.
            business = await Business.objects.aget(Business.public_filter(), id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب‌وکار یافت نشد", code=404)

        # Serve from cache when available (high-traffic public endpoint).
        cache_key = available_slots_key(business_id, target_date)
        cached = await sync_to_async(cache.get)(cache_key)
        if cached is not None:
            return APIResponse.success(data=cached, message="ساعات خالی با موفقیت دریافت شد")

        # A weekly day off (e.g. every Friday) — checked before building any
        # slots so the response says *why* nothing is bookable rather than
        # looking like a fully-booked day. `closed` lets the client tell the
        # two apart instead of guessing from an empty `slots` list.
        if target_date.weekday() in (business.closed_weekdays or []):
            data = {
                'business_id': business_id,
                'date': date_str,
                'duration_minutes': business.default_service_duration,
                'closed': True,
                'slots': [],
            }
            await sync_to_async(cache.set)(cache_key, data, SLOT_CACHE_TTL)
            return APIResponse.success(data=data, message="این روز تعطیل است")

        # Slot grid, busy intervals and per-slot status live in appointment/slots.py.
        # Working hours are wall-clock times in the business's own timezone (see
        # working_hours.py): building them as UTC shifted every slot by the UTC
        # offset, offering slots outside working hours and judging `is_past`
        # against the wrong wall clock.
        duration = business.default_service_duration  # minutes
        busy = await sync_to_async(busy_intervals)(business, target_date)
        slots = build_day_slots(business, target_date, busy, datetime.now(tz=timezone.utc))

        data = {
            'business_id': business_id,
            'date': date_str,
            'duration_minutes': duration,
            'closed': False,
            'slots': slots,
        }
        await sync_to_async(cache.set)(cache_key, data, SLOT_CACHE_TTL)

        return APIResponse.success(data=data, message="ساعات خالی با موفقیت دریافت شد")
