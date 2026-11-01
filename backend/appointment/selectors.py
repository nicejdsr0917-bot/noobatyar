"""
Read queries of the appointment app (see HackSoft Django-Styleguide).

Views and services ask here for querysets / single rows; nothing in this module
writes.
"""

from datetime import timezone as dt_timezone

from django.db.models import Count, IntegerField, OuterRef, Subquery, Value
from django.db.models.functions import Coalesce, TruncDay

from .models import Appointment

# Statuses that hold a place in the day's queue.
QUEUE_STATUSES = ('WAITING', 'IN_PROGRESS')


def _ahead_in_queue():
    """Correlated COUNT of same-business, same-(UTC)-day, earlier, still-queued
    appointments - the value ClientAppointmentSerializer.get_queue_position()
    used to ask for with one query per row."""
    ahead = (
        Appointment.objects
        .filter(
            business_id=OuterRef('business_id'),
            status__in=QUEUE_STATUSES,
            appointment_date__lt=OuterRef('appointment_date'),
            appointment_date__gte=OuterRef('_day_start'),
        )
        .order_by()
        .values('business_id')
        .annotate(n=Count('pk'))
        .values('n')
    )
    return Coalesce(Subquery(ahead, output_field=IntegerField()), Value(0))


def appointment_list_for_visitor(*, visitor):
    """The client's own appointments, newest first (ClientAppointmentListView).

    Everything the serializer reads per row is resolved here or in one batch per
    page: the business insurances are prefetched and the queue position is an
    annotation (``queue_ahead``); the owner's subscription / quota state is
    attached per page by the serializer's list class.
    """
    return (
        Appointment.objects
        .filter(visitor=visitor)
        .select_related('business', 'visitor')
        .prefetch_related('business__insurances')
        .annotate(_day_start=TruncDay('appointment_date', tzinfo=dt_timezone.utc))
        .annotate(queue_ahead=_ahead_in_queue())
        .order_by('-appointment_date')
    )


def appointment_list_for_owner(*, user, business_id):
    """
    Appointments of one business, scoped to the caller's own businesses.

    Expressed as a queryset filter rather than a 403 so an id the caller
    doesn't own comes back empty and the endpoint reveals nothing about whether
    it exists.
    """
    return Appointment.objects.filter(
        business_id=business_id, business__user=user
    ).select_related('visitor')


def appointment_get_for_visitor(*, pk, visitor, select_related=('business', 'visitor')):
    """The visitor's appointment by id, or ``Appointment.DoesNotExist`` (sync)."""
    return Appointment.objects.select_related(*select_related).get(id=pk, visitor=visitor)


def appointment_get_for_owner(*, appointment_id, user):
    """An appointment of one of the user's businesses, or ``DoesNotExist`` (sync)."""
    return Appointment.objects.select_related(
        'business__user', 'visitor__user'
    ).get(id=appointment_id, business__user=user)


def deposit_collectable(business):
    """Whether the client has any way to pay a deposit before the visit.

    Card-to-card needs a card number, online needs a Zibal merchant or a
    payment link. Cash is paid at the visit, so it cannot settle a deposit.
    A mandatory deposit on a business with none of these used to hold the
    slot and then leave the client on a payment step with nothing to pay to.
    """
    accepted = business.accepted_payment_methods or []
    if 'CARD' in accepted and (business.card_number or '').strip():
        return True
    if 'ONLINE' in accepted and (
        (business.merchant_id or '').strip() or (business.payment_link or '').strip()
    ):
        return True
    return False


def business_requires_payment(business):
    """
    Whether booking this business must go through the payment step.

    Deposit and card/online collection are plan-gated capabilities, so the
    owner's live entitlements decide — not just the stored business config,
    which can be left over from a plan the owner no longer has.
    """
    from accounting import entitlements

    owner_id = business.user_id
    accepted = business.accepted_payment_methods or []

    collectable = deposit_collectable(business)

    # An optional deposit can always be settled in cash at the payment step;
    # a mandatory one only when there is a card or online way to pay it.
    # Without one the booking goes straight to the owner, as with no deposit.
    wants_deposit = business.deposit_mode == 'OPTIONAL' or (
        business.deposit_mode == 'MANDATORY' and collectable
    )
    if wants_deposit and entitlements.has_feature(owner_id, entitlements.FEATURE_DEPOSIT):
        return True

    if not collectable:
        return False

    if 'CARD' in accepted and entitlements.has_feature(owner_id, entitlements.FEATURE_DEPOSIT):
        return True

    if 'ONLINE' in accepted and entitlements.has_feature(owner_id, entitlements.FEATURE_ONLINE_GATEWAY):
        return True

    return False
