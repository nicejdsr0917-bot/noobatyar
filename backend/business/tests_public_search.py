"""Public directory search: params parsing, geo helpers and detail error handling."""
from unittest import mock

from django.test import SimpleTestCase, TestCase
from django.utils.datastructures import MultiValueDict

from business.geo import MAX_RADIUS_KM, haversine_km
from business.selectors import PublicSearchParams
from business.tests_query_counts import DirectoryFixtureMixin


class SearchParamsTests(SimpleTestCase):
    def parse(self, **q):
        return PublicSearchParams.from_query({k: str(v) for k, v in q.items()})

    def test_defaults(self):
        p = self.parse()
        self.assertEqual((p.page, p.page_size, p.sort), (1, 10, 'recommended'))
        self.assertIsNone(p.lat)
        self.assertIsNone(p.radius_km)

    def test_paging_clamps(self):
        p = self.parse(page=-3, page_size=1000)
        self.assertEqual((p.page, p.page_size), (1, 100))
        p = self.parse(page='x', page_size='y')
        self.assertEqual((p.page, p.page_size), (1, 10))

    def test_geo_defaults_and_cap(self):
        p = self.parse(lat=35.7, lng=51.4)
        self.assertEqual(p.radius_km, 50.0)
        self.assertEqual(self.parse(lat=35.7, lng=51.4, radius_km=5000).radius_km, MAX_RADIUS_KM)
        self.assertEqual(self.parse(lat=35.7, lng=51.4, radius_km=-1).radius_km, MAX_RADIUS_KM)
        self.assertEqual(self.parse(lat=35.7, lng=51.4, radius_km='bad').radius_km, 50.0)

    def test_out_of_range_coordinates_ignored(self):
        p = self.parse(lat=95, lng=51.4)
        self.assertIsNone(p.lat)
        self.assertIsNone(p.radius_km)

    def test_cache_key_parts_are_stable(self):
        a = self.parse(search=' x ', page=2)
        b = self.parse(page=2, search='x')
        self.assertEqual(a, b)


class HaversineTests(SimpleTestCase):
    def test_zero_and_known_distance(self):
        self.assertEqual(haversine_km(35.7, 51.4, 35.7, 51.4), 0.0)
        self.assertAlmostEqual(haversine_km(35.70, 51.40, 35.75, 51.40), 5.56, places=1)


class DetailErrorHandlingTests(DirectoryFixtureMixin, TestCase):
    def test_unexpected_error_is_a_500_not_a_404(self):
        biz = self.make_businesses(1)[0]
        self.client.raise_request_exception = False
        with mock.patch('business.client_views.PublicBusinessSerializer',
                        side_effect=RuntimeError('boom')):
            with self.assertLogs('business.client_views', level='ERROR'):
                r = self.client.get(f'/api/client/business/{biz.id}/')
        self.assertEqual(r.status_code, 500)

    def test_missing_and_hidden_are_404(self):
        biz = self.make_businesses(1)[0]
        self.assertEqual(self.client.get('/api/client/business/999999/').status_code, 404)
        biz.is_locked = True
        biz.save(update_fields=['is_locked'])
        self.assertEqual(self.client.get(f'/api/client/business/{biz.id}/').status_code, 404)
