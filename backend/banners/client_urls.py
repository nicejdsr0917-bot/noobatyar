from django.urls import path

from .views import ClientPromoListView

urlpatterns = [path('', ClientPromoListView.as_view(), name='client-promos')]
