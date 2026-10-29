"""
SMS / push notifications of the appointment app.

``notify_*`` build the message texts and queue them in the DB outbox
(``core.outbox``); ``send_booking_sms`` / ``send_client_sms`` are the outbox
handler bodies run by the ``run_outbox`` worker.
"""

import logging

from accounting import usage

logger = logging.getLogger(__name__)


def _enqueue_booking_sms(dedupe_key, **payload):
    """Queue one booking/cancellation/deposit notification (kind
    ``appointment.booking_sms``); never raises — a queue hiccup must not fail
    the booking that already committed."""
    from core.outbox.service import safe_enqueue

    safe_enqueue('appointment.booking_sms', payload, dedupe_key=dedupe_key)


def notify_booking_cancelled_by_client(appointment):
    """Tell the owner their client cancelled, so the freed slot is not a surprise."""
    from api.jalali import format_datetime
    from api.sms import signed

    time_str = format_datetime(appointment.appointment_date)

    owner_msg = signed(
        f"❌ لغو نوبت توسط مشتری\n"
        f"مشتری: {appointment.visitor.full_name}\n"
        f"تاریخ: {time_str}"
    )

    _enqueue_booking_sms(
        client_phone=None,     # the client initiated this; no confirmation SMS
        client_msg=None,
        client_push_body=None,
        owner_msg=owner_msg,
        business_id=appointment.business_id,
        visitor_id=appointment.visitor_id,
        appointment_id=appointment.id,
        dedupe_key=f'cancel_sms:{appointment.id}',
    )


def notify_booking_created(appointment, client_phone):
    """Build the client/owner confirmation messages for a booked appointment and
    queue the notification in the outbox. ``appointment.business`` and
    ``appointment.visitor`` must already be loaded (no DB access here)."""
    from api.jalali import format_datetime
    from api.sms import signed

    time_str = format_datetime(appointment.appointment_date)

    client_msg = signed(
        f"✅ نوبت شما در {appointment.business.title}\n"
        f"تاریخ: {time_str}\n"
        f"در انتظار تایید کسب‌وکار"
    )
    owner_msg = signed(
        f"📋 درخواست نوبت جدید\n"
        f"مشتری: {appointment.visitor.full_name}\n"
        f"تاریخ: {time_str}"
    )
    # Same wording as client_msg, minus signed()'s SMS-only regulatory footer
    # (لغو11 etc.) — that text belongs on an SMS, not a push notification.
    client_push_body = f"نوبت شما در {appointment.business.title} ثبت شد — تاریخ: {time_str} — در انتظار تایید کسب‌وکار"

    # Queued in the DB outbox (core.outbox) and sent by the run_outbox worker:
    # independent of the request's event loop and of process restarts. Only
    # primitive ids/values go in the payload.
    _enqueue_booking_sms(
        client_phone=client_phone,
        client_msg=client_msg,
        client_push_body=client_push_body,
        owner_msg=owner_msg,
        business_id=appointment.business_id,
        visitor_id=appointment.visitor_id,
        appointment_id=appointment.id,
        dedupe_key=f'booking_sms:{appointment.id}',
    )


def notify_deposit_paid(appointment):
    """Messages for a booking settled through the gateway.

    Separate from ``notify_booking_created`` because that one tells the client the
    booking is "در انتظار تایید کسب‌وکار" — untrue here, where the bank already
    confirmed the deposit and the appointment goes straight into the queue.
    ``appointment.business`` and ``appointment.visitor`` must already be loaded.
    """
    from api.jalali import format_datetime
    from api.sms import signed

    time_str = format_datetime(appointment.appointment_date)

    client_msg = signed(
        f"✅ نوبت شما در {appointment.business.title} قطعی شد\n"
        f"تاریخ: {time_str}\n"
        f"بیعانه پرداخت شد"
    )
    owner_msg = signed(
        f"💳 نوبت جدید با پرداخت آنلاین\n"
        f"مشتری: {appointment.visitor.full_name}\n"
        f"تاریخ: {time_str}"
    )
    client_push_body = f"نوبت شما در {appointment.business.title} قطعی شد — تاریخ: {time_str} — بیعانه پرداخت شد"

    _enqueue_booking_sms(
        client_phone=appointment.visitor.phone_number,
        client_msg=client_msg,
        client_push_body=client_push_body,
        owner_msg=owner_msg,
        business_id=appointment.business_id,
        visitor_id=appointment.visitor_id,
        appointment_id=appointment.id,
        dedupe_key=f'deposit_sms:{appointment.id}',
    )


def notify_client_of_decision(appointment, old_status, new_status):
    """
    SMS the client when the owner accepts or rejects a pending booking.

    Only fires on the pending → decided transitions; ordinary queue movement
    (WAITING → IN_PROGRESS → COMPLETED) is not worth an SMS credit. Queued in
    the DB outbox and sent by the run_outbox worker, like the booking SMS.
    """
    from api.jalali import format_datetime
    from api.sms import signed

    if old_status not in ('PENDING_APPROVAL', 'PENDING_VERIFICATION'):
        return
    if new_status not in ('WAITING', 'CONFIRMED', 'CANCELLED'):
        return

    try:
        business_title = appointment.business.title
        client_phone = appointment.visitor.phone_number
    except Exception:  # related row missing — nothing we can notify
        return

    if not client_phone:
        return

    time_str = format_datetime(appointment.appointment_date)

    if new_status == 'CANCELLED':
        message = signed(
            f"❌ متأسفانه نوبت شما در {business_title} تایید نشد\n"
            f"تاریخ: {time_str}"
        )
        push_body = f"متأسفانه نوبت شما در {business_title} تایید نشد — تاریخ: {time_str}"
    else:
        message = signed(
            f"✅ نوبت شما در {business_title} تایید شد\n"
            f"تاریخ: {time_str}"
        )
        push_body = f"نوبت شما در {business_title} تایید شد — تاریخ: {time_str}"

    from core.outbox.service import safe_enqueue

    # Inside the status-update transaction (see appointment_change_status), so
    # the notification commits — or rolls back — together with the decision.
    safe_enqueue(
        'appointment.client_sms',
        dict(
            phone=client_phone,
            message=message,
            push_body=push_body,
            business_id=appointment.business_id,
            visitor_id=appointment.visitor_id,
            appointment_id=appointment.id,
        ),
        dedupe_key=f'decision_sms:{appointment.id}:{new_status}',
    )


def _send_metered_sms(label, owner_id, business_id, visitor_id, phone, text, step=None):
    """
    Send one SMS charged to the owner's SMS quota and log it in SmsLog.

    ``label`` ("client" / "owner") only names the recipient in log lines.
    Never raises: a failure here must not take the other channels down.

    ``step`` makes it idempotent per outbox message: if this step was already
    recorded (the worker died after the provider accepted the SMS), neither the
    send nor the quota charge is repeated; it is recorded right after the
    provider accepted the SMS (or the quota skip was logged).
    """
    from api.sms import send_sms
    from core.outbox import service as outbox
    from visitor.models import SmsLog

    try:
        if step and outbox.step_done(step):
            logger.info(f"SMS→{label} {phone}: already sent for this outbox message, skipped")
            return
        receipt = usage.consume_sms(owner_id) if owner_id is not None else None
        if owner_id is not None and not receipt:
            logger.warning(f"SMS→{label} skipped for business {business_id}: SMS quota exhausted")
            SmsLog.objects.create(
                business_id=business_id,
                visitor_id=visitor_id,
                message_text=text,
                status='SKIPPED_QUOTA',
                error_detail="اعتبار پیامک این ماه تمام شده است",
            )
            if step:
                outbox.mark_step(step)
        else:
            ok, err = send_sms(phone, text)
            if not ok:
                # The provider never accepted it, so the owner must not pay
                # for it — put the credit back where it came from.
                usage.refund_sms(receipt)
            elif step:
                # Committed immediately: a crash after this point must not
                # make the retry send and charge a second time.
                outbox.mark_step(step)
            SmsLog.objects.create(
                business_id=business_id,
                visitor_id=visitor_id,
                message_text=text,
                status='SENT' if ok else 'FAILED',
                error_detail=err if not ok else ""
            )
            logger.info(f"SMS→{label} {phone}: {'✓' if ok else '✗'}")
    except Exception as e:
        logger.error(f"SMS→{label} error: {e}")


def send_booking_sms(client_phone, client_msg, owner_msg, business_id, visitor_id, appointment_id=None, client_push_body=None):
    """
    Outbox handler body (``appointment.booking_sms``): sends SMS to client and
    business owner via Melipayamak and logs the SMS results in SmsLog.

    Runs fully synchronously inside the run_outbox worker.
    """
    from business.models import Business

    # Resolve the owner whose plan pays for these SMS, and the number that
    # actually reaches them. business.phone is a display number for
    # customers — often a landline — and was never the owner's own contact;
    # owner notifications go to the phone their account was registered with.
    owner_id = None
    owner_phone = None
    notify_owner = True
    try:
        owner_id, owner_phone, notify_owner = Business.objects.values_list(
            'user_id', 'user__phone', 'notify_owner_by_sms'
        ).get(id=business_id)
    except Business.DoesNotExist:
        pass

    # Send to client (consumes one SMS credit from the owner's plan/wallet).
    # Callers pass client_msg=None when the client triggered the action
    # themselves and does not need to be told about it (e.g. self-cancellation).
    if client_phone and client_msg:
        _send_metered_sms('client', owner_id, business_id, visitor_id, client_phone, client_msg, step='client_sms')

    # Push to the client — free, plan-gated (see push.send_visitor_appointment_push),
    # sent alongside the SMS above rather than instead of it. client_push_body is
    # None for the same cases client_msg is (self-cancellation): nothing to tell
    # the client about their own action.
    try:
        if visitor_id and client_push_body:
            from api.services import push

            push.send_visitor_appointment_push(
                business_id=business_id,
                visitor_id=visitor_id,
                appointment_id=appointment_id,
                title="نوبت‌یار",
                body=client_push_body,
            )
    except Exception as e:
        logger.error(f"Push→client error: {e}")

    # Push to the owner's app — free, so it goes out regardless of the SMS
    # switch below. This is the channel notify_owner_by_sms was defaulted off in
    # favour of: an owner should not pay, out of their own SMS quota, for a
    # message that repeats what the app already shows them.
    try:
        if owner_id is not None and owner_msg:
            from accounting.entitlements import FEATURE_PUSH_NOTIFICATIONS, has_feature
            from api.services import push

            if push.is_configured() and has_feature(owner_id, FEATURE_PUSH_NOTIFICATIONS):
                push.send_to_user(
                    owner_id,
                    title="نوبت جدید",
                    body=owner_msg,
                    data={'type': 'NEW_BOOKING', 'business_id': business_id},
                )
    except Exception as e:
        # Never let the push channel take the SMS channel down with it.
        logger.error(f"Push→owner error: {e}")

    # Send to business owner by SMS — off unless the owner explicitly asked for
    # it. Business.notify_owner_by_sms defaults to False (and existing rows were
    # switched off by business migration 0013); the push above is the intended
    # replacement. An owner who still wants an SMS can turn this back on and
    # keep paying for it.
    if not owner_phone or not owner_msg:
        pass
    elif not notify_owner:
        logger.info(f"SMS→owner disabled for business {business_id}")
    else:
        # Logged like the client message: this one is billed too, so leaving it
        # out would make the SMS report undercount every booking by one.
        # visitor is null — the recipient is the owner, not a client.
        _send_metered_sms('owner', owner_id, business_id, None, owner_phone, owner_msg, step='owner_sms')


def send_client_sms(phone, message, business_id, visitor_id, appointment_id=None, push_body=None):
    """Outbox handler body (``appointment.client_sms``): send one SMS to a client and log it against the business."""
    from business.models import Business

    try:
        owner_id = Business.objects.values_list('user_id', flat=True).get(id=business_id)
    except Business.DoesNotExist:
        owner_id = None

    _send_metered_sms('client', owner_id, business_id, visitor_id, phone, message, step='client_sms')

    # Push — free, plan-gated, independent of the SMS quota above (still
    # fires even when the SMS itself was skipped for exhausted quota).
    try:
        if visitor_id and push_body:
            from api.services import push

            push.send_visitor_appointment_push(
                business_id=business_id,
                visitor_id=visitor_id,
                appointment_id=appointment_id,
                title="نوبت‌یار",
                body=push_body,
            )
    except Exception as e:
        logger.error(f"Push→client error: {e}")
