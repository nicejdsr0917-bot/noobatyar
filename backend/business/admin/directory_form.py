"""The add form for a listing a staff member types in (see BusinessAdmin and the per-guild admins).

A listing added here has no owner yet: it belongs to the system user and is
unclaimed (no tick, booking off) until someone claims it. The form shows only
what a directory listing needs; the rest of the model's fields are folded away
and prefilled.
"""
import re

from django import forms
from django.urls import reverse
from django.utils import timezone

from .. import directory_import
from ..models import Business

_SPLIT = re.compile(r'[,،;\n]+')


class CommaListField(forms.CharField):
    """A list typed as «الف، ب، ج»; stored as a JSON list. Easier than typing JSON."""

    def __init__(self, **kwargs):
        kwargs.setdefault('required', False)
        kwargs.setdefault('widget', forms.Textarea(attrs={'rows': 2}))
        super().__init__(**kwargs)

    def prepare_value(self, value):
        if isinstance(value, (list, tuple)):
            return '، '.join(str(v) for v in value)
        return value or ''

    def to_python(self, value):
        return [part.strip() for part in _SPLIT.split(str(value or '')) if part.strip()]

    def validate(self, value):
        pass

    def has_changed(self, initial, data):
        return self.to_python(self.prepare_value(initial)) != self.to_python(data)


class DirectoryFormMixin:
    """Add-form behaviour shared by the all-categories admin and each guild's admin."""

    FORM_LABELS = {
        'province': 'استان',
        'city': 'شهر',
        'neighborhood': 'محله',
        'is_claimed': 'ثبت‌شده توسط صاحب (تیک نوبت‌یار)',
        'services': 'خدمات',
        'extra_phones': 'شماره‌های دیگر',
    }
    # Examples shown under «خدمات», per guild; a guild admin overrides this.
    SERVICES_HINT = 'با ویرگول جدا کنید.'
    # Whether the guild's businesses take insurance (shows the insurance picker).
    TAKES_INSURANCE = True

    # What a directory listing needs, in the order a person fills it (the fields
    # a public directory such as nobat.ir shows: name and specialty, phone and
    # address with a map pin, services and hours).
    def add_sections(self):
        sections = [
            ('اطلاعات اصلی', ('title', 'category', 'bio')),
            ('تماس و نشانی', (
                'phone', 'extra_phones', 'province', 'city', 'neighborhood', 'address', 'latitude', 'longitude',
            )),
            ('خدمات و ساعت کار', (
                ('services', 'insurances') if self.TAKES_INSURANCE else ('services',)
            ) + ('work_start_hour', 'work_end_hour', 'closed_weekdays')),
            ('تصویر', ('logo',)),
        ]
        return sections

    def get_changeform_initial_data(self, request):
        initial = super().get_changeform_initial_data(request)
        initial.setdefault('user', directory_import.get_system_user().pk)
        initial.setdefault('is_claimed', False)
        initial.setdefault('booking_enabled', False)
        # Required on the model but pointless to ask for a listing: the same defaults the import uses.
        initial.setdefault('default_service_duration', 30)
        initial.setdefault('work_start_hour', 9)
        initial.setdefault('work_end_hour', 18)
        return initial

    def get_fieldsets(self, request, obj=None):
        fieldsets = super().get_fieldsets(request, obj)
        if obj is not None:
            return fieldsets
        available = [
            name for _title, options in fieldsets for entry in options['fields']
            for name in ((entry,) if isinstance(entry, str) else entry)
        ]
        used = set()
        sections = []
        for title, names in self.add_sections():
            names = [n for n in names if n in available]
            used.update(names)
            sections.append((title, {'fields': names}))
        # Everything else is on the form, folded: the model requires it and it is prefilled.
        sections.append((
            'تنظیمات دیگر (معمولاً دست نزنید)',
            {'fields': [n for n in available if n not in used], 'classes': ['collapse']},
        ))
        return sections

    def get_form(self, request, obj=None, **kwargs):
        form = super().get_form(request, obj, **kwargs)
        for name in ('services', 'extra_phones'):
            if name in form.base_fields:
                form.base_fields[name] = CommaListField(
                    label=self.FORM_LABELS[name],
                    help_text=self.SERVICES_HINT if name == 'services' else 'با ویرگول جدا کنید؛ همان قالب «تلفن».',
                )
        for name, label in self.FORM_LABELS.items():
            if name in form.base_fields:
                form.base_fields[name].label = label
        if 'user' in form.base_fields and obj is None:
            form.base_fields['user'].help_text = (
                'برای مرکزی که صاحبش را ندارید، «بانک اطلاعاتی نوبت‌یار» بماند؛ بعداً با ادعای مالکیت منتقل می‌شود.'
            )
        if 'phone' in form.base_fields:
            # A directory listing often has no number; the client then shows no call button.
            form.base_fields['phone'].required = False
        if 'city_code' in form.base_fields:
            form.base_fields['city_code'].help_text = 'خالی بگذارید تا از نام شهر تعیین شود.'
        return form

    def save_model(self, request, obj, form, change):
        if obj.city and not obj.city_code:
            obj.city_code = directory_import.city_code_for(obj.city, obj.province)
        if not change and not obj.origin:
            obj.origin = Business.ORIGIN_ADMIN if obj.is_claimed else Business.ORIGIN_DIRECTORY
        if not change and not obj.is_claimed:
            # Moderation exists for what owners type; a listing the staff adds
            # is already vetted, and would otherwise wait in the queue, unseen.
            obj.moderation_status = Business.MODERATION_APPROVED
            obj.first_approved_at = timezone.now()
        super().save_model(request, obj, form, change)

    def changeform_view(self, request, object_id=None, form_url='', extra_context=None):
        extra_context = extra_context or {}
        extra_context['import_file_url'] = reverse(
            'admin:business_business_import_file', current_app=self.admin_site.name
        )
        extra_context['is_add_form'] = object_id is None
        return super().changeform_view(request, object_id, form_url, extra_context)
