"""The visitor's own appointment list must cost a constant number of queries
(audit A4): business insurances, owner subscription / booking quota and the
queue position are resolved for the whole page, not per row."""
from datetime import timedelta

from django.core.cache import cache
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse
from django.utils import timezone

from appointment.client_serializers import ClientAppointmentSerializer
from appointment.models import Appointment
from business.tests_query_counts import DirectoryFixtureMixin
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

STATUSES = ['WAITING', 'IN_PROGRESS', 'WAITING', 'COMPLETED', 'CANCELLED']


class ClientAppointmentListQueryTests(DirectoryFixtureMixin, TestCase):
    def setUp(self):
        super().setUp()
        self.bizs = self.make_businesses(6)
        self.me = Visitor.objects.create(full_name='me', phone_number='09352220000')
        self.others = [Visitor.objects.create(full_name=f'o{i}', phone_number=f'0935333{i:04d}')
                       for i in range(4)]
        self.auth = {'HTTP_AUTHORIZATION': f'Visitor {sign_visitor_token(self.me.id)}'}
        self.base = timezone.now().replace(hour=8, minute=0, second=0, microsecond=0) + timedelta(days=1)

    def add_mine(self, n, start=0):
        for i in range(start, start + n):
            biz = self.bizs[i % len(self.bizs)]
            # Other people queue on the same business/day so the position is non-trivial.
            Appointment.objects.create(
                business=biz, visitor=self.others[i % 4], status='WAITING',
                appointment_date=self.base + timedelta(minutes=30 * i))
            Appointment.objects.create(
                business=biz, visitor=self.me, status=STATUSES[i % len(STATUSES)],
                appointment_date=self.base + timedelta(minutes=30 * i + 15))

    def fetch(self, page_size=50):
        with CaptureQueriesContext(connection) as ctx:
            r = self.client.get(reverse('client-appointment-list'), {'page_size': page_size}, **self.auth)
        self.assertEqual(r.status_code, 200, r.content)
        return len(ctx), r.json()['data']

    def test_query_count_constant_in_rows(self):
        # Rows are spread over every business from the start: the constant cost
        # (owner-state batch) may depend on which owner states exist, never on
        # how many rows there are.
        self.add_mine(6)
        self.fetch()  # warm the process-level caches (entitlements, contact policy)
        q6, d6 = self.fetch()
        self.assertEqual(len(d6['results']), 6)
        self.add_mine(14, start=6)
        q20, d20 = self.fetch()
        self.assertEqual(len(d20['results']), 20)
        self.assertEqual(q6, q20)
        self.assertLessEqual(q20, 14)
        # A single-row page (one owner state) is never more expensive.
        Appointment.objects.exclude(id=d20['results'][0]['id']).filter(visitor=self.me).delete()
        q1, d1 = self.fetch()
        self.assertEqual(len(d1['results']), 1)
        self.assertLessEqual(q1, q20)

    def test_batched_rows_equal_per_object_serializer(self):
        self.add_mine(12)
        _q, data = self.fetch()
        by_id = {r['id']: r for r in data['results']}
        self.assertEqual(len(by_id), 12)
        self.assertGreater({r['queue_position'] for r in data['results']}, {0})
        for a in Appointment.objects.filter(visitor=self.me).select_related('business').prefetch_related('business__insurances'):
            legacy = ClientAppointmentSerializer(a, context={'request': None}).data
            self.assertEqual(by_id[a.id], dict(legacy), a.id)
