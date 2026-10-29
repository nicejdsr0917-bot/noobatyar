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
from dataclasses import dataclass, field
from typing import Optional

from django.core.cache import cache
from django.core.paginator import Paginator
from django.db.models import Exists, Max, OuterRef, Q
from django.utils import timezone

from accounting import entitlements, usage

from .geo import MAX_RADIUS_KM, geo_queryset, haversine_km
from .iran_cities import city_filter
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


# ── Public directory search ───────────────────────────────────────────────────

def _float_or_none(raw):
    try:
        return float(raw)
    except (ValueError, TypeError):
        return None


def _positive_int(raw, default):
    try:
        return int(raw)
    except (ValueError, TypeError):
        return default


@dataclass(frozen=True)
class PublicSearchParams:
    """Normalised query parameters of the public business list/search.

    ``from_query`` applies the endpoint's lenient rules (bad values fall back to
    defaults, never to a 400), so two requests that mean the same thing compare
    equal - which is also what the response cache key relies on.
    """
    page: int = 1
    page_size: int = 10
    search: str = ''
    category: str = ''
    insurance: str = ''
    city: str = ''
    city_code: str = ''
    only_claimed: bool = False
    lat: Optional[float] = None
    lng: Optional[float] = None
    radius_km: Optional[float] = None
    include_unlocated: bool = False
    sort: str = 'recommended'

    @classmethod
    def from_query(cls, qp):
        page = max(1, _positive_int(qp.get('page', 1), 1))
        page_size = min(max(1, _positive_int(qp.get('page_size', 10), 10)), 100)

        lat = _float_or_none(qp.get('lat')) if qp.get('lat') is not None else None
        lng = _float_or_none(qp.get('lng')) if qp.get('lng') is not None else None
        if qp.get('lat') is None or qp.get('lng') is None \
                or lat is None or lng is None \
                or not (-90.0 <= lat <= 90.0 and -180.0 <= lng <= 180.0):
            lat = lng = None

        radius_km = 50.0 if lat is not None else None
        radius_param = qp.get('radius_km')
        if radius_param:
            parsed = _float_or_none(radius_param)
            if parsed is not None:
                radius_km = parsed
        if radius_km is not None:
            radius_km = min(radius_km, MAX_RADIUS_KM) if radius_km > 0 else MAX_RADIUS_KM

        return cls(
            page=page,
            page_size=page_size,
            search=qp.get('search', '').strip(),
            category=qp.get('category', '').strip(),
            insurance=qp.get('insurance', '').strip(),
            city=qp.get('city', '').strip(),
            city_code=qp.get('city_code', '').strip(),
            only_claimed=qp.get('only_claimed', '').strip().lower() in ('true', '1'),
            lat=lat,
            lng=lng,
            radius_km=radius_km,
            include_unlocated=qp.get('include_unlocated', '').strip().lower() in ('true', '1'),
            sort=qp.get('sort', 'recommended').strip().lower(),
        )


@dataclass
class BusinessPage:
    count: int
    total_pages: int
    current_page: int
    next: Optional[int]
    previous: Optional[int]
    items: list = field(default_factory=list)

    def as_payload(self, results):
        """The list endpoint's ``data`` dict (shape is part of the API contract)."""
        return {
            'count': self.count,
            'total_pages': self.total_pages,
            'current_page': self.current_page,
            'next': self.next,
            'previous': self.previous,
            'results': results,
        }


def _search_filters(params):
    # public_filter() carries both reasons a business stays out of public
    # browsing - locked (subscription downgrade) and not editorially approved.
    filters = Business.public_filter()

    if params.search:
        q = params.search
        filters &= (
            Q(title__icontains=q) |
            Q(unique_code__iexact=q) |
            Q(address__icontains=q) |
            Q(city__icontains=q) |
            Q(neighborhood__icontains=q)
        )

    if params.category:
        group_map = {
            key.upper(): [code for code, _ in pairs]
            for key, _label, pairs in Business.CATEGORY_GROUPS
        }
        cat_upper = params.category.upper()
        if cat_upper in group_map:
            filters &= Q(category__in=group_map[cat_upper])
        else:
            filters &= Q(category=params.category)

    if params.insurance:
        # Exists() instead of a join: a business matching several of the
        # same insurance rows can never duplicate, so no DISTINCT is needed.
        through = Business.insurances.through.objects.filter(business_id=OuterRef('pk'))
        if params.insurance.isdigit():
            through = through.filter(insurance_id=int(params.insurance))
        else:
            through = through.filter(insurance__slug=params.insurance)
        filters &= Q(Exists(through))

    if params.city:
        filters &= Q(city__icontains=params.city)

    if params.city_code.isdigit():
        filters &= city_filter(int(params.city_code))

    if params.only_claimed:
        filters &= Q(is_claimed=True)
    return filters


def public_business_search(params):
    """One page of the public directory for ``params`` (sync; run it in
    sync_to_async). Rows come back with ``distance_km`` and the owner state
    attached, ready for PublicBusinessSerializer."""
    qs = public_businesses(_search_filters(params))

    located = params.lat is not None and params.lng is not None
    if not located:
        qs = qs.order_by('-is_claimed', '-created_at')
    else:
        qs = geo_queryset(qs, params.lat, params.lng, params.radius_km,
                          params.include_unlocated, params.sort)

    paginator = Paginator(qs, params.page_size)
    page_obj = paginator.get_page(params.page)
    items = list(page_obj.object_list)
    for b in items:
        # Exact great-circle distance, only for the rows on this page.
        if located and b.latitude is not None and b.longitude is not None:
            b.distance_km = haversine_km(params.lat, params.lng, b.latitude, b.longitude)
        else:
            b.distance_km = None
    attach_owner_state(items)
    return BusinessPage(
        count=paginator.count,
        total_pages=paginator.num_pages,
        current_page=page_obj.number,
        next=page_obj.next_page_number() if page_obj.has_next() else None,
        previous=page_obj.previous_page_number() if page_obj.has_previous() else None,
        items=items,
    )


def public_business_detail(business_id, lat=None, lng=None):
    """A publicly visible business by id with distance + owner state attached,
    or ``Business.DoesNotExist``."""
    business = public_businesses(Q(id=business_id)).first()
    if business is None:
        raise Business.DoesNotExist(business_id)
    business.distance_km = None
    if lat is not None and lng is not None \
            and business.latitude is not None and business.longitude is not None:
        business.distance_km = haversine_km(lat, lng, business.latitude, business.longitude)
    attach_owner_state([business])
    return business
