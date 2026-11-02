"""
accounting/toman_wallet.py

The owner's toman wallet: non-cash money Noobatyar gives back to the owner
(for example a booking discount Noobatyar funds). It is never paid out. The
owner spends it on a plan or an add-on pack, all of the price or part of it,
or converts it into SMS / booking credit at the prices in
``TomanWalletSettings``.

Balance and history reuse ``CreditBalance`` / ``CreditLedger`` (metric
``toman_wallet``) through ``usage._apply``, so every change is one guarded
UPDATE plus a ledger row in the same transaction; it can never go negative.

Other apps credit it with :func:`credit`, passing their own reason code and a
``ref_type``/``ref_id`` pointing at what caused it.
"""

import logging
import uuid
from datetime import timedelta

from django.db import transaction
from django.utils import timezone

from . import pricing, usage

logger = logging.getLogger(__name__)

METRIC = usage._DB_WALLET_TOMAN

REASON_DISCOUNT_REIMBURSEMENT = "discount_reimbursement"
REASON_ADMIN_CREDIT = "admin_credit"
REASON_ADMIN_DEBIT = "admin_debit"
REASON_PLAN_PURCHASE = "plan_purchase"
REASON_ADDON_PURCHASE = "addon_purchase"
REASON_PURCHASE_REFUND = "purchase_refund"
REASON_CONVERT = "convert"
# Credit rows written by a conversion carry this on the SMS/booking wallet.
REASON_CONVERTED_FROM_TOMAN = "toman_conversion"

REASON_LABELS = {
    REASON_DISCOUNT_REIMBURSEMENT: "بازگشت تخفیف نوبت",
    REASON_ADMIN_CREDIT: "افزایش موجودی",
    REASON_ADMIN_DEBIT: "کسر موجودی",
    REASON_PLAN_PURCHASE: "خرید اشتراک",
    REASON_ADDON_PURCHASE: "خرید بسته افزودنی",
    REASON_PURCHASE_REFUND: "بازگشت مبلغ خرید ناموفق",
    REASON_CONVERT: "تبدیل به اعتبار",
    REASON_CONVERTED_FROM_TOMAN: "تبدیل از کیف‌پول",
}

KIND_SMS = "sms"
KIND_APPOINTMENT = "appointment"

# A purchase whose gateway payment is still pending after this long is treated
# as abandoned and its wallet part is given back (run_subscription_lifecycle,
# hourly). Zibal's payment page has expired long before, so an hour only keeps
# the owner's balance locked for no reason.
STALE_HOLD_AFTER = timedelta(hours=1)


class WalletError(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message


def get_balance(user_or_id):
    return usage._read_balance(usage._uid(user_or_id), METRIC)


def credit(user_or_id, amount, reason=REASON_ADMIN_CREDIT, ref_type="", ref_id=None):
    """Add ``amount`` toman. Returns the new balance."""
    amount = int(amount)
    if amount <= 0:
        raise ValueError("credit amount must be positive")
    return usage._apply(usage._uid(user_or_id), METRIC, amount, reason=reason, ref_type=ref_type, ref_id=ref_id)


def spend(user_or_id, amount, reason, ref_type="", ref_id=None):
    """Take ``amount`` toman if the balance covers it. Returns the new balance,
    or None when it does not (nothing is taken)."""
    amount = int(amount)
    if amount <= 0:
        raise ValueError("spend amount must be positive")
    return usage._apply(
        usage._uid(user_or_id), METRIC, -amount, floor=amount,
        reason=reason, ref_type=ref_type, ref_id=ref_id,
    )


def with_vat(part):
    """What the wallet is charged for covering ``part`` toman of a price: the
    part plus its VAT. VAT is owed on the whole price however it is paid; only
    the gateway fee is skipped for the wallet's share."""
    part = int(part)
    return part + round(part * pricing.VAT_RATE) if part > 0 else 0


def wallet_part(base, balance, channel=pricing.CHANNEL_ZIBAL):
    """How much of a ``base``-toman price (before VAT) the wallet should cover.
    The wallet is charged ``with_vat`` of it.

    The rest goes through the gateway with VAT and fee on top, so a tiny
    remainder would mostly be fee: the wallet leaves at least
    ``MIN_SELLABLE_PRICE`` for the gateway unless it covers everything. A store
    (Myket) price is fixed per SKU, so there the wallet pays all or nothing.
    """
    base, balance = int(base), max(int(balance), 0)
    if base <= 0 or balance <= 0:
        return 0
    if balance >= with_vat(base):
        return base
    if channel != pricing.CHANNEL_ZIBAL:
        return 0
    # Largest part whose VAT-inclusive charge the balance still covers.
    part = balance * 10 // 11
    while with_vat(part + 1) <= balance:
        part += 1
    while part > 0 and with_vat(part) > balance:
        part -= 1
    return max(0, min(part, base - pricing.MIN_SELLABLE_PRICE))


WALLET_CHANGED_MESSAGE = "موجودی کیف‌پول تغییر کرده است؛ دوباره تلاش کنید"


def quote_with_wallet(base, channel, balance):
    """``pricing.quote`` after the wallet part. VAT is on the whole price; the
    gateway fee only on what the gateway collects. Adds ``wallet`` (toman taken
    from the wallet, its VAT included) and keeps ``base`` as the item's full
    price, so base + vat − wallet + fee = total."""
    used = wallet_part(base, balance, channel)
    data = pricing.quote(int(base) - used, channel)
    data["base"] = int(base)
    data["wallet"] = with_vat(used)
    data["vat"] += data["wallet"] - used
    return data


def hold(model, ref_type, wallet, reason, **fields):
    """Create a pending ``model`` row (Transaction / AddOnPurchase) and take
    ``wallet`` toman for it, both in one transaction: either the row exists with
    the money held on it, or neither happened (WalletError). The row gets a
    placeholder track id until the gateway hands out the real one."""
    with transaction.atomic():
        row = model.objects.create(
            track_id=f"HOLD-{uuid.uuid4().hex}", status="pending", wallet_amount=wallet, **fields,
        )
        if spend(row.user_id, wallet, reason, ref_type=ref_type, ref_id=row.pk) is None:
            raise WalletError("موجودی کیف‌پول کافی نیست")
    return row


def cancel_hold(row, ref_type):
    """The gateway refused the payment: cancel the row and return its wallet part."""
    type(row).objects.filter(pk=row.pk, status="pending").update(status="cancelled")
    return give_back(row, ref_type)


def give_back(purchase, ref_type):
    """Return the wallet part of a purchase that did not go through. Idempotent:
    zeroes ``wallet_amount`` in the same transaction, so it runs once."""
    with transaction.atomic():
        locked = type(purchase).objects.select_for_update().get(pk=purchase.pk)
        amount = locked.wallet_amount
        if not amount:
            return 0
        type(purchase).objects.filter(pk=locked.pk).update(wallet_amount=0)
        credit(locked.user_id, amount, reason=REASON_PURCHASE_REFUND, ref_type=ref_type, ref_id=locked.pk)
    purchase.wallet_amount = 0
    return amount


def unit_prices():
    from .models import TomanWalletSettings

    s = TomanWalletSettings.load()
    return {KIND_SMS: s.sms_unit_price, KIND_APPOINTMENT: s.appointment_unit_price}


def convert(user, kind, count):
    """Turn toman into ``count`` SMS or booking credits. Returns
    ``{'cost', 'count', 'kind', 'balance'}`` or raises WalletError."""
    if kind not in (KIND_SMS, KIND_APPOINTMENT):
        raise WalletError("نوع اعتبار نامعتبر است")
    try:
        count = int(count)
    except (TypeError, ValueError):
        raise WalletError("تعداد نامعتبر است")
    if count <= 0:
        raise WalletError("تعداد باید بیشتر از صفر باشد")
    price = unit_prices()[kind]
    if price <= 0:
        raise WalletError("تبدیل این اعتبار فعلاً ممکن نیست")
    cost = price * count

    with transaction.atomic():
        balance = spend(user, cost, REASON_CONVERT, ref_type=kind, ref_id=count)
        if balance is None:
            raise WalletError("موجودی کیف‌پول کافی نیست")
        add = usage.add_wallet if kind == KIND_SMS else usage.add_appt_wallet
        add(user, count, reason=REASON_CONVERTED_FROM_TOMAN, ref_type="toman", ref_id=cost)
    return {"kind": kind, "count": count, "cost": cost, "balance": balance}


def release_stale_holds(now=None):
    """Give back the wallet part of purchases whose payment never finished.
    Marks them cancelled first: verification refuses a cancelled row, so a
    late callback cannot grant the item with the wallet part already returned."""
    from .models import AddOnPurchase, Transaction

    cutoff = (now or timezone.now()) - STALE_HOLD_AFTER
    released = 0
    for model, ref_type in ((Transaction, "Transaction"), (AddOnPurchase, "AddOnPurchase")):
        for row in model.objects.filter(status="pending", wallet_amount__gt=0, created_at__lt=cutoff):
            if model.objects.filter(pk=row.pk, status="pending").update(status="cancelled"):
                released += give_back(row, ref_type)
    return released
