"""Manual business-claim queue."""

from django.contrib import admin, messages
from unfold.admin import ModelAdmin
from ..claims import ClaimError, apply_claim
from ..models import BusinessClaimRequest
from ..services import reject_pending_claim_requests


@admin.register(BusinessClaimRequest)
class BusinessClaimRequestAdmin(ModelAdmin):
    """Manual claim queue: listings whose number can't receive an OTP land here.

    Approving runs the same transfer as a successful OTP claim
    (business.claims.apply_claim), so quota and "already claimed" rules hold.
    """

    list_display = ('created_at', 'business', 'user', 'method', 'status', 'note')
    list_filter = ('status', 'method')
    search_fields = ('business__title', 'business__phone', 'user__phone', 'note')
    raw_id_fields = ('business', 'user')
    readonly_fields = ('created_at', 'resolved_at')
    actions = ['approve_claims', 'reject_claims']

    @admin.action(description='تایید و انتقال مالکیت')
    def approve_claims(self, request, queryset):
        done = 0
        for claim in queryset.filter(status=BusinessClaimRequest.STATUS_PENDING):
            try:
                apply_claim(claim, resolution_note=f'تایید دستی توسط {request.user}')
                done += 1
            except ClaimError as exc:
                self.message_user(request, f"{claim.business}: {exc}", level=messages.ERROR)
        if done:
            self.message_user(request, f"{done} درخواست تایید شد", level=messages.SUCCESS)

    @admin.action(description='رد درخواست')
    def reject_claims(self, request, queryset):
        n = reject_pending_claim_requests(queryset)
        self.message_user(request, f"{n} درخواست رد شد", level=messages.SUCCESS)
