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
REASON_NEW_CLIENT_REWARD = "new_client_reward"
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
    REASON_NEW_CLIENT_REWARD: "پاداش مشتری تازه",
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


# Bookings a client made themselves; owner-entered ones earn nothing.
REWARDED_SOURCES = ("WEB", "APP")


def _phone_tail(phone):
    digits = "".join(ch for ch in str(phone or "") if ch.isdigit())
    return digits[-10:]


# ── Risk flags ────────────────────────────────────────────────────────────
# A fake "new client" costs an owner a phone number that can take an OTP, so
# the reward is withheld when the booking looks like the owner made it. One
# strong flag, or two weak ones, blocks it; the row stays in the admin
# (NewClientReward, status blocked) where staff can still grant it.
FLAG_SAME_IP = "same_ip"                  # strong: booked from an IP the owner uses
FLAG_NEW_ACCOUNT = "new_account"          # weak: client first seen just before, nowhere else
FLAG_QUICK_COMPLETE = "quick_complete"    # weak: marked done right after it was booked
FLAG_BURST = "burst"                      # weak: many new clients in one day
STRONG_FLAGS = {FLAG_SAME_IP}
FLAG_LABELS = {
    FLAG_SAME_IP: "از IP خود صاحب کسب‌وکار ثبت شده",
    FLAG_NEW_ACCOUNT: "مشتری تازه‌ساخت و جای دیگری نوبت نگرفته",
    FLAG_QUICK_COMPLETE: "خیلی زود بعد از ثبت، انجام‌شده زده شده",
    FLAG_BURST: "تعداد زیاد مشتری تازه در یک روز",
}
OWNER_IP_WINDOW = timedelta(days=30)
NEW_ACCOUNT_WINDOW = timedelta(days=1)
QUICK_COMPLETE_WINDOW = timedelta(minutes=30)
BURST_WINDOW = timedelta(days=1)
BURST_COUNT = 5


def risk_flags(appointment, now=None):
    """Why ``appointment`` looks like an owner rewarding themself (list of FLAG_*)."""
    from api.models import UserIpSighting
    from appointment.models import Appointment

    from .models import NewClientReward

    now = now or timezone.now()
    business = appointment.business
    visitor = appointment.visitor
    flags = []
    if appointment.booking_ip and UserIpSighting.objects.filter(
        user_id=business.user_id, ip=appointment.booking_ip, last_seen__gte=now - OWNER_IP_WINDOW,
    ).exists():
        flags.append(FLAG_SAME_IP)
    booked_at = appointment.created_at or now
    if visitor.created_at and visitor.created_at >= booked_at - NEW_ACCOUNT_WINDOW and not (
        Appointment.objects.filter(visitor_id=visitor.pk).exclude(business_id=business.pk).exists()
    ):
        flags.append(FLAG_NEW_ACCOUNT)
    if now - booked_at < QUICK_COMPLETE_WINDOW:
        flags.append(FLAG_QUICK_COMPLETE)
    if NewClientReward.objects.filter(
        business_id=business.pk, created_at__gte=now - BURST_WINDOW,
    ).count() >= BURST_COUNT:
        flags.append(FLAG_BURST)
    return flags


def is_blocked(flags):
    return bool(STRONG_FLAGS.intersection(flags)) or len(flags) >= 2


def reward_new_client(appointment):
    """Credit the owner ``TomanWalletSettings.new_client_reward`` toman for a
    client's first completed booking at the business. Returns the amount given.

    Call it inside the transaction that marks ``appointment`` COMPLETED, with
    the business row locked. Only bookings the client made on the website or
    the app count, never the owner's own phone, and each visitor is considered
    once per business: a ``NewClientReward`` row records the outcome (granted,
    blocked by :func:`risk_flags`, or capped), so deleting the appointment or
    booking again cannot earn it twice. Two optional caps: a lifetime total per
    business and a monthly total per owner. Both are checked with the owner's
    wallet row locked, so two businesses of one owner cannot both slip under
    the monthly one.
    """
    from appointment.models import Appointment

    from .models import CreditBalance, CreditLedger, NewClientReward, TomanWalletSettings

    if appointment.status != "COMPLETED" or appointment.source not in REWARDED_SOURCES:
        return 0
    settings = TomanWalletSettings.load()
    amount = settings.new_client_reward
    if amount <= 0:
        return 0
    business = appointment.business
    owner_id = business.user_id
    visitor = appointment.visitor
    if _phone_tail(visitor.phone_number) == _phone_tail(business.user.phone):
        return 0
    if Appointment.objects.filter(
        business_id=business.pk, visitor_id=visitor.pk, status="COMPLETED",
    ).exclude(pk=appointment.pk).exists():
        return 0

    ref_type = f"business:{business.pk}"
    with transaction.atomic():
        usage._ensure_row(owner_id, METRIC)
        CreditBalance.objects.select_for_update().get(user_id=owner_id, metric=METRIC, period="")
        if NewClientReward.objects.filter(business_id=business.pk, visitor_id=visitor.pk).exists():
            return 0
        rewards = CreditLedger.objects.filter(
            user_id=owner_id, metric=METRIC, reason=REASON_NEW_CLIENT_REWARD,
        )
        if rewards.filter(ref_type=ref_type, ref_id=visitor.pk).exists():
            return 0
        business_cap = settings.new_client_reward_business_cap
        if business_cap > 0:
            given = sum(rewards.filter(ref_type=ref_type).values_list("delta", flat=True))
            amount = min(amount, business_cap - given)
        monthly_cap = settings.new_client_reward_monthly_cap
        if monthly_cap > 0:
            month_start = usage._period_start(usage._period())
            given = sum(rewards.filter(created_at__gte=month_start).values_list("delta", flat=True))
            amount = min(amount, monthly_cap - given)
        flags = risk_flags(appointment)
        if amount <= 0:
            status = NewClientReward.STATUS_CAPPED
        elif is_blocked(flags):
            status = NewClientReward.STATUS_BLOCKED
        else:
            status = NewClientReward.STATUS_GRANTED
        NewClientReward.objects.create(
            owner_id=owner_id, business_id=business.pk, visitor_id=visitor.pk,
            appointment_id=appointment.pk, amount=max(amount, 0), status=status, flags=flags,
        )
        if status != NewClientReward.STATUS_GRANTED:
            logger.warning("New-client reward %s: owner=%s visitor=%s appointment=%s flags=%s",
                           status, owner_id, visitor.pk, appointment.pk, flags)
            return 0
        credit(owner_id, amount, REASON_NEW_CLIENT_REWARD, ref_type=ref_type, ref_id=visitor.pk)
    logger.info("New-client reward: owner=%s visitor=%s appointment=%s amount=%s flags=%s",
                owner_id, visitor.pk, appointment.pk, amount, flags)
    return amount


def grant_blocked_reward(reward, staff_name):
    """Staff decided a blocked reward is genuine: pay it. Returns the amount
    paid, 0 when the row is no longer blocked."""
    from .models import NewClientReward

    with transaction.atomic():
        locked = NewClientReward.objects.select_for_update().get(pk=reward.pk)
        if locked.status != NewClientReward.STATUS_BLOCKED or locked.amount <= 0:
            return 0
        credit(locked.owner_id, locked.amount, REASON_NEW_CLIENT_REWARD,
               ref_type=f"business:{locked.business_id}", ref_id=locked.visitor_id)
        locked.status = NewClientReward.STATUS_GRANTED
        locked.decided_by = staff_name[:150]
        locked.save(update_fields=["status", "decided_by"])
    return locked.amount


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
