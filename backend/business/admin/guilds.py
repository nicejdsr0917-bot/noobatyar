"""One admin per guild (صنف): its own list and its own add form.

Each is a view of the same ``Business`` table through a proxy model
(business/models/guilds.py). The add form offers only that guild's categories,
shows the fields that make sense for it (insurance only where it applies) and
suggests its services. «کسب‌وکارها» stays the place for everything.
"""
from django.contrib import admin
from unfold.admin import ModelAdmin

from ..categories import CATEGORY_GROUPS
from ..models import Business
from ..models.guilds import GUILDS
from .business import BusinessAdmin
from .directory_form import DirectoryFormMixin

SERVICE_EXAMPLES = {
    'MEDICAL': 'مثلاً: ویزیت، نوار قلب، سونوگرافی',
    'DENTAL': 'مثلاً: جرم‌گیری، ترمیم، عصب‌کشی، ایمپلنت',
    'HEALTH': 'مثلاً: جلسه مشاوره، فیزیوتراپی، آزمایش خون',
    'BEAUTY': 'مثلاً: کوتاهی مو، رنگ و مش، کراتین، مانیکور، میکاپ',
    'PROFESSIONAL': 'مثلاً: مشاوره اولیه، تنظیم قرارداد',
    'EDUCATION': 'مثلاً: جلسه آشنایی، کلاس خصوصی',
    'TRADES': 'مثلاً: تعویض روغن، تعمیر موبایل',
    'OTHER': '',
}
TAKES_INSURANCE = {'MEDICAL', 'DENTAL', 'HEALTH'}


class GuildAdmin(DirectoryFormMixin, ModelAdmin):
    """Base of the per-guild admins; ``categories`` is set on each subclass."""

    categories = ()
    # Same banner as the all-categories add page: a pointer to the file import.
    change_form_template = 'admin/business/business/change_form.html'
    list_display = ('title', 'category', 'city', 'phone', 'listing_state', 'created_at')
    search_fields = ('title', 'phone', 'address', 'unique_code')
    filter_horizontal = ('insurances',)
    ordering = ('-created_at',)
    # Same as the all-categories admin: a status typed here would be an unlogged decision.
    readonly_fields = BusinessAdmin.readonly_fields

    def get_queryset(self, request):
        return super().get_queryset(request).filter(category__in=self.categories).select_related('user')

    def formfield_for_choice_field(self, db_field, request, **kwargs):
        if db_field.name == 'category':
            kwargs['choices'] = [(code, label) for code, label in Business.CATEGORY_CHOICES if code in self.categories]
        return super().formfield_for_choice_field(db_field, request, **kwargs)

    def get_changeform_initial_data(self, request):
        initial = super().get_changeform_initial_data(request)
        initial.setdefault('category', self.categories[0])
        return initial

    @admin.display(description='وضعیت')
    def listing_state(self, obj):
        return 'ثبت‌شده توسط صاحب' if obj.is_claimed else 'بدون تیک'


def _register(group, model):
    codes = tuple(code for key, _label, pairs in CATEGORY_GROUPS if key == group for code, _ in pairs)

    class CategoryFilter(admin.SimpleListFilter):
        title = 'صنف'
        parameter_name = 'category'

        def lookups(self, request, model_admin):
            return [(c, l) for c, l in Business.CATEGORY_CHOICES if c in codes]

        def queryset(self, request, queryset):
            return queryset.filter(category=self.value()) if self.value() else queryset

    admin_class = type(f'{model.__name__}Admin', (GuildAdmin,), {
        'categories': codes,
        'list_filter': (CategoryFilter, 'is_claimed'),
        'SERVICES_HINT': (SERVICE_EXAMPLES[group] + ' (با ویرگول جدا کنید)').strip(),
        'TAKES_INSURANCE': group in TAKES_INSURANCE,
        '__module__': __name__,
    })
    admin.site.register(model, admin_class)


for _group, (_model, _title, _icon) in GUILDS.items():
    _register(_group, _model)
