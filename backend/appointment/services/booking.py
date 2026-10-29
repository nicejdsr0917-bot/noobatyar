"""
Creating and editing appointments.

Both creation paths share one skeleton — lock the business row, apply the
path's own admission policy, take a credit from the owner's plan with
``usage.record_appointment`` (the binding decision), create the row — and differ
only in the policy:

* client (``appointment_create_for_client``): monthly quota, time-overlap
  conflict, and a payment hold when the business collects a deposit.
* owner (``appointment_create_for_owner``): monthly quota and the hourly
  capacity cap only — the owner may deliberately overlap appointments
  (walk-ins, parallel chairs) — and the row goes straight into the queue.
"""

import enum
from dataclasses import dataclass
from datetime import timedelta
from typing import Optional

from django.core.exceptions import ValidationError
from django.db import transaction
from django.utils import timezone

from accounting import usage
from appointment.cache_utils import invalidate_slots_cache
from appointment.models import Appointment
from appointment.occupancy import find_conflict, lock_expiry, refund_quota
from business.models import Business


class BookingFailure(enum.Enum):
    QUOTA = 'quota'        # the owner's plan has no appointment credit left
    CONFLICT = 'conflict'  # the requested time overlaps a live appointment
    CAPACITY = 'capacity'  # the hour already holds max_appointments_per_hour


@dataclass(frozen=True)
class BookingResult:
    appointment: Optional[Appointment] = None
    failure: Optional[BookingFailure] = None


class _QuotaExhausted(Exception):
    """Raised inside the create transaction when no credit is left, to roll it back."""


def business_lock(business_id):
    """
    Take the business row lock (call inside ``transaction.atomic``).

    Every booking / settlement path locks the business first, so owner and
    client bookings are serialised and checks cannot be overtaken between the
    SELECT and the INSERT.
    """
    Business.objects.select_for_update().only('id').get(id=business_id)


def hourly_capacity_exceeded(business, appointment_date):
    """
    True when the business already has max_appointments_per_hour bookings
    in the local-time hour containing ``appointment_date``. Sync: call it
    from inside a sync_to_async/atomic block.
    """
    if business.max_appointments_per_hour is None:
        return False

    # Local (Asia/Tehran, +03:30) hour boundaries, not UTC ones.
    hour_start = timezone.localtime(appointment_date).replace(
        minute=0, second=0, microsecond=0
    )
    hour_end = hour_start + timedelta(hours=1)

    count = Appointment.objects.filter(
        business=business,
        appointment_date__gte=hour_start,
        appointment_date__lt=hour_end
    ).exclude(status__in=['CANCELLED', 'NO_SHOW']).count()

    return count >= business.max_appointments_per_hour


def appointment_create_for_client(
    *, business, visitor, appointment_date, duration, description,
    selected_services, requires_payment,
):
    """
    Check quota + slot and create a self-booked appointment atomically.

    Returns a ``BookingResult``; ``failure`` is ``QUOTA`` or ``CONFLICT`` when
    nothing was created. With ``requires_payment`` the row is a LOCKED hold that
    expires on its own; otherwise it waits for the owner's approval.
    """
    with transaction.atomic():
        business_lock(business.id)

        # Monthly appointment quota belongs to the business owner's plan.
        if not usage.can_book_appointment(business.user_id):
            return BookingResult(failure=BookingFailure.QUOTA)

        conflict = find_conflict(business.id, appointment_date, duration)
        if conflict is not None:
            return BookingResult(failure=BookingFailure.CONFLICT)

        # Take the credit first, in this same transaction: record_appointment
        # is the binding decision (can_book_appointment above is advisory),
        # so a booking that loses the race for the last credit creates nothing.
        quota_source = usage.record_appointment(business.user_id)
        if not quota_source:
            return BookingResult(failure=BookingFailure.QUOTA)

        now = timezone.now()
        appointment = Appointment.objects.create(
            # No `User` involved in a self-booked appointment — only the
            # Visitor identifies who this is for.
            user=None,
            business=business,
            visitor=visitor,
            appointment_date=appointment_date,
            service_duration=duration,
            description=description,
            selected_services=selected_services,
            status='LOCKED' if requires_payment else 'PENDING_APPROVAL',
            # Hold the slot only while the client is paying; an abandoned
            # lock expires on its own and frees the slot (see occupancy.py).
            locked_at=now if requires_payment else None,
            expires_at=lock_expiry(now) if requires_payment else None,
            # Which bucket paid, so a cancellation can refund that same one.
            quota_source=quota_source,
        )
        return BookingResult(appointment=appointment)


def appointment_create_for_owner(
    *, business, visitor, appointment_date, service_duration, description,
    selected_services, user,
):
    """
    Create an appointment on behalf of the business owner, straight into WAITING.

    Returns a ``BookingResult``; ``failure`` is ``QUOTA`` or ``CAPACITY``.
    Raises ``ValidationError`` (rolling the credit back) when the row fails
    ``full_clean``. The hourly capacity is checked under the business lock, the
    same lock the client path takes; there is no time-overlap check.
    """
    # Fast pre-check only — the binding decision is record_appointment below.
    if not usage.can_book_appointment(business.user_id):
        return BookingResult(failure=BookingFailure.QUOTA)

    try:
        with transaction.atomic():
            business_lock(business.id)
            if hourly_capacity_exceeded(business, appointment_date):
                return BookingResult(failure=BookingFailure.CAPACITY)
            quota_source = usage.record_appointment(business.user_id)
            if not quota_source:
                raise _QuotaExhausted()
            appointment = Appointment(
                quota_source=quota_source,
                business=business,
                visitor=visitor,
                appointment_date=appointment_date,
                service_duration=service_duration,
                description=description,
                selected_services=selected_services,
                status='WAITING',
                user=user,
            )
            appointment.full_clean()
            appointment.save()
            invalidate_slots_cache(appointment.business_id, appointment.appointment_date)
            return BookingResult(appointment=appointment)
    except _QuotaExhausted:
        return BookingResult(failure=BookingFailure.QUOTA)


@dataclass(frozen=True)
class DetailsUpdateResult:
    validation_error: Optional[ValidationError] = None


def appointment_update_details_for_owner(
    *, appointment, status, appointment_date, service_duration, description,
    selected_services,
):
    """
    Apply the owner's edit to date / duration / description / services (and
    status when sent). ``None`` / falsy values leave the field alone.

    Owners may intentionally overlap appointments, so no overlap check is made.
    Returns a ``DetailsUpdateResult`` carrying the ``ValidationError`` when
    ``full_clean`` rejects the new values (nothing is saved then).
    """
    with transaction.atomic():
        # Serialise with client bookings (they lock this same row).
        business_lock(appointment.business_id)

        # Remember the original date so we can invalidate its cached slots too
        # if the appointment is being rescheduled to a different day.
        old_date = appointment.appointment_date

        # Only overwrite status when the client actually sends it. Otherwise
        # a details-only PATCH (date/duration/description) would set status to
        # None and fail full_clean() with "این فیلد نمی‌تواند پوچ باشد".
        updated_fields = ['updated_at']
        if status is not None:
            appointment.status = status
            updated_fields.append('status')
        if appointment_date:
            appointment.appointment_date = appointment_date
            updated_fields.append('appointment_date')
        if service_duration:
            appointment.service_duration = service_duration
            updated_fields.append('service_duration')
        if description is not None:
            appointment.description = description
            updated_fields.append('description')
        if selected_services is not None:
            appointment.selected_services = selected_services
            updated_fields.append('selected_services')

        try:
            appointment.full_clean()
            appointment.save(update_fields=updated_fields)
        except ValidationError as e:
            return DetailsUpdateResult(validation_error=e)

        invalidate_slots_cache(appointment.business_id, old_date)
        invalidate_slots_cache(appointment.business_id, appointment.appointment_date)
    return DetailsUpdateResult()


def appointment_delete_for_owner(*, appointment):
    """
    Hard-delete an appointment, giving its credit back to the owner first.

    Locks the business row then the appointment row and refunds from the LOCKED
    row, so a retry / race cannot refund twice. A row already gone counts as
    deleted: no refund, nothing to do.
    """
    with transaction.atomic():
        business_lock(appointment.business_id)
        locked = (
            Appointment.objects.select_for_update()
            .select_related('business').filter(pk=appointment.pk).first()
        )
        if locked is None:
            return
        business_id = locked.business_id
        appointment_date = locked.appointment_date
        # Refund before the row goes away, while quota_source is still readable.
        refund_quota(locked, locked.business.user_id)
        locked.delete()
        invalidate_slots_cache(business_id, appointment_date)
