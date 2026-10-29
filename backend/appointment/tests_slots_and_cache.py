"""Slot grid overlap marking and cache-key date unification (local date)."""
from datetime import datetime, timedelta, timezone as dt_timezone

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from business.models import Business
from visitor.models import Visitor

from .cache_utils import available_slots_key, invalidate_slots_cache
from .models import Appointment

User = get_user_model()


class CacheKeyLocalDateTests(TestCase):
    def test_aware_datetime_keys_on_local_date(self):
        # 01:00 Tehran Jun 15 == 21:30 UTC Jun 14
        dt = datetime(2030, 6, 14, 21, 30, tzinfo=dt_timezone.utc)
        self.assertEqual(available_slots_key(1, dt), available_slots_key(1, '2030-06-15'))

    def test_invalidate_hits_the_local_date_key(self):
        dt = datetime(2030, 6, 14, 21, 30, tzinfo=dt_timezone.utc)
        cache.set(available_slots_key(1, '2030-06-15'), {'x': 1}, 60)
        invalidate_slots_cache(1, dt)
        self.assertIsNone(cache.get(available_slots_key(1, '2030-06-15')))


class SlotOverlapTests(TestCase):
    def setUp(self):
        cache.clear()
        owner = User.objects.create_user(phone='09120000601', password='x', name='م')
        self.business = Business.objects.create(
            user=owner, title='ب', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=12,
            moderation_status=Business.MODERATION_APPROVED,
        )
        self.visitor = Visitor.objects.create(full_name='م', phone_number='09390000601')
        self.day = timezone.localdate() + timedelta(days=3)

    def _at(self, h, m):
        return timezone.make_aware(datetime(self.day.year, self.day.month, self.day.day, h, m))

    def _slots(self):
        r = APIClient().get(
            f'/api/client/appointments/{self.business.id}/available-slots/',
            {'date': self.day.isoformat()},
        )
        self.assertEqual(r.status_code, 200, r.content)
        return {s['time']: s['status'] for s in r.json()['data']['slots']}

    def test_off_grid_and_long_appointments_block_overlapping_slots(self):
        # 09:45-10:45 overlaps the 09:30, 10:00 and 10:30 slots.
        Appointment.objects.create(
            business=self.business, visitor=self.visitor, status='WAITING',
            appointment_date=self._at(9, 45), service_duration=60,
        )
        slots = self._slots()
        self.assertEqual(slots['09:00'], 'AVAILABLE')
        for t in ('09:30', '10:00', '10:30'):
            self.assertEqual(slots[t], 'BOOKED', t)
        self.assertEqual(slots['11:00'], 'AVAILABLE')
