from datetime import timedelta

from django.db import models
from django.utils import timezone

from business.models import Business

STORY_LIFETIME = timedelta(hours=24)


def _expiry():
    return timezone.now() + STORY_LIFETIME


class Story(models.Model):
    """A business's photo story, shown to customers for 24 hours.

    No admin approval (owner's decision, 2026-11-05). Expired rows are not
    served; ``services.create_story`` deletes a business's expired rows and
    their files, so there is no cron job.

    Without a business it is Noobatyar's own story, posted from the admin
    panel: first in the client app's story row, with an optional link button
    instead of a discount.
    """

    PLATFORM_TITLE = 'نوبت‌یار'

    CAPTION_MAX = 100
    MAX_ACTIVE_PER_BUSINESS = 10

    BUTTON_NONE = ''
    BUTTON_DISCOUNT = 'discount'
    BUTTON_LINK = 'link'
    BUTTON_CHOICES = [(BUTTON_NONE, 'بدون دکمه'), (BUTTON_DISCOUNT, 'تخفیف'), (BUTTON_LINK, 'لینک (فقط نوبت‌یار)')]
    LINK_LABEL_MAX = 30

    DISCOUNT_PERCENT = 'percent'
    DISCOUNT_AMOUNT = 'amount'
    DISCOUNT_CHOICES = [(DISCOUNT_PERCENT, 'درصدی'), (DISCOUNT_AMOUNT, 'مبلغی (تومان)')]

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='stories', null=True, blank=True,
        verbose_name='کسب‌وکار', help_text='خالی = استوری خود نوبت‌یار',
    )
    image = models.ImageField(upload_to='stories/', verbose_name='عکس')
    caption = models.CharField(max_length=CAPTION_MAX, blank=True, default='', verbose_name='متن')
    button = models.CharField(max_length=20, choices=BUTTON_CHOICES, blank=True, default=BUTTON_NONE, verbose_name='دکمه')
    discount_type = models.CharField(max_length=10, choices=DISCOUNT_CHOICES, blank=True, default='', verbose_name='نوع تخفیف')
    discount_value = models.PositiveIntegerField(null=True, blank=True, verbose_name='مقدار تخفیف')
    link_label = models.CharField(max_length=LINK_LABEL_MAX, blank=True, default='', verbose_name='متن دکمهٔ لینک')
    link_url = models.CharField(
        max_length=500, blank=True, default='', verbose_name='مقصد لینک',
        help_text='آدرس https://… یا business:<شناسه> برای باز کردن صفحهٔ یک کسب‌وکار در اپ',
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ')
    expires_at = models.DateTimeField(default=_expiry, db_index=True, verbose_name='انقضا')

    class Meta:
        verbose_name = 'استوری'
        verbose_name_plural = 'استوری‌ها'
        ordering = ['created_at']
        indexes = [models.Index(fields=['business', 'expires_at'])]

    def __str__(self):
        return f'{self.business_id or self.PLATFORM_TITLE} · {self.created_at:%Y-%m-%d %H:%M}'

    @classmethod
    def active(cls):
        return cls.objects.filter(expires_at__gt=timezone.now())
