from django.contrib import admin, messages
from django.utils import timezone
from django.utils.html import format_html
from unfold.admin import ModelAdmin

from .models import Certificate, ModerationStatus, PortfolioItem


class _ShowcaseAdmin(ModelAdmin):
    """Review queue for owner uploads. Default filter shows what is waiting."""

    list_filter = ('status',)
    list_select_related = ('business',)
    list_per_page = 30
    raw_id_fields = ('business',)
    readonly_fields = ('preview', 'reviewed_by', 'reviewed_at', 'created_at', 'updated_at')
    actions = ['approve_selected', 'reject_selected']
    ordering = ('status', '-created_at')

    @admin.display(description='پیش‌نمایش')
    def preview(self, obj):
        if not obj.pk or not obj.image:
            return '—'
        return format_html('<img src="{}" style="max-height:240px;border-radius:8px">', obj.image.url)

    @admin.display(description='کسب‌وکار', ordering='business__title')
    def business_title(self, obj):
        return obj.business.title

    @admin.display(description='عکس')
    def thumb(self, obj):
        f = obj.thumbnail or obj.image
        return format_html('<img src="{}" style="height:48px;border-radius:4px">', f.url) if f else '—'

    def _stamp(self, request, obj):
        obj.reviewed_by = request.user
        obj.reviewed_at = timezone.now()

    def save_model(self, request, obj, form, change):
        if change and 'status' in form.changed_data:
            if obj.status == ModerationStatus.REJECTED and not obj.reject_reason.strip():
                obj.reject_reason = 'رد شده توسط پشتیبانی'
            if obj.status != ModerationStatus.REJECTED:
                obj.reject_reason = ''
            self._stamp(request, obj)
        super().save_model(request, obj, form, change)

    @admin.action(description='تأیید موارد انتخاب‌شده')
    def approve_selected(self, request, queryset):
        n = 0
        for obj in queryset.exclude(status=ModerationStatus.APPROVED):
            obj.status, obj.reject_reason = ModerationStatus.APPROVED, ''
            self._stamp(request, obj)
            obj.save()
            n += 1
        self.message_user(request, f'{n} مورد تأیید شد', messages.SUCCESS)

    @admin.action(description='رد موارد انتخاب‌شده (دلیل: کیفیت یا محتوای نامناسب)')
    def reject_selected(self, request, queryset):
        n = 0
        for obj in queryset.exclude(status=ModerationStatus.REJECTED):
            obj.status, obj.reject_reason = ModerationStatus.REJECTED, 'کیفیت یا محتوای عکس مناسب نیست'
            self._stamp(request, obj)
            obj.save()
            n += 1
        self.message_user(request, f'{n} مورد رد شد؛ برای دلیل دلخواه، خود مورد را باز کنید', messages.WARNING)


@admin.register(PortfolioItem)
class PortfolioItemAdmin(_ShowcaseAdmin):
    list_display = ('thumb', 'business_title', 'caption', 'status', 'order', 'created_at')
    search_fields = ('business__title', 'caption')
    fields = ('business', 'preview', 'image', 'caption', 'order', 'status', 'reject_reason',
              'reviewed_by', 'reviewed_at', 'created_at', 'updated_at')


@admin.register(Certificate)
class CertificateAdmin(_ShowcaseAdmin):
    list_display = ('thumb', 'business_title', 'title', 'issuer', 'year', 'status', 'created_at')
    search_fields = ('business__title', 'title', 'issuer')
    fields = ('business', 'preview', 'image', 'title', 'issuer', 'year', 'order', 'status', 'reject_reason',
              'reviewed_by', 'reviewed_at', 'created_at', 'updated_at')
