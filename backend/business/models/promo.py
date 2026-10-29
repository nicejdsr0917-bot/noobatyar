from django.db import models
from django.contrib.auth import get_user_model

from .core import Business

User = get_user_model()


class PromoCampaign(models.Model):
    """One promotional SMS blast an owner sent to their own customers.

    The owner's counterpart to the platform's own campaigns in core/: the
    message goes out of the same advertising line, is billed to the owner's
    SMS quota (monthly allowance, then wallet) like every other message they
    choose to send, and each attempt is also written to ``visitor.SmsLog`` so
    the SMS report reconciles against what was charged. See
    business/promo_sms.py for the send flow.
    """

    AUDIENCE_CHOICES = [
        ('all', 'همه‌ی مشتری‌ها'),
        ('recent', 'مراجعه در ۳۰ روز اخیر'),
        ('inactive', 'بیش از ۶۰ روز مراجعه نکرده‌اند'),
    ]
    STATUS_CHOICES = [
        ('SENDING', 'در حال ارسال'),
        ('DONE', 'ارسال شد'),
    ]

    business = models.ForeignKey(Business, on_delete=models.CASCADE, related_name='promo_campaigns')
    created_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name='+')
    audience = models.CharField(max_length=20, choices=AUDIENCE_CHOICES)
    # What the owner typed, before {name} substitution and before the footer
    # and لغو11 are appended.
    message = models.TextField()
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default='SENDING')
    recipient_count = models.PositiveIntegerField(default=0)
    # Billed SMS parts reserved at creation — what the owner was shown before
    # confirming, kept so the history can say what they agreed to.
    total_parts = models.PositiveIntegerField(default=0)
    sent_count = models.PositiveIntegerField(default=0)
    failed_count = models.PositiveIntegerField(default=0)
    skipped_count = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    # Touched after every recipient by the live sender, so the resume command
    # can tell a sender that died from one that is simply still working
    # through a long list.
    heartbeat_at = models.DateTimeField(null=True, blank=True)
    finished_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'business_promo_campaign'
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.business_id} · {self.get_audience_display()} · {self.sent_count}/{self.recipient_count}"


class PromoCampaignRecipient(models.Model):
    """One person in a PromoCampaign, with the exact text they get.

    Rows are created up front (status PENDING) and then claimed one at a time
    by the sender, so a send interrupted by a restart can be resumed by
    ``python manage.py send_promo_campaigns`` without anyone getting it twice.
    """

    STATUS_CHOICES = [
        ('PENDING', 'در صف'),
        ('SENDING', 'در حال ارسال'),
        ('SENT', 'ارسال شد'),
        ('FAILED', 'ناموفق'),
        ('SKIPPED_QUOTA', 'اعتبار کافی نبود'),
    ]

    campaign = models.ForeignKey(PromoCampaign, on_delete=models.CASCADE, related_name='recipients')
    visitor = models.ForeignKey('visitor.Visitor', on_delete=models.SET_NULL, null=True, blank=True, related_name='+')
    name = models.CharField(max_length=255, blank=True, default='')
    phone = models.CharField(max_length=20)
    # Final text as sent (signed, with footer and لغو11), and its billed parts.
    text = models.TextField()
    parts = models.PositiveSmallIntegerField(default=1)
    status = models.CharField(max_length=15, choices=STATUS_CHOICES, default='PENDING', db_index=True)
    error_detail = models.TextField(blank=True, default='')
    sent_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'business_promo_campaign_recipient'
        ordering = ['id']

    def __str__(self):
        return f"{self.phone} · {self.status}"
