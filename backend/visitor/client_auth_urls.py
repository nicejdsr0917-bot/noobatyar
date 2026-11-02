from django.urls import path
from . import client_auth_views
from .client_views import ClientAvatarView, ClientDeviceRegisterView, ClientDeviceUnregisterView, ClientMeView

urlpatterns = [
    path('otp/send/', client_auth_views.client_send_otp_view, name='client-otp-send'),
    path('otp/verify/', client_auth_views.client_verify_otp_view, name='client-otp-verify'),
    path('register/', client_auth_views.client_register_view, name='client-register'),
    path('me/', ClientMeView.as_view(), name='client-me'),
    path('me/avatar/', ClientAvatarView.as_view(), name='client-me-avatar'),
    path('devices/register/', ClientDeviceRegisterView.as_view(), name='client-device-register'),
    path('devices/unregister/', ClientDeviceUnregisterView.as_view(), name='client-device-unregister'),
]
