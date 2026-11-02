"""Settling a Zibal deposit callback."""

import logging

from django.db import transaction
from django.utils import timezone

from appointment.models import Appointment
from appointment.occupancy import find_conflict

from .booking import business_lock

logger = logging.getLogger(__name__)


def deposit_settle(track_id):
    """
    Sync body of the Zibal deposit callback. Returns ``(state, appointment)``
    where state is ``success_new`` (just confirmed — caller fires side
    effects), ``success`` (replay, nothing to do), ``failed`` or ``conflict``.

    Lookup is limited to gateway holds: ``payment_reference`` is free text on
    the CARD path, so only LOCKED rows (the online view stores the trackId
    while the row is LOCKED) are candidates for settlement. Zibal is verified
    OUTSIDE any DB lock; the status flip then re-checks everything under the
    business + appointment row locks.
    """
    from appointment.payment.zibal_deposit import verify_deposit_payment

    candidates = list(
        Appointment.objects.select_related('business', 'visitor')
        .filter(payment_reference=track_id, status='LOCKED')[:2]
    )
    if len(candidates) > 1:
        logger.error("Deposit callback trackId %s matches several LOCKED appointments", track_id)
        return 'failed', None
    if not candidates:
        # Replay of a callback we already settled?
        settled = (
            Appointment.objects.filter(
                payment_reference=track_id, deposit_payment_method='GATEWAY',
            ).exclude(status='CANCELLED').order_by('-id').first()
        )
        if settled is not None:
            return 'success', settled
        logger.warning("Deposit callback for unknown trackId %s", track_id)
        return 'failed', None

    appointment = candidates[0]
    result = verify_deposit_payment(appointment, track_id)
    if not result['success']:
        return 'failed', appointment

    with transaction.atomic():
        # Same lock order as the booking path: business first, then the row.
        business_lock(appointment.business_id)
        locked = (
            Appointment.objects.select_for_update()
            .select_related('business', 'visitor').get(pk=appointment.pk)
        )
        if locked.status == 'CANCELLED':
            # Cancelled / expired while we were verifying: the money was taken
            # (verify stamped deposit_verified_at) but there is no booking.
            logger.error(
                "Paid deposit on CANCELLED appointment: appointment=%s trackId=%s "
                "— needs manual refund", locked.pk, track_id,
            )
            return 'conflict', locked
        if locked.status != 'LOCKED':
            # Already settled / cancelled by someone else: replay, no side effects.
            return 'success', locked

        now = timezone.now()
        if locked.expires_at and locked.expires_at <= now:
            # The hold lapsed, so the slot may have been rebooked meanwhile.
            duration = locked.service_duration or locked.business.default_service_duration
            clash = find_conflict(
                locked.business_id, locked.appointment_date, duration,
                exclude_id=locked.pk, now=now,
            )
            if clash is not None:
                # No status fits "paid but slot lost", so the row is left LOCKED
                # (expired, non-blocking) for manual refund handling.
                logger.error(
                    "Paid deposit on EXPIRED lock lost its slot: appointment=%s "
                    "trackId=%s conflicts_with=%s — needs manual refund",
                    locked.pk, track_id, clash.pk,
                )
                return 'conflict', locked

        changed = Appointment.objects.filter(pk=locked.pk, status='LOCKED').update(
            status='WAITING', locked_at=None, expires_at=None,
            deposit_payment_method='GATEWAY', updated_at=now,
        )
        if not changed:
            return 'success', locked
        locked.status = 'WAITING'
        locked.locked_at = None
        locked.expires_at = None
        locked.deposit_payment_method = 'GATEWAY'
    return 'success_new', locked
