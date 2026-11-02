"""
business/tests_city_code.py

The business's city (Business.city_code, a Divar city id): the owner picks it,
the names are filled from the list, and the client directory filters by it.
"""

from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient

from .iran_cities import get_city
from .models import Business
from .tests_schedule_and_phones import OwnerWriteTestCaseMixin

TEHRAN = 1   # Divar's id for the city of Tehran
QOM = 8


class OwnerCityCodeTests(OwnerWriteTestCaseMixin, TestCase):
    def test_city_code_fills_city_and_province(self):
        response = self._put(city_code=QOM)
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.city_code, QOM)
        self.assertEqual((self.business.city, self.business.province), ('قم', 'قم'))
        self.assertEqual(response.json()['data']['city'], 'قم')

    def test_client_cannot_override_the_names(self):
        self._put(city_code=TEHRAN, city='جای دیگر', province='استان دیگر')
        self.business.refresh_from_db()
        self.assertEqual((self.business.city, self.business.province), ('تهران', 'تهران'))

    def test_unknown_code_is_rejected(self):
        response = self._put(city_code=999999)
        self.assertEqual(response.status_code, 400)

    def test_clearing_clears_the_names(self):
        self._put(city_code=QOM)
        response = self._put(city_code=None)
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertIsNone(self.business.city_code)
        self.assertEqual((self.business.city, self.business.province), ('', ''))


class ClientCityFilterTests(OwnerWriteTestCaseMixin, TestCase):
    def setUp(self):
        super().setUp()
        Business.objects.filter(pk=self.business.pk).update(
            city_code=TEHRAN, moderation_status=Business.MODERATION_APPROVED,
        )
        tehran = get_city(TEHRAN)

        def make(title, **fields):
            return Business.objects.create(
                user=self.owner, title=title, category=self.business.category, address='-',
                default_service_duration=30, work_start_hour=9, work_end_hour=18,
                moderation_status=Business.MODERATION_APPROVED, **fields,
            )

        self.pinned_no_code = make('پین در تهران', latitude=tehran['lat'], longitude=tehran['lng'])
        self.in_qom = make('قم', city_code=QOM)
        self.nowhere = make('بدون شهر و پین')

    def _titles(self, **params):
        response = APIClient().get(reverse('client-business-list'), params)
        self.assertEqual(response.status_code, 200, response.content)
        return {b['title'] for b in response.json()['data']['results']}

    def test_filter_by_city(self):
        titles = self._titles(city_code=TEHRAN)
        self.assertIn(self.business.title, titles)
        self.assertIn(self.pinned_no_code.title, titles)
        self.assertNotIn(self.in_qom.title, titles)
        self.assertNotIn(self.nowhere.title, titles)

    def test_other_city(self):
        self.assertEqual(self._titles(city_code=QOM), {self.in_qom.title})

    def test_no_city_lists_everything(self):
        self.assertEqual(len(self._titles()), 4)
