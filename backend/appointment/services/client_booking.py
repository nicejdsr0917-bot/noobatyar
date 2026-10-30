"""
Client (visitor) booking flow: book, submit a manual payment, cancel, and settle
a gateway deposit.

The views only parse the request and shape the response; everything that decides
or has a side effect lives here. Two rules hold for every function:

* Synchronous: callers hop in with ``sync_to_async``.
* Side effects (slot cache invalidation, activity log, SMS/push outbox rows) are
  registered with ``transaction.on_commit`` — they never run for work that was
  rolled back, and never run before the rows they describe are visible to the
  outbox worker.

A refusal is raised as ``ClientBookingError`` carrying the Persian message and
HTTP status the view turns into the usual error response.
"""

import logging
import uuid
from datetime import timedelta

from django.core.files.storage import default_storage
from django.db import transaction
from django.utils import timezone
from rest_framework.exceptions import ValidationError as DRFValidationError

from PIL import Image

from appointment import selectors
from appointment.cache_utils import invalidate_slots_cache
from appointment.models import Appointment
from appointment.services.booking import BookingFailure, appointment_create_for_client
from appointment.services.deposits import deposit_settle
from appointment.services.notifications import (
    notify_booking_cancelled_by_client,
    notify_booking_created,
    notify_deposit_paid,
)
from appointment.working_hours import LOCAL_TZ, working_window
from showcase.images import ImageRejected, encode_jpeg, flatten_rgb, open_upload
from appointment.services.transitions import (
    CLIENT_CANCELLABLE_STATUSES,
    appointment_cancel_by_client,
    appointment_expire_lock_if_due,
    appointment_submit_payment,
)
from business.serializers import normalize_service_names
from visitor.activity import record_activity
from visitor.models import VisitorActivity

logger = logging.getLogger(__name__)

LOCK_EXPIRED_MESSAGE = "مهلت پرداخت این نوبت به پایان رسیده است. لطفاً دوباره نوبت بگیرید"

PAYMENT_METHODS = ('CARD', 'ONLINE', 'CASH')

# Services carry no durations of their own (the menu is a list of names), so a
# client-sent duration is bounded by this instead — or by the business's own
# default when that is longer.
MAX_CLIENT_DURATION_MINUTES = 480


class ClientBookingError(Exception):
    """A refused client action: ``message`` is Persian, ``code`` the HTTP status."""

    def __init__(self, message, code=400):
        super().__init__(message)
        self.message = message
        self.code = code


def _after_commit(func, *args, **kwargs):
    """Run ``func`` once the surrounding transaction commits (immediately when
    there is none). ``robust``: a failing side effect is logged, never raised
    into the request that already committed."""
    transaction.on_commit(lambda: func(*args, **kwargs), robust=True)


def _drop_slot_cache(business_id, appointment_date):
    _after_commit(invalidate_slots_cache, business_id, appointment_date)


def resolve_selected_services(business, raw):
    """
    What the client says they're coming for, as the comma-joined value stored
    on the appointment. Raises ``ClientBookingError`` (400) when unusable.

    Anything off-menu is rejected unless the owner opted into it — otherwise the
    switch in the business settings would be decoration, since the client
    controls the payload.
    """
    try:
        selected_services = normalize_service_names(raw)
    except DRFValidationError as exc:
        raise ClientBookingError(str(exc.detail[0]), 400)

    if not business.allow_client_add_service:
        menu = set(normalize_service_names(business.services))
        unknown = [name for name in selected_services if name not in menu]
        if unknown:
            raise ClientBookingError("خدمت انتخاب‌شده در لیست خدمات این کسب‌وکار نیست", 400)

    # Appointment.selected_services is a CharField(500); on Postgres an
    # over-long value is an error, not a truncation. Trimmed *after* the
    # menu check so dropping the overflow can never hide an off-menu name
    # that should have been rejected.
    while selected_services and len(','.join(selected_services)) > 500:
        selected_services.pop()
    return ','.join(selected_services)


def validate_client_booking(business, appointment_date, duration, now=None):
    """
    Refuse a booking the business could never honour: bookings switched off,
    a directory listing nobody owns, a start in the past, a weekly day off,
    outside working hours, or a nonsensical duration. ``duration`` is minutes,
    already defaulted. Raises ``ClientBookingError`` (400).

    Slot-grid alignment is deliberately not enforced: owners overlap and shift
    appointments freely, and clients may legitimately pick an off-grid time.
    """
    if not business.booking_enabled:
        raise ClientBookingError("نوبت‌دهی این کسب‌وکار در حال حاضر غیرفعال است", 400)
    if not business.is_claimed:
        raise ClientBookingError("این کسب‌وکار هنوز نوبت‌دهی آنلاین را فعال نکرده است", 400)

    max_duration = max(MAX_CLIENT_DURATION_MINUTES, business.default_service_duration or 0)
    if duration <= 0 or duration > max_duration:
        raise ClientBookingError("مدت زمان نوبت نامعتبر است", 400)

    if appointment_date < (now or timezone.now()):
        raise ClientBookingError("زمان انتخاب‌شده گذشته است", 400)

    local_start = appointment_date.astimezone(LOCAL_TZ)
    local_day = local_start.date()
    if local_day.weekday() in (business.closed_weekdays or []):
        raise ClientBookingError("این روز تعطیل است", 400)

    opens_at, closes_at = working_window(business, local_day)
    if local_start < opens_at or local_start + timedelta(minutes=duration) > closes_at:
        raise ClientBookingError("زمان انتخاب‌شده خارج از ساعات کاری است", 400)


def client_book(*, business, visitor, appointment_date, duration, description='', raw_services=None,
                source=Appointment.SOURCE_WEB):
    """
    Book ``business`` for ``visitor`` at ``appointment_date`` (aware datetime).

    ``duration`` is the client-sent value (``None`` = business default).
    Returns ``(appointment, requires_payment)``; raises ``ClientBookingError``.
    """
    # A business that is not publicly visible does not accept new bookings.
    # Two unrelated causes end up here — the owner's plan lapsed
    # (`is_locked`) and a moderator rejected/suspended the listing — and they
    # deliberately share one neutral message: which one applies is the
    # owner's business, not something to disclose to whoever followed a stale
    # link. 403 rather than 404 because the client reached this from a real
    # link and deserves to be told the booking was refused, not that the page
    # never existed.
    if not business.is_publicly_visible:
        raise ClientBookingError("این کسب‌وکار در حال حاضر نوبت جدید نمی‌پذیرد", 403)

    # A payment step only applies when the business actually collects money
    # at booking time — a deposit, or a card/online method. Those are all
    # premium capabilities, so they are re-checked against the owner's
    # *current* plan: a downgraded owner whose business row still carries a
    # stale CARD/deposit config must not push clients into a payment step.
    requires_payment = selectors.business_requires_payment(business)

    selected_services = resolve_selected_services(business, raw_services)
    # Only an omitted duration falls back to the default: an explicit 0 or a
    # negative number is a bad request, not a request for the default.
    if duration is None:
        duration = business.default_service_duration
    validate_client_booking(business, appointment_date, duration)

    # Quota, slot conflict, row creation and quota consumption all happen in ONE
    # transaction that holds the business row lock, so two clients racing for
    # the last credit / the same time cannot both win. The outer atomic block
    # makes the side effects below wait for that commit.
    with transaction.atomic():
        result = appointment_create_for_client(
            business=business,
            visitor=visitor,
            appointment_date=appointment_date,
            duration=duration,
            description=description,
            selected_services=selected_services,
            requires_payment=requires_payment,
            source=source,
        )
        if result.failure is BookingFailure.QUOTA:
            raise ClientBookingError("ظرفیت نوبت‌های این کسب‌وکار برای این ماه تکمیل شده است", 409)
        if result.failure is not None:
            raise ClientBookingError("این زمان به‌تازگی رزرو شد. لطفاً زمان دیگری انتخاب کنید", 409)
        appointment = result.appointment
        appointment.business = business
        appointment.visitor = visitor

        # Slot occupancy changed → drop cached slot views for this business/date.
        _drop_slot_cache(business.id, appointment_date)
        _after_commit(
            record_activity,
            visitor,
            'APPOINTMENT_BOOKED',
            actor_type=VisitorActivity.ACTOR_VISITOR,
            business=business,
            appointment=appointment,
            status=appointment.status,
        )
        # With a payment step the SMS is deferred until payment is submitted
        # (client_submit_payment); otherwise notify client + owner now.
        if not requires_payment:
            _after_commit(notify_booking_created, appointment, visitor.phone_number)
    return appointment, requires_payment


# A phone photo of a bank receipt stays readable at this size.
RECEIPT_MAX_SIDE = 1600


def _store_receipt(appointment, receipt):
    """
    Check the upload really is an image (8 MB cap), shrink it to
    ``RECEIPT_MAX_SIDE`` and re-encode it as JPEG, then save it to storage and
    return its name. Raises ``ClientBookingError`` before anything is written.
    """
    try:
        img = flatten_rgb(open_upload(receipt))
    except ImageRejected as exc:
        raise ClientBookingError(str(exc), 400)
    img.thumbnail((RECEIPT_MAX_SIDE, RECEIPT_MAX_SIDE), Image.LANCZOS)
    content = encode_jpeg(img, f'{uuid.uuid4().hex}.jpg')
    appointment.payment_receipt.save(content.name, content, save=False)
    return appointment.payment_receipt.name


def client_submit_payment(*, visitor, appointment_id, method, payment_reference, payment_receipt):
    """
    Record how the client paid for their LOCKED hold. Returns the success
    message; raises ``ClientBookingError``.
    """
    try:
        appointment = selectors.appointment_get_for_visitor(pk=appointment_id, visitor=visitor)
    except Appointment.DoesNotExist:
        raise ClientBookingError("نوبت یافت نشد", 404)

    if appointment.status != 'LOCKED':
        raise ClientBookingError("این نوبت قابل پرداخت نیست", 400)

    # Lazy lock expiry — no cleanup job needed.
    if appointment_expire_lock_if_due(appointment):
        raise ClientBookingError(LOCK_EXPIRED_MESSAGE, 400)

    # The client states how they are paying. Older clients that predate this
    # field submitted a receipt/reference, so default to CARD for them.
    method = (method or 'CARD').upper()
    if method not in PAYMENT_METHODS:
        raise ClientBookingError("روش پرداخت نامعتبر است", 400)

    business = appointment.business
    payment_reference = (payment_reference or '').strip()
    stored_receipt = None

    if method == 'CASH':
        # Paying in person: nothing to verify, so the booking goes straight
        # to the owner's approval queue instead of the receipt queue.
        if business.deposit_mode == 'MANDATORY':
            raise ClientBookingError("پرداخت بیعانه برای این کسب‌وکار الزامی است", 400)
        changes = {'status': 'PENDING_APPROVAL', 'deposit_payment_method': 'NONE'}
        success_message = "نوبت شما ثبت شد و در انتظار تایید کسب‌وکار است"
    else:
        # Card / online transfers must come with proof, otherwise the owner
        # sees a "verify the receipt" item with no receipt to verify.
        if not payment_receipt and not payment_reference:
            raise ClientBookingError(
                "لطفاً شماره پیگیری را وارد کنید یا تصویر فیش را آپلود نمایید", 400
            )
        changes = {
            'status': 'PENDING_VERIFICATION',
            'payment_reference': payment_reference,
            'deposit_payment_method': 'CARD',
        }
        if payment_receipt:
            # The update below needs the stored name, so the file is written
            # first — and removed again if the update does not take effect, so
            # a lost race leaves no orphan in storage.
            stored_receipt = _store_receipt(appointment, payment_receipt)
            changes['payment_receipt'] = stored_receipt
        success_message = "پرداخت با موفقیت ثبت شد و نوبت در انتظار تایید است"

    try:
        won = appointment_submit_payment(appointment=appointment, changes=changes)
    except Exception:
        _discard_receipt(stored_receipt)
        raise
    if not won:
        _discard_receipt(stored_receipt)
        raise ClientBookingError("این نوبت قابل پرداخت نیست", 400)

    # Slot occupancy changed → drop cached slot views for this business/date.
    _drop_slot_cache(appointment.business_id, appointment.appointment_date)
    _after_commit(notify_booking_created, appointment, visitor.phone_number)
    return appointment, success_message


def _discard_receipt(name):
    if not name:
        return
    try:
        default_storage.delete(name)
    except Exception:
        logger.exception("Could not remove orphaned payment receipt %s", name)


def client_cancel(*, visitor, appointment_id):
    """Cancel the client's own appointment; raises ``ClientBookingError``."""
    try:
        appointment = selectors.appointment_get_for_visitor(pk=appointment_id, visitor=visitor)
    except Appointment.DoesNotExist:
        raise ClientBookingError("نوبت یافت نشد", 404)

    if appointment.status not in CLIENT_CANCELLABLE_STATUSES:
        raise ClientBookingError("این نوبت قابل لغو نیست", 400)

    if appointment.appointment_date <= timezone.now():
        raise ClientBookingError("زمان این نوبت گذشته است و قابل لغو نیست", 400)

    previous_status = appointment.status
    with transaction.atomic():
        # Conditional transition: if a callback/owner action moved the row since
        # we read it, nothing is cancelled and no credit is refunded.
        if not appointment_cancel_by_client(appointment, previous_status):
            raise ClientBookingError("این نوبت قابل لغو نیست", 400)

        # Freeing the slot changes availability for everyone.
        _drop_slot_cache(appointment.business_id, appointment.appointment_date)
        _after_commit(
            record_activity,
            appointment.visitor,
            'APPOINTMENT_CANCELLED',
            actor_type=VisitorActivity.ACTOR_VISITOR,
            business=appointment.business,
            appointment=appointment,
            old=previous_status,
            new='CANCELLED',
        )
        _after_commit(notify_booking_cancelled_by_client, appointment)
    return appointment


def client_deposit_returned(track_id):
    """
    The client's browser is back from the gateway: settle by Zibal's verdict.
    Returns ``(state, appointment)``; a first-time success is reported as
    ``'success'`` after queuing the slot-cache drop and the deposit SMS.
    """
    # Not wrapped in a transaction: settling verifies with the gateway over the
    # network and takes its own row locks; the side effects run right after its
    # commit.
    state, appointment = deposit_settle(track_id)
    if appointment is not None and state == 'success_new':
        _drop_slot_cache(appointment.business_id, appointment.appointment_date)
        _after_commit(notify_deposit_paid, appointment)
        state = 'success'
    return state, appointment
