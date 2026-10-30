from django import forms
from django.contrib import admin
from django.utils import timezone
from unfold.admin import ModelAdmin, TabularInline

from business.categories import CATEGORY_CHOICES

from . import services
from .models import LeadNote, MarketingLead


class LeadAddForm(forms.ModelForm):
    """Add form: collects the centre's details and creates the Business too."""
    title = forms.CharField(max_length=255, label='نام مرکز')
    category = forms.ChoiceField(choices=CATEGORY_CHOICES, label='دسته‌بندی')
    phone = forms.CharField(max_length=20, label='تلفن')
    address = forms.CharField(widget=forms.Textarea(attrs={'rows': 3}), label='نشانی')
    city = forms.CharField(max_length=100, required=False, label='شهر')
    province = forms.CharField(max_length=100, required=False, label='استان')
    latitude = forms.FloatField(required=False, min_value=-90, max_value=90, label='عرض جغرافیایی (پین)')
    longitude = forms.FloatField(required=False, min_value=-180, max_value=180, label='طول جغرافیایی (پین)')

    class Meta:
        model = MarketingLead
        fields = ('assigned_to', 'next_follow_up')

    def clean(self):
        data = super().clean()
        if (data.get('latitude') is None) != (data.get('longitude') is None):
            raise forms.ValidationError('پین نقشه باید هر دو مختصات را داشته باشد.')
        return data


class NoteInline(TabularInline):
    model = LeadNote
    extra = 1
    fields = ('text', 'created_by', 'created_at')
    readonly_fields = ('created_by', 'created_at')


class TodayCallsFilter(admin.SimpleListFilter):
    title = 'تماس‌های امروز'
    parameter_name = 'today'

    def lookups(self, request, model_admin):
        return [('1', 'تماس‌های امروز (و عقب‌افتاده)')]

    def queryset(self, request, queryset):
        if self.value() == '1':
            return queryset.filter(next_follow_up__lte=timezone.localdate()).exclude(
                status__in=MarketingLead.CLOSED_STATUSES)
        return queryset


@admin.register(MarketingLead)
class MarketingLeadAdmin(ModelAdmin):
    list_display = ('business_title', 'phone', 'city', 'status', 'next_follow_up', 'assigned_to')
    list_filter = (TodayCallsFilter, 'status', 'assigned_to')
    list_select_related = ('business', 'assigned_to')
    search_fields = ('business__title', 'business__phone')
    list_editable = ('status', 'next_follow_up')
    autocomplete_fields = ()
    inlines = [NoteInline]

    @admin.display(description='مرکز', ordering='business__title')
    def business_title(self, obj):
        return obj.business.title

    @admin.display(description='تلفن')
    def phone(self, obj):
        return obj.business.phone

    @admin.display(description='شهر')
    def city(self, obj):
        return obj.business.city

    def get_form(self, request, obj=None, **kwargs):
        if obj is None:
            kwargs['form'] = LeadAddForm
            kwargs['fields'] = ('title', 'category', 'phone', 'address', 'province', 'city',
                                'latitude', 'longitude', 'assigned_to', 'next_follow_up')
        return super().get_form(request, obj, **kwargs)

    def get_fields(self, request, obj=None):
        if obj is None:
            return ('title', 'category', 'phone', 'address', 'province', 'city',
                    'latitude', 'longitude', 'assigned_to', 'next_follow_up')
        return ('business', 'status', 'next_follow_up', 'assigned_to')

    def get_readonly_fields(self, request, obj=None):
        return ('business',) if obj else ()

    def get_inline_instances(self, request, obj=None):
        return super().get_inline_instances(request, obj) if obj else []

    def save_model(self, request, obj, form, change):
        if change:
            return super().save_model(request, obj, form, change)
        cd = form.cleaned_data
        extra = {k: cd[k] for k in ('city', 'province', 'latitude', 'longitude') if cd.get(k) not in (None, '')}
        lead = services.create_lead(
            title=cd['title'], category=cd['category'], phone=cd['phone'], address=cd['address'],
            assigned_to=cd.get('assigned_to'), next_follow_up=cd.get('next_follow_up'), **extra)
        obj.pk, obj.business = lead.pk, lead.business
        obj.created_at, obj.updated_at = lead.created_at, lead.updated_at

    def save_formset(self, request, form, formset, change):
        for note in formset.save(commit=False):
            if note.pk is None:
                note.created_by = request.user
            note.save()
        formset.save_m2m()
        for obj in formset.deleted_objects:
            obj.delete()
