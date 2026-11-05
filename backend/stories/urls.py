from django.urls import path

from . import views as v

urlpatterns = [
    path('owner/stories/', v.OwnerStoryListView.as_view(), name='owner-stories'),
    path('owner/stories/<int:pk>/', v.OwnerStoryDetailView.as_view(), name='owner-story-detail'),
    path('client/stories/feed/', v.ClientStoryFeedView.as_view(), name='client-stories-feed'),
    path('client/stories/platform/', v.ClientPlatformStoriesView.as_view(), name='client-stories-platform'),
    path('client/stories/active/', v.ClientActiveStoriesView.as_view(), name='client-stories-active'),
    path('client/businesses/<int:business_id>/stories/', v.ClientBusinessStoriesView.as_view(), name='client-business-stories'),
]
