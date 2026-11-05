"""Insurance catalogue admin."""

from django.contrib import admin
from unfold.admin import ModelAdmin
from ..models import Insurance


@admin.register(Insurance)
class InsuranceAdmin(ModelAdmin):
    list_display = ('name', 'has_logo', 'slug', 'insurance_type', 'is_active', 'display_order')
    list_filter = ('insurance_type', 'is_active')
    search_fields = ('name', 'slug')
    ordering = ('display_order', 'name')

    @admin.display(boolean=True, description='لوگو')
    def has_logo(self, obj):
        return bool(obj.logo)
