"""«اولین نوبت خالی» on public business rows (client app list card perk).

Added to the serialized payload *after* the list/detail cache: those entries
live for minutes, while a slot can be taken at any moment. The value has its
own 30 s cache per business, dropped by ``invalidate_slots_cache`` whenever an
appointment of that business changes.
"""

from collections import defaultdict
from datetime import datetime, timedelta, timezone

from django.core.cache import cache

from appointment.cache_utils import SLOT_CACHE_TTL, first_free_slot_key
from appointment.models import Appointment
from appointment.occupancy import blocking_q
from appointment.slots import first_free_slot_ms, first_free_window

from .models import Business

# Stored for "no free slot" so the miss is cached too.
_NONE = -1


def attach_first_free_slots(rows):
    """Return copies of ``rows`` (serialized public businesses) with
    ``first_free_slot``: epoch ms, or None when the business is not bookable
    online or has nothing free in the window. Sync (touches the DB)."""
    rows = [dict(r) for r in rows]
    bookable = [r['id'] for r in rows if r.get('is_claimed') and r.get('booking_enabled')]
    values = cache.get_many([first_free_slot_key(i) for i in bookable]) if bookable else {}
    found = {i: values.get(first_free_slot_key(i)) for i in bookable}
    missing = [i for i, v in found.items() if v is None]
    if missing:
        now = datetime.now(tz=timezone.utc)
        # Two queries for the whole page, whatever its size: the businesses,
        # then every blocking appointment of theirs in the window.
        businesses = list(Business.objects.filter(id__in=missing))
        start, end = first_free_window(now)
        busy = defaultdict(list)
        for bid, at, minutes in Appointment.objects.filter(
            blocking_q(), business_id__in=missing,
            appointment_date__gt=start, appointment_date__lt=end,
        ).values_list('business_id', 'appointment_date', 'service_duration'):
            busy[bid].append((at, minutes))
        computed = {}
        for business in businesses:
            intervals = [
                (at, at + timedelta(minutes=minutes or business.default_service_duration))
                for at, minutes in busy[business.id]
            ]
            ms = first_free_slot_ms(business, now, busy=intervals)
            found[business.id] = computed[first_free_slot_key(business.id)] = _NONE if ms is None else ms
        cache.set_many(computed, SLOT_CACHE_TTL)
    for r in rows:
        v = found.get(r['id']) if r.get('is_claimed') and r.get('booking_enabled') else None
        r['first_free_slot'] = None if v in (None, _NONE) else v
    return rows
