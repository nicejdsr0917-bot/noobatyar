from django.urls import path

from .views import OwnerBannerListView

urlpatterns = [path('', OwnerBannerListView.as_view(), name='owner-banners')]
