from django import forms
from django.contrib import admin
from django.utils import timezone
from django.utils.html import format_html
from unfold.admin import ModelAdmin

from . import services
from .models import Story


class StoryAdminForm(forms.ModelForm):
    """Leave «کسب‌وکار» empty to post as Noobatyar. A link button is only for
    Noobatyar's stories; a discount only for a business's."""

    class Meta:
        model = Story
        fields = ('business', 'image', 'caption', 'button', 'link_label', 'link_url',
                  'discount_type', 'discount_value', 'expires_at')

    def clean(self):
        data = super().clean()
        button = data.get('button') or ''
        business = data.get('business')
        if button == Story.BUTTON_LINK and business is not None:
            raise forms.ValidationError('دکمه لینک فقط برای استوری خود نوبت‌یار است (کسب‌وکار را خالی بگذارید).')
        if button == Story.BUTTON_DISCOUNT and business is None:
            raise forms.ValidationError('دکمه تخفیف فقط برای استوری یک کسب‌وکار است.')
        try:
            data['link_label'], data['link_url'] = services.validate_platform_link(
                button, data.get('link_label'), data.get('link_url'))
        except services.StoryError as exc:
            raise forms.ValidationError(str(exc))
        return data


class StoryStatusFilter(admin.SimpleListFilter):
    title = 'نوع'
    parameter_name = 'kind'

    def lookups(self, request, model_admin):
        return [('platform', 'نوبت‌یار'), ('business', 'کسب‌وکارها'), ('active', 'فعال')]

    def queryset(self, request, queryset):
        if self.value() == 'platform':
            return queryset.filter(business__isnull=True)
        if self.value() == 'business':
            return queryset.filter(business__isnull=False)
        if self.value() == 'active':
            return queryset.filter(expires_at__gt=timezone.now())
        return queryset


@admin.register(Story)
class StoryAdmin(ModelAdmin):
    form = StoryAdminForm
    list_display = ('preview', 'owner', 'caption', 'button', 'created_at', 'expires_at', 'is_active')
    list_filter = (StoryStatusFilter,)
    list_select_related = ('business',)
    raw_id_fields = ('business',)
    search_fields = ('business__title', 'caption')

    @admin.display(description='عکس')
    def preview(self, obj):
        if not obj.image:
            return '—'
        return format_html('<img src="{}" style="height:56px;border-radius:6px" />', obj.image.url)

    @admin.display(description='صاحب', ordering='business__title')
    def owner(self, obj):
        return obj.business.title if obj.business_id else Story.PLATFORM_TITLE

    @admin.display(description='فعال', boolean=True)
    def is_active(self, obj):
        return obj.expires_at > timezone.now()

    def save_model(self, request, obj, form, change):
        # Scaled and re-encoded like an owner's upload (strips metadata too).
        if 'image' in form.changed_data and obj.image:
            obj.image = services.process_admin_image(obj.image)
        super().save_model(request, obj, form, change)
