from django.conf import settings
from django.db import models

from business.models import Business


class ModerationStatus(models.TextChoices):
    PENDING = 'PENDING', 'در انتظار بررسی'
    APPROVED = 'APPROVED', 'تأییدشده'
    REJECTED = 'REJECTED', 'ردشده'


class ShowcaseItem(models.Model):
    """Shared shape of owner-uploaded content that staff approve before the
    public sees it (same idea as the logo flow: nothing shows until APPROVED)."""

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='%(class)ss',
        verbose_name='کسب‌وکار',
    )
    status = models.CharField(
        max_length=10, choices=ModerationStatus.choices, default=ModerationStatus.PENDING,
        db_index=True, verbose_name='وضعیت',
    )
    reject_reason = models.CharField(max_length=255, blank=True, default='', verbose_name='دلیل رد')
    reviewed_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='+', verbose_name='بررسی‌کننده',
    )
    reviewed_at = models.DateTimeField(null=True, blank=True, verbose_name='زمان بررسی')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین ویرایش')

    class Meta:
        abstract = True


class PortfolioItem(ShowcaseItem):
    MAX_PER_BUSINESS = 30
    CAPTION_MAX = 300
    # Fields that change what the public sees: editing one sends an approved
    # item back to review.
    MODERATED_FIELDS = ('image', 'caption')

    image = models.ImageField(upload_to='portfolio/', verbose_name='عکس')
    thumbnail = models.ImageField(upload_to='portfolio/thumbs/', blank=True, null=True, verbose_name='بندانگشتی')
    caption = models.CharField(max_length=CAPTION_MAX, blank=True, default='', verbose_name='توضیح')
    order = models.PositiveIntegerField(default=0, verbose_name='ترتیب')

    class Meta:
        db_table = 'showcase_portfolio_item'
        verbose_name = 'نمونه‌کار'
        verbose_name_plural = 'نمونه‌کارها'
        ordering = ['order', 'id']
        indexes = [models.Index(fields=['business', 'status', 'order'], name='portfolio_public_idx')]

    def __str__(self):
        return f'{self.business_id}: {self.caption[:30] or self.pk}'


class Certificate(ShowcaseItem):
    MAX_PER_BUSINESS = 30
    MODERATED_FIELDS = ('image', 'title', 'issuer', 'year')

    title = models.CharField(max_length=150, verbose_name='عنوان')
    issuer = models.CharField(max_length=150, blank=True, default='', verbose_name='صادرکننده')
    year = models.PositiveSmallIntegerField(null=True, blank=True, verbose_name='سال')
    image = models.ImageField(upload_to='certificates/', verbose_name='عکس')
    thumbnail = models.ImageField(upload_to='certificates/thumbs/', blank=True, null=True, verbose_name='بندانگشتی')
    order = models.PositiveIntegerField(default=0, verbose_name='ترتیب')

    class Meta:
        db_table = 'showcase_certificate'
        verbose_name = 'مدرک'
        verbose_name_plural = 'مدارک'
        ordering = ['order', 'id']
        indexes = [models.Index(fields=['business', 'status', 'order'], name='certificate_public_idx')]

    def __str__(self):
        return f'{self.business_id}: {self.title}'
