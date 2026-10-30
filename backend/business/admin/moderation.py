"""Audit-trail browser and banned-keyword list."""

from api.jalali import format_datetime
from django.contrib import admin
from django.urls import reverse
from django.utils.html import format_html
from unfold.admin import ModelAdmin
from ..models import BannedKeyword, BusinessModerationLog
from ._common import _badge


@admin.register(BusinessModerationLog)
class BusinessModerationLogAdmin(ModelAdmin):
    """Browse-only view of the audit trail.

    No add, no change, and no deleting a log on its own — including for
    superusers. If a moderation log can be edited or removed from the admin it
    stops being evidence, and the whole reason this table exists separately
    from LogEntry is that it has to answer "who decided this, and why" long
    after the fact.

    Cascading with the business it describes is the one exception; see
    has_delete_permission.
    """

    list_display = ('created_jalali', 'business_link', 'transition', 'actor', 'short_note')
    list_filter = ('to_status', 'from_status')
    search_fields = ('business__title', 'business__unique_code', 'note', 'actor__phone')
    date_hierarchy = 'created_at'

    def get_readonly_fields(self, request, obj=None):
        return [f.name for f in self.model._meta.fields]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        """Block deleting a log directly; allow it to cascade with its business.

        Returning a flat False here is what made *businesses* undeletable for
        everyone, superusers included. Django's delete-confirmation page walks
        every object that would cascade and asks that object's own ModelAdmin
        for delete permission (admin.utils.get_deleted_objects); a refusal adds
        the model to `perms_needed`, producing "شناسه شما اجازه حذف اینگونه از
        موجودیت‌ها را ندارد: business moderation log". Since every business
        accumulates a log on its first moderation decision, that silently
        locked deletion for the entire table.

        A log is evidence *about a business*. Once the business itself is being
        deleted the log has no subject left, so letting it cascade is not an
        audit hole — whereas deleting a log while its business lives on is
        exactly the tampering this admin exists to prevent. We tell the two
        apart by which admin is handling the request: anything routed to this
        model's own admin views (changelist, change, delete, and the
        "delete selected" action posted back to the changelist) stays refused.
        """
        match = request.resolver_match
        if match is None or not match.url_name:
            return False
        own_views = f'{self.opts.app_label}_{self.opts.model_name}_'
        return not match.url_name.startswith(own_views)

    def get_queryset(self, request):
        return super().get_queryset(request).select_related('business', 'actor')

    @admin.display(description='زمان', ordering='created_at')
    def created_jalali(self, obj):
        return format_datetime(obj.created_at) if obj.created_at else '—'

    @admin.display(description='کسب‌وکار', ordering='business__title')
    def business_link(self, obj):
        url = reverse('admin:business_business_change', args=[obj.business_id])
        return format_html('<a href="{}">{}</a>', url, obj.business.title)

    @admin.display(description='تغییر وضعیت')
    def transition(self, obj):
        return f"{obj.from_status or '—'} ← {obj.to_status}"

    @admin.display(description='دلیل')
    def short_note(self, obj):
        return (obj.note[:80] + '…') if len(obj.note) > 80 else (obj.note or '—')


@admin.register(BannedKeyword)
class BannedKeywordAdmin(ModelAdmin):
    """Full CRUD — this list is meant to be tuned by the people using it.

    `is_active` is inline-editable because turning off a term that is flagging
    every legitimate salon needs to take one click during a review session, not
    a trip through a change form. Unlike Business.is_locked nothing recomputes
    this field, so an edit here sticks.
    """

    list_display = ('term', 'severity_badge', 'note', 'is_active', 'created_at')
    list_editable = ('is_active',)
    list_filter = ('severity', 'is_active')
    search_fields = ('term', 'note')

    @admin.display(description='شدت', ordering='severity')
    def severity_badge(self, obj):
        if obj.severity == BannedKeyword.SEVERITY_HIGH:
            return _badge('زیاد', 'danger')
        return _badge('کم', 'warn')
