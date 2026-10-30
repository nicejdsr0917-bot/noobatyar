from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.views import APIView

from business.models import Business
from common.params import business_id_param
from common.responses import APIResponse

from .models import Banner, ClientPromo, SiteSetting


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


class ClientPromoListView(APIView):
    """GET /api/client/promos/?placement=EXPLORE|BUSINESS_MAP — the active promo
    strips for one spot in the client app, best first. Public."""
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        placement = (request.query_params.get('placement') or '').upper()
        valid = {ClientPromo.PLACEMENT_EXPLORE, ClientPromo.PLACEMENT_BUSINESS_MAP}
        if placement not in valid:
            return APIResponse.error('محل نمایش نامعتبر است', code=400)
        promos = [
            {
                'id': p.id,
                'text': p.text,
                'action_label': p.action_label,
                'business_id': p.business_id,
                'link_url': p.link_url,
            }
            for p in ClientPromo.active_for(placement)[:3]
        ]
        return APIResponse.success(data={'promos': promos})
