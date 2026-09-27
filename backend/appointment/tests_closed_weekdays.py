"""
appointment/tests_closed_weekdays.py

A business's weekly recurring days off (Business.closed_weekdays) and their
one effect on the appointment system: the public slots endpoint reports the
day as closed with no bookable slots, before any of the usual work-hours/
duration slot-building runs.

Deliberately narrow. closed_weekdays does not cascade anywhere else — an
appointment already booked on a day later marked closed is left exactly as it
was (see the field's docstring in business/models.py), and this file pins
that down with a test rather than leaving it as an assumption.
"""

from datetime import date, timedelta

from django.core.cache import cache
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse

from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


def _next_date_with_weekday(weekday: int) -> date:
    """The nearest future date (never today) that falls on `weekday`.

    Never today: picking "today" would make a test's outcome depend on which
    day it happens to run, since AvailableSlotsView also marks past slots on
    the current day. A day off a few days out sidesteps that entirely.
    """
    today = date.today()
    for offset in range(1, 8):
        candidate = today + timedelta(days=offset)
        if candidate.weekday() == weekday:
            return candidate
    raise AssertionError("unreachable: every weekday appears within 7 days")


class ClosedWeekdaySlotsTests(TestCase):
    """GET /api/client/appointments/<business_id>/available-slots/ on a closed day."""

    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000030', password='x', name='مالک')
        self.business = Business.objects.create(
            user=self.owner,
            title='کسب‌وکار تست',
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )

    def _slots(self, on_date: date):
        return self.client.get(
            reverse('client-available-slots', args=[self.business.id]),
            {'date': on_date.isoformat()},
        )

    def test_closed_weekday_reports_closed_with_no_slots(self):
        friday = _next_date_with_weekday(4)  # Friday
        self.business.closed_weekdays = [4]
        self.business.save(update_fields=['closed_weekdays'])

        response = self._slots(friday)
        data = response.json()['data']

        self.assertEqual(response.status_code, 200)
        self.assertTrue(data['closed'])
        self.assertEqual(data['slots'], [])

    def test_open_weekday_is_unaffected_by_an_unrelated_closed_day(self):
        saturday = _next_date_with_weekday(5)
        self.business.closed_weekdays = [4]  # only Friday is closed
        self.business.save(update_fields=['closed_weekdays'])

        response = self._slots(saturday)
        data = response.json()['data']

        self.assertFalse(data['closed'])
        self.assertTrue(data['slots'])

    def test_no_closed_weekdays_means_every_day_is_open(self):
        """The default (empty list) must not accidentally close anything."""
        any_day = _next_date_with_weekday(4)
        response = self._slots(any_day)
        data = response.json()['data']

        self.assertFalse(data['closed'])
        self.assertTrue(data['slots'])

    def test_closing_a_weekday_does_not_touch_an_existing_appointment(self):
        """The bug this suite exists to prevent: closing a day going forward
        must never reach back and alter a booking already made on it."""
        friday = _next_date_with_weekday(4)
        visitor = Visitor.objects.create(full_name='مشتری', phone_number='09350000030')
        appt = Appointment.objects.create(
            business=self.business,
            visitor=visitor,
            appointment_date=friday.isoformat() + 'T10:00:00+03:30',
            status='PENDING',
        )

        self.business.closed_weekdays = [4]
        self.business.save(update_fields=['closed_weekdays'])

        appt.refresh_from_db()
        self.assertEqual(appt.status, 'PENDING')
        self.assertEqual(appt.appointment_date.date(), friday)
