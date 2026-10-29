"""
Appointment status transitions.

Every transition is a conditional UPDATE keyed on the status the caller saw, so
a concurrent callback / cancel / owner action that already moved the row wins
and only one caller ever refunds the credit.
"""

import enum
from dataclasses import dataclass
from typing import Optional, Tuple

from django.db import transaction
from django.db.models import Q
from django.utils import timezone

from appointment.cache_utils import invalidate_slots_cache
from appointment.models import Appointment
from appointment.occupancy import refund_quota
from visitor.activity import record_activity
from visitor.models import VisitorActivity

from .notifications import notify_client_of_decision

# Anything still ahead of the appointment time can be dropped by the client.
CLIENT_CANCELLABLE_STATUSES = (
    'LOCKED',
    'PENDING_APPROVAL',
    'PENDING_VERIFICATION',
    'WAITING',
    'CONFIRMED',
)

# Valid owner-driven status transitions (state machine).
OWNER_STATUS_TRANSITIONS = {
    # Owner creates / confirms manually
    'PENDING_APPROVAL':     ['WAITING', 'CANCELLED'],
    # Client paid (card-to-card) — owner must verify receipt
    'PENDING_VERIFICATION': ['WAITING', 'CANCELLED'],
    # Slot locked (client in checkout) — owner can force-cancel stale locks
    'LOCKED':               ['CANCELLED'],
    # Normal queue flow. WAITING → COMPLETED is allowed because the owner
    # marks a walk-in served straight from the queue, without stepping
    # through CONFIRMED/IN_PROGRESS first.
    'WAITING':    ['IN_PROGRESS', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'],
    'CONFIRMED':  ['WAITING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'],
    'IN_PROGRESS':['COMPLETED', 'CANCELLED', 'NO_SHOW'],
    'COMPLETED':  [],
    'CANCELLED':  [],
    'NO_SHOW':    [],
}


def appointment_expire_lock_if_due(appointment):
    """
    Lazy lock expiry (sync). If ``appointment`` is a LOCKED hold whose payment
    window has passed, close it out as CANCELLED and give the quota back.

    Returns True when the hold is (now) expired. The transition is a
    conditional update, so a concurrent callback/cancel that already moved the
    row wins and only one caller ever refunds the credit.
    """
    if not (appointment.expires_at and appointment.expires_at <= timezone.now()):
        return False

    changed = Appointment.objects.filter(
        pk=appointment.pk, status='LOCKED', expires_at__lte=timezone.now(),
    ).update(status='CANCELLED', quota_source='', updated_at=timezone.now())
    if changed:
        # The client never paid, so the owner keeps the credit.
        refund_quota(appointment, appointment.business.user_id)
        appointment.status = 'CANCELLED'
        invalidate_slots_cache(appointment.business_id, appointment.appointment_date)
    return True


def appointment_cancel_by_client(appointment, previous_status):
    """Sync: CANCELLED only if still in ``previous_status``; refund once."""
    changed = Appointment.objects.filter(
        pk=appointment.pk, status=previous_status
    ).update(
        status='CANCELLED', locked_at=None, expires_at=None,
        quota_source='', updated_at=timezone.now(),
    )
    if not changed:
        return False
    # A slot the owner never served should not keep costing them a credit.
    refund_quota(appointment, appointment.business.user_id)
    appointment.status = 'CANCELLED'
    appointment.locked_at = None
    appointment.expires_at = None
    return True


def appointment_submit_payment(*, appointment, changes):
    """
    Move a still-live LOCKED hold to the status in ``changes`` once the client
    has submitted payment (a single UPDATE, no refund involved).

    The payment window is over once payment is submitted: the slot is now held
    by the status itself, so the lock timestamps are cleared. Conditional +
    field-limited: only a still-live LOCKED hold moves, and concurrent owner
    edits to other columns are not overwritten. Returns False when the hold was
    no longer live; otherwise mirrors the change onto ``appointment``.
    """
    now = timezone.now()
    changed = Appointment.objects.filter(
        Q(expires_at__isnull=True) | Q(expires_at__gt=now),
        pk=appointment.pk, status='LOCKED',
    ).update(locked_at=None, expires_at=None, updated_at=now, **changes)
    if not changed:
        return False
    for field, value in changes.items():
        setattr(appointment, field, value)
    appointment.locked_at = None
    appointment.expires_at = None
    return True


def appointment_store_gateway_track_id(*, appointment, track_id):
    """Remember the Zibal trackId on a still-LOCKED hold; False if it moved on."""
    return bool(Appointment.objects.filter(
        pk=appointment.pk, status='LOCKED'
    ).update(payment_reference=track_id, updated_at=timezone.now()))


class StatusChangeFailure(enum.Enum):
    INVALID_STATUS = 'invalid_status'          # not a known status value
    INVALID_TRANSITION = 'invalid_transition'  # state machine forbids it


@dataclass(frozen=True)
class StatusChangeResult:
    old_status: str
    failure: Optional[StatusChangeFailure] = None
    # Statuses reachable from old_status (set for INVALID_TRANSITION).
    allowed: Tuple[str, ...] = ()


def appointment_change_status_by_owner(*, appointment, new_status):
    """
    Owner-driven status change with state-machine validation.

    On success saves the row, frees its slot, records the activity and queues
    the client's verdict SMS — all in one transaction. Cancelling refunds the
    credit; NO_SHOW does not: the slot was held and wasted, which is what the
    quota is meant to price.
    """
    with transaction.atomic():
        valid_statuses = dict(Appointment.STATUS_CHOICES).keys()
        old_status = appointment.status
        if new_status not in valid_statuses:
            return StatusChangeResult(old_status, StatusChangeFailure.INVALID_STATUS)

        allowed = OWNER_STATUS_TRANSITIONS.get(old_status, [])
        if new_status not in allowed and new_status != old_status:
            return StatusChangeResult(
                old_status, StatusChangeFailure.INVALID_TRANSITION, tuple(allowed)
            )

        appointment.status = new_status
        updated = ['status', 'updated_at']
        if new_status == 'CANCELLED' and refund_quota(appointment, appointment.business.user_id):
            updated.append('quota_source')
        appointment.save(update_fields=updated)
        invalidate_slots_cache(appointment.business_id, appointment.appointment_date)

        # The caller is already scoped to the business owner, so the owner of
        # the business is the actor.
        record_activity(
            appointment.visitor_id,
            'APPOINTMENT_STATUS_CHANGED',
            actor_type=VisitorActivity.ACTOR_OWNER,
            actor_user=appointment.business.user_id,
            business=appointment.business_id,
            appointment=appointment,
            old=old_status,
            new=new_status,
        )

        # Tell the client the verdict — until now they were only told their
        # booking was received, never whether it was accepted or rejected.
        notify_client_of_decision(appointment, old_status, new_status)
    return StatusChangeResult(old_status)
