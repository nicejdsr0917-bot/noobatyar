from django.db import models

from .core import Business


class BusinessFollow(models.Model):
    """A customer following a business page. No limit on how many a customer
    follows. ``Business.followers_count`` mirrors the number of rows; only
    business/follows.py adds or removes them.

    Only the owner sees who follows their business, and then only the first
    name and profile photo (business/follow_views.py), never the phone.
    """

    business = models.ForeignKey(Business, on_delete=models.CASCADE, related_name='follows', verbose_name='کسب‌وکار')
    visitor = models.ForeignKey('visitor.Visitor', on_delete=models.CASCADE, related_name='follows', verbose_name='مشتری')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ')

    class Meta:
        verbose_name = 'دنبال‌کردن کسب‌وکار'
        verbose_name_plural = 'دنبال‌کردن کسب‌وکارها'
        constraints = [
            models.UniqueConstraint(fields=['business', 'visitor'], name='uniq_business_follow'),
        ]
        indexes = [models.Index(fields=['visitor', '-created_at'])]

    def __str__(self):
        return f'{self.visitor_id} → {self.business_id}'
