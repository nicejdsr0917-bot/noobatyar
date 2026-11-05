import logging

from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import AllowAny
from rest_framework.throttling import SimpleRateThrottle

from api.responses import APIResponse
from business.client_views import ContentReportRateThrottle, MAX_REPORT_DETAIL_LEN, _VALID_REPORT_REASONS
from business.models import ContentReport
from common.pagination import page_dict
from common.params import page_params
from visitor.auth import IsVisitorAuthenticated, VisitorTokenAuthentication

from . import selectors, services
from .models import Review
from .serializers import OwnReviewSerializer, PublicReviewSerializer

logger = logging.getLogger(__name__)

NOT_FOUND = 'کسب و کار مورد نظر یافت نشد'


class ReviewCreateThrottle(SimpleRateThrottle):
    """Per-visitor cap on review writes (create/edit/delete)."""
    scope = 'review_write'
    rate = '20/hour'

    def get_cache_key(self, request, view):
        if request.method == 'GET':
            return None
        return self.cache_format % {'scope': self.scope, 'ident': f'v{request.user.pk}'}


class BusinessReviewListView(APIView):
    """GET public reviews of one business, newest first, hidden excluded."""
    authentication_classes = []
    permission_classes = [AllowAny]

    async def get(self, request, business_id):
        page, page_size, err = page_params(request)
        if err:
            return err

        def _load():
            if selectors.public_business_or_none(business_id) is None:
                return None
            return page_dict(
                selectors.public_reviews(business_id), page, page_size,
                lambda rows: PublicReviewSerializer(rows, many=True, context={'request': request}).data,
            )

        data = await sync_to_async(_load)()
        if data is None:
            return APIResponse.error(NOT_FOUND, code=404)
        return APIResponse.success(data=data, message='لیست نظرات دریافت شد')


class BusinessReviewSummaryView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    async def get(self, request, business_id):
        business = await sync_to_async(selectors.public_business_or_none)(business_id)
        if business is None:
            return APIResponse.error(NOT_FOUND, code=404)
        return APIResponse.success(data=selectors.rating_summary(business), message='خلاصه نظرات دریافت شد')


class MyReviewView(APIView):
    """The signed-in customer's own review of a business: GET/POST/PUT/DELETE."""
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]
    throttle_classes = [ReviewCreateThrottle]

    async def get(self, request, business_id):
        review = await Review.objects.filter(
            business_id=business_id, visitor=request.user).afirst()
        if review is None:
            return APIResponse.error('نظری ثبت نشده است', code=404)
        return APIResponse.success(data=OwnReviewSerializer(review).data)

    async def post(self, request, business_id):
        business = await sync_to_async(selectors.public_business_or_none)(business_id)
        if business is None:
            return APIResponse.error(NOT_FOUND, code=404)
        try:
            review = await sync_to_async(services.create_review)(
                business=business, visitor=request.user,
                rating=request.data.get('rating'), text=request.data.get('text'),
            )
        except services.ReviewError as exc:
            return APIResponse.error(exc.message, code=exc.code)
        return APIResponse.success(
            data=OwnReviewSerializer(review).data, message='نظر شما ثبت شد', status=201)

    async def put(self, request, business_id):
        try:
            review = await sync_to_async(services.update_review)(
                business_id=business_id, visitor=request.user,
                rating=request.data.get('rating'), text=request.data.get('text'),
            )
        except services.ReviewError as exc:
            return APIResponse.error(exc.message, code=exc.code)
        return APIResponse.success(data=OwnReviewSerializer(review).data, message='نظر شما ویرایش شد')

    async def delete(self, request, business_id):
        try:
            await sync_to_async(services.delete_review)(
                business_id=business_id, visitor=request.user)
        except services.ReviewError as exc:
            return APIResponse.error(exc.message, code=exc.code)
        return APIResponse.success(message='نظر شما حذف شد')


class ReviewReportView(APIView):
    """POST report one review (anonymous or visitor), filed as a ContentReport."""
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [AllowAny]
    throttle_classes = [ContentReportRateThrottle]

    async def post(self, request, review_id):
        review = await Review.objects.filter(
            pk=review_id, is_hidden=False).select_related('business').afirst()
        if review is None:
            return APIResponse.error('نظر مورد نظر یافت نشد', code=404)
        reason = str(request.data.get('reason') or '').strip().upper()
        if reason not in _VALID_REPORT_REASONS:
            return APIResponse.error('دلیل گزارش نامعتبر است', code=400)
        from visitor.models import Visitor
        reporter = request.user if isinstance(request.user, Visitor) else None
        await ContentReport.objects.acreate(
            business_id=review.business_id, review=review, reason=reason,
            detail=str(request.data.get('detail') or '').strip()[:MAX_REPORT_DETAIL_LEN],
            reporter_visitor=reporter,
        )
        return APIResponse.success(message='گزارش شما ثبت شد و توسط تیم بررسی خواهد شد', status=201)
