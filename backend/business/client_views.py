from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import AllowAny
from rest_framework.throttling import AnonRateThrottle
from django.core.paginator import Paginator
from django.db.models import F, Q
import hashlib
import logging
import math

from api.phone import is_iran_phone, normalize_phone
from visitor.auth import VisitorTokenAuthentication
from visitor.models import Visitor

from .iran_cities import city_filter
from .models import Business, ContentReport, Insurance, OnlineBookingRequest
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

# Upper bound on a geo search. The ranking below loads every row inside the
# bounding box into Python, so an unbounded radius would turn one request into
# a scan of the whole directory.
MAX_RADIUS_KM = 100.0


def request_fingerprint(request):
    """Stable, anonymised identity of the caller for de-duplicating votes.

    Prefers the app's per-install X-Device-Id header and falls back to the IP;
    only a hash is stored, never the raw value.
    """
    device_id = (request.headers.get('X-Device-Id') or '').strip()[:128]
    if device_id:
        raw = f"device:{device_id}"
    else:
        forwarded = request.META.get('HTTP_X_FORWARDED_FOR', '')
        ip = forwarded.split(',')[0].strip() if forwarded else request.META.get('REMOTE_ADDR', '')
        raw = f"ip:{ip}"
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()


def haversine_km(lat1, lon1, lat2, lon2):
    """Calculate the great circle distance between two points on the earth in kilometers."""
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2.0) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(dlon / 2.0) ** 2)
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c


class ClientBusinessListView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request):
        try:
            page = max(1, int(request.query_params.get('page', 1)))
        except (ValueError, TypeError):
            page = 1
        try:
            page_size = min(max(1, int(request.query_params.get('page_size', 10))), 100)
        except (ValueError, TypeError):
            page_size = 10

        search_query = request.query_params.get('search', '').strip()
        category_query = request.query_params.get('category', '').strip()
        insurance_query = request.query_params.get('insurance', '').strip()
        city_query = request.query_params.get('city', '').strip()
        city_code_query = request.query_params.get('city_code', '').strip()
        only_claimed_query = request.query_params.get('only_claimed', '').strip().lower()

        lat_param = request.query_params.get('lat')
        lng_param = request.query_params.get('lng')
        radius_param = request.query_params.get('radius_km')
        include_unlocated = request.query_params.get('include_unlocated', '').strip().lower() in ('true', '1')
        sort = request.query_params.get('sort', 'recommended').strip().lower()

        lat = None
        lng = None
        if lat_param is not None and lng_param is not None:
            try:
                lat = float(lat_param)
                lng = float(lng_param)
                if not (-90.0 <= lat <= 90.0 and -180.0 <= lng <= 180.0):
                    lat = None
                    lng = None
            except (ValueError, TypeError):
                lat = None
                lng = None

        radius_km = 50.0 if (lat is not None and lng is not None) else None
        if radius_param:
            try:
                radius_km = float(radius_param)
            except (ValueError, TypeError):
                pass
        if radius_km is not None:
            radius_km = min(radius_km, MAX_RADIUS_KM) if radius_km > 0 else MAX_RADIUS_KM

        # Build Q objects for filtering. public_filter() carries both reasons a
        # business stays out of public browsing — locked (subscription
        # downgrade) and not editorially approved.
        filters = Business.public_filter()

        if search_query:
            filters &= (
                Q(title__icontains=search_query) |
                Q(unique_code__iexact=search_query) |
                Q(address__icontains=search_query) |
                Q(city__icontains=search_query) |
                Q(neighborhood__icontains=search_query)
            )

        if category_query:
            group_map = {
                key.upper(): [code for code, _ in pairs]
                for key, _label, pairs in Business.CATEGORY_GROUPS
            }
            cat_upper = category_query.upper()
            if cat_upper in group_map:
                filters &= Q(category__in=group_map[cat_upper])
            else:
                filters &= Q(category=category_query)

        if insurance_query:
            if insurance_query.isdigit():
                filters &= Q(insurances__id=int(insurance_query))
            else:
                filters &= Q(insurances__slug=insurance_query)

        if city_query:
            filters &= Q(city__icontains=city_query)

        if city_code_query.isdigit():
            filters &= city_filter(int(city_code_query))

        if only_claimed_query in ('true', '1'):
            filters &= Q(is_claimed=True)

        def _fetch_and_paginate():
            qs = Business.objects.filter(filters).prefetch_related('insurances').distinct()

            if lat is None or lng is None:
                qs = qs.order_by('-is_claimed', '-created_at')
                paginator = Paginator(qs, page_size)
                page_obj = paginator.get_page(page)
                page_items = list(page_obj.object_list)
                for b in page_items:
                    b.distance_km = None
                return paginator.count, paginator.num_pages, page_obj.number, (
                    page_obj.next_page_number() if page_obj.has_next() else None
                ), (
                    page_obj.previous_page_number() if page_obj.has_previous() else None
                ), page_items

            # Database-level bounding-box pre-filter on indexed latitude/longitude
            # columns before loading rows into Python memory for exact Haversine ranking.
            if radius_km is not None and radius_km > 0:
                lat_delta = (radius_km / 110.5) * 1.05
                cos_lat = max(math.cos(math.radians(lat)), 0.01)
                lng_delta = (radius_km / (111.32 * cos_lat)) * 1.05
                bbox_q = Q(
                    latitude__gte=lat - lat_delta,
                    latitude__lte=lat + lat_delta,
                    longitude__gte=lng - lng_delta,
                    longitude__lte=lng + lng_delta,
                )
                if include_unlocated:
                    qs = qs.filter(bbox_q | Q(latitude__isnull=True) | Q(longitude__isnull=True))
                else:
                    qs = qs.filter(bbox_q)

            items = list(qs)
            filtered = []
            for b in items:
                if b.latitude is not None and b.longitude is not None:
                    b.distance_km = haversine_km(lat, lng, b.latitude, b.longitude)
                else:
                    b.distance_km = None

                if radius_km is not None and radius_km > 0:
                    if b.distance_km is not None:
                        if b.distance_km <= radius_km:
                            filtered.append(b)
                    else:
                        if include_unlocated:
                            filtered.append(b)
                else:
                    filtered.append(b)

            if sort == 'distance':
                filtered.sort(key=lambda b: (
                    b.distance_km is None,
                    b.distance_km if b.distance_km is not None else 999999,
                    not b.is_claimed,
                ))
            else:
                # Default: 'recommended'
                filtered.sort(key=lambda b: (
                    not b.is_claimed,
                    b.distance_km is None,
                    b.distance_km if b.distance_km is not None else 999999,
                    -b.id,
                ))

            paginator = Paginator(filtered, page_size)
            page_obj = paginator.get_page(page)
            return paginator.count, paginator.num_pages, page_obj.number, (
                page_obj.next_page_number() if page_obj.has_next() else None
            ), (
                page_obj.previous_page_number() if page_obj.has_previous() else None
            ), list(page_obj.object_list)

        count, total_pages, current_page, next_page, prev_page, page_items = await sync_to_async(_fetch_and_paginate)()

        serializer = PublicBusinessSerializer(
            page_items,
            many=True,
            context={'request': request}
        )

        results = await sync_to_async(lambda: serializer.data)()

        return APIResponse.success(
            data={
                'count': count,
                'total_pages': total_pages,
                'current_page': current_page,
                'next': next_page,
                'previous': prev_page,
                'results': results
            },
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
        try:
            business = await sync_to_async(
                lambda: Business.objects.filter(Business.public_filter(), id=business_id)
                .prefetch_related('insurances')
                .first()
            )()
            if not business:
                return APIResponse.error(
                    message="کسب و کار مورد نظر یافت نشد",
                    code=404
                )
        except Exception:
            return APIResponse.error(
                message="کسب و کار مورد نظر یافت نشد",
                code=404
            )

        lat_param = request.query_params.get('lat')
        lng_param = request.query_params.get('lng')
        if lat_param is not None and lng_param is not None:
            try:
                lat = float(lat_param)
                lng = float(lng_param)
                if business.latitude is not None and business.longitude is not None:
                    business.distance_km = haversine_km(lat, lng, business.latitude, business.longitude)
                else:
                    business.distance_km = None
            except (ValueError, TypeError):
                business.distance_km = None
        else:
            business.distance_km = None

        serializer = PublicBusinessSerializer(business, context={'request': request})
        data = await sync_to_async(lambda: serializer.data)()
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
