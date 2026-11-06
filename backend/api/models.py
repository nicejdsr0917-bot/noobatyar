# api/models.py
from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, PermissionsMixin
from django.db import models


class UserManager(BaseUserManager):
    """مدیریت ساخت کاربران عادی و سوپریوزر"""
    
    def create_user(self, phone, password=None, **extra):
        if not phone:
            raise ValueError('شماره موبایل الزامی است')
        
        user = self.model(phone=phone, **extra)
        user.set_password(password)  # Argon2 hash
        user.save(using=self._db)
        return user

    def create_superuser(self, phone, password=None, **extra):
        extra.setdefault('is_staff', True)
        extra.setdefault('is_superuser', True)
        extra.setdefault('role', 'ADMIN')
        return self.create_user(phone, password, **extra)


class User(AbstractBaseUser, PermissionsMixin):
    """
    مدل کاربر سفارشی با phone به جای username
    - phone: شناسه یکتا (09XXXXXXXXX)
    - password: hash شده با Argon2
    - role: نقش کاربر در سیستم
    """
    ROLE_CHOICES = [
        ('BUSINESS_OWNER', 'صاحب کسب‌وکار'),
        ('CLIENT', 'مشتری'),
        ('ADMIN', 'مدیر'),
    ]

    # verbose_name matters here beyond cosmetics: USERNAME_FIELD is 'phone', so
    # this label is what the admin login form renders. Without it the one English
    # word "Phone:" sits in the middle of an otherwise fully Persian RTL panel.
    phone = models.CharField(max_length=11, unique=True, db_index=True, verbose_name='شماره موبایل')
    name = models.CharField(max_length=100, verbose_name='نام')
    role = models.CharField(max_length=20, choices=ROLE_CHOICES, default='CLIENT', verbose_name='نقش')
    is_employee = models.BooleanField(default=False, verbose_name='کارمند')
    is_staff = models.BooleanField(default=False, verbose_name='کارمند پنل')
    is_active = models.BooleanField(default=True, verbose_name='فعال')
    joined_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ عضویت')
    # Stamped by the forgot-password reset; enforces "one change per
    # PASSWORD_CHANGE_COOLDOWN_HOURS" (api/views.py). A DB column rather than
    # a cache key: the limit has to survive a Redis flush or restart.
    password_changed_at = models.DateTimeField(null=True, blank=True, verbose_name='آخرین تغییر رمز')

    USERNAME_FIELD = 'phone'
    REQUIRED_FIELDS = ['name']

    objects = UserManager()

    class Meta:
        verbose_name = 'کاربر'
        verbose_name_plural = 'کاربران'

    def __str__(self):
        return f"{self.name} ({self.phone})"


class DeviceToken(models.Model):
    """
    An FCM registration token for one installation of the owner app.

    One row per device, not per user: an owner with a phone and a tablet gets a
    push on both. ``token`` is unique across the whole table rather than unique
    *per user* on purpose — FCM hands the same token to whoever installs the app
    on that device, so when a second account signs in on a phone the row has to
    move to the new user instead of leaving the previous owner receiving
    somebody else's appointments.

    Dead tokens are deactivated rather than deleted (see
    ``api.services.push.send_to_user``) so that a device that comes back — the
    app reinstalled, the same token re-registered — simply flips ``is_active``
    back on rather than losing its history.
    """

    PLATFORM_CHOICES = [
        ('ANDROID', 'اندروید'),
        ('IOS', 'iOS'),
        ('WEB', 'وب'),
    ]

    user = models.ForeignKey(
        'api.User',
        on_delete=models.CASCADE,
        related_name='device_tokens',
        help_text='مالک این دستگاه',
        verbose_name='کاربر',
    )
    token = models.CharField(
        max_length=255,
        unique=True,
        db_index=True,
        help_text='توکن ثبت FCM',
        verbose_name='توکن',
    )
    platform = models.CharField(
        max_length=10,
        choices=PLATFORM_CHOICES,
        default='ANDROID',
        verbose_name='پلتفرم',
    )
    device_name = models.CharField(max_length=100, blank=True, default='', verbose_name='نام دستگاه')
    app_version = models.CharField(max_length=30, blank=True, default='', verbose_name='نسخه‌ی اپ')
    is_active = models.BooleanField(
        default=True,
        db_index=True,
        help_text='وقتی FCM توکن را نامعتبر اعلام کند، غیرفعال می‌شود',
        verbose_name='فعال',
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')

    class Meta:
        verbose_name = 'توکن دستگاه'
        verbose_name_plural = 'توکن‌های دستگاه'
        ordering = ['-updated_at']

    def __str__(self):
        return f"{self.user.phone} · {self.platform} · {self.token[:12]}…"


class UserIpSighting(models.Model):
    """An IP an owner's app or panel was seen on (refreshed at most hourly).

    Only used to spot an owner booking their own business as a "new client"
    for the new-client reward (accounting.toman_wallet.reward_new_client).
    """

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='ip_sightings', verbose_name='کاربر')
    ip = models.GenericIPAddressField(verbose_name='IP')
    last_seen = models.DateTimeField(verbose_name='آخرین بار')

    class Meta:
        verbose_name = 'IP دیده‌شده کاربر'
        verbose_name_plural = 'IPهای دیده‌شده کاربران'
        constraints = [models.UniqueConstraint(fields=['user', 'ip'], name='uniq_user_ip_sighting')]

    def __str__(self):
        return f'{self.user_id} {self.ip}'
