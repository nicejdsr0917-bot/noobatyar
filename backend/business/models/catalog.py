from django.db import models

from .core import Business


class ServiceCatalogItem(models.Model):
    """
    A pickable "service received" name, scoped to a business CATEGORY rather
    than to a single Business.

    The whole point of this table is cross-business sharing within a
    category: when a hairdresser adds "رنگ ابرو" while booking a client, that
    name becomes a selectable chip for every other BEAUTY_SALON business too
    — not just the one that typed it. Owners were previously typing the same
    handful of service names into a free-text field over and over, with every
    typo and phrasing variant treated as a different service.

    Deliberately not gated/moderated: get_or_create on (category, name) is
    the whole write path (see ServiceCatalogView.post). A bad or duplicate-ish
    entry costs nothing to leave in place, and blocking on review would have
    defeated the "just add it inline while booking" UX this exists for.
    """
    category = models.CharField(
        max_length=50,
        choices=Business.CATEGORY_CHOICES,
        db_index=True,
        help_text='مشترک بین همه‌ی کسب‌وکارهای این دسته'
    )
    name = models.CharField(max_length=100)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'business_service_catalog_item'
        ordering = ['name']
        constraints = [
            models.UniqueConstraint(
                fields=['category', 'name'],
                name='uniq_service_catalog_category_name'
            )
        ]
        indexes = [
            models.Index(fields=['category', 'name']),
        ]

    def __str__(self):
        return f"{self.name} ({self.category})"
