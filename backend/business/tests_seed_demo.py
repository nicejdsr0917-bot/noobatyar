from django.core.management import call_command
from django.test import TestCase

from business.categories import CATEGORY_GROUPS
from business.models import Business

GROUP_OF = {key: group for group, _label, pairs in CATEGORY_GROUPS for key, _ in pairs}


class SeedDemoBusinessesTests(TestCase):
    def test_spreads_over_the_groups_and_is_idempotent(self):
        call_command('seed_demo_businesses', count=40, verbosity=0)
        demo = Business.objects.filter(unique_code__startswith='DEMO')
        self.assertEqual(demo.count(), 40)
        self.assertGreaterEqual(len({GROUP_OF[b.category] for b in demo}), 6)
        self.assertTrue(all(b.latitude and b.longitude and b.services for b in demo))

        call_command('seed_demo_businesses', count=40, verbosity=0)
        self.assertEqual(Business.objects.filter(unique_code__startswith='DEMO').count(), 40)
