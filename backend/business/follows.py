"""Customers following business pages."""

from django.db import IntegrityError, transaction
from django.db.models import F

from core.outbox.service import safe_enqueue

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
                # One push per customer and business, ever: unfollow and
                # follow again does not tell the owner twice.
                safe_enqueue('business.new_follower_push',
                             {'business_id': business.id, 'visitor_id': visitor.id},
                             dedupe_key=f'follow_push:{business.id}:{visitor.id}')
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


def recent_followers(business):
    """Who follows ``business``, newest first, for its owner."""
    return (BusinessFollow.objects.filter(business=business)
            .select_related('visitor').order_by('-created_at'))


def send_new_follower_push(business_id, visitor_id):
    """Outbox handler body: tell the owner who just followed. First name only."""
    from api.services.push import send_to_user
    from reviews.serializers import first_name
    follow = (BusinessFollow.objects.select_related('business', 'visitor')
              .filter(business_id=business_id, visitor_id=visitor_id).first())
    if follow is None:
        return  # unfollowed before the push went out
    name = first_name(follow.visitor.full_name) or 'یک مشتری'
    send_to_user(
        follow.business.user_id,
        'دنبال‌کننده جدید',
        f'{name} «{follow.business.title}» را دنبال کرد.',
        {'type': 'NEW_FOLLOWER', 'business_id': str(business_id)},
    )
