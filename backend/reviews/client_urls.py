from django.urls import path

from .client_views import (
    BusinessReviewListView, BusinessReviewSummaryView, MyReviewView, PendingReviewPromptsView,
    ReviewReportView,
)

urlpatterns = [
    path('pending/', PendingReviewPromptsView.as_view(), name='review-pending'),
    path('business/<int:business_id>/', BusinessReviewListView.as_view(), name='review-public-list'),
    path('business/<int:business_id>/summary/', BusinessReviewSummaryView.as_view(), name='review-summary'),
    path('business/<int:business_id>/mine/', MyReviewView.as_view(), name='review-mine'),
    path('<int:review_id>/report/', ReviewReportView.as_view(), name='review-report'),
]
