"""One proxy model per guild (صنف), so the admin can give each its own list and add form.

They share the ``Business`` table; a proxy only changes how the admin presents
it (see business/admin/guilds.py). ``GUILDS`` ties each to the category group
whose categories it holds.
"""
from .core import Business


class MedicalListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز پزشکی'
        verbose_name_plural = 'مراکز پزشکی'


class DentalListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز دندان‌پزشکی'
        verbose_name_plural = 'مراکز دندان‌پزشکی'


class HealthListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز سلامت'
        verbose_name_plural = 'مراکز سلامت'


class BeautyListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'سالن زیبایی'
        verbose_name_plural = 'سالن‌های زیبایی'


class ProfessionalListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز خدمات حرفه‌ای'
        verbose_name_plural = 'مراکز خدمات حرفه‌ای'


class EducationListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز آموزش و ورزش'
        verbose_name_plural = 'مراکز آموزش و ورزش'


class TradesListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز خدمات فنی'
        verbose_name_plural = 'مراکز خدمات فنی'


class OtherListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز دیگر'
        verbose_name_plural = 'مراکز دیگر'


# category group key → (proxy model, short sidebar title, sidebar icon)
GUILDS = {
    'MEDICAL': (MedicalListing, 'پزشکی', 'stethoscope'),
    'DENTAL': (DentalListing, 'دندان‌پزشکی', 'medical_services'),
    'HEALTH': (HealthListing, 'سلامت', 'psychology'),
    'BEAUTY': (BeautyListing, 'زیبایی', 'content_cut'),
    'PROFESSIONAL': (ProfessionalListing, 'حرفه‌ای', 'work'),
    'EDUCATION': (EducationListing, 'آموزش', 'school'),
    'TRADES': (TradesListing, 'فنی', 'build'),
    'OTHER': (OtherListing, 'سایر', 'category'),
}
