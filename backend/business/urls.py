from django.urls import path

from .first_visit import OwnerFirstVisitDiscountView
from .follow_views import OwnerFollowerListView
from .claim_views import ClaimableBusinessSearchView, ClaimStartView, ClaimVerifyView
from .promo_views import PromoSmsDetailView, PromoSmsPreviewView, PromoSmsView
from .sms_views import PushLogListView, PushLogSummaryView, SmsLogListView, SmsLogSummaryView
from .views import BusinessCategoriesView, BusinessView, ServiceCatalogView

urlpatterns = [
    # ... existing routes
    path('', BusinessView.as_view(), name='business-list-create'),
    # Declared before the '<int:business_id>/' detail route: Django resolves in
    # order, and while the detail pattern ends at its own trailing slash and so
    # would not swallow these anyway, keeping the more specific paths first makes
    # that independent of how the detail route is written later.
    path('<int:business_id>/sms-logs/', SmsLogListView.as_view(), name='business-sms-logs'),
    path('<int:business_id>/sms-logs/summary/', SmsLogSummaryView.as_view(), name='business-sms-logs-summary'),
    path('<int:business_id>/push-logs/', PushLogListView.as_view(), name='business-push-logs'),
    path('<int:business_id>/push-logs/summary/', PushLogSummaryView.as_view(), name='business-push-logs-summary'),
    path('<int:business_id>/followers/', OwnerFollowerListView.as_view(), name='business-followers'),
    path('<int:business_id>/first-visit-discount/', OwnerFirstVisitDiscountView.as_view(), name='business-first-visit-discount'),
    path('<int:business_id>/promo-sms/', PromoSmsView.as_view(), name='business-promo-sms'),
    path('<int:business_id>/promo-sms/preview/', PromoSmsPreviewView.as_view(), name='business-promo-sms-preview'),
    path('<int:business_id>/promo-sms/<int:campaign_id>/', PromoSmsDetailView.as_view(), name='business-promo-sms-detail'),
    # Category-scoped, not business-scoped — deliberately not nested under
    # '<int:business_id>/' since a catalog item is shared across every
    # business in the category, not owned by one.
    path('service-catalog/', ServiceCatalogView.as_view(), name='service-catalog'),
    # Static vocabulary, no business context — same "not nested under
    # <business_id>" reasoning as service-catalog above.
    path('categories/', BusinessCategoriesView.as_view(), name='business-categories'),
    # Claiming an imported directory listing — see business/claim_views.py.
    path('claimable/', ClaimableBusinessSearchView.as_view(), name='business-claimable'),
    path('<int:business_id>/claim/', ClaimStartView.as_view(), name='business-claim-start'),
    path('<int:business_id>/claim/verify/', ClaimVerifyView.as_view(), name='business-claim-verify'),
    path('<int:business_id>/', BusinessView.as_view(), name='business-detail'),
]
