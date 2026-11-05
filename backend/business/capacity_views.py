"""Owner settings for how a time slot is booked: how many clients it takes at
once (``slot_capacity``) and how long a client's booking is held while they
pay the deposit (``hold_minutes``). See appointment.occupancy."""
from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import IsAuthenticated

from api.responses import APIResponse

from .models import Business

# How many days ahead of today the slot caches are cleared on a change.
SLOT_CACHE_DAYS = 62


def _data(business):
    return {
        'slot_capacity': business.slot_capacity,
        'hold_minutes': business.hold_minutes,
        'min_slot_capacity': Business.MIN_SLOT_CAPACITY,
        'max_slot_capacity': Business.MAX_SLOT_CAPACITY,
        'min_hold_minutes': Business.MIN_HOLD_MINUTES,
        'max_hold_minutes': Business.MAX_HOLD_MINUTES,
    }


def _int_in(value, low, high):
    try:
        number = int(value)
    except (TypeError, ValueError):
        return None
    return number if low <= number <= high else None


class OwnerBookingCapacityView(APIView):
    """GET / PUT /api/business/<id>/booking-capacity/ — {"slot_capacity", "hold_minutes"}
    (either or both on PUT). More than one client per slot is the paid
    capacity-control feature, checked like any other business setting."""
    permission_classes = [IsAuthenticated]

    async def get(self, request, business_id):
        business = await Business.objects.filter(pk=business_id, user=request.user).afirst()
        if business is None:
            return APIResponse.error('کسب و کار یافت نشد', code=404)
        return APIResponse.success(data=_data(business))

    async def put(self, request, business_id):
        changes = {}
        if 'slot_capacity' in request.data:
            value = _int_in(request.data.get('slot_capacity'), Business.MIN_SLOT_CAPACITY, Business.MAX_SLOT_CAPACITY)
            if value is None:
                return APIResponse.error('ظرفیت هم‌زمان باید بین ۱ تا ۵۰۰ نفر باشد', code=400)
            changes['slot_capacity'] = value
        if 'hold_minutes' in request.data:
            value = _int_in(request.data.get('hold_minutes'), Business.MIN_HOLD_MINUTES, Business.MAX_HOLD_MINUTES)
            if value is None:
                return APIResponse.error('زمان نگه‌داشتن نوبت باید بین ۲ تا ۱۵ دقیقه باشد', code=400)
            changes['hold_minutes'] = value
        if not changes:
            return APIResponse.error('چیزی برای ذخیره نیست', code=400)

        def _save():
            from accounting.permissions import validate_business_settings
            from datetime import timedelta

            from django.utils import timezone

            from appointment.cache_utils import invalidate_slots_cache
            from . import cache as public_cache

            business = Business.objects.filter(pk=business_id, user=request.user).first()
            if business is None:
                return None, 'کسب و کار یافت نشد'
            error = validate_business_settings(request.user, changes, business)
            if error:
                return None, error
            for field, value in changes.items():
                setattr(business, field, value)
            business.save(update_fields=list(changes))
            # Free / full slots change with the capacity: drop the cached days
            # a client can book (the cache is per business and day).
            today = timezone.localdate()
            for offset in range(SLOT_CACHE_DAYS):
                invalidate_slots_cache(business.pk, today + timedelta(days=offset))
            public_cache.bump_business(business.pk)
            return business, None

        business, error = await sync_to_async(_save)()
        if business is None:
            return APIResponse.error(error, code=404 if error == 'کسب و کار یافت نشد' else 403)
        return APIResponse.success(data=_data(business), message='ذخیره شد')
