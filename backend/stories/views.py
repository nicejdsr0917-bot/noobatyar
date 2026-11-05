from rest_framework import status
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.views import APIView

from business import follows
from business.client_views import CUSTOMER_AUTHENTICATION
from business.models import Business
from common.responses import APIResponse
from showcase.images import ImageRejected
from visitor.models import Visitor

from . import services
from .models import Story
from .serializers import StorySerializer


def _owned_business(request, business_id):
    try:
        return Business.objects.filter(pk=int(business_id), user=request.user).first()
    except (TypeError, ValueError):
        return None


class OwnerStoryListView(APIView):
    """GET (active stories) and POST (multipart: business_id, image, caption,
    button, discount_type, discount_value) /api/owner/stories/."""
    permission_classes = [IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser, JSONParser]

    def get(self, request):
        business = _owned_business(request, request.query_params.get('business_id'))
        if business is None:
            return APIResponse.error('کسب‌وکار یافت نشد', code=404)
        stories = Story.active().filter(business=business)
        return APIResponse.success(data=StorySerializer(stories, many=True, context={'request': request}).data)

    def post(self, request):
        business = _owned_business(request, request.data.get('business_id'))
        if business is None:
            return APIResponse.error('کسب‌وکار یافت نشد', code=404)
        image = request.FILES.get('image')
        if image is None:
            return APIResponse.error('عکس الزامی است', code=400)
        try:
            story = services.create_story(
                business, image,
                caption=request.data.get('caption', ''),
                button=request.data.get('button', ''),
                discount_type=request.data.get('discount_type', ''),
                discount_value=request.data.get('discount_value'),
            )
        except (ImageRejected, services.StoryError) as exc:
            return APIResponse.error(str(exc), code=400)
        return APIResponse.success(
            data=StorySerializer(story, context={'request': request}).data,
            message='استوری منتشر شد', status=status.HTTP_201_CREATED)


class OwnerStoryDetailView(APIView):
    """DELETE /api/owner/stories/<id>/."""
    permission_classes = [IsAuthenticated]

    def delete(self, request, pk):
        story = Story.objects.filter(pk=pk, business__user=request.user).first()
        if story is None:
            return APIResponse.error('استوری یافت نشد', code=404)
        services.delete_story(story)
        return APIResponse.success(message='حذف شد')


class ClientActiveStoriesView(APIView):
    """GET /api/client/stories/active/ — ids of businesses with a story up,
    for the ring around their avatar."""
    permission_classes = [AllowAny]

    def get(self, request):
        return APIResponse.success(data={'business_ids': services.active_business_ids()})


class ClientBusinessStoriesView(APIView):
    """GET /api/client/businesses/<id>/stories/ — active stories, oldest first.
    ``is_following`` only with a customer token (for the follow button)."""
    authentication_classes = CUSTOMER_AUTHENTICATION
    permission_classes = [AllowAny]

    def get(self, request, business_id):
        business = Business.objects.filter(Business.public_filter(), pk=business_id).first()
        if business is None:
            return APIResponse.error('کسب و کار مورد نظر یافت نشد', code=404)
        stories = Story.active().filter(business=business)
        data = {
            'business_id': business.id,
            'title': business.title,
            'stories': StorySerializer(stories, many=True, context={'request': request}).data,
        }
        if isinstance(request.user, Visitor):
            data['is_following'] = follows.is_following(request.user, business.id)
        return APIResponse.success(data=data)
