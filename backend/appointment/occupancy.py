"""
appointment/occupancy.py

Single source of truth for "does this appointment occupy its slot?".

Before this module the two slot endpoints disagreed:
  * available_slots_view counted only WAITING / IN_PROGRESS / PENDING_APPROVAL
    — so LOCKED, PENDING_VERIFICATION and even CONFIRMED bookings left the slot
    showing as free.
  * public_slots_view counted only CONFIRMED plus LOCKED rows whose
    ``expires_at`` was still in the future — and since nothing ever set
    ``expires_at``, LOCKED never matched there either.

Both now build their queryset from :func:`blocking_q`, so availability is
consistent across endpoints and with the conflict check performed at booking
time.
"""

from datetime import timedelta

from django.db.models import Q
from django.utils import timezone

# How long a client may hold a slot while completing payment, when the
# business has not set its own (Business.hold_minutes).
LOCK_TTL_MINUTES = 5

# Once the client is sent to the online gateway the hold lasts at least this
# long, so a payment made there does not come back to a slot given away.
GATEWAY_HOLD_MINUTES = 10

# Statuses that hold a slot unconditionally: the booking is live, whether it is
# waiting on the owner, on payment verification, or already confirmed/running.
BLOCKING_STATUSES = (
    'PENDING_APPROVAL',
    'PENDING_VERIFICATION',
    'WAITING',
    'CONFIRMED',
    'IN_PROGRESS',
)


def lock_expiry(now=None, minutes=None):
    """Expiry timestamp for a lock created at ``now`` (default: current time),
    ``minutes`` long (default: :data:`LOCK_TTL_MINUTES`)."""
    return (now or timezone.now()) + timedelta(minutes=minutes or LOCK_TTL_MINUTES)


def peak_concurrency(intervals, start, end):
    """
    Most of ``intervals`` (``(s, e)`` pairs) running at the same moment inside
    ``[start, end)``. Two bookings that only follow each other (one ends as the
    next starts) never count together.
    """
    events = []
    for s, e in intervals:
        s, e = max(s, start), min(e, end)
        if s < e:
            events.append((s, 1))
            events.append((e, -1))
    # Ends before starts at the same instant: back-to-back is not overlap.
    events.sort(key=lambda ev: (ev[0], ev[1]))
    peak = current = 0
    for _, delta in events:
        current += delta
        peak = max(peak, current)
    return peak


def full_intervals(intervals, capacity):
    """
    The stretches of time where at least ``capacity`` of ``intervals`` run at
    once, as merged ``(start, end)`` pairs: what a client sees as taken.
    """
    events = []
    for s, e in intervals:
        if s < e:
            events.append((s, 1))
            events.append((e, -1))
    events.sort(key=lambda ev: (ev[0], ev[1]))
    full, current, opened = [], 0, None
    for at, delta in events:
        before = current
        current += delta
        if before < capacity <= current:
            opened = at
        elif current < capacity <= before and opened is not None:
            if full and full[-1][1] == opened:
                full[-1] = (full[-1][0], at)
            elif opened < at:
                full.append((opened, at))
            opened = None
    return full


def blocking_q(now=None):
    """
    Q object matching appointments that currently occupy their slot.

    That is every :data:`BLOCKING_STATUSES` row, plus LOCKED rows whose payment
    window has not run out yet. An expired LOCKED row matches nothing, which is
    what frees its slot again without needing a cleanup job.
    """
    now = now or timezone.now()
    return Q(status__in=BLOCKING_STATUSES) | Q(
        status='LOCKED', expires_at__gt=now
    )


def overlapping_appointments(business_id, start, duration_minutes, now=None):
    """
    Queryset of slot-occupying appointments that overlap
    ``[start, start + duration_minutes)`` for ``business_id``.

    Overlap is computed in Python-friendly bounds rather than with a duration
    expression: candidates are limited to a window wide enough to contain any
    appointment that could reach ``start``, then filtered precisely by the
    caller-visible end time.
    """
    from .models import Appointment

    now = now or timezone.now()
    end = start + timedelta(minutes=duration_minutes)

    # A stored appointment can only overlap if it starts before our end; we
    # bound the scan on the left by the longest allowed service (8h, enforced by
    # AppointmentCreateSerializer.validate_service_duration).
    window_start = start - timedelta(hours=8)

    candidates = Appointment.objects.filter(
        blocking_q(now),
        business_id=business_id,
        appointment_date__gt=window_start,
        appointment_date__lt=end,
    )

    return candidates


def refund_quota(appointment, owner_id):
    """
    Close out the credit of a booking that is cancelled, deleted or whose
    payment hold ran out. The credit is NOT given back: the booking already
    cost Noobatyar its messages (confirmation, reminders), and handing the
    credit back made every cancellation free for the owner and a loss for
    Noobatyar. Product decision, 2026-11-05.

    Still clears ``quota_source`` so the row reads as settled. Returns True
    when there was a source to clear, so callers keep persisting the field.
    """
    if not appointment.quota_source or owner_id is None:
        return False
    appointment.quota_source = ''
    return True


def find_conflict(business_id, start, duration_minutes, exclude_id=None, now=None, capacity=None):
    """
    Return an appointment that leaves no room in the requested window, or None.

    With the business's ``slot_capacity`` at 1 (the default) that is the first
    overlapping appointment. With more, the window is full only when that many
    bookings already run at the same moment inside it (see
    :func:`peak_concurrency`); the returned row is one of them. ``capacity``
    defaults to the business's own.

    The precise end-time comparison happens here (in Python) because
    ``service_duration`` may be null and then falls back to the business
    default, which is awkward to express in SQL.
    """
    from datetime import timedelta as _timedelta

    if capacity is None:
        from business.models import Business
        capacity = Business.objects.filter(pk=business_id).values_list('slot_capacity', flat=True).first() or 1

    end = start + _timedelta(minutes=duration_minutes)
    qs = overlapping_appointments(business_id, start, duration_minutes, now=now)
    if exclude_id is not None:
        qs = qs.exclude(id=exclude_id)

    overlapping = []
    for appt in qs.select_related('business').only(
        'id', 'appointment_date', 'service_duration', 'status', 'business__default_service_duration'
    ):
        appt_duration = appt.service_duration or appt.business.default_service_duration
        appt_end = appt.appointment_date + _timedelta(minutes=appt_duration)
        if appt.appointment_date < end and start < appt_end:
            if capacity <= 1:
                return appt
            overlapping.append((appt, appt.appointment_date, appt_end))

    if overlapping and peak_concurrency([(s, e) for _, s, e in overlapping], start, end) >= capacity:
        return overlapping[0][0]
    return None
