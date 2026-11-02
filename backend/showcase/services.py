"""Owner operations and moderation decisions for portfolio items/certificates."""
import uuid

from django.db import transaction
from django.utils import timezone

from .images import CERTIFICATE_SPEC, PORTFOLIO_SPEC, process_image
from .models import Certificate, ModerationStatus, PortfolioItem

KIND_PORTFOLIO = 'portfolio'
KIND_CERTIFICATE = 'certificate'
MODELS = {KIND_PORTFOLIO: PortfolioItem, KIND_CERTIFICATE: Certificate}
SPECS = {KIND_PORTFOLIO: PORTFOLIO_SPEC, KIND_CERTIFICATE: CERTIFICATE_SPEC}


class LimitReached(Exception):
    pass


def _attach_image(item, uploaded, kind):
    """Validate/recompress and set image + thumbnail (raises ImageRejected)."""
    main, thumb = process_image(uploaded, SPECS[kind], uuid.uuid4().hex)
    item.image.save(main.name, main, save=False)
    item.thumbnail.save(thumb.name, thumb, save=False)


def _queue_for_review(item, kind):
    item.status = ModerationStatus.PENDING
    item.reject_reason = ''
    item.reviewed_by = None
    item.reviewed_at = None
    item._queued = kind


def _announce(item, kind):
    from bale.notify import notify_showcase_queued
    notify_showcase_queued(kind, item.pk)


def create_item(business, kind, uploaded, **fields):
    model = MODELS[kind]
    with transaction.atomic():
        # Lock the business row so two concurrent uploads cannot both slip past
        # the per-business cap.
        type(business).objects.select_for_update().filter(pk=business.pk).first()
        if model.objects.filter(business=business).count() >= model.MAX_PER_BUSINESS:
            raise LimitReached
        last = model.objects.filter(business=business).order_by('-order').values_list('order', flat=True).first()
        order = fields.pop('order', None)
        item = model(business=business, order=(last + 1 if last is not None else 0) if order is None else order,
                     **fields)
        _attach_image(item, uploaded, kind)
        item.save()
        _announce(item, kind)
    return item


def update_item(item, kind, uploaded=None, **fields):
    """Apply owner edits. Changing anything the public sees re-queues it."""
    changed = False
    for name, value in fields.items():
        if name in item.MODERATED_FIELDS and getattr(item, name) != value:
            changed = True
        setattr(item, name, value)
    if uploaded is not None:
        _attach_image(item, uploaded, kind)
        changed = True
    with transaction.atomic():
        if changed:
            _queue_for_review(item, kind)
        item.save()
        if changed:
            _announce(item, kind)
    return item


def reorder(business, kind, ids):
    model = MODELS[kind]
    owned = set(model.objects.filter(business=business).values_list('id', flat=True))
    if len(set(ids)) != len(ids) or set(ids) != owned:
        raise ValueError('ids must list every item of this business exactly once')
    with transaction.atomic():
        for position, pk in enumerate(ids):
            model.objects.filter(pk=pk, business=business).update(order=position)
        # update() sends no signals: invalidate the public payload explicitly.
        from business import cache as public_cache
        public_cache.bump_business(business.pk)


def decide(item, kind, to_status, actor=None, reason=''):
    """Approve/reject. Returns False when the item is already in ``to_status``
    (replay guard for double-tapped Bale buttons)."""
    if to_status == ModerationStatus.REJECTED and not reason.strip():
        raise ValueError('rejection needs a reason')
    with transaction.atomic():
        locked = type(item).objects.select_for_update().get(pk=item.pk)
        if locked.status == to_status:
            return False
        locked.status = to_status
        locked.reject_reason = reason.strip() if to_status == ModerationStatus.REJECTED else ''
        locked.reviewed_by = actor
        locked.reviewed_at = timezone.now()
        locked.save()
    item.refresh_from_db()
    return True
