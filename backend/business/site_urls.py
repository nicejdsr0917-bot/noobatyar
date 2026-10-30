from django.urls import path

from .site_views import ActiveCustomersView, NewestBusinessesView

urlpatterns = [
    path('active-customers/', ActiveCustomersView.as_view(), name='site-active-customers'),
    path('newest-businesses/', NewestBusinessesView.as_view(), name='site-newest-businesses'),
]
