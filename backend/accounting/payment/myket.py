"""
accounting/payment/myket.py

Myket in-app billing for the owner app's Myket build.

Myket rejects apps that sell "digital products" (our plans and add-on packs)
through a direct bank gateway, so the Myket build buys through Myket's own
billing UI instead of Zibal. The flow:

1. ``create_intent`` — the app says what it wants to buy; we reserve a
   ``StorePurchase`` with a random ``payload`` and return the Myket product id
   (``myket_sku``) to charge. The app passes ``payload`` to Myket as the
   developer payload.
2. The app runs Myket's purchase UI and gets back a purchase token.
3. ``verify_and_grant`` — the app sends the token; we ask Myket's
   server-to-server API whether that token is a real, successful purchase of
   that product, and only then grant the plan/pack through the same code a
   Zibal payment uses. Each token is accepted once, ever.
4. We consume the purchase with Myket, so the same (consumable) product can
   be bought again next time.

Step 3 is idempotent: an app that crashed between paying and verifying sends
the same token again on its next start (it finds the purchase still owned in
Myket's inventory) and gets the same answer instead of a second benefit.

API docs: https://myket.ir/kb/pages/server-to-server-payment-validation-api/
          https://myket.ir/kb/pages/api-consume/
"""

import logging
import secrets

import httpx
from django.conf import settings
from django.db import transaction as db_transaction
from django.utils import timezone

from accounting.models import AddOnPack, AddOnPurchase, Plan, StorePurchase, Transaction

logger = logging.getLogger(__name__)

API_BASE = "https://developer.myket.ir/api/partners/applications"
TIMEOUT = 15.0

# The trial plan is granted at sign-up and is never for sale (same rule the
# Zibal flow applies in the app's CreatePaymentUseCase).
TRIAL_PLAN_ID = 1


class StoreError(Exception):
    """A purchase the app has to show a message for."""

    def __init__(self, message, status=400):
        super().__init__(message)
        self.message = message
        self.status = status


# ── Myket API ────────────────────────────────────────────────────────────────

def _headers():
    token = getattr(settings, "MYKET_ACCESS_TOKEN", "")
    if not token:
        raise StoreError("پرداخت مایکت روی سرور پیکربندی نشده است.", status=503)
    return {"X-Access-Token": token, "Content-Type": "application/json"}


def _product_url(sku):
    return f"{API_BASE}/{settings.MYKET_PACKAGE_NAME}/purchases/products/{sku}"


def myket_verify(sku, token):
    """Myket's verdict on one purchase token, or raise StoreError."""
    try:
        response = httpx.post(
            f"{_product_url(sku)}/verify", headers=_headers(),
            json={"tokenId": token}, timeout=TIMEOUT,
        )
    except httpx.HTTPError as exc:
        logger.error("Myket verify unreachable for %s: %s", sku, exc)
        raise StoreError("ارتباط با مایکت برقرار نشد؛ چند لحظه بعد دوباره تلاش کنید.", status=502)
    if response.status_code != 200:
        logger.warning("Myket verify %s -> %s %s", sku, response.status_code, response.text[:300])
        raise StoreError("مایکت این خرید را تأیید نکرد.", status=400)
    return response.json()


def myket_consume(sku, token):
    """Consume a verified purchase so the product can be bought again. True on success."""
    try:
        response = httpx.put(f"{_product_url(sku)}/tokens/{token}/consume", headers=_headers(), timeout=TIMEOUT)
    except (httpx.HTTPError, StoreError) as exc:
        logger.error("Myket consume failed for %s: %s", sku, exc)
        return False
    if response.status_code == 200:
        return True
    # Already consumed is the outcome we wanted anyway.
    if response.status_code == 400 and "SkuAlreadyConsumed" in response.text:
        return True
    logger.warning("Myket consume %s -> %s %s", sku, response.status_code, response.text[:300])
    return False


# ── Flow ─────────────────────────────────────────────────────────────────────

def _item(kind, item_id):
    if kind == StorePurchase.KIND_PLAN:
        plan = Plan.objects.filter(id=item_id, is_active=True).first()
        if not plan or plan.id == TRIAL_PLAN_ID:
            raise StoreError("این پلن قابل خرید نیست.", status=404)
        if not plan.myket_sku:
            raise StoreError("این پلن هنوز در مایکت برای فروش تعریف نشده است.", status=404)
        return plan, None, plan.myket_sku
    if kind == StorePurchase.KIND_ADDON:
        pack = AddOnPack.objects.filter(id=item_id, is_active=True).first()
        if not pack:
            raise StoreError("این بسته قابل خرید نیست.", status=404)
        if not pack.myket_sku:
            raise StoreError("این بسته هنوز در مایکت برای فروش تعریف نشده است.", status=404)
        return None, pack, pack.myket_sku
    raise StoreError("نوع خرید نامعتبر است.")


def create_intent(user, kind, item_id):
    plan, pack, sku = _item(kind, item_id)
    if pack is not None:
        from accounting.payment.addon_payment import NO_SUBSCRIPTION_MESSAGE, can_buy_addon
        if not can_buy_addon(user):
            raise StoreError(NO_SUBSCRIPTION_MESSAGE, status=403)
    purchase = StorePurchase.objects.create(
        user=user, store=StorePurchase.STORE_MYKET, kind=kind,
        plan=plan, pack=pack, sku=sku, payload=secrets.token_hex(16),
    )
    return {"sku": sku, "payload": purchase.payload}


def verify_and_grant(user, payload, sku, token, order_id=""):
    """Verify ``token`` with Myket and grant what it paid for. Returns a result dict."""
    if not token or not sku:
        raise StoreError("اطلاعات خرید ناقص است.")

    # The same token again (a retry after a crash, or the recovery pass on
    # app start) returns the original outcome and never grants twice.
    done = StorePurchase.objects.filter(purchase_token=token, status="success").first()
    if done:
        if done.user_id != user.id:
            raise StoreError("این خرید متعلق به حساب دیگری است.", status=403)
        _consume_if_needed(done)
        return _result(done)

    purchase = StorePurchase.objects.filter(payload=payload, user=user).first() if payload else None
    if purchase is None:
        raise StoreError("سفارش این خرید پیدا نشد.", status=404)
    if purchase.sku != sku:
        raise StoreError("محصول خریداری‌شده با سفارش مطابقت ندارد.")

    verdict = myket_verify(sku, token)
    purchase.store_response = verdict
    purchase.order_id = order_id or purchase.order_id

    returned_payload = verdict.get("developerPayload") or ""
    if verdict.get("purchaseState") != 0:
        return _fail(purchase, "پرداخت در مایکت موفق نبوده است.")
    if returned_payload and returned_payload != purchase.payload:
        return _fail(purchase, "این خرید متعلق به سفارش دیگری است.")
    # We are the only ones who consume; a consumed token we never granted was
    # spent somewhere else.
    if verdict.get("consumptionState") == 1:
        return _fail(purchase, "این خرید قبلاً استفاده شده است.")

    with db_transaction.atomic():
        locked = StorePurchase.objects.select_for_update().get(pk=purchase.pk)
        if locked.status == "success":
            return _result(locked)
        _grant(locked, token, verdict)
        locked.purchase_token = token
        locked.order_id = purchase.order_id
        locked.store_response = verdict
        locked.status = "success"
        locked.error_detail = ""
        locked.save()

    _consume_if_needed(locked)
    return _result(locked)


def _grant(purchase, token, verdict):
    ref = f"myket-{token}"[:100]
    if purchase.kind == StorePurchase.KIND_PLAN:
        from accounting.payment.zibal_payment_verification import activate_plan_transaction
        transaction = Transaction.objects.create(
            user=purchase.user, plan=purchase.plan,
            amount=purchase.plan.discount_price or purchase.plan.price,
            track_id=ref, order_id=f"myket-{purchase.payload}",
            status="pending",
        )
        activate_plan_transaction(transaction, {"store": "myket", **verdict})
    else:
        from accounting.payment.addon_payment import grant_addon_benefit
        addon = AddOnPurchase.objects.create(
            user=purchase.user, pack=purchase.pack, amount=purchase.pack.price,
            track_id=ref, order_id=f"myket-{purchase.payload}", status="pending",
        )
        grant_addon_benefit(addon, {"store": "myket", **verdict})


def _consume_if_needed(purchase):
    if purchase.consumed_at or not purchase.purchase_token:
        return
    if myket_consume(purchase.sku, purchase.purchase_token):
        purchase.consumed_at = timezone.now()
        purchase.save(update_fields=["consumed_at", "updated_at"])


def _fail(purchase, message):
    purchase.status = "failed"
    purchase.error_detail = message
    purchase.save()
    raise StoreError(message)


def _result(purchase):
    name = purchase.plan.name if purchase.plan_id else purchase.pack.name
    return {
        "status": purchase.status,
        "kind": purchase.kind,
        "message": f"«{name}» با موفقیت فعال شد",
    }
