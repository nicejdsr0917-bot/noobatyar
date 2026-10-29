from django.db import models
from django.contrib.auth import get_user_model

from .core import Business

User = get_user_model()


class OnlineBookingRequest(models.Model):
    """One client's "please enable online booking here" vote on an unclaimed
    business.

    Stored per (business, fingerprint) rather than as a bare counter so the
    number shown publicly — and pitched to the owner as demand — can't be
    pumped by replaying the endpoint: a repeat from the same device is a no-op.
    `fingerprint` is a hash (see client_views.request_fingerprint), never the
    raw device id or IP.
    """

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='online_booking_requests',
    )
    fingerprint = models.CharField(max_length=64)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'online_booking_request'
        constraints = [
            models.UniqueConstraint(
                fields=['business', 'fingerprint'], name='uniq_booking_request_per_device',
            ),
        ]

    def __str__(self):
        return f"درخواست رزرو آنلاین برای {self.business_id}"


class BusinessClaimRequest(models.Model):
    """An owner asking to take over an imported (unclaimed) directory listing.

    Two ways through: if the listing's phone is a mobile number the owner proves
    control of it by OTP and the claim is applied immediately (METHOD_OTP);
    otherwise — landline, or no number at all — the request waits for an admin
    (METHOD_MANUAL), who checks the supplied documents/contact and approves it
    from the admin. Either way the transfer itself is business.claims.apply_claim.
    """

    METHOD_OTP = 'OTP'
    METHOD_MANUAL = 'MANUAL'
    METHOD_CHOICES = [
        (METHOD_OTP, 'تأیید پیامکی'),
        (METHOD_MANUAL, 'بررسی دستی'),
    ]

    STATUS_PENDING = 'PENDING'
    STATUS_APPROVED = 'APPROVED'
    STATUS_REJECTED = 'REJECTED'
    STATUS_CHOICES = [
        (STATUS_PENDING, 'در انتظار'),
        (STATUS_APPROVED, 'تأیید شد'),
        (STATUS_REJECTED, 'رد شد'),
    ]

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='claim_requests',
    )
    user = models.ForeignKey(
        User, on_delete=models.CASCADE, related_name='business_claims',
    )
    method = models.CharField(max_length=10, choices=METHOD_CHOICES)
    status = models.CharField(
        max_length=10, choices=STATUS_CHOICES, default=STATUS_PENDING, db_index=True,
    )
    note = models.TextField(blank=True, default='', help_text="توضیح مالک (مثلاً شماره نظام پزشکی)")
    resolution_note = models.TextField(blank=True, default='')
    created_at = models.DateTimeField(auto_now_add=True)
    resolved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = 'business_claim_request'
        ordering = ['-created_at']

    def __str__(self):
        return f"ادعای مالکیت {self.business_id} توسط {self.user_id}"
