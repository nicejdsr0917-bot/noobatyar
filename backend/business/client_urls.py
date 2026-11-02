from django.urls import path

from .client_views import (
    ClientBusinessListView,
    ClientBusinessDetailView,
    ClientContentReportView,
    ClientInsuranceListView,
    ClientRequestOnlineBookingView,
)

urlpatterns = [
    path('', ClientBusinessListView.as_view(), name='client-business-list'),
    path('insurances/', ClientInsuranceListView.as_view(), name='client-insurance-list'),
    path('<int:business_id>/', ClientBusinessDetailView.as_view(), name='client-business-detail'),
    path('<int:business_id>/report/', ClientContentReportView.as_view(), name='client-business-report'),
    path('<int:business_id>/request-booking/', ClientRequestOnlineBookingView.as_view(), name='client-business-request-booking'),
]
