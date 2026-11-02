"""Cache invalidation for accounting.entitlements.get_entitlements().

Per-user entries are dropped when that user's Subscription / AddOnPurchase
changes; Plan / AddOnPack changes bump the global version (every subscriber is
affected). Each invalidation runs twice on purpose:

  * immediately — the writer's own next read (and every test running inside a
    TestCase transaction, where on_commit hooks never fire) must not see stale
    data;
  * again on commit — a concurrent reader may have re-cached the pre-commit
    state between the first delete and the commit; the second delete closes
    that window, so a user who just paid sees the new plan at once.
"""
from django.conf import settings
from django.db import transaction
from django.db.models.signals import post_delete, post_save
from django.dispatch import receiver

from . import entitlements
from .models import AddOnPack, AddOnPurchase, Plan, Subscription


def _drop_user(user_id):
    entitlements.invalidate_entitlements(user_id)
    transaction.on_commit(lambda: entitlements.invalidate_entitlements(user_id))


def _drop_all():
    entitlements.invalidate_all_entitlements()
    transaction.on_commit(entitlements.invalidate_all_entitlements)


@receiver([post_save, post_delete], sender=Subscription, dispatch_uid="ent_sub")
@receiver([post_save, post_delete], sender=AddOnPurchase, dispatch_uid="ent_addon")
def _user_scoped_changed(sender, instance, **kwargs):
    _drop_user(instance.user_id)


@receiver([post_save, post_delete], sender=Plan, dispatch_uid="ent_plan")
@receiver([post_save, post_delete], sender=AddOnPack, dispatch_uid="ent_pack")
def _definition_changed(sender, instance, **kwargs):
    _drop_all()


@receiver(post_save, sender=settings.AUTH_USER_MODEL, dispatch_uid="ent_user_created")
def _user_created(sender, instance, created, **kwargs):
    # A brand-new user id can collide with a cached entry of a user that no
    # longer exists (deleted / rolled back); start from a clean slate.
    if created:
        entitlements.invalidate_entitlements(instance.pk)
