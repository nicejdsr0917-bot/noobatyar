"""Cache invalidation for the public directory (see business/cache.py).

Each bump runs immediately *and* again on commit: the immediate one keeps
non-transactional callers (and tests) consistent, the on-commit one closes the
window where a reader re-caches the old row between the bump and the commit.
No handler touches the database.
"""
from django.db import transaction
from django.db.models.signals import m2m_changed, post_delete, post_save

from . import cache as public_cache
from .models import Business, Insurance


def _bump(fn, *args):
    fn(*args)
    transaction.on_commit(lambda: fn(*args))


def _business_changed(sender, instance, **kwargs):
    _bump(public_cache.bump_business, instance.pk)


def _everything_changed(sender, created=False, **kwargs):
    # ContactVisibilityPolicy.load() lazily creates the singleton with its
    # defaults on the first read: that is not a policy change, and bumping here
    # would invalidate the very entry being computed.
    if created and sender.__name__ == 'ContactVisibilityPolicy':
        return
    _bump(public_cache.bump_all)


def _insurances_changed(sender, instance, action, reverse, pk_set, **kwargs):
    if action not in ('post_add', 'post_remove', 'post_clear'):
        return
    if reverse:  # insurance.businesses.add(...)
        _bump(public_cache.bump_all)
    else:
        _bump(public_cache.bump_business, instance.pk)


def connect():
    from accounting.models import AddOnPurchase, ContactVisibilityPolicy, Subscription

    post_save.connect(_business_changed, sender=Business, dispatch_uid='pubdir_biz_save')
    post_delete.connect(_business_changed, sender=Business, dispatch_uid='pubdir_biz_delete')
    m2m_changed.connect(_insurances_changed, sender=Business.insurances.through,
                        dispatch_uid='pubdir_biz_insurances')
    # The payload also depends on the owner's plan (is_expired, booking block)
    # and on the admin contact-visibility policy.
    for model in (Insurance, Subscription, AddOnPurchase, ContactVisibilityPolicy):
        post_save.connect(_everything_changed, sender=model, dispatch_uid=f'pubdir_all_save_{model.__name__}')
        post_delete.connect(_everything_changed, sender=model, dispatch_uid=f'pubdir_all_del_{model.__name__}')
