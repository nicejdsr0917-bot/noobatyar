"""Showcase changes alter the public showcase payload: bump the business stamp."""
from django.db import transaction
from django.db.models.signals import post_delete, post_save

from business import cache as public_cache

from .models import Certificate, PortfolioItem


def _bump(instance, **kwargs):
    bid = instance.business_id
    public_cache.bump_business(bid)
    transaction.on_commit(lambda: public_cache.bump_business(bid))


for _model in (PortfolioItem, Certificate):
    post_save.connect(_bump, sender=_model, dispatch_uid=f'showcase_save_{_model.__name__}')
    post_delete.connect(_bump, sender=_model, dispatch_uid=f'showcase_del_{_model.__name__}')
