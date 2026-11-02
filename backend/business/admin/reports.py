"""Queue of content reports filed against businesses."""

from api.jalali import format_datetime
from django.contrib import admin, messages
from django.core.exceptions import PermissionDenied
from django.urls import reverse
from django.utils.html import format_html
from unfold.admin import ModelAdmin
from ..models import ContentReport
from ..services import resolve_content_reports
from ._common import _badge, _BulkConfirmMixin


@admin.register(ContentReport)
class ContentReportAdmin(_BulkConfirmMixin, ModelAdmin):
    """Queue-style view of reports filed against businesses.

    Reports arrive from the public booking page, so the working order is
    "newest NEW first, triage, resolve" — hence the status filter, the status
    actions, and the link straight through to the reported business rather than
    a report detail page nobody would read twice.
    """

    list_display = (
        'created_jalali', 'business_link', 'reason', 'status_badge',
        'reporter_display', 'short_detail', 'resulting_decision_link',
    )
    list_filter = ('status', 'reason')
    search_fields = (
        'business__title', 'business__unique_code', 'detail',
        'reporter_phone', 'reporter_user__phone',
    )
    date_hierarchy = 'created_at'
    # Stamped by the actions below, never typed — same rule as the moderation
    # fields on BusinessAdmin. resulting_moderation_log is stamped too, but only
    # by apply_moderation_decision() (business/services.py) when a suspension or
    # rejection auto-resolves this report — never by an admin action here.
    readonly_fields = ('resolved_by', 'resolved_at', 'resulting_moderation_log', 'created_at')
    actions = ('mark_reviewing', 'mark_actioned', 'mark_dismissed')

    def get_queryset(self, request):
        return super().get_queryset(request).select_related(
            'business', 'reporter_user', 'reporter_visitor', 'resulting_moderation_log',
        )

    @admin.display(description='تصمیم مرتبط')
    def resulting_decision_link(self, obj):
        if not obj.resulting_moderation_log_id:
            return '—'
        url = reverse(
            'admin:business_businessmoderationlog_change',
            args=[obj.resulting_moderation_log_id],
        )
        return format_html(
            '<a href="{}">{}</a>', url, obj.resulting_moderation_log.get_to_status_display(),
        )

    @admin.display(description='زمان', ordering='created_at')
    def created_jalali(self, obj):
        return format_datetime(obj.created_at) if obj.created_at else '—'

    @admin.display(description='کسب‌وکار گزارش‌شده', ordering='business__title')
    def business_link(self, obj):
        url = reverse('admin:business_business_change', args=[obj.business_id])
        return format_html(
            '<a href="{}">{}</a> <span class="nb-quiet">({})</span>',
            url, obj.business.title, obj.business.get_moderation_status_display(),
        )

    @admin.display(description='وضعیت', ordering='status')
    def status_badge(self, obj):
        variants = {
            ContentReport.STATUS_NEW:       'danger',
            ContentReport.STATUS_REVIEWING: 'warn',
            ContentReport.STATUS_ACTIONED:  'ok',
            ContentReport.STATUS_DISMISSED: '',
        }
        return _badge(obj.get_status_display(), variants.get(obj.status, ''))

    @admin.display(description='گزارش‌دهنده')
    def reporter_display(self, obj):
        # Anonymous visitors are the common case, so fall back through the
        # three identities rather than showing a blank column.
        if obj.reporter_user_id:
            return str(obj.reporter_user)
        if obj.reporter_visitor_id:
            return str(obj.reporter_visitor)
        return obj.reporter_phone or 'ناشناس'

    @admin.display(description='شرح')
    def short_detail(self, obj):
        return (obj.detail[:60] + '…') if len(obj.detail) > 60 else (obj.detail or '—')

    def _set_status(self, request, queryset, status, label, resolving):
        """Shared body for the three changelist actions.

        Resolving (actioned/dismissed) routes through a confirmation page that
        asks for a reason — same rule as BusinessAdmin's reject/suspend bulk
        actions: a decision that closes a report needs a recoverable "why" for
        whoever reads it later. `mark_reviewing` is not a resolution, so it
        skips this and updates in place.
        """
        if not self.has_change_permission(request):
            raise PermissionDenied

        if not resolving:
            updated = queryset.update(status=status)
            self.message_user(request, f'{updated} گزارش «{label}» شد.', level=messages.SUCCESS)
            return None

        note = (request.POST.get('resolution_note') or '').strip()

        if not request.POST.get('apply'):
            return self._bulk_confirm_response(
                request, queryset, f'{label} گزارش‌ها', label,
                'admin/business/content_report_bulk.html',
            )

        if not note:
            return self._bulk_confirm_response(
                request, queryset, f'{label} گزارش‌ها', label,
                'admin/business/content_report_bulk.html', note_error='نوشتن دلیل الزامی است.',
            )

        updated = resolve_content_reports(queryset, status, request.user, note)
        self.message_user(request, f'{updated} گزارش «{label}» شد.', level=messages.SUCCESS)
        return None

    @admin.action(description='علامت‌گذاری به‌عنوان «در حال بررسی»')
    def mark_reviewing(self, request, queryset):
        # Not a resolution — resolved_by/resolved_at stay empty so the report
        # is still visibly open.
        return self._set_status(request, queryset, ContentReport.STATUS_REVIEWING, 'در حال بررسی', False)

    @admin.action(description='علامت‌گذاری به‌عنوان «اقدام شد»')
    def mark_actioned(self, request, queryset):
        return self._set_status(request, queryset, ContentReport.STATUS_ACTIONED, 'اقدام شد', True)

    @admin.action(description='علامت‌گذاری به‌عنوان «رد شد»')
    def mark_dismissed(self, request, queryset):
        return self._set_status(request, queryset, ContentReport.STATUS_DISMISSED, 'رد شد', True)
