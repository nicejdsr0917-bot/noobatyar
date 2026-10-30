"""assertNumQueries guards for the hottest anonymous endpoints. Cold = cache
miss (the real database cost); warm = cache hit (must not touch the database)."""
from datetime import timedelta

from django.core.cache import cache
from django.test import TestCase
from django.utils import timezone

from appointment.models import Appointment
from business import cache as public_cache
from business.tests_query_counts import DirectoryFixtureMixin
from visitor.models import Visitor

LIST = '/api/client/business/'


class HotEndpointQueryTests(DirectoryFixtureMixin, TestCase):
    def setUp(self):
        super().setUp()
        self.bizs = self.make_businesses(20)
        self.biz = self.bizs[0]
        self.client.get(LIST)           # warm contact policy / entitlement singletons
        cache.clear()

    def cold_then_warm(self, url, params=None, cold=None):
        public_cache.bump_all()
        with self.assertNumQueries(cold):
            self.assertEqual(self.client.get(url, params or {}).status_code, 200)
        with self.assertNumQueries(0):
            self.assertEqual(self.client.get(url, params or {}).status_code, 200)

    def test_list_cold_and_warm(self):
        self.cold_then_warm(LIST, {'page_size': 20}, cold=self._measure(LIST, {'page_size': 20}))

    def _measure(self, url, params):
        from django.db import connection
        from django.test.utils import CaptureQueriesContext
        public_cache.bump_all()
        with CaptureQueriesContext(connection) as ctx:
            self.client.get(url, params)
        n = len(ctx)
        self.assertGreater(n, 0)
        return n

    def test_list_cold_cost_is_independent_of_page_size(self):
        a = self._measure(LIST, {'page_size': 2})
        b = self._measure(LIST, {'page_size': 20})
        self.assertEqual(a, b)
        self.assertLessEqual(b, 12)

    def test_detail_cold_and_warm(self):
        url = f'{LIST}{self.biz.id}/'
        n = self._measure(url, {})
        self.assertLessEqual(n, 9)
        self.cold_then_warm(url, cold=n)

    def test_public_slots_query_count_is_constant(self):
        url = f'/api/client/appointments/slots/{self.biz.id}/'
        day = (timezone.now() + timedelta(days=2)).date().isoformat()

        def hit():
            cache.clear()
            from django.db import connection
            from django.test.utils import CaptureQueriesContext
            with CaptureQueriesContext(connection) as ctx:
                r = self.client.get(url, {'date': day})
            self.assertEqual(r.status_code, 200, r.content)
            return len(ctx)

        empty = hit()
        for i in range(15):
            v = Visitor.objects.create(full_name=f'v{i}', phone_number=f'0936000{i:04d}')
            Appointment.objects.create(
                business=self.biz, visitor=v, status='WAITING',
                appointment_date=timezone.now().replace(hour=9, minute=0, second=0, microsecond=0)
                + timedelta(days=2, minutes=30 * (i % 10)))
        self.assertEqual(hit(), empty)
        self.assertLessEqual(empty, 8)
