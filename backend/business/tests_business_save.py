"""Business.save(): the logo comparison SELECT is skipped when update_fields excludes `logo`; categories alias."""

from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase

from business import categories
from business.models import Business

User = get_user_model()


class BusinessSaveTests(TestCase):
    def setUp(self):
        owner = User.objects.create_user(phone='09120000401', password='x', name='م')
        self.business = Business.objects.create(
            user=owner, title='سالن', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
        )

    def test_update_fields_without_logo_skips_the_comparison(self):
        self.business.title = 'جدید'
        with patch.object(Business, '_logo_changed', return_value=False) as changed:
            self.business.save(update_fields=['title'])
        changed.assert_not_called()
        with patch.object(Business, '_logo_changed', return_value=False) as changed:
            self.business.save(update_fields=['title', 'logo'])
            self.business.save()
        self.assertEqual(changed.call_count, 2)

    def test_update_fields_without_logo_saves_one_query_less(self):
        self.business.title = 'جدید'
        with self.assertNumQueries(1):
            self.business.save(update_fields=['title'])

    def test_category_vocabulary_is_shared_with_the_model(self):
        self.assertIs(Business.CATEGORY_GROUPS, categories.CATEGORY_GROUPS)
        self.assertIs(Business.CATEGORY_CHOICES, categories.CATEGORY_CHOICES)
        self.assertEqual(Business._meta.get_field('category').choices, categories.CATEGORY_CHOICES)
