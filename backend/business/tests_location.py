"""
business/tests_location.py

The business's map pin (latitude/longitude): the owner sets and clears it
through the same write path as every other profile field, it is a pair or
nothing, and the public serializer hides it exactly when it hides the
address.
"""

from django.core.cache import cache
from django.test import TestCase

from .serializers import PublicBusinessSerializer
from .tests_query_counts import DirectoryFixtureMixin
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

    # ── Area only ────────────────────────────────────────────────────────────

    def _area_business(self):
        self.business.latitude, self.business.longitude = 35.7, 51.4
        self.business.allow_anonymous_view = True
        self.business.location_privacy = 'AREA'
        self.business.save()

    def test_owner_chooses_area_only(self):
        response = self._put(latitude=35.7, longitude=51.4, location_privacy='AREA')
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.location_privacy, 'AREA')
        # The owner still sees the real point.
        self.assertEqual(response.json()['data']['latitude'], 35.7)

    def test_exact_is_the_default(self):
        self.business.latitude, self.business.longitude = 35.7, 51.4
        self.business.allow_anonymous_view = True
        self.business.save()
        data = PublicBusinessSerializer(self.business).data
        self.assertFalse(data['location_approximate'])
        self.assertIsNone(data['location_radius_m'])

    def test_area_only_never_sends_the_real_point(self):
        from .geo import haversine_km
        from .public_policy import AREA_RADIUS_M, AREA_SHIFT_MAX_M, AREA_SHIFT_MIN_M
        self._area_business()
        data = PublicBusinessSerializer(self.business).data
        self.assertTrue(data['location_approximate'])
        self.assertEqual(data['location_radius_m'], AREA_RADIUS_M)
        self.assertNotEqual((data['latitude'], data['longitude']), (35.7, 51.4))
        shift_m = haversine_km(35.7, 51.4, data['latitude'], data['longitude']) * 1000
        # The real point is inside the drawn circle, but not at its middle.
        self.assertGreaterEqual(shift_m, AREA_SHIFT_MIN_M - 1)
        self.assertLessEqual(shift_m, AREA_SHIFT_MAX_M + 1)
        # The same centre every time, so asking again narrows nothing.
        again = PublicBusinessSerializer(self.business).data
        self.assertEqual((again['latitude'], again['longitude']), (data['latitude'], data['longitude']))

    def test_area_only_distance_is_coarse(self):
        self._area_business()
        self.business.distance_km = 1.234
        data = PublicBusinessSerializer(self.business).data
        self.assertEqual(data['distance_km'], 1.0)
        self.business.distance_km = 0.05
        self.assertEqual(PublicBusinessSerializer(self.business).data['distance_km'], 0.5)


class AreaOnlyEndpointTests(DirectoryFixtureMixin, TestCase):
    """Through the real (cached) detail and list views, not just the serializer."""

    def setUp(self):
        super().setUp()
        cache.clear()
        self.biz = self.make_businesses(1)[0]
        self.real = (self.biz.latitude, self.biz.longitude)
        self.biz.location_privacy = 'AREA'
        self.biz.save()

    def _point(self, data):
        return (data['latitude'], data['longitude'])

    def test_detail_hides_the_point_on_every_request(self):
        url = f'/api/client/businesses/{self.biz.id}/'
        for _ in range(2):  # the second answer comes from the cache
            response = self.client.get(url, {'lat': self.real[0], 'lng': self.real[1]})
            self.assertEqual(response.status_code, 200, response.content)
            data = response.json()['data']
            self.assertTrue(data['location_approximate'])
            self.assertNotEqual(self._point(data), self.real)
            # Measured to the shifted centre and coarse: never "0.0 km away".
            self.assertEqual(data['distance_km'], 0.5)

    def test_list_hides_the_point(self):
        response = self.client.get('/api/client/business/')
        row = next(r for r in response.json()['data']['results'] if r['id'] == self.biz.id)
        self.assertTrue(row['location_approximate'])
        self.assertNotEqual(self._point(row), self.real)
