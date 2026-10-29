"""
accounting/payment/addon_payment.py

Zibal payment + verification for one-off add-on packs (SMS credit or a temporary
feature). Mirrors the plan payment flow but writes to AddOnPurchase and, on
success, grants the purchased benefit:

  * sms_pack → credits ``sms_amount`` into the buyer's SMS wallet.
  * feature  → activates ``feature_key`` for ``duration_days`` days.
"""

import logging
from datetime import timedelta
from typing import Any, Dict, Optional

from django.conf import settings
from django.db import transaction as db_transaction
from django.utils import timezone

from accounting.models import AddOnPack, AddOnPurchase
from accounting import pricing, usage
from integrations import zibal

logger = logging.getLogger(__name__)


# Add-on packs top up a plan; they are not a plan of their own. Without an
# active subscription (the trial counts) there is nothing to top up, so a
# purchase is refused before any money moves. Only the *start* of a purchase
# checks this — verifying one already paid never does.
NO_SUBSCRIPTION_MESSAGE = "برای خرید بسته‌ی افزودنی ابتدا باید اشتراک فعال داشته باشید."


def can_buy_addon(user) -> bool:
    from accounting.entitlements import get_active_subscription
    return get_active_subscription(user) is not None


def grant_addon_benefit(purchase: AddOnPurchase, response_data: Optional[Dict] = None) -> Dict[str, Any]:
    """
    Mark ``purchase`` as successful and apply its benefit:
      * sms_pack → credits ``sms_amount`` into the buyer's SMS wallet.
      * feature  → activates ``feature_key`` for ``duration_days`` days.

    Idempotent guard is the caller's responsibility (check ``activated_at is
    None`` before calling) — this always (re-)applies the benefit when called.
    Shared by the Zibal verification flow and the Django admin manual-grant
    flow, so a staff member can grant a pack to a user without a real payment.
    """
    pack = purchase.pack
    # The wallet is a DB row, so the credit joins this transaction: it commits
    # with the success status or rolls back with it (the purchase then stays
    # retryable) — never "paid" without the credit.
    with db_transaction.atomic():
        purchase.status = "success"
        purchase.zibal_response = response_data if response_data is not None else purchase.zibal_response
        purchase.activated_at = timezone.now()

        if pack.kind == AddOnPack.KIND_SMS:
            usage.add_wallet(purchase.user_id, pack.sms_amount, reason="addon_purchase",
                             ref_type="AddOnPurchase", ref_id=purchase.id)
        elif pack.kind == AddOnPack.KIND_APPOINTMENT:
            usage.add_appt_wallet(purchase.user_id, pack.appointment_amount, reason="addon_purchase",
                                  ref_type="AddOnPurchase", ref_id=purchase.id)
        elif pack.kind == AddOnPack.KIND_FEATURE:
            purchase.expires_at = timezone.now() + timedelta(days=pack.duration_days)

        purchase.save(update_fields=["status", "zibal_response", "activated_at", "expires_at", "updated_at"])

    return {
        "success": True,
        "message": f"بسته‌ی «{pack.name}» با موفقیت فعال شد",
        "data": {
            "pack": pack.name,
            "kind": pack.kind,
            "sms_amount": pack.sms_amount,
            "appointment_amount": pack.appointment_amount,
            "expires_at": purchase.expires_at.isoformat() if purchase.expires_at else None,
        },
    }


class AddOnPaymentService:
    """Create a Zibal payment request for an add-on pack."""

    def create_payment(self, user, pack: AddOnPack, callback_url: str) -> Dict[str, Any]:
        order_id = f"ADDON-{user.id}-{pack.id}-{int(timezone.now().timestamp())}"
        # Pack price + VAT + Zibal's fee — the total the confirm dialog showed.
        total = pricing.quote(pack.price, pricing.CHANNEL_ZIBAL)["total"]
        result = zibal.request_payment(
            settings.ZIBAL_MERCHANT_ID, total * 10,  # تومان → ریال
            callback_url, order_id,
        )
        if result.transport_error:
            return {"success": False, "error": "خطا در اتصال به درگاه پرداخت"}
        if not result.success:
            logger.error(f"Zibal add-on error: {result.message}")
            return {"success": False, "error": "خطا در ایجاد پرداخت" if result.message is None else result.message}

        track_id = str(result.track_id)
        AddOnPurchase.objects.create(
            user=user,
            pack=pack,
            amount=total,
            track_id=track_id,
            order_id=order_id,
            status="pending",
        )
        return {
            "success": True,
            "payment_url": zibal.start_url(track_id),
            "track_id": track_id,
        }


class AddOnVerificationService:
    """Verify an add-on payment and grant the benefit (idempotent, crash-safe).

    Same contract as ``PaymentVerificationService``: Zibal is asked outside the
    DB lock, the row is re-checked under ``select_for_update``, a replay answers
    success without granting twice, and a network error leaves it pending.
    """

    def __init__(self, track_id: str):
        self.track_id = track_id
        self.purchase: Optional[AddOnPurchase] = None

    def verify_and_grant(self) -> Dict[str, Any]:
        try:
            self.purchase = (
                AddOnPurchase.objects.select_related("user", "pack").get(track_id=self.track_id)
            )
        except AddOnPurchase.DoesNotExist:
            return {"success": False, "message": "تراکنش یافت نشد", "data": {}}

        try:
            if self.purchase.status == "success":
                return self._already_granted(self.purchase)
            if self.purchase.status == "cancelled":
                return {"success": False, "message": "این تراکنش لغو شده است", "data": {}}

            result = zibal.verify(self.track_id)

            if result.paid and not zibal.amount_matches(result.data, self.purchase.amount):
                logger.error(
                    "Zibal add-on amount mismatch for %s: verified=%r expected=%s toman",
                    self.track_id, (result.data or {}).get("amount"), self.purchase.amount,
                )
                return {"success": False, "message": "تأیید پرداخت با تأخیر مواجه شد؛ چند لحظه بعد دوباره تلاش کنید",
                        "data": {}}

            if result.outcome == zibal.Outcome.TRANSPORT_ERROR:
                return {"success": False, "message": "تأیید پرداخت با تأخیر مواجه شد؛ چند لحظه بعد دوباره تلاش کنید",
                        "data": result.data}

            if result.outcome == zibal.Outcome.FAILED:
                AddOnPurchase.objects.filter(pk=self.purchase.pk, status="pending").update(
                    status="failed", zibal_response=result.data, updated_at=timezone.now()
                )
                return {"success": False, "message": result.message, "data": result.data}

            with db_transaction.atomic():
                locked = AddOnPurchase.objects.select_for_update().select_related(
                    "user", "pack"
                ).get(pk=self.purchase.pk)
                if locked.status == "success":
                    return self._already_granted(locked)
                if locked.status == "cancelled":
                    return {"success": False, "message": "این تراکنش لغو شده است", "data": {}}
                return grant_addon_benefit(locked, response_data=result.data)
        except Exception:
            logger.exception("Unexpected error verifying add-on payment %s", self.track_id)
            return {"success": False, "message": "خطای سیستمی در پردازش پرداخت", "data": {}}

    @staticmethod
    def _already_granted(purchase: AddOnPurchase) -> Dict[str, Any]:
        pack = purchase.pack
        return {
            "success": True,
            "message": f"بسته‌ی «{pack.name}» با موفقیت فعال شد",
            "data": {
                "pack": pack.name,
                "kind": pack.kind,
                "sms_amount": pack.sms_amount,
                "appointment_amount": pack.appointment_amount,
                "expires_at": purchase.expires_at.isoformat() if purchase.expires_at else None,
            },
        }
