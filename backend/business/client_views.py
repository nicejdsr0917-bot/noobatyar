from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import AllowAny
from rest_framework.throttling import AnonRateThrottle
from django.db.models import F
import hashlib
import logging

from api.phone import is_iran_phone, normalize_phone
from api.throttles import client_ip
from visitor.auth import VisitorTokenAuthentication
from visitor.models import Visitor

from . import cache as public_cache
from .geo import MAX_RADIUS_KM, haversine_km  # noqa: F401  (re-exported for older imports)
from .models import Business, ContentReport, Insurance, OnlineBookingRequest
from .selectors import (
    PublicSearchParams, _float_or_none, public_business_detail, public_business_search,
)
from .serializers import PublicBusinessSerializer, InsuranceSerializer
from api.responses import APIResponse

logger = logging.getLogger(__name__)

MAX_REPORT_DETAIL_LEN = 2000
_VALID_REPORT_REASONS = {code for code, _ in ContentReport.REASON_CHOICES}


class ContentReportRateThrottle(AnonRateThrottle):
    """Per-IP throttle for the abuse-report submission endpoint.

    Rate comes from THROTTLE_RATES['content_report'] (core/settings.py) —
    same subclassing pattern as api/throttles.py's OTPRateThrottle, defined
    here instead of there because api/ is outside this phase's scope.

    AnonRateThrottle (not the shared 'anon' scope every other endpoint uses)
    deliberately: this is a no-account, no-authentication endpoint with
    nothing else limiting it, so a spam/DoS-minded caller filing hundreds of
    fake reports would otherwise only be bounded by the generous general-
    purpose anon rate (60/min) — nowhere near tight enough for something that
    creates a database row and lands in a human reviewer's queue every time.

    get_cache_key is overridden because plain AnonRateThrottle exempts any
    request whose ``request.user.is_authenticated`` is true — and
    ``visitor.models.Visitor.is_authenticated`` is hard-coded ``True`` (see
    that property's own docstring: it exists so DRF's generic
    "is this user logged in" checks work, not to mark a Visitor as a trusted,
    unthrottled account tier). Without this override, a signed-in visitor's
    requests skipped the throttle entirely — verified by hand while wiring
    this up: six requests with a valid ``Authorization: Visitor <token>``
    header all returned 201 with the base class. This endpoint always keys by
    IP, authenticated visitor or not, since nothing about holding a Visitor
    token makes repeated report filing legitimate.
    """
    scope = 'content_report'

    def get_cache_key(self, request, view):
        return self.cache_format % {
            'scope': self.scope,
            'ident': self.get_ident(request),
        }


class RequestBookingRateThrottle(AnonRateThrottle):
    """Per-IP throttle for the unclaimed-business online booking request endpoint."""
    scope = 'request_booking'

    def get_cache_key(self, request, view):
        return self.cache_format % {
            'scope': self.scope,
            'ident': self.get_ident(request),
        }

def request_fingerprint(request):
    """Stable, anonymised identity of the caller for de-duplicating votes.

    Prefers the app's per-install X-Device-Id header and falls back to the IP;
    only a hash is stored, never the raw value.
    """
    device_id = (request.headers.get('X-Device-Id') or '').strip()[:128]
    if device_id:
        raw = f"device:{device_id}"
    else:
        raw = f"ip:{client_ip(request)}"
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()


class ClientBusinessListView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request):
        params = PublicSearchParams.from_query(request.query_params)
        # A position makes every response unique: only position-less anonymous
        # requests share cache entries (business/cache.py).
        use_cache = public_cache.is_cacheable_request(request) and params.lat is None

        def _fetch_and_serialize():
            key = public_cache.list_key(params) if use_cache else None
            if key is not None:
                cached = public_cache.get(key)
                if cached is not None:
                    return cached
            page = public_business_search(params)
            results = PublicBusinessSerializer(
                page.items, many=True, context={'request': request}
            ).data
            payload = page.as_payload(results)
            if key is not None:
                public_cache.put(key, payload, public_cache.LIST_TTL)
            return payload

        # One sync hop for query + serialisation: nothing below touches the DB.
        data = await sync_to_async(_fetch_and_serialize)()

        return APIResponse.success(
            data=data,
            message="لیست کسب و کارها با موفقیت دریافت شد"
        )


class ClientInsuranceListView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request):
        def _get_insurances():
            return list(Insurance.objects.filter(is_active=True).order_by('display_order', 'name'))

        insurances = await sync_to_async(_get_insurances)()
        serializer = InsuranceSerializer(insurances, many=True)
        data = await sync_to_async(lambda: serializer.data)()
        return APIResponse.success(
            data=data,
            message="لیست بیمه‌ها با موفقیت دریافت شد"
        )


class ClientRequestOnlineBookingView(APIView):
    permission_classes = [AllowAny]
    throttle_classes = [RequestBookingRateThrottle]

    async def post(self, request, business_id):
        try:
            business = await Business.objects.aget(Business.public_filter(), id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب و کار مورد نظر یافت نشد", code=404)

        if business.is_claimed:
            return APIResponse.error(message="این مرکز خودش نوبت آنلاین دارد", code=400)

        fingerprint = request_fingerprint(request)

        def _record():
            _req, created = OnlineBookingRequest.objects.get_or_create(
                business=business, fingerprint=fingerprint,
            )
            if created:
                Business.objects.filter(id=business.id).update(
                    online_booking_requests_count=F('online_booking_requests_count') + 1
                )
            business.refresh_from_db(fields=['online_booking_requests_count'])
            return created, business.online_booking_requests_count

        created, count = await sync_to_async(_record)()
        return APIResponse.success(
            data={
                'id': business.id,
                'online_booking_requests_count': count,
                'already_requested': not created,
            },
            message=(
                "درخواست شما برای فعال‌سازی نوبت‌دهی آنلاین این مرکز ثبت شد" if created
                else "درخواست شما قبلاً ثبت شده بود"
            )
        )


class ClientBusinessDetailView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request, business_id):
        lat = _float_or_none(request.query_params.get('lat'))
        lng = _float_or_none(request.query_params.get('lng'))

        use_cache = public_cache.is_cacheable_request(request)

        def _load_and_serialize():
            if not use_cache:
                business = public_business_detail(business_id, lat=lat, lng=lng)
                return PublicBusinessSerializer(business, context={'request': request}).data
            # Cached without the viewer's distance; it is added per request.
            key = public_cache.detail_key(business_id)
            entry = public_cache.get(key)
            if entry is None:
                business = public_business_detail(business_id)
                entry = {
                    'data': dict(PublicBusinessSerializer(business, context={'request': request}).data),
                    'pin': (business.latitude, business.longitude),
                }
                public_cache.put(key, entry, public_cache.DETAIL_TTL)
            data = dict(entry['data'])
            pin_lat, pin_lng = entry['pin']
            if lat is not None and lng is not None and pin_lat is not None and pin_lng is not None:
                data['distance_km'] = round(haversine_km(lat, lng, pin_lat, pin_lng), 2)
            return data

        try:
            data = await sync_to_async(_load_and_serialize)()
        except Business.DoesNotExist:
            return APIResponse.error(
                message="کسب و کار مورد نظر یافت نشد",
                code=404
            )
        except Exception:
            # Anything else is a server fault, not "not found": log it and let
            # it surface as a 500 instead of hiding it behind a 404.
            logger.exception("business detail failed (id=%s)", business_id)
            raise
        return APIResponse.success(
            data=data,
            message="اطلاعات کسب و کار با موفقیت دریافت شد"
        )


class ClientContentReportView(APIView):
    """File an abuse/content report against a business — public booking page,
    "report this listing".

    Client identity handling mirrors ClientBusinessListView/DetailView's own
    stance: this must not require the caller to already be a Visitor with an
    account, since most people who notice a problem on a public page are
    anonymous browsers, not signed-in customers (business.models.ContentReport's
    own docstring). VisitorTokenAuthentication.authenticate() already returns
    ``None`` (not a failure) when no ``Authorization: Visitor …`` header is
    present, so pairing it with AllowAny — the same pairing
    ClientAppointmentListView uses for its *required* case, used here for the
    optional case — gives three outcomes without any extra branching:

      * A valid Visitor token → request.user is that Visitor; reporter_visitor
        is set automatically, no phone field needed from the client.
      * No token at all → request.user is AnonymousUser; the caller may
        optionally supply reporter_phone so staff have a way to follow up.
      * An expired/invalid token → VisitorTokenAuthentication raises
        AuthenticationFailed itself (401), same as every other endpoint using
        it — this view does not special-case that away, a stale token should
        surface to the client like anywhere else.

    Throttled by IP (ContentReportRateThrottle above) — the only defence this
    endpoint has against being spammed, since it accepts anonymous callers by
    design and therefore cannot rely on a per-user limit.
    """
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [AllowAny]
    throttle_classes = [ContentReportRateThrottle]

    async def post(self, request, business_id):
        try:
            # public_filter(), same as ClientBusinessDetailView — reporting a
            # business the caller cannot otherwise see would both 404-leak its
            # existence to a stale/guessed id and let a report be filed against
            # a listing nobody on the public site can currently reach.
            business = await Business.objects.aget(Business.public_filter(), id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب و کار مورد نظر یافت نشد", code=404)

        reason = str(request.data.get('reason') or '').strip().upper()
        if reason not in _VALID_REPORT_REASONS:
            return APIResponse.error(message="دلیل گزارش نامعتبر است", code=400)

        detail = str(request.data.get('detail') or '').strip()[:MAX_REPORT_DETAIL_LEN]

        reporter_visitor = None
        reporter_phone = ''
        if isinstance(request.user, Visitor):
            reporter_visitor = request.user
        else:
            raw_phone = str(request.data.get('reporter_phone') or '').strip()
            if raw_phone:
                normalized = normalize_phone(raw_phone)
                if not is_iran_phone(normalized):
                    return APIResponse.error(message="شماره تماس نامعتبر است", code=400)
                reporter_phone = normalized

        await ContentReport.objects.acreate(
            business=business,
            reason=reason,
            detail=detail,
            reporter_visitor=reporter_visitor,
            reporter_phone=reporter_phone,
        )

        return APIResponse.success(
            message="گزارش شما ثبت شد و توسط تیم بررسی خواهد شد",
            status=201,
        )
