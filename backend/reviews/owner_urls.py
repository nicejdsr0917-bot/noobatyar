from django.urls import path

from .owner_views import OwnerReviewListView

urlpatterns = [
    path('owner/', OwnerReviewListView.as_view(), name='review-owner-list'),
]
