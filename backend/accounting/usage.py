"""
accounting/usage.py

Usage metering for plan quotas. The DATABASE (``CreditBalance``) is the source
of truth; Redis is no longer authoritative.

Two kinds of state:

  * Monthly counters — appointments / SMS consumed in the current month.
    Row: ``CreditBalance(user, metric='appointment_monthly'|'sms_monthly',
    period='YYYY-MM')``; ``balance`` is the CONSUMED count. A new month is a
    new row (counter "resets" by absence).

  * Wallets — persistent, non-expiring credit bought via add-on packs.
    Row: ``CreditBalance(user, metric='sms_wallet'|'appointment_wallet',
    period='')``; ``balance`` is credit remaining.

Every mutation is one conditional ``UPDATE ... balance = balance +/- n WHERE
<guard>`` (wallet: ``balance >= n``; quota: ``balance <= limit - n``), so two
concurrent callers can never spend the same credit or exceed a quota, and
a Redis outage / crash can no longer lose paid credit. Mutations run inside
``transaction.atomic`` together with their CreditLedger row, and they join the
caller's transaction: a booking that rolls back also gives its credit back.

Redis is kept only as a write-through MIRROR of the two wallets (set after
commit) because ``core/segments.py`` still reads ``sms_wallet:{id}`` straight
from the cache. Reads in this module go to the DB. The mirror is best-effort.

Lazy initialisation: the first time a bucket is touched and has no row, it is
seeded from the legacy Redis value (or, if Redis has none, from the latest
CreditLedger row) so deploying this code never shows users a 0 balance.
``manage.py migrate_credits_to_db`` just pre-warms those rows.

All counters are keyed on the **business owner's** user id, because quotas belong
to the plan the owner pays for (a client booking consumes the owner's quota).
"""

import logging
from datetime import datetime, timezone as dt_timezone

from django.core.cache import cache
from django.db import transaction
from django.db.models import F
from django.utils import timezone

from . import entitlements

logger = logging.getLogger(__name__)

# Metrics
METRIC_APPOINTMENTS = "appointments"
METRIC_SMS = "sms"

# CreditBalance / CreditLedger metric names
_DB_WALLET_SMS = "sms_wallet"
_DB_WALLET_APPT = "appointment_wallet"
_DB_MONTHLY = {
    METRIC_APPOINTMENTS: "appointment_monthly",
    METRIC_SMS: "sms_monthly",
}

# Which bucket an appointment credit was drawn from, so a cancellation can put
# it back where it came from instead of guessing.
SOURCE_MONTHLY = "monthly"
SOURCE_WALLET = "wallet"

# Legacy Redis monthly key TTL (only used by the migration/backfill reads now).
_MONTHLY_TTL = 60 * 60 * 24 * 62


def _period():
    return datetime.now(dt_timezone.utc).strftime("%Y-%m")


def _uid(user_or_id):
    return user_or_id.id if hasattr(user_or_id, "id") else user_or_id


def _month_key(user_id, metric, period=None):
    return f"usage:{user_id}:{metric}:{period or _period()}"


def _wallet_key(user_id):
    return f"sms_wallet:{user_id}"


def _appt_wallet_key(user_id):
    return f"appt_wallet:{user_id}"


def _legacy_redis_key(user_id, db_metric, period):
    if db_metric == _DB_WALLET_SMS:
        return _wallet_key(user_id)
    if db_metric == _DB_WALLET_APPT:
        return _appt_wallet_key(user_id)
    for usage_metric, m in _DB_MONTHLY.items():
        if m == db_metric:
            return _month_key(user_id, usage_metric, period)
    raise ValueError(db_metric)


def _period_start(period):
    year, month = period.split("-")
    return datetime(int(year), int(month), 1, tzinfo=dt_timezone.utc)


def _legacy_value(user_id, db_metric, period):
    """
    The value this bucket should start from when it has no CreditBalance row:
    the legacy Redis value if present, else the latest CreditLedger row
    (``balance_after``; for monthly buckets only a row from ``period``), else 0.
    Never raises.
    """
    try:
        raw = cache.get(_legacy_redis_key(user_id, db_metric, period))
        if raw is not None:
            return max(0, int(raw)) if period else int(raw)
    except Exception:
        logger.warning("Redis read failed while seeding credit balance", exc_info=True)
    try:
        from .models import CreditLedger

        qs = CreditLedger.objects.filter(user_id=user_id, metric=db_metric)
        if period:
            qs = qs.filter(created_at__gte=_period_start(period))
        latest = qs.order_by("-created_at", "-id").first()
        if latest is not None:
            return int(latest.balance_after)
    except Exception:
        logger.exception("Ledger read failed while seeding credit balance")
    return 0


def _read_balance(user_id, db_metric, period=""):
    """Current balance; falls back to the legacy value for an un-seeded bucket (no write)."""
    from .models import CreditBalance

    value = (
        CreditBalance.objects.filter(user_id=user_id, metric=db_metric, period=period)
        .values_list("balance", flat=True)
        .first()
    )
    if value is not None:
        return int(value)
    return _legacy_value(user_id, db_metric, period)


def _ensure_row(user_id, db_metric, period=""):
    """Make sure the bucket's row exists (seeding it lazily). Race-safe."""
    from .models import CreditBalance

    if CreditBalance.objects.filter(user_id=user_id, metric=db_metric, period=period).exists():
        return
    # get_or_create handles a concurrent creator (IntegrityError -> re-get).
    CreditBalance.objects.get_or_create(
        user_id=user_id, metric=db_metric, period=period,
        defaults={"balance": _legacy_value(user_id, db_metric, period)},
    )


def _mirror_wallet(user_id, db_metric, value):
    key = {_DB_WALLET_SMS: _wallet_key, _DB_WALLET_APPT: _appt_wallet_key}.get(db_metric)
    if key is None:
        return

    def _set():
        try:
            cache.set(key(user_id), value, timeout=None)
        except Exception:
            logger.warning("Redis wallet mirror write failed", exc_info=True)

    transaction.on_commit(_set)


def _apply(user_id, db_metric, delta, period="", floor=None, cap=None,
           reason=None, ref_type="", ref_id=None):
    """
    The one atomic mutation primitive. ``balance += delta`` only if the guard
    holds (``floor``: balance >= floor; ``cap``: balance <= cap). Returns the
    new balance, or None when the guard rejected it. Writes the ledger row (when
    ``reason`` is given) in the same transaction.
    """
    from .models import CreditBalance

    _ensure_row(user_id, db_metric, period)
    with transaction.atomic():
        qs = CreditBalance.objects.filter(user_id=user_id, metric=db_metric, period=period)
        if floor is not None:
            qs = qs.filter(balance__gte=floor)
        if cap is not None:
            qs = qs.filter(balance__lte=cap)
        if not qs.update(balance=F("balance") + delta, updated_at=timezone.now()):
            return None
        # The UPDATE holds the row lock until commit, so this read is ours.
        new_value = int(
            CreditBalance.objects.filter(user_id=user_id, metric=db_metric, period=period)
            .values_list("balance", flat=True).get()
        )
        if reason:
            _write_ledger(user_id, db_metric, delta, new_value, reason, ref_type=ref_type, ref_id=ref_id)
        _mirror_wallet(user_id, db_metric, new_value)
        return new_value


# ── Ledger (audit trail) ────────────────────────────────────────────────────
#
# CreditLedger is the append-only history next to CreditBalance. It is written
# in the SAME transaction as the balance change (see ``_apply``), inside a
# savepoint so a ledger failure cannot poison the caller's transaction. The
# balance (the thing that gates behaviour and holds real money) stays correct
# even if the ledger row fails, so the failure is logged at ERROR rather than
# blocking a booking / SMS; operators should alert on that log line.

# Reason codes, as constants rather than free-form strings at each call site
# so a typo can never silently mint an new, unqueryable reason.
REASON_BOOKING = "booking"
REASON_CANCELLATION_REFUND = "cancellation_refund"
REASON_SMS_SEND = "sms_send"
REASON_SMS_REFUND = "sms_refund"
REASON_WALLET_CREDIT = "wallet_credit"
REASON_WALLET_DEBIT = "wallet_debit"


def _write_ledger(user_id, metric, delta, balance_after, reason, ref_type="", ref_id=None):
    """
    Ledger write that never raises (failures are logged at ERROR). Imported lazily so this module
    (imported early by async views, management commands, etc.) does not carry
    a module-level ORM dependency that needs the app registry ready.

    Some call sites (e.g. appointment cancellation in
    appointment/views/views.py) run inside an outer ``@transaction.atomic``
    block and write to the same row again right after this returns. On
    Postgres, a DB-level failure during the ``create()`` — a deadlock, a
    connection blip — aborts the *whole* transaction at the protocol level
    the instant it happens, regardless of whether Python catches the
    exception: the bare ``except Exception`` below stops the ledger error from
    propagating, but the connection itself is left unable to execute anything
    else until the transaction ends, so the very next statement in the outer
    block (the appointment save) would raise anyway and roll back a real
    change that Redis has no way to undo. Wrapping the write in its own nested
    ``atomic()`` makes Django open a SAVEPOINT for it; catching the exception
    while still inside that block rolls back only to the savepoint, leaving
    the outer transaction free to continue. Cheap and correct even when this
    function is called with no outer transaction at all — a top-level atomic()
    just behaves like a normal transaction.
    """
    try:
        with transaction.atomic():
            from .models import CreditLedger

            CreditLedger.objects.create(
                user_id=user_id,
                metric=metric,
                delta=delta,
                balance_after=balance_after,
                reason=reason,
                ref_type=ref_type or "",
                ref_id=ref_id,
            )
    except Exception:
        logger.error(
            "CreditLedger write FAILED (user=%s metric=%s delta=%s reason=%s) "
            "— CreditBalance is correct, the audit trail has a gap",
            user_id, metric, delta, reason,
            exc_info=True,
        )


# ── Monthly counters ──────────────────────────────────────────────────────────

def get_usage(user_or_id, metric):
    """How much of ``metric`` has been consumed this month."""
    return _read_balance(_uid(user_or_id), _DB_MONTHLY[metric], _period())


def add_usage(user_or_id, metric, amount=1):
    """Atomically increment this month's counter (uncapped) and return the new value."""
    return _apply(_uid(user_or_id), _DB_MONTHLY[metric], amount, period=_period())


def _consume_monthly(user_id, metric, quota, amount, reason):
    """
    Take up to ``amount`` from this month's allowance, atomically and never
    past ``quota``. Returns how many units were actually taken (0..amount).
    """
    db_metric = _DB_MONTHLY[metric]
    period = _period()
    # Fast path: the whole amount fits -> one guarded UPDATE, no lock.
    if _apply(user_id, db_metric, amount, period=period, cap=quota - amount, reason=reason) is not None:
        return amount
    # Partial path: take whatever room is left, under a row lock.
    from .models import CreditBalance

    with transaction.atomic():
        row = CreditBalance.objects.select_for_update().get(
            user_id=user_id, metric=db_metric, period=period)
        take = min(amount, max(0, quota - row.balance))
        if take <= 0:
            return 0
        _apply(user_id, db_metric, take, period=period, reason=reason)
        return take


def within_quota(user_or_id, metric, quota):
    """True if there is still room this month (``quota`` = -1 means unlimited)."""
    if quota == entitlements.UNLIMITED:
        return True
    return get_usage(user_or_id, metric) < quota


def remaining(user_or_id, metric, quota):
    """Remaining monthly allowance; -1 for unlimited."""
    if quota == entitlements.UNLIMITED:
        return entitlements.UNLIMITED
    return max(0, quota - get_usage(user_or_id, metric))


# ── SMS wallet (add-on credit) ────────────────────────────────────────────────

def get_wallet(user_or_id):
    return _read_balance(_uid(user_or_id), _DB_WALLET_SMS)


def _wallet_add(user_id, db_metric, amount, reason, ref_type, ref_id):
    return _apply(
        user_id, db_metric, amount,
        reason=reason or (REASON_WALLET_CREDIT if amount >= 0 else REASON_WALLET_DEBIT),
        ref_type=ref_type, ref_id=ref_id,
    )


def add_wallet(user_or_id, amount, reason=None, ref_type="", ref_id=None):
    """
    Add (or with a negative amount, unconditionally debit) persistent SMS credit.
    Atomic in the DB; survives any Redis outage. Returns the new balance.

    ``reason``/``ref_type``/``ref_id`` are optional so every existing caller
    keeps working unchanged; pass them when you have real context (e.g. an
    ``AddOnPurchase`` id). When omitted, the reason is inferred from the sign.
    """
    return _wallet_add(_uid(user_or_id), _DB_WALLET_SMS, amount, reason, ref_type, ref_id)


def _spend_wallet(user_id, db_metric, amount, reason):
    """Guarded spend: True only if the wallet held >= amount."""
    return _apply(user_id, db_metric, -amount, floor=amount, reason=reason) is not None


# ── Appointment wallet (add-on credit) ────────────────────────────────────────

def get_appt_wallet(user_or_id):
    return _read_balance(_uid(user_or_id), _DB_WALLET_APPT)


def add_appt_wallet(user_or_id, amount, reason=None, ref_type="", ref_id=None):
    """Appointment-wallet twin of :func:`add_wallet` (same contract)."""
    return _wallet_add(_uid(user_or_id), _DB_WALLET_APPT, amount, reason, ref_type, ref_id)


# ── Appointment quota (monthly allowance first, then wallet) ──────────────────

def can_book_appointment(owner_or_id):
    """
    Advisory pre-check: True if the monthly allowance has room or the
    appointment wallet has credit. Not a reservation — :func:`record_appointment`
    makes the binding, atomic decision.
    """
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_APPOINTMENTS)
    if within_quota(owner_or_id, METRIC_APPOINTMENTS, quota):
        return True
    return get_appt_wallet(owner_or_id) > 0


def record_appointment(owner_or_id):
    """
    Consume one appointment credit atomically: this month's plan allowance
    first (guarded ``balance < quota`` UPDATE), then the wallet (guarded
    ``balance >= 1`` UPDATE).

    Returns which bucket paid (:data:`SOURCE_MONTHLY` / :data:`SOURCE_WALLET`),
    or ``""`` when neither could pay — even if the caller's earlier
    :func:`can_book_appointment` said yes (another request got there first).
    Keep the returned value for :func:`release_appointment`.
    """
    user_id = _uid(owner_or_id)
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_APPOINTMENTS)
    if quota == entitlements.UNLIMITED:
        add_usage(user_id, METRIC_APPOINTMENTS, 1)
        _ledger_monthly(user_id, METRIC_APPOINTMENTS)
        return SOURCE_MONTHLY
    if _consume_monthly(user_id, METRIC_APPOINTMENTS, quota, 1, REASON_BOOKING):
        return SOURCE_MONTHLY
    if _spend_wallet(user_id, _DB_WALLET_APPT, 1, REASON_BOOKING):
        return SOURCE_WALLET
    return ""


def _ledger_monthly(user_id, metric):
    # Unlimited plans still keep a ledger row per consumption.
    _write_ledger(user_id, _DB_MONTHLY[metric], 1, get_usage(user_id, metric),
                  REASON_BOOKING if metric == METRIC_APPOINTMENTS else REASON_SMS_SEND)


def release_appointment(owner_or_id, source, booked_at=None):
    """
    Give back the credit a cancelled appointment consumed.

    ``source`` is the value :func:`record_appointment` returned for it, and
    ``booked_at`` is when the booking was made. Returns True if a credit was
    actually returned.

    Wallet credit is bought with money and never expires, so it is always
    refundable. The monthly counter is per month: refunding a booking made in
    an earlier month would hand back an allowance that month never spent.
    """
    if source == SOURCE_WALLET:
        add_appt_wallet(owner_or_id, 1, reason=REASON_CANCELLATION_REFUND)
        return True

    if source != SOURCE_MONTHLY:
        return False

    booked_period = (
        booked_at.astimezone(dt_timezone.utc).strftime("%Y-%m") if booked_at else _period()
    )
    if booked_period != _period():
        return False

    return _decrement_usage(
        _uid(owner_or_id), METRIC_APPOINTMENTS, booked_period,
        ledger_metric="appointment_monthly", reason=REASON_CANCELLATION_REFUND,
    )


def _decrement_usage(user_id, metric, period, amount=1, ledger_metric=None, reason=None):
    """
    Give back units of a month's counter, never dropping below zero.
    Returns True if anything was returned.
    """
    from .models import CreditBalance

    db_metric = _DB_MONTHLY[metric]
    _ensure_row(user_id, db_metric, period)
    with transaction.atomic():
        row = CreditBalance.objects.select_for_update().get(
            user_id=user_id, metric=db_metric, period=period)
        actual = min(amount, row.balance)
        if actual <= 0:
            return False
        _apply(user_id, db_metric, -actual, period=period, floor=actual,
               reason=reason if ledger_metric else None)
        return True


def appointment_balance(owner_or_id):
    """Convenience for dashboards: {'quota', 'monthly_remaining', 'wallet', 'used'}."""
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_APPOINTMENTS)
    return {
        "quota": quota,
        "used": get_usage(owner_or_id, METRIC_APPOINTMENTS),
        "monthly_remaining": remaining(owner_or_id, METRIC_APPOINTMENTS, quota),
        "wallet": get_appt_wallet(owner_or_id),
    }


# ── SMS quota (monthly allowance first, then wallet) ──────────────────────────

def _sms_receipt(owner_or_id, monthly, wallet):
    return {
        "owner": _uid(owner_or_id),
        SOURCE_MONTHLY: monthly,
        SOURCE_WALLET: wallet,
        "period": _period(),
    }


class _OutOfCredit(Exception):
    pass


def consume_sms(owner_or_id, amount=1):
    """
    Atomically consume ``amount`` SMS credits: first from this month's plan
    allowance (never past the quota), the shortfall from the wallet (never
    below zero). All-or-nothing: if the wallet cannot cover the shortfall the
    monthly part is rolled back too.

    Returns a receipt recording which buckets paid, or ``None`` when the owner
    is out of credit (caller should skip sending). Hand the receipt to
    :func:`refund_sms` if the send then fails.
    """
    owner_id = _uid(owner_or_id)
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_SMS)
    if quota == entitlements.UNLIMITED:
        with transaction.atomic():
            new_value = add_usage(owner_id, METRIC_SMS, amount)
            _write_ledger(owner_id, "sms_monthly", amount, new_value, REASON_SMS_SEND)
        return _sms_receipt(owner_or_id, monthly=amount, wallet=0)

    try:
        with transaction.atomic():
            taken = _consume_monthly(owner_id, METRIC_SMS, quota, amount, REASON_SMS_SEND)
            shortfall = amount - taken
            if shortfall and not _spend_wallet(owner_id, _DB_WALLET_SMS, shortfall, REASON_SMS_SEND):
                raise _OutOfCredit
    except _OutOfCredit:
        return None
    return _sms_receipt(owner_or_id, monthly=taken, wallet=shortfall)


def refund_sms(receipt):
    """
    Give back the credit a failed SMS consumed, using the receipt
    :func:`consume_sms` returned. Returns True if anything was actually returned.

    Wallet credit is always refundable; the monthly part only if the charge
    happened in the current month (the counter has reset otherwise).
    """
    if not receipt:
        return False

    owner = receipt.get("owner")
    monthly = int(receipt.get(SOURCE_MONTHLY) or 0)
    wallet = int(receipt.get(SOURCE_WALLET) or 0)
    refunded = False

    if wallet:
        add_wallet(owner, wallet, reason=REASON_SMS_REFUND)
        refunded = True

    period = receipt.get("period") or _period()
    if monthly and period == _period():
        refunded = _decrement_usage(
            owner, METRIC_SMS, period, monthly,
            ledger_metric="sms_monthly", reason=REASON_SMS_REFUND,
        ) or refunded

    return refunded


def sms_balance(owner_or_id):
    """Convenience for dashboards: {'monthly_remaining', 'wallet', 'quota', 'skipped_this_month'}.

    ``skipped_this_month`` counts SmsLog rows with status='SKIPPED_QUOTA' this
    (UTC, matching :func:`_period`) month. Uses a half-open date range so the
    ``sent_at`` index can be used.
    """
    from visitor.models import SmsLog

    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_SMS)
    owner_id = _uid(owner_or_id)
    now = datetime.now(dt_timezone.utc)
    start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    next_start = (
        start.replace(year=start.year + 1, month=1) if start.month == 12
        else start.replace(month=start.month + 1)
    )
    skipped_this_month = SmsLog.objects.filter(
        business__user_id=owner_id,
        status='SKIPPED_QUOTA',
        sent_at__gte=start,
        sent_at__lt=next_start,
    ).count()
    return {
        "quota": quota,
        "monthly_remaining": remaining(owner_or_id, METRIC_SMS, quota),
        "wallet": get_wallet(owner_or_id),
        "skipped_this_month": skipped_this_month,
    }
