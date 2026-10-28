"""
business/tests_location.py

The business's map pin (latitude/longitude): the owner sets and clears it
through the same write path as every other profile field, it is a pair or
nothing, and the public serializer hides it exactly when it hides the
address.
"""

from django.test import TestCase

from .serializers import PublicBusinessSerializer
from .tests_schedule_and_phones import OwnerWriteTestCaseMixin


class LocationTests(OwnerWriteTestCaseMixin, TestCase):
    def test_owner_sets_a_location(self):
        response = self._put(latitude=35.7001234567, longitude=51.4004321)
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        # Rounded to six decimals.
        self.assertEqual((self.business.latitude, self.business.longitude), (35.700123, 51.400432))
        self.assertEqual(response.json()['data']['latitude'], 35.700123)

    def test_half_a_pair_is_rejected(self):
        response = self._put(latitude=35.7)
        self.assertEqual(response.status_code, 400)

    def test_out_of_range_is_rejected(self):
        response = self._put(latitude=95, longitude=51.4)
        self.assertEqual(response.status_code, 400)

    def test_owner_clears_the_location(self):
        self._put(latitude=35.7, longitude=51.4)
        response = self._put(latitude=None, longitude=None)
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertIsNone(self.business.latitude)
        self.assertIsNone(self.business.longitude)

    def test_multipart_blank_clears_too(self):
        """The owner app saves the profile as multipart (for the logo), where
        "no value" can only be an empty string."""
        self._put(latitude=35.7, longitude=51.4)
        response = self.client.put(
            f'/api/business/{self.business.id}/', {'latitude': '', 'longitude': ''}, format='multipart',
        )
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertIsNone(self.business.latitude)

    def test_public_page_shows_it_like_the_address(self):
        self.business.latitude, self.business.longitude = 35.7, 51.4
        self.business.allow_anonymous_view = True
        self.business.save()
        data = PublicBusinessSerializer(self.business).data
        self.assertEqual((data['latitude'], data['longitude']), (35.7, 51.4))

    def test_masked_with_the_address_for_anonymous_viewers(self):
        self.business.latitude, self.business.longitude = 35.7, 51.4
        self.business.allow_anonymous_view = False
        self.business.save()
        data = PublicBusinessSerializer(self.business).data
        self.assertIsNone(data['address'])
        self.assertIsNone(data['latitude'])
        self.assertIsNone(data['longitude'])
