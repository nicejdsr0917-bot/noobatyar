"""
Read queries of the appointment app (see HackSoft Django-Styleguide).

Views and services ask here for querysets / single rows; nothing in this module
writes.
"""

from .models import Appointment


def appointment_list_for_visitor(*, visitor):
    """The client's own appointments, newest first (ClientAppointmentListView)."""
    return (
        Appointment.objects
        .filter(visitor=visitor)
        .select_related('business', 'visitor')
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

    wants_deposit = business.deposit_mode in ('MANDATORY', 'OPTIONAL')
    if wants_deposit and entitlements.has_feature(owner_id, entitlements.FEATURE_DEPOSIT):
        return True

    if 'CARD' in accepted and entitlements.has_feature(owner_id, entitlements.FEATURE_DEPOSIT):
        return True

    if 'ONLINE' in accepted and entitlements.has_feature(owner_id, entitlements.FEATURE_ONLINE_GATEWAY):
        return True

    return False
