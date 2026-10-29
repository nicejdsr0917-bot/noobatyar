"""
Owner-side booking correctness.

* The owner create/update path takes the same Business row lock the client
  booking path takes (client_views.ClientAppointmentCreateView._create_if_free).
* Owners may deliberately overlap appointments (walk-ins, parallel chairs): the
  conflict checks were commented out in the first commit with no explanation,
  and the queue model (WAITING -> IN_PROGRESS) is built around it, so overlap is
  NOT blocked. Only max_appointments_per_hour is enforced.
* Hourly capacity uses local (Asia/Tehran, +03:30) hour boundaries.
"""

import threading
from datetime import datetime, timedelta, timezone as dt_timezone
from unittest import skipUnless
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.db import connection
from django.test import TestCase, TransactionTestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from accounting.models import Plan, Subscription
from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


class _Base:
    def _setup(self, phone, **business_kwargs):
        self.owner = User.objects.create_user(phone=phone, password='x', name='مالک')
        self.business = Business.objects.create(
            user=self.owner, title='کسب‌وکار', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED, **business_kwargs,
        )
        plan = Plan.objects.create(
            name='تست' + phone, price=0, duration_value=1, duration_unit='month',
            features={'monthly_appointments': -1},
        )
        Subscription.objects.create(
            user=self.owner, plan=plan, status='active',
            ends_at=timezone.now() + timedelta(days=30),
        )
        self.visitor = Visitor.objects.create(user=self.owner, full_name='مشتری', phone_number='09390000' + phone[-3:])
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _post(self, when):
        return self.client.post(
            reverse('appointments:appointment-create'),
            data={
                'business_id': self.business.id,
                'visitor_id': self.visitor.id,
                'appointment_date': int(when.timestamp() * 1000),
            },
            format='json',
        )


def _tehran(y, mo, d, h, mi=0):
    return datetime(y, mo, d, h, mi, tzinfo=dt_timezone.utc) - timedelta(hours=3, minutes=30)


class OwnerBookingTests(_Base, TestCase):
    def setUp(self):
        self._setup('09120000501')

    def test_owner_overlap_is_still_allowed(self):
        """Backward compat: overlapping owner bookings are not blocked."""
        when = timezone.now() + timedelta(days=1)
        self.assertEqual(self._post(when).status_code, 201)
        self.assertEqual(self._post(when).status_code, 201)

    def test_create_takes_the_business_row_lock(self):
        when = timezone.now() + timedelta(days=1)
        with patch('django.db.models.query.QuerySet.select_for_update',
                   autospec=True, side_effect=lambda qs, *a, **k: qs) as sfu:
            self.assertEqual(self._post(when).status_code, 201)
        self.assertTrue(sfu.called)

    def test_details_update_takes_the_business_row_lock(self):
        when = timezone.now() + timedelta(days=1)
        appt = Appointment.objects.create(
            business=self.business, visitor=self.visitor, appointment_date=when, status='WAITING',
        )
        with patch('django.db.models.query.QuerySet.select_for_update',
                   autospec=True, side_effect=lambda qs, *a, **k: qs) as sfu:
            r = self.client.patch(
                reverse('appointments:appointment-update', args=[appt.id]),
                data={'description': 'x'}, format='json',
            )
        self.assertEqual(r.status_code, 200)
        self.assertTrue(sfu.called)


class HourlyCapacityLocalHourTests(_Base, TestCase):
    """max_appointments_per_hour = 1; the hour is the *Tehran* hour."""

    def setUp(self):
        self._setup('09120000502', max_appointments_per_hour=1)
        self.day = (timezone.now() + timedelta(days=2)).date()

    def _local(self, h, m):
        return _tehran(self.day.year, self.day.month, self.day.day, h, m)

    def test_same_local_hour_but_different_utc_hour_is_full(self):
        # 10:10 Tehran = 06:40 UTC; 10:50 Tehran = 07:20 UTC: same local hour,
        # different UTC hours, so a UTC window would let the second one in.
        self.assertEqual(self._post(self._local(10, 10)).status_code, 201)
        self.assertEqual(self._post(self._local(10, 50)).status_code, 409)

    def test_adjacent_local_hours_do_not_share_capacity(self):
        # 10:50 and 11:10 Tehran share the same UTC hour (07:20, 07:40) but are
        # different local hours, so both fit.
        self.assertEqual(self._post(self._local(10, 50)).status_code, 201)
        self.assertEqual(self._post(self._local(11, 10)).status_code, 201)


@skipUnless(connection.vendor in ('postgresql', 'mysql'), 'needs row locks')
class OwnerClientRaceTests(_Base, TransactionTestCase):
    def test_parallel_owner_posts_respect_capacity(self):
        self._setup('09120000503', max_appointments_per_hour=1)
        when = _tehran(2030, 1, 5, 10, 0)
        results = []

        def go():
            from django.db import connection as c
            try:
                results.append(self._post(when).status_code)
            finally:
                c.close()

        threads = [threading.Thread(target=go) for _ in range(4)]
        [t.start() for t in threads]
        [t.join() for t in threads]
        self.assertEqual(sorted(results), [201, 409, 409, 409])
        self.assertEqual(Appointment.objects.count(), 1)
