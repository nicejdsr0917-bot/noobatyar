"""
business/claims.py

Ownership transfer for imported directory listings (``Business.is_claimed=False``).

The listing already exists — with its public link, its accumulated
"enable online booking" requests and its place in the client app's search — so
claiming it hands that row to the owner instead of letting them create a
duplicate next to it. Both claim paths (OTP in claim_views, admin approval in
admin.py) end in :func:`apply_claim`, so the rules live in one place.
"""

import logging

from django.db import transaction
from django.utils import timezone

from accounting import entitlements

from .models import Business, BusinessClaimRequest, CenterOutreach

logger = logging.getLogger(__name__)


class ClaimError(Exception):
    """A claim that cannot go through; ``str(exc)`` is safe to show the user."""


def check_claim_quota(user):
    """Return an error message if taking one more business exceeds the plan.

    Mirrors BusinessView._check_business_quota: a claimed listing occupies a
    business slot exactly like a newly created one.
    """
    quota = entitlements.get_quota(user, entitlements.QUOTA_MAX_BUSINESSES)
    if entitlements.is_unlimited(quota):
        return None
    current = Business.objects.filter(user=user, is_locked=False).count()
    if current >= quota:
        return (
            f"با پلن فعلی حداکثر می‌توانید {quota} کسب‌وکار فعال داشته باشید. "
            f"برای ثبت این مرکز، پلن خود را ارتقا دهید."
        )
    return None


def apply_claim(claim_request, resolution_note=''):
    """Transfer the claimed business to the requesting user.

    Locks the business row so two concurrent approvals can't both "win"; the
    loser gets a ClaimError. Every other open request for the same business is
    rejected in the same transaction.
    """
    with transaction.atomic():
        business = Business.objects.select_for_update().get(pk=claim_request.business_id)
        if business.is_claimed:
            raise ClaimError("این مرکز قبلاً توسط مالک دیگری ثبت شده است")

        quota_error = check_claim_quota(claim_request.user)
        if quota_error:
            raise ClaimError(quota_error)

        now = timezone.now()
        business.user = claim_request.user
        business.is_claimed = True
        business.origin = Business.ORIGIN_CONVERTED
        business.save(update_fields=['user', 'is_claimed', 'origin', 'updated_at'])
        # The call-tracking row (if any) closes with the conversion.
        CenterOutreach.objects.filter(business=business).update(status=CenterOutreach.STATUS_CONVERTED)

        claim_request.status = BusinessClaimRequest.STATUS_APPROVED
        claim_request.resolved_at = now
        claim_request.resolution_note = resolution_note
        claim_request.save(update_fields=['status', 'resolved_at', 'resolution_note'])

        BusinessClaimRequest.objects.filter(
            business=business, status=BusinessClaimRequest.STATUS_PENDING,
        ).exclude(pk=claim_request.pk).update(
            status=BusinessClaimRequest.STATUS_REJECTED,
            resolved_at=now,
            resolution_note='مرکز توسط مالک دیگری ثبت شد',
        )

    logger.info("Business %s claimed by user %s", business.id, claim_request.user_id)
    return business


def mask_phone(phone):
    """09121234567 -> 0912***4567, for telling the claimant where the code went."""
    if not phone or len(phone) < 8:
        return ''
    return f"{phone[:4]}***{phone[-4:]}"
