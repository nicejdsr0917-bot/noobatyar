from django.core.management.base import BaseCommand

from business.insurance_catalog import INSURANCES
from business.models import Insurance


class Command(BaseCommand):
    help = 'Creates or updates the catalog of basic and supplementary insurers (idempotent).'

    def handle(self, *args, **options):
        created = 0
        for name, slug, kind, order in INSURANCES:
            _, was_created = Insurance.objects.update_or_create(
                slug=slug,
                defaults={'name': name, 'insurance_type': kind, 'is_active': True, 'display_order': order},
            )
            created += was_created
        self.stdout.write(self.style.SUCCESS(f'{len(INSURANCES)} insurers ({created} new).'))
