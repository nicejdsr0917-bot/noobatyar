from django.contrib import admin
from django.db.models import Q
from django.utils import timezone
from unfold.admin import ModelAdmin
from .models import Appointment


class PaidWithoutSlotFilter(admin.SimpleListFilter):
    """Deposits Zibal confirmed whose slot was lost, so the money must go back.

    The deposit callback can land after the 15-minute hold expired and someone
    else took the time. The row is then left LOCKED (expired) with a trackId in
    ``payment_reference`` and ``deposit_verified_at`` set by
    ``verify_deposit_payment``. Nothing else ever turns those rows into an
    appointment, so this list is the manual-refund queue.
    """
    title = 'پرداخت‌شده بدون نوبت'
    parameter_name = 'paid_without_slot'

    def lookups(self, request, model_admin):
        return [('1', 'پرداخت‌شده بدون نوبت (نیاز به بازگشت وجه)')]

    def queryset(self, request, queryset):
        if self.value() != '1':
            return queryset
        # Verified by Zibal but never confirmed. Not limited to LOCKED: the
        # lazy expiry may later flip such a hold to CANCELLED, and it still
        # needs its refund.
        return queryset.filter(
            Q(status='LOCKED', expires_at__lt=timezone.now()) | Q(status='CANCELLED'),
            deposit_verified_at__isnull=False,
        ).exclude(deposit_payment_method='GATEWAY')


@admin.register(Appointment)
class AppointmentAdmin(ModelAdmin):
    list_display = ('id', 'business', 'visitor', 'status', 'created_at')
    list_filter = (PaidWithoutSlotFilter, 'status', 'created_at')
    search_fields = ('visitor__phone_number', 'business__title', 'payment_reference')
    readonly_fields = ('deposit_verified_at',)
