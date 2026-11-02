from django.contrib import admin
from django.utils.html import format_html
from unfold.admin import ModelAdmin, TabularInline

from business.models import Business

from .models import (
    BANNER_HEIGHT, BANNER_WIDTH, GROUP_BY_CATEGORY, Banner, BannerCategoryGroup, ClientPromo, SiteSetting,
    categories_of_group,
)


class BannerInline(TabularInline):
    model = Banner
    fk_name = 'group'
    extra = 0
    fields = ('image', 'order', 'is_active', 'starts_at', 'ends_at')


@admin.register(BannerCategoryGroup)
class BannerCategoryGroupAdmin(ModelAdmin):
    """One row per category group, with how many owners will see its banners."""

    list_display = ('key_label', 'owners', 'banner_count')
    inlines = [BannerInline]

    def get_inline_instances(self, request, obj=None):
        return super().get_inline_instances(request, obj)

    @admin.display(description='گروه', ordering='key')
    def key_label(self, obj):
        return str(obj)

    @admin.display(description='تعداد اونرها')
    def owners(self, obj):
        return obj.owner_count()

    @admin.display(description='تعداد بنرها')
    def banner_count(self, obj):
        return obj.banners.count()


@admin.register(Banner)
class BannerAdmin(ModelAdmin):
    list_display = ('thumb', 'target', 'owners', 'order', 'is_active', 'starts_at', 'ends_at')
    list_filter = ('is_active', 'group', 'category')
    list_editable = ('order', 'is_active')
    list_select_related = ('group',)
    fieldsets = (
        (None, {
            'description': f'اندازهٔ عکس بنر دقیقاً {BANNER_WIDTH}×{BANNER_HEIGHT} پیکسل است؛ عکس با اندازهٔ دیگر رد می‌شود. '
                           'یا یک «گروه دسته‌بندی» یا یک «دسته‌بندی» انتخاب کنید (نه هر دو).',
            'fields': ('group', 'category', 'image', 'order', 'is_active', 'starts_at', 'ends_at'),
        }),
    )

    @admin.display(description='عکس')
    def thumb(self, obj):
        return format_html('<img src="{}" style="height:40px;border-radius:4px">', obj.image.url) if obj.image else '—'

    @admin.display(description='نمایش برای')
    def target(self, obj):
        return obj.target_label()

    @admin.display(description='تعداد اونرها')
    def owners(self, obj):
        if obj.category:
            cats = [obj.category]
        elif obj.group_id:
            cats = categories_of_group(obj.group.key)
        else:
            return 0
        return Business.objects.filter(category__in=cats, is_claimed=True).values('user').distinct().count()


@admin.register(SiteSetting)
class SiteSettingAdmin(ModelAdmin):
    list_display = ('__str__', 'banner_link_url')

    def has_add_permission(self, request):
        return not SiteSetting.objects.exists()

    def has_delete_permission(self, request, obj=None):
        return False

    def changelist_view(self, request, extra_context=None):
        SiteSetting.load()
        return super().changelist_view(request, extra_context)


@admin.register(ClientPromo)
class ClientPromoAdmin(ModelAdmin):
    list_display = ('text', 'placement', 'business', 'order', 'is_active', 'starts_at', 'ends_at')
    list_filter = ('is_active', 'placement')
    list_editable = ('order', 'is_active')
    list_select_related = ('business',)
    autocomplete_fields = ('business',)
    fieldsets = (
        (None, {
            'description': 'یک خط کوتاه بالای لیست کاوش یا روی نقشهٔ مرکز در اپ مشتری. '
                           'با لمس، مرکز انتخاب‌شده در اپ باز می‌شود؛ اگر مرکزی نیست، لینک.',
            'fields': ('text', 'action_label', 'business', 'link_url', 'placement',
                       'order', 'is_active', 'starts_at', 'ends_at'),
        }),
    )
