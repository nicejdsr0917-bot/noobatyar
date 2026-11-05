"""Creating, listing and announcing business stories."""
from django.db import transaction
from django.utils import timezone
from PIL import Image

from core.outbox.service import safe_enqueue
from showcase.images import encode_jpeg, flatten_rgb, open_upload

from .models import Story

# Any aspect ratio (a gallery photo as is), scaled down to fit a phone screen.
MAX_WIDTH, MAX_HEIGHT = 1080, 1920


class StoryError(ValueError):
    """Shown to the owner as is."""


def _process_image(uploaded):
    img = flatten_rgb(open_upload(uploaded))
    img.thumbnail((MAX_WIDTH, MAX_HEIGHT), Image.LANCZOS)
    return encode_jpeg(img, 'story.jpg')


def _validate_button(button, discount_type, discount_value):
    if not button:
        return Story.BUTTON_NONE, '', None
    if button != Story.BUTTON_DISCOUNT:
        raise StoryError('دکمه نامعتبر است.')
    if discount_type not in (Story.DISCOUNT_PERCENT, Story.DISCOUNT_AMOUNT):
        raise StoryError('نوع تخفیف را انتخاب کنید.')
    try:
        value = int(discount_value)
    except (TypeError, ValueError):
        raise StoryError('مقدار تخفیف را وارد کنید.')
    if discount_type == Story.DISCOUNT_PERCENT and not 1 <= value <= 100:
        raise StoryError('درصد تخفیف باید بین ۱ تا ۱۰۰ باشد.')
    if discount_type == Story.DISCOUNT_AMOUNT and not 1000 <= value <= 100_000_000:
        raise StoryError('مبلغ تخفیف باید دست‌کم ۱۰۰۰ تومان باشد.')
    return button, discount_type, value


def purge_expired(business):
    for story in Story.objects.filter(business=business, expires_at__lte=timezone.now()):
        story.image.delete(save=False)
        story.delete()


def create_story(business, uploaded, caption='', button='', discount_type='', discount_value=None):
    caption = (caption or '').strip()
    if len(caption) > Story.CAPTION_MAX:
        raise StoryError(f'متن استوری حداکثر {Story.CAPTION_MAX} نویسه است.')
    button, discount_type, discount_value = _validate_button(button, discount_type, discount_value)
    purge_expired(business)
    if Story.active().filter(business=business).count() >= Story.MAX_ACTIVE_PER_BUSINESS:
        raise StoryError(f'حداکثر {Story.MAX_ACTIVE_PER_BUSINESS} استوری فعال می‌توانید داشته باشید.')
    image = _process_image(uploaded)
    with transaction.atomic():
        story = Story.objects.create(
            business=business, image=image, caption=caption, button=button,
            discount_type=discount_type, discount_value=discount_value,
        )
        # Followers hear about it once a day at most, however many stories.
        safe_enqueue('stories.notify_followers', {'story_id': story.id},
                     dedupe_key=f'story_push:{business.id}:{timezone.localdate():%Y%m%d}')
    return story


def delete_story(story):
    story.image.delete(save=False)
    story.delete()


def active_business_ids():
    """Ids of public businesses that have a story up right now."""
    from business.models import Business
    return list(
        Story.active().filter(business__in=Business.objects.filter(Business.public_filter()))
        # order_by(): the model's default ordering would add created_at to
        # the SELECT and defeat distinct().
        .order_by().values_list('business_id', flat=True).distinct()
    )


def notify_followers(story_id):
    """Outbox handler body: push every follower of the story's business."""
    from api.services.push import send_to_visitor
    from business.models import BusinessFollow
    story = Story.active().select_related('business').filter(pk=story_id).first()
    if story is None:
        return
    title = story.business.title
    body = story.caption or 'استوری تازه گذاشت. ببینید!'
    data = {'type': 'NEW_STORY', 'business_id': str(story.business_id)}
    visitor_ids = BusinessFollow.objects.filter(business_id=story.business_id).values_list('visitor_id', flat=True)
    for visitor_id in visitor_ids.iterator():
        send_to_visitor(visitor_id, title, body, data)
