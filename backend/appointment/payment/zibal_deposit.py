"""
appointment/payment/zibal_deposit.py

Zibal gateway for the deposit a client pays when booking an appointment.

This is deliberately separate from ``accounting/payment/*``. Those charge the
business *owner* for a subscription, so they bill the platform's own
``settings.ZIBAL_MERCHANT_ID``. Here the *client* is charged and the money has to
land in the owner's own Zibal account, so the merchant is ``Business.merchant_id``
— the field that sat unused while "online payment" was only a static link.

Flow
----
1. request  → POST /v1/request  {merchant, amount, callbackUrl, orderId} → trackId
2. redirect → GET  /start/{trackId}                     (client pays at the bank)
3. callback → Zibal sends the browser back with ?trackId=…&success=…
4. verify   → POST /v1/verify   {merchant, trackId}     (the authoritative step)

Only step 4 is trusted. The callback's query string is attacker-controllable, so
success is decided by Zibal's verify response, never by ``success=1`` in the URL.
"""

import logging
from typing import Any, Dict

from django.utils import timezone

from integrations import zibal

logger = logging.getLogger(__name__)


def deposit_amount_rial(business) -> int:
    """Deposit in Rial. Amounts are stored and displayed in Toman."""
    return int(business.deposit_amount or 0) * 10


def create_deposit_payment(appointment, callback_url: str) -> Dict[str, Any]:
    """Open a Zibal payment for this appointment's deposit.

    Returns ``{'success': True, 'payment_url': ..., 'track_id': ...}`` or
    ``{'success': False, 'error': <persian message>}``.
    """
    business = appointment.business
    merchant = (business.merchant_id or "").strip()
    amount = deposit_amount_rial(business)

    if not merchant:
        return {"success": False, "error": "درگاه پرداخت این کسب‌وکار پیکربندی نشده است"}
    if amount <= 0:
        return {"success": False, "error": "مبلغ بیعانه برای این کسب‌وکار تعیین نشده است"}

    result = zibal.request_payment(
        merchant,
        amount,
        callback_url,
        f"APT-{appointment.id}-{int(timezone.now().timestamp())}",
        description=f"بیعانه نوبت {appointment.id} — {business.title}",
    )
    if result.transport_error:
        logger.error("Zibal deposit request failed for appointment %s: %s", appointment.id, result.message)
        return {"success": False, "error": "خطا در اتصال به درگاه پرداخت"}

    if not result.success:
        # A wrong merchant id is the usual cause, and it is the owner's
        # misconfiguration rather than anything the client can fix.
        logger.error(
            "Zibal deposit rejected for appointment %s (business %s): %s",
            appointment.id, business.id, result.message,
        )
        return {"success": False, "error": "درگاه پرداخت این کسب‌وکار در دسترس نیست"}

    track_id = str(result.track_id)
    logger.info("Zibal deposit opened for appointment %s, trackId=%s", appointment.id, track_id)
    return {
        "success": True,
        "payment_url": zibal.start_url(track_id),
        "track_id": track_id,
    }


def verify_deposit_payment(appointment, track_id: str) -> Dict[str, Any]:
    """Ask Zibal whether ``track_id`` was really paid, for this appointment.

    The amount is checked against the business's current deposit, so a client who
    tampered with the request cannot settle a 500,000 booking with a 5,000 payment.
    """
    business = appointment.business
    merchant = (business.merchant_id or "").strip()
    if not merchant:
        return {"success": False, "error": "درگاه پرداخت این کسب‌وکار پیکربندی نشده است"}

    result = zibal.verify(track_id, merchant=merchant)
    if result.outcome is zibal.Outcome.TRANSPORT_ERROR:
        logger.error(
            "Zibal deposit verify unavailable for appointment %s, trackId=%s: %s",
            appointment.id, track_id, result.data,
        )
        return {"success": False, "error": "خطا در تایید پرداخت"}
    if not result.paid:
        logger.warning(
            "Zibal deposit not verified for appointment %s, trackId=%s: %s",
            appointment.id, track_id, result.data,
        )
        return {"success": False, "error": "پرداخت تایید نشد", "response": result.data}
    data = result.data

    # Zibal confirmed the money. Stamp it on the row so a deposit whose slot is
    # then lost (callback lands after the hold expired and someone else booked)
    # can be found in the admin and refunded. Queryset update: touches only this
    # column, so it cannot clobber the status flip that follows.
    # Stamped BEFORE the amount check so an underpayment is refundable too.
    from appointment.models import Appointment
    now = timezone.now()
    Appointment.objects.filter(pk=appointment.pk).update(deposit_verified_at=now)
    appointment.deposit_verified_at = now

    paid = int(data.get("amount") or 0)
    expected = deposit_amount_rial(business)
    if expected and paid < expected:
        logger.error(
            "Zibal deposit underpaid for appointment %s: paid=%s expected=%s",
            appointment.id, paid, expected,
        )
        return {"success": False, "error": "مبلغ پرداخت‌شده با مبلغ بیعانه مطابقت ندارد", "response": data}

    return {"success": True, "response": data}
