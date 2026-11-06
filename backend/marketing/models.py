from django.conf import settings
from django.db import models

from business.models import Business


class MarketingLead(models.Model):
    """Follow-up record for a manually listed centre (an unclaimed Business).

    Staff-only: there is deliberately no API for this app.
    """

    NOT_CALLED = 'NOT_CALLED'
    CALLED = 'CALLED'
    INTERESTED = 'INTERESTED'
    JOINED = 'JOINED'
    NOT_INTERESTED = 'NOT_INTERESTED'
    STATUS_CHOICES = [
        (NOT_CALLED, 'تماس نگرفته'),
        (CALLED, 'تماس گرفته شد'),
        (INTERESTED, 'علاقه‌مند'),
        (JOINED, 'عضو شد'),
        (NOT_INTERESTED, 'علاقه ندارد'),
    ]
    CLOSED_STATUSES = (JOINED, NOT_INTERESTED)

    business = models.OneToOneField(
        Business, on_delete=models.CASCADE, related_name='marketing_lead', verbose_name='مرکز')
    status = models.CharField(
        max_length=20, choices=STATUS_CHOICES, default=NOT_CALLED, db_index=True, verbose_name='وضعیت')
    next_follow_up = models.DateField(null=True, blank=True, db_index=True, verbose_name='پیگیری بعدی')
    assigned_to = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        limit_choices_to={'is_staff': True}, related_name='marketing_leads', verbose_name='مسوول پیگیری')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین ویرایش')

    class Meta:
        db_table = 'marketing_lead'
        verbose_name = 'مرکز بازاریابی'
        verbose_name_plural = 'مراکز بازاریابی'
        ordering = ['next_follow_up', '-created_at']

    def __str__(self):
        return self.business.title


class LeadNote(models.Model):
    lead = models.ForeignKey(MarketingLead, on_delete=models.CASCADE, related_name='notes', verbose_name='مرکز')
    text = models.TextField(verbose_name='یادداشت تماس')
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='+', verbose_name='ثبت‌کننده')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='زمان ثبت')

    class Meta:
        db_table = 'marketing_lead_note'
        verbose_name = 'یادداشت تماس'
        verbose_name_plural = 'یادداشت‌های تماس'
        ordering = ['-created_at']

    def __str__(self):
        return self.text[:40]
