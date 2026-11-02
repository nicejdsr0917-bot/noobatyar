"""
accounting/sms_billing.py

The one place an SMS is charged to an owner, sent, logged and — when the
provider refuses it — refunded. Appointment outbox messages, reminders and
promo campaigns used to carry three hand-copied versions of this sequence.

Order, and why:

1. ``step`` (outbox idempotency): if this step was already recorded, the worker
   died *after* the provider accepted the SMS — neither the send nor the charge
   is repeated.
2. ``usage.consume_sms`` — charge first, so an owner without credit never costs
   us a provider call. No credit -> ``SmsLog`` row ``SKIPPED_QUOTA``, no send.
3. ``send_sms`` — a provider exception counts as a rejection.
4. A rejected SMS is refunded from the receipt, so the owner never pays for
   something that was not delivered. ``SmsLog`` records SENT / FAILED.

``owner_id=None`` (business gone) sends without charging, as before.
"""

import logging
from dataclasses import dataclass

logger = logging.getLogger(__name__)

SENT = 'SENT'
FAILED = 'FAILED'
SKIPPED_QUOTA = 'SKIPPED_QUOTA'
ALREADY_SENT = 'ALREADY_SENT'

DEFAULT_QUOTA_DETAIL = "اعتبار پیامک این ماه تمام شده است"


@dataclass(frozen=True)
class SmsOutcome:
    status: str
    error: str = ''


def send_metered_sms_detailed(owner_id, business_id, visitor_id, phone, text, step=None,
                              amount=1, quota_detail=DEFAULT_QUOTA_DETAIL):
    """Charge, send, log and (on rejection) refund one SMS. Returns an :class:`SmsOutcome`.

    ``amount`` is the number of billed parts. ``quota_detail`` is the text stored
    on the ``SKIPPED_QUOTA`` log row. ``SmsLog`` rows are written here; a
    failure of the log write itself propagates (the charge has already settled).
    """
    # Imported lazily so tests can patch ``api.sms.send_sms`` / ``usage.*``.
    from api.sms import send_sms
    from accounting import usage
    from core.outbox import service as outbox
    from visitor.models import SmsLog

    if step and outbox.step_done(step):
        return SmsOutcome(ALREADY_SENT)

    receipt = usage.consume_sms(owner_id, amount=amount) if owner_id is not None else None
    if owner_id is not None and not receipt:
        SmsLog.objects.create(
            business_id=business_id,
            visitor_id=visitor_id,
            message_text=text,
            status=SKIPPED_QUOTA,
            error_detail=quota_detail,
        )
        if step:
            outbox.mark_step(step)
        return SmsOutcome(SKIPPED_QUOTA, quota_detail)

    try:
        ok, err = send_sms(phone, text)
    except Exception as exc:  # noqa: BLE001 — one bad send must not stop the caller's loop
        logger.error("SMS send raised for business %s: %s", business_id, exc)
        ok, err = False, str(exc)

    if not ok:
        # The provider never accepted it, so the owner must not pay for it —
        # put the credit back where it came from.
        usage.refund_sms(receipt)
    elif step:
        # Committed immediately: a crash after this point must not make the
        # retry send and charge a second time.
        outbox.mark_step(step)

    SmsLog.objects.create(
        business_id=business_id,
        visitor_id=visitor_id,
        message_text=text,
        status=SENT if ok else FAILED,
        error_detail='' if ok else (err or ''),
    )
    return SmsOutcome(SENT if ok else FAILED, '' if ok else (err or ''))


def send_metered_sms(owner_id, business_id, visitor_id, phone, text, step=None):
    """Same as :func:`send_metered_sms_detailed` but returns only the status string."""
    return send_metered_sms_detailed(
        owner_id, business_id, visitor_id, phone, text, step=step,
    ).status
