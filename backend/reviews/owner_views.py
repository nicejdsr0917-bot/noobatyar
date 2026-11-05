from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import IsAuthenticated

from api.responses import APIResponse
from business.models import Business
from common.pagination import page_dict
from common.params import business_id_param, page_params

from . import selectors
from .serializers import OwnerReviewSerializer


class OwnerReviewListView(APIView):
    """GET /api/reviews/owner/?business_id= — read-only, owner's own business."""
    permission_classes = [IsAuthenticated]

    async def get(self, request):
        business_id, err = business_id_param(request)
        if err:
            return err
        page, page_size, err = page_params(request)
        if err:
            return err

        def _load():
            business = Business.objects.filter(pk=business_id, user=request.user).first()
            if business is None:
                return None
            data = page_dict(
                selectors.owner_reviews(business_id), page, page_size,
                lambda rows: OwnerReviewSerializer(rows, many=True, context={'request': request}).data,
            )
            data['summary'] = selectors.rating_summary(business)
            return data

        data = await sync_to_async(_load)()
        if data is None:
            return APIResponse.error('کسب و کار یافت نشد', code=404)
        return APIResponse.success(data=data, message='لیست نظرات دریافت شد')
