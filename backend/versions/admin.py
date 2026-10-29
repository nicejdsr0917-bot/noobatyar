# versions/admin.py
from django.contrib import admin
from unfold.admin import ModelAdmin, StackedInline
from .models import AppVersion, VersionChangelog

class ChangelogInline(StackedInline):
    model = VersionChangelog

@admin.register(AppVersion)
class AppVersionAdmin(ModelAdmin):
    inlines = [ChangelogInline]
    list_display = ['app', 'version_name', 'version_code', 'force_update', 'is_active']
    list_filter = ['app', 'is_active', 'force_update']
