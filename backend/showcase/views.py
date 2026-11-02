import logging

from rest_framework import status
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.views import APIView

from business import cache as public_cache
from business.models import Business
from common.responses import APIResponse

from . import services
from .images import ImageRejected
from .models import Certificate, ModerationStatus, PortfolioItem
from .serializers import (
    CertificateInput, CertificatePatchInput, OwnerCertificateSerializer, OwnerPortfolioSerializer,
    PortfolioInput, PublicCertificateSerializer, PublicPortfolioSerializer,
)

logger = logging.getLogger(__name__)

NOT_FOUND = 'مورد یافت نشد'


def _owned_business(request, raw):
    try:
        bid = int(raw)
    except (TypeError, ValueError):
        return None
    return Business.objects.filter(pk=bid, user=request.user).first()


def _first_error(errors):
    for key, val in errors.items():
        msg = val[0] if isinstance(val, (list, tuple)) else val
        return f'{key}: {msg}'
    return 'اطلاعات نامعتبر است'


class _OwnerBase(APIView):
    permission_classes = [IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser, JSONParser]
    kind = None
    model = None
    out_serializer = None
    in_serializer = None
    patch_serializer = None
    limit_message = ''

    def _ser(self, request, obj, many=False):
        return self.out_serializer(obj, many=many, context={'request': request}).data


class OwnerCollectionView(_OwnerBase):
    def get(self, request):
        business = _owned_business(request, request.query_params.get('business_id'))
        if business is None:
            return APIResponse.error('کسب‌وکار یافت نشد', code=404)
        items = self.model.objects.filter(business=business)
        return APIResponse.success(data=self._ser(request, items, many=True))

    def post(self, request):
        business = _owned_business(request, request.data.get('business_id'))
        if business is None:
            return APIResponse.error('کسب‌وکار یافت نشد', code=404)
        inp = self.in_serializer(data=request.data)
        if not inp.is_valid():
            return APIResponse.error(_first_error(inp.errors), code=400)
        image = request.FILES.get('image')
        if image is None:
            return APIResponse.error('عکس الزامی است', code=400)
        try:
            item = services.create_item(business, self.kind, image, **inp.validated_data)
        except ImageRejected as exc:
            return APIResponse.error(str(exc), code=400)
        except services.LimitReached:
            return APIResponse.error(self.limit_message, code=400)
        return APIResponse.success(
            data=self._ser(request, item),
            message='ثبت شد و پس از تأیید برای مشتریان نمایش داده می‌شود',
            status=status.HTTP_201_CREATED)


class OwnerDetailView(_OwnerBase):
    def _get(self, request, pk):
        return self.model.objects.filter(pk=pk, business__user=request.user).first()

    def patch(self, request, pk):
        item = self._get(request, pk)
        if item is None:
            return APIResponse.error(NOT_FOUND, code=404)
        inp = self.patch_serializer(data=request.data, partial=True)
        if not inp.is_valid():
            return APIResponse.error(_first_error(inp.errors), code=400)
        try:
            item = services.update_item(
                item, self.kind, uploaded=request.FILES.get('image'), **inp.validated_data)
        except ImageRejected as exc:
            return APIResponse.error(str(exc), code=400)
        return APIResponse.success(data=self._ser(request, item), message='ذخیره شد')

    put = patch

    def delete(self, request, pk):
        item = self._get(request, pk)
        if item is None:
            return APIResponse.error(NOT_FOUND, code=404)
        item.delete()
        return APIResponse.success(message='حذف شد')


class OwnerReorderView(_OwnerBase):
    def post(self, request):
        business = _owned_business(request, request.data.get('business_id'))
        if business is None:
            return APIResponse.error('کسب‌وکار یافت نشد', code=404)
        ids = request.data.get('ids')
        if not isinstance(ids, list) or not all(isinstance(i, int) for i in ids):
            return APIResponse.error('ids باید فهرستی از شناسه‌ها باشد', code=400)
        try:
            services.reorder(business, self.kind, ids)
        except ValueError as exc:
            return APIResponse.error(str(exc), code=400)
        items = self.model.objects.filter(business=business)
        return APIResponse.success(data=self._ser(request, items, many=True))


def _bind(base, **attrs):
    return type(base.__name__, (base,), attrs)


_P = dict(kind=services.KIND_PORTFOLIO, model=PortfolioItem, out_serializer=OwnerPortfolioSerializer,
          in_serializer=PortfolioInput, patch_serializer=PortfolioInput,
          limit_message=f'حداکثر {PortfolioItem.MAX_PER_BUSINESS} نمونه‌کار می‌توانید داشته باشید')
_C = dict(kind=services.KIND_CERTIFICATE, model=Certificate, out_serializer=OwnerCertificateSerializer,
          in_serializer=CertificateInput, patch_serializer=CertificatePatchInput,
          limit_message=f'حداکثر {Certificate.MAX_PER_BUSINESS} مدرک می‌توانید داشته باشید')

PortfolioCollectionView = _bind(OwnerCollectionView, **_P)
PortfolioDetailView = _bind(OwnerDetailView, **_P)
PortfolioReorderView = _bind(OwnerReorderView, **_P)
CertificateCollectionView = _bind(OwnerCollectionView, **_C)
CertificateDetailView = _bind(OwnerDetailView, **_C)
CertificateReorderView = _bind(OwnerReorderView, **_C)


class ClientShowcaseView(APIView):
    """GET /api/client/businesses/<id>/showcase/ — approved items only."""
    permission_classes = [AllowAny]

    def get(self, request, business_id):
        if not Business.objects.filter(Business.public_filter(), pk=business_id).exists():
            return APIResponse.error('کسب و کار مورد نظر یافت نشد', code=404)
        use_cache = public_cache.is_cacheable_request(request)
        key = None
        if use_cache:
            origin = request.build_absolute_uri('/')
            key = public_cache.detail_key(business_id, origin) + ':showcase'
            hit = public_cache.get(key)
            if hit is not None:
                return APIResponse.success(data=hit)
        ctx = {'request': request}
        approved = {'business_id': business_id, 'status': ModerationStatus.APPROVED}
        data = {
            'portfolio': PublicPortfolioSerializer(
                PortfolioItem.objects.filter(**approved), many=True, context=ctx).data,
            'certificates': PublicCertificateSerializer(
                Certificate.objects.filter(**approved), many=True, context=ctx).data,
        }
        if key:
            public_cache.put(key, data, public_cache.DETAIL_TTL)
        return APIResponse.success(data=data)
