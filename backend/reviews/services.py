"""Review writes. Synchronous; views hop in with sync_to_async."""
import logging
from datetime import timedelta

from django.db import IntegrityError, transaction
from django.db.models import Count
from django.utils import timezone

from business import cache as public_cache
from business.models import Business
from core.outbox.service import safe_enqueue

from .models import Review

logger = logging.getLogger(__name__)

EDIT_WINDOW = timedelta(days=7)


class ReviewError(Exception):
    def __init__(self, message, code=400):
        super().__init__(message)
        self.message = message
        self.code = code


def validate_input(rating, text):
    """-> (rating:int, text:str); raises ReviewError(400)."""
    if isinstance(rating, bool):
        raise ReviewError('امتیاز باید بین ۱ تا ۵ باشد')
    try:
        rating = int(rating)
    except (TypeError, ValueError):
        raise ReviewError('امتیاز باید بین ۱ تا ۵ باشد')
    if not 1 <= rating <= 5:
        raise ReviewError('امتیاز باید بین ۱ تا ۵ باشد')
    text = str(text or '').strip()
    if len(text) > Review.MAX_TEXT:
        raise ReviewError(f'متن نظر نباید بیش از {Review.MAX_TEXT} حرف باشد')
    return rating, text


def recompute_summary(business_id):
    """Refresh the business' denormalised rating fields from visible reviews and
    drop its public cache entries."""
    rows = (Review.objects.filter(business_id=business_id, is_hidden=False)
            .values('rating').annotate(n=Count('id')))
    histogram = {str(i): 0 for i in range(1, 6)}
    total = weighted = 0
    for r in rows:
        histogram[str(r['rating'])] = r['n']
        total += r['n']
        weighted += r['rating'] * r['n']
    # update(): no Business post_save (and no logo/moderation side effects).
    Business.objects.filter(pk=business_id).update(
        rating_avg=round(weighted / total, 2) if total else 0,
        rating_count=total,
        rating_histogram=histogram,
    )
    public_cache.bump_business(business_id)
    transaction.on_commit(lambda: public_cache.bump_business(business_id))


def _latest_completed_appointment(business_id, visitor_id):
    from appointment.models import Appointment
    return (Appointment.objects
            .filter(business_id=business_id, visitor_id=visitor_id, status='COMPLETED')
            .order_by('-appointment_date').first())


def create_review(*, business, visitor, rating, text):
    rating, text = validate_input(rating, text)
    try:
        with transaction.atomic():
            review = Review.objects.create(
                business=business, visitor=visitor, rating=rating, text=text,
                appointment=_latest_completed_appointment(business.id, visitor.id),
            )
            recompute_summary(business.id)
            safe_enqueue('reviews.new_review_push', {'review_id': review.id},
                         dedupe_key=f'review_push:{review.id}')
    except IntegrityError:
        raise ReviewError('شما قبلاً برای این کسب‌وکار نظر ثبت کرده‌اید', 409)
    return review


def update_review(*, business_id, visitor, rating, text):
    rating, text = validate_input(rating, text)
    with transaction.atomic():
        review = (Review.objects.select_for_update()
                  .filter(business_id=business_id, visitor=visitor).first())
        if review is None:
            raise ReviewError('نظری برای ویرایش یافت نشد', 404)
        if timezone.now() - review.created_at > EDIT_WINDOW:
            raise ReviewError('مهلت ویرایش نظر (۷ روز) تمام شده است', 403)
        review.rating, review.text = rating, text
        review.save(update_fields=['rating', 'text', 'updated_at'])
        recompute_summary(business_id)
    return review


def delete_review(*, business_id, visitor):
    with transaction.atomic():
        deleted, _ = Review.objects.filter(business_id=business_id, visitor=visitor).delete()
        if not deleted:
            raise ReviewError('نظری برای حذف یافت نشد', 404)
        recompute_summary(business_id)


def send_new_review_push(review_id):
    """Outbox handler body: tell the business owner."""
    from api.services.push import send_to_user
    review = Review.objects.select_related('business', 'visitor').filter(pk=review_id).first()
    if review is None or review.is_hidden:
        return
    first = (review.visitor.full_name or '').split(' ')[0] or 'یک مشتری'
    send_to_user(
        review.business.user_id,
        'نظر جدید',
        f'{first} به «{review.business.title}» امتیاز {review.rating} از ۵ داد.',
        {'type': 'NEW_REVIEW', 'business_id': str(review.business_id), 'review_id': str(review.id)},
    )
