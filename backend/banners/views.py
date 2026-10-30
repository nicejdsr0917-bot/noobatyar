from rest_framework.permissions import IsAuthenticated
from rest_framework.views import APIView

from business.models import Business
from common.params import business_id_param
from common.responses import APIResponse

from .models import Banner, SiteSetting


class OwnerBannerListView(APIView):
    """GET /api/owner/banners/?business_id= — active banners for the category
    of one of the caller's own businesses, plus the shared link."""
    permission_classes = [IsAuthenticated]

    def get(self, request):
        business_id, err = business_id_param(request)
        if err:
            return err
        category = (Business.objects.filter(pk=business_id, user=request.user)
                    .values_list('category', flat=True).first())
        if category is None:
            return APIResponse.error('کسب‌وکار یافت نشد', code=404)
        banners = [
            {'id': b.id, 'image': request.build_absolute_uri(b.image.url), 'order': b.order}
            for b in Banner.active_for_category(category)
        ]
        return APIResponse.success(data={
            'banners': banners,
            'link_url': SiteSetting.load().banner_link_url,
        })
