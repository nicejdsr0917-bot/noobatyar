"""Owner-side visitor operations (attach / edit / archive).

Views only parse input and shape the response; the rules live here. All
functions are synchronous — async views call them through sync_to_async.
"""
from django.db import IntegrityError, transaction
from django.utils import timezone

from .activity import record_activity
from .models import (
    Visitor, VisitorActivity, VisitorArchive, VisitorContact, VisitorDeviceToken,
)
from .serializers import VisitorSerializer


class VisitorNotFound(Exception):
    pass


class VisitorEditForbidden(Exception):
    """Readable by the owner, but not theirs to edit."""


class PhoneLocked(Exception):
    """The visitor has a life outside this owner; their number is identity."""


class InvalidVisitorData(Exception):
    def __init__(self, errors):
        super().__init__(errors)
        self.errors = errors


def get_readable(visitor_id, user):
    return Visitor.readable_by(user).filter(id=visitor_id).first()


def get_owned(visitor_id, user):
    """A contact this owner created themselves (the only rows they may edit)."""
    return Visitor.objects.filter(id=visitor_id, user=user).first()


def has_independent_identity(visitor, owner):
    """True when the visitor demonstrably exists outside this owner's contacts.

    Phone number is how a client logs into the client app (OTP), so letting an
    owner re-number such a visitor would hand over the account. Signals, from
    strongest to weakest: a device token (they used the client app and enabled
    push), a recorded client-app login (last_client_login_at), another owner's
    contact row or activity, any activity the visitor performed themselves (VisitorActivity with
    actor VISITOR, written by client bookings/cancels), and any appointment at a
    business this owner does not own. Client logins themselves are stateless
    signed tokens and leave no row, so these are the best persisted evidence.
    """
    from appointment.models import Appointment
    if visitor.last_client_login_at is not None:
        return True
    if VisitorDeviceToken.objects.filter(visitor=visitor).exists():
        return True
    # Another owner already has this person in their book (their SMS go to this
    # number) or has acted on them.
    if VisitorContact.objects.filter(visitor=visitor).exclude(owner=owner).exists():
        return True
    if VisitorActivity.objects.filter(
            visitor=visitor, actor_user__isnull=False).exclude(actor_user=owner).exists():
        return True
    if VisitorActivity.objects.filter(
            visitor=visitor, actor_type=VisitorActivity.ACTOR_VISITOR).exists():
        return True
    return Appointment.objects.filter(visitor=visitor).exclude(business__user=owner).exists()


def visitor_attach(owner, phone, full_name):
    """Create the visitor, or attach an existing one to the owner's contacts.

    Returns (visitor, created, display_name). ``user`` is only ever set on
    create: assigning it on an existing row used to turn any self-registered
    client into an owner-editable contact. ``display_name`` is what the
    response should show: the stored global name only if the owner could
    already read this visitor, otherwise the name they submitted.
    """
    already_readable = False
    try:
        with transaction.atomic():
            visitor, created = Visitor.objects.get_or_create(
                phone_number=phone,
                defaults={'full_name': full_name, 'user': owner},
            )
    except IntegrityError:
        visitor, created = Visitor.objects.get(phone_number=phone), False
    if not created:
        already_readable = Visitor.readable_by(owner).filter(pk=visitor.pk).exists()

    contact, made = VisitorContact.objects.get_or_create(
        owner=owner, visitor=visitor, defaults={'display_name': full_name})
    if not made and contact.display_name != full_name and not already_readable:
        contact.display_name = full_name
        contact.save(update_fields=['display_name'])
    # Adding someone back undoes an earlier archive.
    VisitorArchive.objects.filter(owner=owner, visitor=visitor).delete()

    display_name = visitor.full_name if (created or already_readable) else full_name
    return visitor, created, display_name


def visitor_update_by_owner(owner, visitor_id, data):
    """Edit a contact this owner created. Returns the updated Visitor."""
    visitor = get_owned(visitor_id, owner)
    if not visitor:
        if get_readable(visitor_id, owner):
            raise VisitorEditForbidden()
        raise VisitorNotFound()

    before = {'full_name': visitor.full_name, 'phone_number': visitor.phone_number}
    serializer = VisitorSerializer(visitor, data=data, partial=True)
    if not serializer.is_valid():
        raise InvalidVisitorData(serializer.errors)

    new_phone = serializer.validated_data.get('phone_number')
    if new_phone is not None and new_phone != visitor.phone_number \
            and has_independent_identity(visitor, owner):
        raise PhoneLocked()

    phone_changed = new_phone is not None and new_phone != visitor.phone_number
    if phone_changed:
        serializer.validated_data['phone_changed_at'] = timezone.now()
    updated = serializer.save()
    changed = {
        field: {'from': old, 'to': getattr(updated, field)}
        for field, old in before.items()
        if getattr(updated, field) != old
    }
    if changed:
        record_activity(
            updated, 'PROFILE_UPDATED',
            actor_type=VisitorActivity.ACTOR_OWNER, actor_user=owner, changed=changed,
        )
    return updated


def visitor_archive(owner, visitor_id):
    """Hide a visitor from the owner's list. Returns its display name."""
    visitor = get_readable(visitor_id, owner)
    if not visitor:
        raise VisitorNotFound()
    name = visitor.full_name or visitor.phone_number or f"ID:{visitor.id}"
    _, created = VisitorArchive.objects.get_or_create(owner=owner, visitor=visitor)
    if created:
        record_activity(
            visitor, 'ARCHIVED_BY_OWNER',
            actor_type=VisitorActivity.ACTOR_OWNER, actor_user=owner,
        )
    return visitor.id, name, created


def display_names_for_owner(owner, visitors):
    """{visitor_id: name} overrides for visitors the owner only knows as a contact.

    When the owner's sole link is a VisitorContact (they did not create the
    row and have no appointment at their business) the global ``full_name`` is
    someone else's data; show the name this owner gave instead. Contacts
    without a display_name (older rows) are left alone.
    """
    from appointment.models import Appointment
    ids = [v.id for v in visitors if v.user_id != owner.id]
    if not ids:
        return {}
    names = dict(VisitorContact.objects.filter(
        owner=owner, visitor_id__in=ids, display_name__isnull=False,
    ).values_list('visitor_id', 'display_name'))
    if not names:
        return {}
    booked = set(Appointment.objects.filter(
        visitor_id__in=list(names), business__user=owner,
    ).values_list('visitor_id', flat=True))
    return {vid: n for vid, n in names.items() if vid not in booked}


def serialize_for_owner(owner, visitors, many=True):
    """VisitorSerializer data with per-owner contact names applied."""
    items = list(visitors) if many else [visitors]
    overrides = display_names_for_owner(owner, items)
    data = VisitorSerializer(items, many=True).data
    out = []
    for v, d in zip(items, data):
        d = dict(d)
        if v.id in overrides:
            d['full_name'] = overrides[v.id]
        out.append(d)
    return out if many else out[0]
