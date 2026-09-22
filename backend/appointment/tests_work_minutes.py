"""
appointment/tests_work_minutes.py

Business.work_start_minute/work_end_minute (minute precision on top of the
existing whole-hour work_start_hour/work_end_hour) actually reaching the slot
list, not just passing serializer validation. See business/models.py for why
they are separate fields rather than a single minutes-since-midnight column.
"""

from datetime import date, timedelta

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse

from business.models import Business

User = get_user_model()


class WorkMinutesSlotsTests(TestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000041', password='x', name='مالک')
        self.business = Business.objects.create(
            user=self.owner,
            title='کسب‌وکار تست',
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=19,
            work_start_minute=30,
            work_end_hour=21,
            work_end_minute=0,
            moderation_status=Business.MODERATION_APPROVED,
        )

    def _slots(self, on_date):
        return self.client.get(
            reverse('client-available-slots', args=[self.business.id]),
            {'date': on_date.isoformat()},
        )

    def test_first_slot_starts_at_the_half_hour(self):
        tomorrow = date.today() + timedelta(days=1)
        data = self._slots(tomorrow).json()['data']
        self.assertEqual(data['slots'][0]['time'], '19:30')

    def test_no_slot_before_the_configured_start_minute(self):
        tomorrow = date.today() + timedelta(days=1)
        data = self._slots(tomorrow).json()['data']
        times = [s['time'] for s in data['slots']]
        self.assertNotIn('19:00', times)

    def test_last_slot_respects_the_end_minute(self):
        """21:00 end, 30-min service: the last bookable start is 20:30, not
        21:00 — a slot must fit entirely before closing."""
        tomorrow = date.today() + timedelta(days=1)
        data = self._slots(tomorrow).json()['data']
        times = [s['time'] for s in data['slots']]
        self.assertIn('20:30', times)
        self.assertNotIn('21:00', times)
