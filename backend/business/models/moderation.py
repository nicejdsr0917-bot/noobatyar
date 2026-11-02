from django.db import models
from django.contrib.auth import get_user_model

from .core import Business

User = get_user_model()


class BusinessModerationLog(models.Model):
    """Append-only record of every moderation decision.

    Separate from `django.contrib.admin.models.LogEntry` because that only
    covers changes made through the admin change form, and records them as an
    opaque message string. A rejection needs to be answerable months later —
    who decided, on what grounds, and what the business looked like at the time.
    Nothing here is ever updated or deleted.
    """

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='moderation_logs',
        verbose_name='کسب‌وکار',
    )
    from_status = models.CharField(max_length=20, blank=True, default='', verbose_name='از وضعیت')
    to_status = models.CharField(max_length=20, verbose_name='به وضعیت')
    note = models.TextField(blank=True, default='', verbose_name='یادداشت')
    # SET_NULL, not CASCADE: the decision has to outlive the staff account that
    # made it, otherwise offboarding a moderator erases the audit trail.
    actor = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='moderation_actions',
        verbose_name='انجام‌دهنده',
    )
    # Snapshot of MODERATED_FIELDS at decision time, so the log still explains
    # itself after the owner edits the business.
    snapshot = models.JSONField(default=dict, blank=True, verbose_name='نسخه‌ی ذخیره‌شده')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')

    class Meta:
        verbose_name = 'سابقه‌ی بررسی'
        verbose_name_plural = 'سوابق بررسی'
        db_table = 'business_moderation_log'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['business', '-created_at'], name='biz_mod_log_recent_idx'),
        ]

    def __str__(self):
        return f"{self.business_id}: {self.from_status or '—'} → {self.to_status}"


class BannedKeyword(models.Model):
    """A term that flags a business for closer review.

    Explicitly advisory: matching a keyword never rejects, hides, or blocks
    anything on its own. It only highlights the match in the moderation queue so
    a human looks harder. Automated rejection was ruled out deliberately —
    Persian morphology and legitimate business names produce far too many false
    positives to act on without a person in the loop.
    """

    SEVERITY_LOW = 'LOW'
    SEVERITY_HIGH = 'HIGH'
    SEVERITY_CHOICES = [
        (SEVERITY_LOW, 'کم — فقط نشانه‌گذاری'),
        (SEVERITY_HIGH, 'زیاد — بررسی فوری'),
    ]

    term = models.CharField(max_length=100, unique=True, db_index=True, verbose_name='عبارت')
    severity = models.CharField(max_length=10, choices=SEVERITY_CHOICES, default=SEVERITY_LOW, verbose_name='شدت')
    note = models.CharField(max_length=255, blank=True, default='', help_text="چرا این کلمه اضافه شده", verbose_name='یادداشت')
    is_active = models.BooleanField(default=True, verbose_name='فعال')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')

    class Meta:
        verbose_name = 'کلمه‌ی ممنوع'
        verbose_name_plural = 'کلمات ممنوع'
        db_table = 'banned_keyword'
        ordering = ['term']

    def __str__(self):
        return self.term


class ContentReport(models.Model):
    """A report of inappropriate content, filed against a business.

    Reporters are not required to have an account — most people who would
    notice a problem on a public booking page are visitors, not owners — so both
    `reporter_user` and `reporter_visitor` are optional and `reporter_phone`
    carries the contact for anonymous reports.
    """

    REASON_CHOICES = [
        ('INAPPROPRIATE', 'محتوای نامناسب'),
        ('MISLEADING', 'اطلاعات گمراه‌کننده'),
        ('IMPERSONATION', 'جعل هویت'),
        ('ILLEGAL', 'فعالیت غیرقانونی'),
        ('SPAM', 'اسپم'),
        ('OTHER', 'سایر'),
    ]

    STATUS_NEW = 'NEW'
    STATUS_REVIEWING = 'REVIEWING'
    STATUS_ACTIONED = 'ACTIONED'
    STATUS_DISMISSED = 'DISMISSED'
    STATUS_CHOICES = [
        (STATUS_NEW, 'جدید'),
        (STATUS_REVIEWING, 'در حال بررسی'),
        (STATUS_ACTIONED, 'اقدام شد'),
        (STATUS_DISMISSED, 'رد شد'),
    ]

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='content_reports',
        verbose_name='کسب‌وکار',
    )
    reason = models.CharField(max_length=20, choices=REASON_CHOICES, verbose_name='دلیل')
    detail = models.TextField(blank=True, default='', verbose_name='جزئیات')
    reporter_user = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='filed_reports',
        verbose_name='کاربر گزارش‌دهنده',
    )
    reporter_visitor = models.ForeignKey(
        'visitor.Visitor', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='filed_reports',
        verbose_name='مشتری گزارش‌دهنده',
    )
    reporter_phone = models.CharField(max_length=20, blank=True, default='', verbose_name='تلفن گزارش‌دهنده')
    # Set when the report targets one review rather than the whole business
    # (``business`` is still that review's business, so the queue stays one list).
    review = models.ForeignKey(
        'reviews.Review', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='reports', verbose_name='نظر گزارش‌شده',
    )
    status = models.CharField(
        max_length=20, choices=STATUS_CHOICES, default=STATUS_NEW, db_index=True,
        verbose_name='وضعیت',
    )
    resolution_note = models.TextField(blank=True, default='', verbose_name='یادداشت رسیدگی')
    resolved_by = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='resolved_reports',
        verbose_name='رسیدگی‌کننده',
    )
    resolved_at = models.DateTimeField(null=True, blank=True, verbose_name='زمان رسیدگی')
    # Set only when resolving this report was a side effect of a moderation
    # decision (suspending/rejecting the business it names) rather than some
    # other resolution — a warning email, a phone call, dismissal as
    # unfounded. SET_NULL, not CASCADE: the report should still show it *was*
    # linked to a decision even if that log row were ever removed, but nothing
    # here ever deletes a BusinessModerationLog in practice.
    resulting_moderation_log = models.ForeignKey(
        BusinessModerationLog, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='resolved_reports',
        verbose_name='سابقه‌ی بررسی مرتبط',
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')

    class Meta:
        verbose_name = 'گزارش تخلف'
        verbose_name_plural = 'گزارش‌های تخلف'
        db_table = 'content_report'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['status', '-created_at'], name='content_report_queue_idx'),
        ]

    def __str__(self):
        return f"گزارش {self.get_reason_display()} برای {self.business_id}"
