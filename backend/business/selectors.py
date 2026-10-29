"""Read-side query helpers for the public directory.

The public list/detail used to cost ~6 queries *per business* (subscription
lookup, entitlements, add-ons, credit balances). Everything the serializer
needs per row is now resolved for the whole page in a constant number of
queries and attached to the objects before serialization:

  * ``business.has_active_subscription`` -> serializer's ``is_expired``
  * ``business.can_book``                -> serializer's booking-block rule

The serializer falls back to the old per-object path when those attributes are
absent, so other callers keep working unchanged.
"""
from django.core.cache import cache
from django.db.models import Max
from django.utils import timezone

from accounting import entitlements, usage

from .models import Business


def public_businesses(extra_filter=None):
    """Public-visible businesses with their insurances prefetched (1 extra query)."""
    qs = Business.objects.filter(Business.public_filter())
    if extra_filter is not None:
        qs = qs.filter(extra_filter)
    return qs.prefetch_related('insurances')


# ── Batched owner state ───────────────────────────────────────────────────────

def _active_subscriptions(user_ids):
    """{user_id: Subscription} mirroring entitlements.get_active_subscription():
    the newest status='active' row per user, kept only if still valid."""
    from accounting.models import Subscription

    now = timezone.now()
    newest = {}
    rows = (
        Subscription.objects.filter(user_id__in=user_ids, status='active')
        .select_related('plan')
        .order_by('-started_at')
    )
    for sub in rows:
        newest.setdefault(sub.user_id, sub)
    return {uid: sub for uid, sub in newest.items() if sub.ends_at > now}


def _feature_addons(user_ids):
    """{user_id: {feature_key,...}} mirroring entitlements._active_feature_addons()."""
    from accounting.models import AddOnPack, AddOnPurchase

    out = {}
    rows = AddOnPurchase.objects.filter(
        user_id__in=user_ids, status='success',
        pack__kind=AddOnPack.KIND_FEATURE, expires_at__gt=timezone.now(),
    ).values_list('user_id', 'pack__feature_key')
    for uid, key in rows:
        if key:
            out.setdefault(uid, set()).add(key)
    return out


def _balances(user_ids, db_metric, period=''):
    """{user_id: balance} mirroring usage._read_balance(): the CreditBalance row
    when it exists, otherwise the legacy value (cache, then latest ledger row)
    - all batched."""
    from accounting.models import CreditBalance, CreditLedger

    user_ids = list(user_ids)
    if not user_ids:
        return {}
    out = {
        uid: int(bal) for uid, bal in CreditBalance.objects.filter(
            user_id__in=user_ids, metric=db_metric, period=period,
        ).values_list('user_id', 'balance')
    }
    missing = [uid for uid in user_ids if uid not in out]
    if not missing:
        return out

    # Legacy Redis value.
    keys = {usage._legacy_redis_key(uid, db_metric, period): uid for uid in missing}
    try:
        cached = cache.get_many(list(keys))
    except Exception:
        cached = {}
    for key, raw in cached.items():
        if raw is None:
            continue
        try:
            out[keys[key]] = max(0, int(raw)) if period else int(raw)
        except (TypeError, ValueError):
            pass
    missing = [uid for uid in missing if uid not in out]
    if not missing:
        return out

    # Latest ledger row (append-only, so highest id == latest).
    ledger = CreditLedger.objects.filter(user_id__in=missing, metric=db_metric)
    if period:
        ledger = ledger.filter(created_at__gte=usage._period_start(period))
    latest_ids = list(ledger.values('user_id').annotate(m=Max('id')).values_list('m', flat=True))
    if latest_ids:
        for uid, after in CreditLedger.objects.filter(id__in=latest_ids).values_list('user_id', 'balance_after'):
            out[uid] = int(after)
    for uid in missing:
        out.setdefault(uid, 0)
    return out


def owner_booking_state(user_ids, need_capacity=None):
    """Resolve, for a set of owner user ids, in a constant number of queries:

    ``{user_id: (has_active_subscription, can_book)}``

    ``can_book`` is accounting.usage.can_book_appointment() for the same user;
    it is only computed for ids in ``need_capacity`` (default: all) and is
    ``False`` placeholder otherwise.
    """
    user_ids = set(user_ids)
    if not user_ids:
        return {}
    need = user_ids if need_capacity is None else (set(need_capacity) & user_ids)

    subs = _active_subscriptions(user_ids)
    addons = _feature_addons(need) if need else {}

    quotas = {}
    for uid in need:
        sub = subs.get(uid)
        ent = entitlements.resolve_entitlements(sub.plan.features if sub else None)
        for feature_key in addons.get(uid, ()):
            ent[feature_key] = True
        quotas[uid] = int(ent.get(entitlements.QUOTA_MONTHLY_APPOINTMENTS, 0))

    can_book = {}
    limited = [uid for uid, q in quotas.items() if q != entitlements.UNLIMITED]
    for uid, q in quotas.items():
        if q == entitlements.UNLIMITED:
            can_book[uid] = True
    if limited:
        used = _balances(limited, usage._DB_MONTHLY[usage.METRIC_APPOINTMENTS], usage._period())
        exhausted = []
        for uid in limited:
            if used.get(uid, 0) < quotas[uid]:
                can_book[uid] = True
            else:
                exhausted.append(uid)
        if exhausted:
            wallet = _balances(exhausted, usage._DB_WALLET_APPT)
            for uid in exhausted:
                can_book[uid] = wallet.get(uid, 0) > 0

    return {uid: (uid in subs, can_book.get(uid, False)) for uid in user_ids}


def attach_owner_state(businesses):
    """Annotate each business with ``has_active_subscription`` / ``can_book``
    (one batch for the whole list) and return the list."""
    businesses = list(businesses)
    need = {b.user_id for b in businesses if b.is_claimed and b.booking_enabled}
    state = owner_booking_state({b.user_id for b in businesses if b.is_claimed} | need, need)
    for b in businesses:
        sub, can = state.get(b.user_id, (False, False))
        b.has_active_subscription = sub
        b.can_book = can
    return businesses
