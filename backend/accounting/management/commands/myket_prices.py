"""
List the price each Myket SKU must have in the Myket developer panel.

Myket charges whatever the panel says, not what our server asks, so after
changing a plan or pack price here the matching SKU price has to be updated
there by hand. This prints the store price that leaves the item's own price
after VAT and Myket's cut (accounting/pricing.py). Myket wants rials.
"""
from django.core.management.base import BaseCommand

from accounting import pricing
from accounting.models import AddOnPack, Plan


class Command(BaseCommand):
    help = "Prices to set for each SKU in the Myket panel"

    def handle(self, *args, **options):
        rows = [
            (p.myket_sku, p.name, pricing.plan_base(p))
            for p in Plan.objects.filter(is_active=True).exclude(myket_sku="")
        ] + [
            (a.myket_sku, a.name, a.price)
            for a in AddOnPack.objects.filter(is_active=True).exclude(myket_sku="")
        ]
        for sku, name, base in rows:
            total = pricing.quote(base, pricing.CHANNEL_MYKET)["total"]
            self.stdout.write(f"{sku}\t{name}\t{base:,} → {total:,} toman = {total * 10:,} rial")
