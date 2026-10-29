"""Rules a client booking must satisfy beyond quota and slot conflict (audit A2)."""

from datetime import datetime, timedelta
from unittest.mock import patch
from zoneinfo import ZoneInfo

from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from business.models import Business
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()
LOCAL = ZoneInfo(settings.TIME_ZONE)


def local_slot(days=2, hour=10, minute=0):
    """A wall-clock time ``days`` from today in the project's timezone."""
    day = timezone.localtime().date() + timedelta(days=days)
    return datetime(day.year, day.month, day.day, hour, minute, tzinfo=LOCAL)


class ClientBookingRulesTests(TestCase):
    def setUp(self):
        cache.clear()
        owner = User.objects.create_user(phone='09120000088', password='x', name='o')
        self.business = Business.objects.create(
            user=owner, title='b', phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )
        visitor = Visitor.objects.create(full_name='v', phone_number='09350000088')
        self.auth = f'Visitor {sign_visitor_token(visitor.id)}'
        for target, value in (
            ('accounting.usage.can_book_appointment', True),
            ('accounting.usage.record_appointment', 'monthly'),
        ):
            patcher = patch(target, return_value=value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def book(self, when=None, **extra):
        when = when or local_slot()
        payload = {
            'business_id': self.business.id,
            'appointment_date': int(when.timestamp() * 1000),
        }
        payload.update(extra)
        return self.client.post(
            reverse('client-appointment-list'), payload,
            content_type='application/json', HTTP_AUTHORIZATION=self.auth,
        )

    def assertRejected(self, response, code=400):
        self.assertEqual(response.status_code, code, response.content)
        self.assertEqual(Appointment.objects.count(), 0)
        body = response.json()
        self.assertEqual(body['status'], 'error')
        self.assertTrue(body['message'])

    def test_happy_path(self):
        r = self.book()
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(Appointment.objects.count(), 1)

    def test_booking_at_work_start_and_ending_exactly_at_work_end_is_allowed(self):
        self.assertEqual(self.book(local_slot(hour=9)).status_code, 200)
        self.assertEqual(self.book(local_slot(hour=16, minute=30)).status_code, 200)

    def test_off_grid_start_inside_working_hours_is_allowed(self):
        # Slot-grid alignment is deliberately not enforced.
        self.assertEqual(self.book(local_slot(hour=10, minute=7)).status_code, 200)

    def test_booking_disabled(self):
        self.business.booking_enabled = False
        self.business.save(update_fields=['booking_enabled'])
        self.assertRejected(self.book())

    def test_unclaimed_business(self):
        self.business.is_claimed = False
        self.business.save(update_fields=['is_claimed'])
        self.assertRejected(self.book())

    def test_start_in_the_past(self):
        past = timezone.localtime() - timedelta(hours=2)
        self.assertRejected(self.book(past))

    def test_closed_weekday(self):
        when = local_slot()
        self.business.closed_weekdays = [when.weekday()]
        self.business.save(update_fields=['closed_weekdays'])
        self.assertRejected(self.book(when))

    def test_before_opening(self):
        self.assertRejected(self.book(local_slot(hour=8, minute=59)))

    def test_after_closing(self):
        self.assertRejected(self.book(local_slot(hour=17, minute=30)))

    def test_running_past_closing(self):
        # Starts inside working hours but 30 minutes would end after 17:00.
        self.assertRejected(self.book(local_slot(hour=16, minute=45)))

    def test_midnight_utc_is_not_working_hours(self):
        self.assertRejected(self.book(local_slot(hour=0, minute=0)))

    def test_negative_duration(self):
        self.assertRejected(self.book(service_duration=-30))

    def test_zero_duration(self):
        self.assertRejected(self.book(service_duration=0))

    def test_absurd_duration(self):
        self.assertRejected(self.book(service_duration=100000))

    def test_explicit_valid_duration_is_kept(self):
        r = self.book(service_duration=45)
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(Appointment.objects.get().service_duration, 45)

    def test_rules_run_before_quota_is_spent(self):
        with patch('accounting.usage.record_appointment') as record:
            self.assertRejected(self.book(local_slot(hour=3)))
        record.assert_not_called()
