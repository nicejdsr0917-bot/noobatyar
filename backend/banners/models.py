from django.core.exceptions import ValidationError
from django.db import models
from django.utils import timezone
from PIL import Image, UnidentifiedImageError

from business.categories import CATEGORY_CHOICES, CATEGORY_GROUPS

BANNER_WIDTH = 1200
BANNER_HEIGHT = 400
GROUP_CHOICES = [(key, label) for key, label, _cats in CATEGORY_GROUPS]
GROUP_BY_CATEGORY = {cat: key for key, _l, cats in CATEGORY_GROUPS for cat, _n in cats}


def categories_of_group(group_key):
    for key, _label, cats in CATEGORY_GROUPS:
        if key == group_key:
            return [c for c, _n in cats]
    return []


def validate_banner_size(image):
    """Reject anything that is not exactly 1200x400 (no tolerance)."""
    try:
        pos = image.tell() if hasattr(image, 'tell') else 0
        image.seek(0)
        with Image.open(image) as im:
            size = im.size
        image.seek(pos)
    except (UnidentifiedImageError, OSError, ValueError):
        raise ValidationError('فایل انتخاب‌شده عکس معتبر نیست.')
    if size != (BANNER_WIDTH, BANNER_HEIGHT):
        raise ValidationError(
            f'ابعاد بنر باید دقیقاً {BANNER_WIDTH}×{BANNER_HEIGHT} پیکسل باشد '
            f'(عکس انتخاب‌شده {size[0]}×{size[1]} است).')


class BannerCategoryGroup(models.Model):
    """A group of business categories (e.g. all medical specialties) that
    share banners. Created per group key from ``CATEGORY_GROUPS``."""

    key = models.CharField(max_length=30, unique=True, choices=GROUP_CHOICES, verbose_name='گروه دسته‌بندی')

    class Meta:
        db_table = 'banners_category_group'
        verbose_name = 'گروه دسته‌بندی بنر'
        verbose_name_plural = 'گروه‌های دسته‌بندی بنر'
        ordering = ['id']

    def __str__(self):
        return self.get_key_display()

    @property
    def categories(self):
        return categories_of_group(self.key)

    def owner_count(self):
        from business.models import Business
        return (Business.objects.filter(category__in=self.categories, is_claimed=True)
                .values('user').distinct().count())


class Banner(models.Model):
    group = models.ForeignKey(
        BannerCategoryGroup, null=True, blank=True, on_delete=models.CASCADE, related_name='banners',
        verbose_name='گروه دسته‌بندی', help_text='برای همهٔ دسته‌های این گروه نمایش داده می‌شود')
    category = models.CharField(
        max_length=50, blank=True, default='', choices=CATEGORY_CHOICES, verbose_name='دسته‌بندی',
        help_text='فقط برای یک دستهٔ مشخص (به‌جای گروه)')
    image = models.ImageField(
        upload_to='banners/', validators=[validate_banner_size], verbose_name='عکس',
        help_text=f'اندازهٔ دقیق عکس باید {BANNER_WIDTH}×{BANNER_HEIGHT} پیکسل باشد؛ اندازهٔ دیگر پذیرفته نمی‌شود.')
    order = models.PositiveIntegerField(default=0, verbose_name='ترتیب')
    is_active = models.BooleanField(default=True, db_index=True, verbose_name='فعال')
    starts_at = models.DateTimeField(null=True, blank=True, verbose_name='شروع نمایش')
    ends_at = models.DateTimeField(null=True, blank=True, verbose_name='پایان نمایش')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')

    class Meta:
        db_table = 'banners_banner'
        verbose_name = 'بنر'
        verbose_name_plural = 'بنرها'
        ordering = ['order', 'id']

    def __str__(self):
        return f'بنر {self.pk} — {self.target_label()}'

    def target_label(self):
        if self.category:
            return self.get_category_display()
        return str(self.group) if self.group_id else '—'

    def clean(self):
        if bool(self.group_id) == bool(self.category):
            raise ValidationError('دقیقاً یکی از «گروه دسته‌بندی» یا «دسته‌بندی» را انتخاب کنید.')
        if self.starts_at and self.ends_at and self.ends_at <= self.starts_at:
            raise ValidationError('پایان نمایش باید بعد از شروع باشد.')

    @classmethod
    def active_for_category(cls, category):
        now = timezone.now()
        group_key = GROUP_BY_CATEGORY.get(category)
        q = models.Q(category=category)
        if group_key:
            q |= models.Q(group__key=group_key)
        return (cls.objects.filter(q, is_active=True)
                .filter(models.Q(starts_at__isnull=True) | models.Q(starts_at__lte=now))
                .filter(models.Q(ends_at__isnull=True) | models.Q(ends_at__gte=now))
                .order_by('order', 'id'))


class SiteSetting(models.Model):
    """Singleton (pk=1). Every banner opens this one shared link."""

    banner_link_url = models.URLField(
        max_length=500, blank=True, default='', verbose_name='لینک بنرها',
        help_text='همهٔ بنرها با کلیک این لینک را باز می‌کنند (مثلاً صفحهٔ ارتقای پلن)')

    class Meta:
        db_table = 'banners_site_setting'
        verbose_name = 'تنظیمات سایت'
        verbose_name_plural = 'تنظیمات سایت'

    def save(self, *args, **kwargs):
        self.pk = 1
        super().save(*args, **kwargs)

    @classmethod
    def load(cls):
        obj, _ = cls.objects.get_or_create(pk=1)
        return obj

    def __str__(self):
        return 'تنظیمات سایت'


class ClientPromo(models.Model):
    """
    A one-line promo strip in the client app (like a ride app's offer bar):
    on the explore screen's list sheet, on the business map, or both. A tap
    opens [business] in the app, or [link_url], or nothing.
    """

    PLACEMENT_EXPLORE = 'EXPLORE'
    PLACEMENT_BUSINESS_MAP = 'BUSINESS_MAP'
    PLACEMENT_ALL = 'ALL'
    PLACEMENT_CHOICES = [
        (PLACEMENT_EXPLORE, 'صفحهٔ کاوش (بالای لیست)'),
        (PLACEMENT_BUSINESS_MAP, 'نقشهٔ مرکز'),
        (PLACEMENT_ALL, 'هر دو'),
    ]

    text = models.CharField(max_length=120, verbose_name='متن')
    action_label = models.CharField(
        max_length=24, blank=True, default='', verbose_name='متن دکمه',
        help_text='مثلاً «مشاهده»؛ خالی یعنی بدون دکمه')
    business = models.ForeignKey(
        'business.Business', null=True, blank=True, on_delete=models.SET_NULL, related_name='+',
        verbose_name='مرکز', help_text='با لمس، صفحهٔ این مرکز در اپ باز می‌شود')
    link_url = models.URLField(
        max_length=500, blank=True, default='', verbose_name='لینک',
        help_text='اگر مرکزی انتخاب نشده، این لینک باز می‌شود')
    placement = models.CharField(
        max_length=16, choices=PLACEMENT_CHOICES, default=PLACEMENT_ALL, verbose_name='محل نمایش')
    order = models.PositiveIntegerField(default=0, verbose_name='ترتیب')
    is_active = models.BooleanField(default=True, db_index=True, verbose_name='فعال')
    starts_at = models.DateTimeField(null=True, blank=True, verbose_name='شروع نمایش')
    ends_at = models.DateTimeField(null=True, blank=True, verbose_name='پایان نمایش')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')

    class Meta:
        db_table = 'banners_client_promo'
        verbose_name = 'نوار تبلیغ اپ مشتری'
        verbose_name_plural = 'نوارهای تبلیغ اپ مشتری'
        ordering = ['order', 'id']

    def __str__(self):
        return self.text

    def clean(self):
        if self.starts_at and self.ends_at and self.ends_at <= self.starts_at:
            raise ValidationError('پایان نمایش باید بعد از شروع باشد.')

    @classmethod
    def active_for(cls, placement):
        now = timezone.now()
        return (cls.objects.filter(is_active=True, placement__in=[placement, cls.PLACEMENT_ALL])
                .filter(models.Q(starts_at__isnull=True) | models.Q(starts_at__lte=now))
                .filter(models.Q(ends_at__isnull=True) | models.Q(ends_at__gte=now))
                .order_by('order', 'id'))
