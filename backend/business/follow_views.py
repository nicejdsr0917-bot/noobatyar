from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import IsAuthenticated

from api.responses import APIResponse
from common.pagination import page_dict
from common.params import page_params
from reviews.serializers import first_name

from . import follows
from .models import Business


def _follower_json(follow, request):
    visitor = follow.visitor
    avatar = request.build_absolute_uri(visitor.avatar.url) if visitor.avatar else None
    return {'name': first_name(visitor.full_name), 'avatar': avatar, 'created_at': follow.created_at}


class OwnerFollowerListView(APIView):
    """GET /api/business/<id>/followers/ — the owner's newest followers:
    first name and photo only, no phone."""
    permission_classes = [IsAuthenticated]

    async def get(self, request, business_id):
        page, page_size, err = page_params(request)
        if err:
            return err

        def _load():
            business = Business.objects.filter(pk=business_id, user=request.user).first()
            if business is None:
                return None
            return page_dict(
                follows.recent_followers(business), page, page_size,
                lambda rows: [_follower_json(f, request) for f in rows],
            )

        data = await sync_to_async(_load)()
        if data is None:
            return APIResponse.error('کسب و کار یافت نشد', code=404)
        return APIResponse.success(data=data, message='لیست دنبال‌کننده‌ها دریافت شد')
