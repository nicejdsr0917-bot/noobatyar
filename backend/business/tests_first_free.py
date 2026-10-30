"""«اولین نوبت خالی» on public business rows (client app list card perk)."""
from datetime import datetime, timedelta

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from appointment.cache_utils import first_free_slot_key, invalidate_slots_cache
from appointment.models import Appointment
from appointment.slots import first_free_slot_ms
from visitor.models import Visitor

from .first_free import attach_first_free_slots
from .models import Business

User = get_user_model()


class FirstFreeSlotTests(TestCase):
    def setUp(self):
        cache.clear()
        owner = User.objects.create_user(phone='09120000701', password='x', name='م')
        self.business = Business.objects.create(
            user=owner, title='ب', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=11,
            moderation_status=Business.MODERATION_APPROVED,
        )
        self.visitor = Visitor.objects.create(full_name='م', phone_number='09390000701')
        # Local midnight of a day well ahead, so no slot is "past".
        self.day = timezone.localdate() + timedelta(days=10)
        self.now = self._at(self.day, 0, 0)

    def _at(self, day, h, m):
        return timezone.make_aware(datetime(day.year, day.month, day.day, h, m))

    def _ms(self, day, h, m):
        return int(self._at(day, h, m).timestamp() * 1000)

    def _book(self, day, h, m, minutes=30):
        Appointment.objects.create(
            business=self.business, visitor=self.visitor, status='WAITING',
            appointment_date=self._at(day, h, m), service_duration=minutes,
        )

    def test_first_open_slot_of_the_day(self):
        self.assertEqual(first_free_slot_ms(self.business, self.now), self._ms(self.day, 9, 0))

    def test_booked_slots_are_skipped(self):
        self._book(self.day, 9, 0, minutes=60)
        self.assertEqual(first_free_slot_ms(self.business, self.now), self._ms(self.day, 10, 0))

    def test_full_day_and_day_off_roll_over(self):
        self._book(self.day, 9, 0, minutes=120)
        next_day = self.day + timedelta(days=1)
        self.business.closed_weekdays = [next_day.weekday()]
        self.business.save()
        expected = self.day + timedelta(days=2)
        self.assertEqual(first_free_slot_ms(self.business, self.now), self._ms(expected, 9, 0))

    def test_none_when_nothing_free_in_the_window(self):
        self.business.closed_weekdays = list(range(7))
        self.business.save()
        self.assertIsNone(first_free_slot_ms(self.business, self.now))

    def test_only_bookable_rows_get_a_value(self):
        rows = [
            {'id': self.business.id, 'is_claimed': True, 'booking_enabled': True},
            {'id': self.business.id, 'is_claimed': False, 'booking_enabled': True},
            {'id': self.business.id, 'is_claimed': True, 'booking_enabled': False},
        ]
        out = attach_first_free_slots(rows)
        self.assertIsNotNone(out[0]['first_free_slot'])
        self.assertIsNone(out[1]['first_free_slot'])
        self.assertIsNone(out[2]['first_free_slot'])
        self.assertNotIn('first_free_slot', rows[0], 'input rows (maybe cached) are not mutated')

    def test_value_is_cached_and_dropped_on_appointment_change(self):
        row = {'id': self.business.id, 'is_claimed': True, 'booking_enabled': True}
        attach_first_free_slots([row])
        self.assertIsNotNone(cache.get(first_free_slot_key(self.business.id)))
        with self.assertNumQueries(0):
            attach_first_free_slots([row])
        invalidate_slots_cache(self.business.id, self.day)
        self.assertIsNone(cache.get(first_free_slot_key(self.business.id)))

    def test_list_and_detail_payloads_carry_the_field(self):
        client = APIClient()
        r = client.get('/api/client/businesses/')
        self.assertEqual(r.status_code, 200, r.content)
        rows = r.json()['data']['results']
        self.assertTrue(rows and all('first_free_slot' in row for row in rows))
        r = client.get(f'/api/client/businesses/{self.business.id}/')
        self.assertEqual(r.status_code, 200, r.content)
        self.assertIn('first_free_slot', r.json()['data'])
