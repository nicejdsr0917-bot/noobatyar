"""«مراکز»: unclaimed directory listings, apart from «کسب‌وکارها» (owners with a Noobatyar tick).

A center has no owner and no booking. This page is where staff call them: a
status, a note and a call date on each, a filter by status, and a CSV of the
ones worth calling. Turning a center into a business is the claim flow
(business/claims.py), which keeps the row and its public link.
"""
import csv

from django.contrib import admin, messages
from django.db.models import Count
from django.http import HttpResponse
from django.urls import reverse
from django.utils import timezone
from django.utils.html import format_html
from unfold.admin import ModelAdmin, StackedInline

from ..models import Business, CenterListing, CenterOutreach
from .business import BusinessAdmin
from .directory_form import DirectoryFormMixin


class OutreachStatusFilter(admin.SimpleListFilter):
    title = 'وضعیت تماس'
    parameter_name = 'outreach'

    def lookups(self, request, model_admin):
        return CenterOutreach.STATUS_CHOICES

    def queryset(self, request, queryset):
        value = self.value()
        if not value:
            return queryset
        if value == CenterOutreach.STATUS_NEW:
            # No row yet counts as «بدون تماس».
            return queryset.filter(outreach__isnull=True) | queryset.filter(outreach__status=CenterOutreach.STATUS_NEW)
        return queryset.filter(outreach__status=value)


class HasPhoneFilter(admin.SimpleListFilter):
    title = 'تلفن'
    parameter_name = 'has_phone'

    def lookups(self, request, model_admin):
        return [('1', 'دارد'), ('0', 'ندارد')]

    def queryset(self, request, queryset):
        if self.value() == '1':
            return queryset.exclude(phone='')
        if self.value() == '0':
            return queryset.filter(phone='')
        return queryset


class HasLocationFilter(admin.SimpleListFilter):
    title = 'موقعیت روی نقشه'
    parameter_name = 'has_location'

    def lookups(self, request, model_admin):
        return [('1', 'دارد'), ('0', 'ندارد')]

    def queryset(self, request, queryset):
        if self.value() == '1':
            return queryset.exclude(latitude__isnull=True)
        if self.value() == '0':
            return queryset.filter(latitude__isnull=True)
        return queryset


class CenterOutreachInline(StackedInline):
    model = CenterOutreach
    extra = 0
    max_num = 1
    can_delete = False
    fields = ('status', 'contacted_at', 'note')
    verbose_name = 'پیگیری تماس'
    verbose_name_plural = 'پیگیری تماس'


def _set_status(request, queryset, status):
    now = timezone.now()
    changed = 0
    for business in queryset:
        outreach, _ = CenterOutreach.objects.get_or_create(business=business)
        outreach.status = status
        outreach.updated_by = request.user
        if status != CenterOutreach.STATUS_NEW and outreach.contacted_at is None:
            outreach.contacted_at = now
        outreach.save()
        changed += 1
    return changed


@admin.register(CenterListing)
class CenterAdmin(DirectoryFormMixin, ModelAdmin):
    list_display = ('title', 'category', 'city', 'phone', 'insurance_count', 'outreach_badge', 'source', 'location_mark')
    list_filter = (OutreachStatusFilter, HasPhoneFilter, HasLocationFilter, 'category', 'external_source')
    search_fields = ('title', 'phone', 'address', 'city', 'unique_code')
    filter_horizontal = ('insurances',)
    ordering = ('-created_at',)
    inlines = [CenterOutreachInline]
    list_per_page = 50
    actions = ('action_export_call_list', 'action_mark_called', 'action_mark_interested', 'action_mark_rejected')
    # A status typed on a business form would be an unlogged decision (see BusinessAdmin).
    readonly_fields = BusinessAdmin.readonly_fields
    change_list_template = 'admin/business/guild_change_list.html'
    change_form_template = 'admin/business/business/change_form.html'

    def get_queryset(self, request):
        return (
            super().get_queryset(request)
            .filter(is_claimed=False)
            .select_related('outreach')
            .annotate(_insurance_count=Count('insurances', distinct=True))
        )

    def changelist_view(self, request, extra_context=None):
        extra_context = extra_context or {}
        extra_context['bulk_add_url'] = reverse('admin:business_business_import_file', current_app=self.admin_site.name)
        return super().changelist_view(request, extra_context)

    def save_formset(self, request, form, formset, change):
        instances = formset.save(commit=False)
        for instance in instances:
            if isinstance(instance, CenterOutreach):
                instance.updated_by = request.user
            instance.save()
        formset.save_m2m()

    # ── Columns ──────────────────────────────────────────────────────────

    @admin.display(description='بیمه‌ها', ordering='_insurance_count')
    def insurance_count(self, obj):
        return obj._insurance_count

    @admin.display(description='وضعیت تماس')
    def outreach_badge(self, obj):
        outreach = getattr(obj, 'outreach', None)
        return outreach.get_status_display() if outreach else CenterOutreach.STATUS_CHOICES[0][1]

    @admin.display(description='منبع', ordering='external_source')
    def source(self, obj):
        return obj.external_source or '—'

    @admin.display(description='نقشه', boolean=True)
    def location_mark(self, obj):
        return obj.latitude is not None

    # ── Actions ──────────────────────────────────────────────────────────

    @admin.action(description='خروجی CSV لیست تماس (فقط دارای تلفن)')
    def action_export_call_list(self, request, queryset):
        response = HttpResponse(content_type='text/csv; charset=utf-8')
        response['Content-Disposition'] = 'attachment; filename="centers-call-list.csv"'
        response.write('﻿')  # BOM so Excel reads the Persian text
        writer = csv.writer(response)
        writer.writerow(['نام', 'شهر', 'استان', 'تلفن', 'آدرس', 'دسته', 'وضعیت تماس', 'یادداشت'])
        for center in queryset.exclude(phone='').order_by('province', 'city', 'title'):
            outreach = getattr(center, 'outreach', None)
            writer.writerow([
                center.title, center.city, center.province, center.phone, center.address,
                center.get_category_display(),
                outreach.get_status_display() if outreach else CenterOutreach.STATUS_CHOICES[0][1],
                outreach.note if outreach else '',
            ])
        return response

    @admin.action(description='علامت: تماس گرفته شد')
    def action_mark_called(self, request, queryset):
        self.message_user(request, f'{_set_status(request, queryset, CenterOutreach.STATUS_CALLED)} مرکز به‌روز شد.', messages.SUCCESS)

    @admin.action(description='علامت: علاقه‌مند')
    def action_mark_interested(self, request, queryset):
        self.message_user(request, f'{_set_status(request, queryset, CenterOutreach.STATUS_INTERESTED)} مرکز به‌روز شد.', messages.SUCCESS)

    @admin.action(description='علامت: رد کرد')
    def action_mark_rejected(self, request, queryset):
        self.message_user(request, f'{_set_status(request, queryset, CenterOutreach.STATUS_REJECTED)} مرکز به‌روز شد.', messages.SUCCESS)
