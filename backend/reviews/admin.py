from django.contrib import admin, messages
from unfold.admin import ModelAdmin

from . import services
from .models import Review


@admin.register(Review)
class ReviewAdmin(ModelAdmin):
    list_display = ('created_at', 'business', 'visitor', 'rating', 'is_hidden', 'short_text')
    list_filter = ('is_hidden', 'rating')
    search_fields = ('business__title', 'visitor__full_name', 'visitor__phone_number', 'text')
    raw_id_fields = ('business', 'visitor', 'appointment')
    readonly_fields = ('created_at', 'updated_at')
    actions = ['hide_reviews', 'unhide_reviews']
    list_per_page = 50

    @admin.display(description='متن')
    def short_text(self, obj):
        return (obj.text or '')[:60]

    def _set_hidden(self, request, queryset, hidden):
        business_ids = set(queryset.values_list('business_id', flat=True))
        n = queryset.update(is_hidden=hidden, hidden_reason='' if not hidden else 'hidden by admin')
        for bid in business_ids:
            services.recompute_summary(bid)
        self.message_user(request, f'{n} نظر به‌روزرسانی شد', messages.SUCCESS)

    @admin.action(description='مخفی کردن نظرهای انتخاب‌شده')
    def hide_reviews(self, request, queryset):
        self._set_hidden(request, queryset, True)

    @admin.action(description='نمایش دوباره نظرهای انتخاب‌شده')
    def unhide_reviews(self, request, queryset):
        self._set_hidden(request, queryset, False)

    def save_model(self, request, obj, form, change):
        super().save_model(request, obj, form, change)
        services.recompute_summary(obj.business_id)

    def delete_model(self, request, obj):
        bid = obj.business_id
        super().delete_model(request, obj)
        services.recompute_summary(bid)
