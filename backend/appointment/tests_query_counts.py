from datetime import timedelta

from django.contrib.auth import get_user_model
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.utils import timezone
from rest_framework.test import APIClient

from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


class AppointmentListQueryCountTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000201', password='x', name='O')
        self.biz = Business.objects.create(
            user=self.owner, title='t', phone='02100000000', address='x',
            default_service_duration=30, work_start_hour=9, work_end_hour=17)
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _mk(self, n, start=0):
        for i in range(start, start + n):
            v = Visitor.objects.create(full_name=f'v{i}', phone_number=f'0935100{i:04d}')
            Appointment.objects.create(
                business=self.biz, visitor=v, status='WAITING',
                appointment_date=timezone.now() + timedelta(hours=i + 1))

    def _count(self):
        with CaptureQueriesContext(connection) as ctx:
            r = self.client.get('/api/appointment/query', {'business_id': self.biz.id, 'page_size': 100})
        self.assertEqual(r.status_code, 200)
        return len(ctx.captured_queries)

    def test_constant_queries(self):
        self._mk(3)
        small = self._count()
        self._mk(30, start=3)
        self.assertEqual(small, self._count())
        self.assertLessEqual(small, 4)
