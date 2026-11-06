from django.utils import timezone
from django.db import models

# Create your models here.
from django.db import models
from django.core.exceptions import ValidationError

from business.models import Business
from visitor.models import Visitor
from django.contrib.auth import get_user_model

User = get_user_model()

class Appointment(models.Model):
    # ── Status choices ────────────────────────────────────────────────────
    # LOCKED              : slot is temporarily held (15 min) while client pays
    # PENDING_VERIFICATION: client submitted payment receipt; owner must verify
    # PENDING_APPROVAL    : (legacy) client booked; owner must approve (free flow)
    # CONFIRMED           : owner approved / payment verified / gateway succeeded
    # WAITING             : confirmed and waiting for the appointment time
    # IN_PROGRESS         : currently being served
    # COMPLETED           : done
    # NO_SHOW             : client did not show up
    # CANCELLED           : rejected or manually cancelled
    STATUS_CHOICES = [
        ('LOCKED',               'قفل شده (در حال پرداخت)'),
        ('PENDING_VERIFICATION', 'در انتظار تأیید فیش'),
        ('PENDING_APPROVAL',     'در انتظار تأیید مالک'),
        ('CONFIRMED',            'تأیید شده'),
        ('WAITING',              'در صف انتظار'),
        ('IN_PROGRESS',          'در حال سرویس'),
        ('COMPLETED',            'تکمیل شده'),
        ('NO_SHOW',              'غیبت'),
        ('CANCELLED',            'لغو شده'),
    ]

    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='appointments',
        null=True,
        blank=True,
        help_text="وقتی مالک به‌جای مشتری نوبت ثبت کند، همان مالک. برای نوبتی "
                  "که مشتری خودش از سایت گرفته خالی است.",
                  verbose_name='کاربر',
    )
    business = models.ForeignKey(
        Business,
        on_delete=models.CASCADE,
        related_name='appointments',
        db_index=True,
        verbose_name='کسب‌وکار',
    )
    visitor = models.ForeignKey(
        Visitor,
        on_delete=models.CASCADE,
        related_name='appointments',
        db_index=True,
        verbose_name='مشتری',
    )
    appointment_date = models.DateTimeField(db_index=True, verbose_name='زمان نوبت')
    service_duration = models.PositiveIntegerField(
        null=True,
        blank=True,
        help_text='مدت به دقیقه؛ خالی یعنی مدت پیش‌فرض کسب‌وکار.',
        verbose_name='مدت خدمت (دقیقه)',
    )
    status = models.CharField(
        max_length=25,
        choices=STATUS_CHOICES,
        default='WAITING',
        db_index=True,
        verbose_name='وضعیت',
    )
    description = models.TextField(blank=True, null=True, verbose_name='توضیحات')
    # Comma-separated names picked from the business-category's service
    # catalog (business.models.ServiceCatalogItem) — additive to
    # `description`, not a replacement: description stays free text, this is
    # the structured "what did they actually get" chips the owner tapped.
    selected_services = models.CharField(max_length=500, blank=True, default='', verbose_name='خدمات انتخاب‌شده')

    # ── Slot-lock fields (Red Line #2) ────────────────────────────────────
    locked_at = models.DateTimeField(
        null=True,
        blank=True,
        help_text='زمانی که مشتری این ساعت را قفل کرد',
        verbose_name='زمان قفل',
    )
    expires_at = models.DateTimeField(
        null=True,
        blank=True,
        db_index=True,
        help_text='پایان قفل (۱۵ دقیقه پس از قفل)؛ پس از آن ساعت آزاد می‌شود.',
        verbose_name='انقضا',
    )

    # ── Payment tracking fields ───────────────────────────────────────────
    tracking_code = models.CharField(
        max_length=20,
        blank=True,
        null=True,
        unique=True,
        help_text='کد خودکار که به‌عنوان شماره‌ی پیگیری نوبت به مشتری نشان داده می‌شود',
        verbose_name='کد پیگیری',
    )
    payment_reference = models.CharField(
        max_length=100,
        blank=True,
        null=True,
        help_text='شماره پیگیری که مشتری برای پرداخت کارت‌به‌کارت ثبت کرده',
        verbose_name='شماره پیگیری پرداخت',
    )
    payment_receipt = models.ImageField(
        upload_to='payment_receipts/',
        blank=True,
        null=True,
        help_text='تصویر رسید بارگذاری‌شده برای پرداخت کارت‌به‌کارت',
        verbose_name='رسید پرداخت',
    )

    deposit_verified_at = models.DateTimeField(
        null=True,
        blank=True,
        help_text='زمانی که زیبال پرداخت بیعانهٔ آنلاین را تأیید کرد. اگر نوبت بعد از آن '
                  'قفل‌شده بماند (ساعت از دست رفته)، پول گرفته شده و باید برگردانده شود.',
        verbose_name='زمان تأیید پرداخت بیعانه',
    )

    DEPOSIT_METHOD_CHOICES = [
        ('NONE',    'بدون بیعانه'),
        ('CARD',    'کارت به کارت'),
        ('GATEWAY', 'درگاه پرداخت آنلاین'),
    ]
    deposit_payment_method = models.CharField(
        max_length=10,
        choices=DEPOSIT_METHOD_CHOICES,
        default='NONE',
        help_text="بیعانه (اگر گرفته شده) چطور پرداخت شده؛ تا مالک هنگام دریافت "
                  "باقی مبلغ بداند بیعانه قبلاً و از چه راهی پرداخت شده.",
                  verbose_name='روش پرداخت بیعانه',
    )

    quota_source = models.CharField(
        max_length=10,
        blank=True,
        default='',
        help_text="هزینه‌ی این نوبت از کدام اعتبار کم شده (ماهانه یا کیف پول) تا "
                  "هنگام لغو همان برگردد. خالی = چیزی کم نشده.",
                  verbose_name='منبع سهمیه',
    )

    reminder_sent_at = models.DateTimeField(
        null=True,
        blank=True,
        db_index=True,
        help_text='زمان ارسال پیامک یادآوری پیش از نوبت (خالی = هنوز ارسال نشده)',
        verbose_name='زمان ارسال یادآوری',
    )

    reminder_push_sent_at = models.DateTimeField(
        null=True,
        blank=True,
        db_index=True,
        help_text=(
            "When the owner's push reminder was sent (null = not yet). Separate "
            "from reminder_sent_at because the two travel on different channels "
            "to different people: the client's SMS only goes out for PANEL "
            "businesses and costs quota, while the owner's push is free and goes "
            "out for every business with notifications on. One shared stamp "
            "would make a sent SMS suppress the push, and vice versa."
        ),
        verbose_name='زمان اعلان یادآوری',
    )

    reminder_visitor_push_sent_at = models.DateTimeField(
        null=True,
        blank=True,
        db_index=True,
        help_text=(
            "When the client's push reminder was sent (null = not yet). A "
            "third, independent stamp alongside reminder_sent_at (client SMS) "
            "and reminder_push_sent_at (owner's push) — same reasoning: one "
            "shared stamp would make one channel silently suppress another."
        ),
        verbose_name='زمان اعلان یادآوری مشتری',
    )

    # Where the booking came from. Rows that predate the field are UNKNOWN;
    # every creation path sets it explicitly.
    SOURCE_WEB = 'WEB'
    SOURCE_APP = 'APP'
    SOURCE_OWNER = 'OWNER'
    SOURCE_UNKNOWN = 'UNKNOWN'
    SOURCE_CHOICES = [
        (SOURCE_WEB, 'سایت'),
        (SOURCE_APP, 'اپ نوبت‌یار'),
        (SOURCE_OWNER, 'ثبت توسط مالک'),
        (SOURCE_UNKNOWN, 'نامشخص'),
    ]
    source = models.CharField(
        max_length=8, choices=SOURCE_CHOICES, default=SOURCE_UNKNOWN,
        verbose_name='منبع نوبت',
    )

    # How far the visitor was when booking from the app, and a rough drive
    # time as a range: an estimate from their last saved position, never
    # required (null when the app had no position or an older client booked).
    travel_distance_m = models.PositiveIntegerField(
        null=True, blank=True, verbose_name='فاصلهٔ مستقیم مراجع (متر)',
    )
    drive_minutes_min = models.PositiveSmallIntegerField(
        null=True, blank=True, verbose_name='زمان تقریبی رانندگی، از (دقیقه)',
    )
    drive_minutes_max = models.PositiveSmallIntegerField(
        null=True, blank=True, verbose_name='زمان تقریبی رانندگی، تا (دقیقه)',
    )

    # The supplementary insurance the visitor said they will use, picked from
    # the business's own list at booking (medical guilds). Null when none was
    # picked or an older client booked.
    insurance = models.ForeignKey(
        'business.Insurance', null=True, blank=True, on_delete=models.SET_NULL,
        related_name='+', verbose_name='بیمه تکمیلی مراجع',
    )

    # Where a client booking came from; compared with the owner's own IPs to
    # spot owners booking themselves for the new-client reward.
    booking_ip = models.GenericIPAddressField(
        null=True, blank=True, editable=False, verbose_name='IP ثبت نوبت',
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')


    class Meta:
        verbose_name = 'نوبت'
        verbose_name_plural = 'نوبت‌ها'
        ordering = ['-appointment_date']
        indexes = [
            models.Index(fields=['business', 'appointment_date']),
            models.Index(fields=['visitor', 'appointment_date']),
            # Serves the hot status-filtered range scans (capacity checks,
            # slot occupancy queries, appointment listing by status).
            # Explicit name must match migration 0005 exactly — Django can't
            # infer a name for an existing index, so an implicit name here
            # constantly looks like a pending rename to makemigrations.
            models.Index(fields=['business', 'status', 'appointment_date'], name='appt_biz_status_date_idx'),
            models.Index(fields=['payment_reference'], name='appt_payment_ref_idx'),
        ]

    # In appointment/models.py - Appointment.clean()
    def clean(self):
        pass
        # Keep only business logic validations:
        # if self.appointment_date and self.appointment_date < timezone.now():
        #     raise ValidationError({'appointment_date': 'تاریخ قرار ملاقات نمی‌تواند در گذشته باشد'})
        #
        # if self.service_duration and self.service_duration <= 0:
        #     raise ValidationError({'service_duration': 'مدت زمان سرویس باید مثبت باشد'})

    # NOTE: save() intentionally does NOT call full_clean(). Model validation
    # runs at the serializer/view layer (create & update paths call full_clean()
    # explicitly where needed). Calling full_clean() on every save added a
    # per-write uniqueness query for `tracking_code`, which hurts write
    # throughput on the hot booking path.

    def __str__(self):
        return f"{self.visitor.full_name} - {self.business.title} ({self.appointment_date})"
