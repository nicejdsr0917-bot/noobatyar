"""Customers following business pages."""

from django.db import IntegrityError, transaction
from django.db.models import F

from . import cache as public_cache
from .models import Business, BusinessFollow


def set_following(visitor, business_id, follow):
    """Follow (``follow=True``) or unfollow a publicly visible business.
    Idempotent. Returns ``(is_following, followers_count)``; raises
    ``Business.DoesNotExist`` for a business the public cannot see."""
    business = Business.objects.only('id').get(Business.public_filter(), id=business_id)
    with transaction.atomic():
        if follow:
            try:
                with transaction.atomic():
                    BusinessFollow.objects.create(business=business, visitor=visitor)
                changed = 1
            except IntegrityError:
                changed = 0  # already following
        else:
            deleted, _ = BusinessFollow.objects.filter(business=business, visitor=visitor).delete()
            changed = -1 if deleted else 0
        if changed:
            Business.objects.filter(id=business.id).update(followers_count=F('followers_count') + changed)
    if changed:
        # The public detail page shows the count; drop its cached copy.
        public_cache.bump_business(business.id)
    count = Business.objects.filter(id=business.id).values_list('followers_count', flat=True).first() or 0
    return follow, max(count, 0)


def is_following(visitor, business_id):
    return BusinessFollow.objects.filter(business_id=business_id, visitor=visitor).exists()


def followed_businesses(visitor):
    """Businesses ``visitor`` follows, most recently followed first, that the
    public can still see."""
    return (
        Business.objects.filter(Business.public_filter(), follows__visitor=visitor)
        .order_by('-follows__created_at')
    )
