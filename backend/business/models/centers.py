"""«مراکز»: unclaimed directory listings, and the call tracking staff keep on them.

A center is a ``Business`` row with ``is_claimed=False`` (no Noobatyar tick, no
booking, owned by the system user). ``CenterListing`` is a proxy over the same
table so the admin can show centers on their own page; ``CenterOutreach`` holds
the follow-up (called? interested? converted?) in a side table, so the main
business table stays untouched. Converting a center is the existing claim flow
(business/claims.py): the row and its public link are kept.
"""
from django.conf import settings
from django.db import models

from .core import Business


class CenterListing(Business):
    class Meta:
        proxy = True
        app_label = 'business'
        verbose_name = 'مرکز'
        verbose_name_plural = 'مراکز'


class CenterOutreach(models.Model):
    STATUS_NEW = 'NEW'
    STATUS_CALLED = 'CALLED'
    STATUS_INTERESTED = 'INTERESTED'
    STATUS_CONVERTED = 'CONVERTED'
    STATUS_REJECTED = 'REJECTED'
    STATUS_CHOICES = [
        (STATUS_NEW, 'بدون تماس'),
        (STATUS_CALLED, 'تماس گرفته شد'),
        (STATUS_INTERESTED, 'علاقه‌مند'),
        (STATUS_CONVERTED, 'تبدیل شد'),
        (STATUS_REJECTED, 'رد کرد'),
    ]

    business = models.OneToOneField(Business, on_delete=models.CASCADE, related_name='outreach')
    status = models.CharField(max_length=12, choices=STATUS_CHOICES, default=STATUS_NEW, db_index=True, verbose_name='وضعیت تماس')
    note = models.TextField(blank=True, default='', verbose_name='یادداشت')
    contacted_at = models.DateTimeField(null=True, blank=True, verbose_name='تاریخ تماس')
    updated_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL,
        related_name='+', verbose_name='ثبت‌کننده',
    )
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        app_label = 'business'
        verbose_name = 'پیگیری تماس'
        verbose_name_plural = 'پیگیری تماس مراکز'

    def __str__(self):
        return f'{self.business_id}: {self.get_status_display()}'
