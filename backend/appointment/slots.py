"""Slot arithmetic shared by the two public availability endpoints.

* ``available_slots_view`` (client app) returns the *full grid* of the day with
  a per-slot status: :func:`day_bounds`, :func:`busy_intervals`,
  :func:`build_day_slots`.
* ``views/public_slots_view`` returns only the *occupied* appointments of the
  day: :func:`day_bounds` only.

The two endpoints differ deliberately and each keeps its exact behaviour
(clients depend on both shapes):

=====================  ============================  ============================
                       available_slots_view          public_slots_view
=====================  ============================  ============================
day window             ``[00:00, next 00:00)``       ``[00:00, 23:59:59]`` closed
                       (start strictly ``>``         (``appointment_date__range``)
                       ``00:00 - 8h`` to catch
                       overnight spill)
blocking filter        ``blocking_q()`` (clock read  ``blocking_q(now)`` with the
                       at query time)                request's ``now``
=====================  ============================  ============================
"""

from datetime import datetime, timedelta

from appointment.models import Appointment
from appointment.occupancy import blocking_q
from appointment.working_hours import LOCAL_TZ, working_window

# Longest service the scan reaches back for, to catch appointments that start
# before midnight and spill into the day.
LOOKBACK_HOURS = 8


def day_bounds(date):
    """``(start, end)`` of the local calendar ``date``: midnight and the next midnight (exclusive)."""
    start = datetime(date.year, date.month, date.day, 0, 0, tzinfo=LOCAL_TZ)
    return start, start + timedelta(days=1)


def busy_intervals(business, date):
    """Sync: ``[(start, end), ...]`` of blocking appointments touching ``date``.

    Any blocking appointment that overlaps a slot marks it booked, not just one
    that starts exactly at the slot's start (owner bookings and longer services
    can start off-grid or span several slots).
    """
    day_start, day_end = day_bounds(date)
    default_duration = business.default_service_duration
    busy = []
    for appt in Appointment.objects.filter(
        blocking_q(),
        business=business,
        appointment_date__gt=day_start - timedelta(hours=LOOKBACK_HOURS),
        appointment_date__lt=day_end,
    ):
        busy.append((
            appt.appointment_date,
            appt.appointment_date + timedelta(minutes=appt.service_duration or default_duration),
        ))
    return busy


def build_day_slots(business, date, busy, now):
    """The day's slot grid as response dicts.

    Slots start at the business's opening time and repeat every
    ``default_service_duration`` minutes while a whole slot still fits before
    closing. ``busy`` is a list of ``(start, end)``; ``now`` an aware datetime.
    """
    duration = business.default_service_duration  # minutes
    current, day_end = working_window(business, date)

    grid = []
    while current + timedelta(minutes=duration) <= day_end:
        grid.append(current)
        current += timedelta(minutes=duration)

    slots = []
    for slot in grid:
        slot_end = slot + timedelta(minutes=duration)
        is_booked = any(b_start < slot_end and slot < b_end for b_start, b_end in busy)
        is_past = slot < now
        slots.append({
            'time': slot.strftime('%H:%M'),
            'timestamp': int(slot.timestamp() * 1000),
            'available': not is_booked and not is_past,
            'status': 'PAST' if is_past else ('BOOKED' if is_booked else 'AVAILABLE'),
        })
    return slots


# How far ahead the list card's «اولین نوبت خالی» looks.
FIRST_FREE_DAYS = 7


def first_free_window(now):
    """``(start, end)`` of the appointments that can block a slot in the
    :data:`FIRST_FREE_DAYS` window (with the overnight lookback)."""
    start, _ = day_bounds(now.astimezone(LOCAL_TZ).date())
    return start - timedelta(hours=LOOKBACK_HOURS), start + timedelta(days=FIRST_FREE_DAYS)


def first_free_slot_ms(business, now, busy=None):
    """Sync: epoch ms of the earliest bookable slot in the next
    :data:`FIRST_FREE_DAYS` local days, or ``None``. Same grid and occupancy
    rules as the available-slots endpoint, so the card never promises a slot
    the booking screen would not offer.

    ``busy`` — every blocking ``(start, end)`` in :func:`first_free_window`,
    when the caller batch-loaded them; otherwise each day is queried."""
    today = now.astimezone(LOCAL_TZ).date()
    closed = set(business.closed_weekdays or [])
    for offset in range(FIRST_FREE_DAYS):
        date = today + timedelta(days=offset)
        if date.weekday() in closed:
            continue
        day_busy = busy if busy is not None else busy_intervals(business, date)
        for slot in build_day_slots(business, date, day_busy, now):
            if slot['available']:
                return slot['timestamp']
    return None
