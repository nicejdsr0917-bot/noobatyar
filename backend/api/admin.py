from django.contrib import admin
from unfold.admin import ModelAdmin
from .models import DeviceToken, User


@admin.register(User)
class UserAdmin(ModelAdmin):
    list_display = ['phone', 'name', 'role', 'is_employee', 'joined_at']
    list_filter = ['role', 'is_employee']
    search_fields = ['phone', 'name']


@admin.register(DeviceToken)
class DeviceTokenAdmin(ModelAdmin):
    list_display = ['user', 'platform', 'device_name', 'is_active', 'updated_at']
    list_filter = ['platform', 'is_active']
    search_fields = ['user__phone', 'user__name', 'token']
    readonly_fields = ['created_at', 'updated_at']


# Third-party models registered against the stock ModelAdmin, re-registered on
# unfold's so their pages match the rest of the panel instead of rendering as
# bare, unstyled Django admin inside it.
from django.contrib.auth.admin import GroupAdmin as BaseGroupAdmin  # noqa: E402
from django.contrib.auth.models import Group  # noqa: E402
from rest_framework_simplejwt.token_blacklist import admin as jwt_admin  # noqa: E402
from rest_framework_simplejwt.token_blacklist.models import (  # noqa: E402
    BlacklistedToken,
    OutstandingToken,
)

for _model in (Group, OutstandingToken, BlacklistedToken):
    if admin.site.is_registered(_model):
        admin.site.unregister(_model)


@admin.register(Group)
class GroupAdmin(BaseGroupAdmin, ModelAdmin):
    pass


@admin.register(OutstandingToken)
class OutstandingTokenAdmin(jwt_admin.OutstandingTokenAdmin, ModelAdmin):
    pass


@admin.register(BlacklistedToken)
class BlacklistedTokenAdmin(jwt_admin.BlacklistedTokenAdmin, ModelAdmin):
    pass
