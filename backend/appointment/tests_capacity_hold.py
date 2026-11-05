"""Slot capacity (several clients at the same time) and the owner-set hold
(how long a client's booking waits for its deposit), including the gateway
stretch that keeps a paying client's slot."""

from datetime import datetime, timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import SimpleTestCase, TestCase
from django.urls import reverse
from django.utils import timezone

from business.models import Business
from business.serializers import BusinessSerializer
from visitor.models import Visitor

from .models import Appointment
from .occupancy import GATEWAY_HOLD_MINUTES, find_conflict, full_intervals, peak_concurrency
from .services.booking import BookingFailure, appointment_create_for_client
from .services.transitions import appointment_store_gateway_track_id
from .slots import LOCAL_TZ, build_day_slots, busy_intervals

User = get_user_model()


def t(h, m=0):
    return datetime(2026, 1, 3, h, m, tzinfo=LOCAL_TZ)


class IntervalMathTests(SimpleTestCase):
    def test_back_to_back_is_not_overlap(self):
        self.assertEqual(peak_concurrency([(t(10), t(10, 30)), (t(10, 30), t(11))], t(10), t(11)), 1)

    def test_peak_counts_the_busiest_moment(self):
        busy = [(t(10), t(11)), (t(10, 15), t(10, 45)), (t(10, 30), t(11)), (t(9), t(10))]
        self.assertEqual(peak_concurrency(busy, t(10), t(11)), 3)
        # Outside the window does not count.
        self.assertEqual(peak_concurrency(busy, t(9), t(10)), 1)

    def test_full_intervals_only_where_capacity_is_reached(self):
        busy = [(t(10), t(11)), (t(10, 30), t(11, 30)), (t(10, 45), t(11, 15))]
        self.assertEqual(full_intervals(busy, 2), [(t(10, 30), t(11, 15))])
        self.assertEqual(full_intervals(busy, 3), [(t(10, 45), t(11))])
        self.assertEqual(full_intervals(busy, 4), [])
        self.assertEqual(full_intervals(busy, 1), [(t(10), t(11, 30))])


class Base(TestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000771', password='x', name='o')
        self.business = Business.objects.create(
            user=self.owner, title='b', phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )
        self.when = (timezone.now() + timedelta(days=2)).astimezone(LOCAL_TZ).replace(
            hour=10, minute=0, second=0, microsecond=0)
        self.n = 0
        # Quota is not what these tests are about.
        p1 = patch('accounting.usage.can_book_appointment', return_value=True)
        p2 = patch('accounting.usage.record_appointment', return_value='monthly')
        p1.start(); p2.start()
        self.addCleanup(p1.stop); self.addCleanup(p2.stop)

    def visitor(self):
        self.n += 1
        return Visitor.objects.create(full_name=f'v{self.n}', phone_number=f'0935000{self.n:04d}')

    def book(self, when=None, requires_payment=False):
        return appointment_create_for_client(
            business=self.business, visitor=self.visitor(), appointment_date=when or self.when,
            duration=30, description='', selected_services='', requires_payment=requires_payment,
        )

    def slot_at(self, when):
        busy = busy_intervals(self.business, when.date())
        for s in build_day_slots(self.business, when.date(), busy, timezone.now()):
            if s['time'] == when.strftime('%H:%M'):
                return s
        raise AssertionError('no such slot')


class SlotCapacityTests(Base):
    def test_default_capacity_one_keeps_the_old_rule(self):
        self.assertEqual(self.business.slot_capacity, 1)
        self.assertIsNone(self.book().failure)
        self.assertEqual(self.book().failure, BookingFailure.CONFLICT)
        self.assertEqual(self.slot_at(self.when)['status'], 'BOOKED')

    def test_capacity_three_takes_three_then_full(self):
        Business.objects.filter(pk=self.business.pk).update(slot_capacity=3)
        self.business.refresh_from_db()
        for _ in range(3):
            self.assertEqual(self.slot_at(self.when)['status'], 'AVAILABLE')
            self.assertIsNone(self.book().failure)
        self.assertEqual(self.slot_at(self.when)['status'], 'BOOKED')
        self.assertEqual(self.book().failure, BookingFailure.CONFLICT)
        # The next slot is untouched.
        self.assertIsNone(self.book(self.when + timedelta(minutes=30)).failure)

    def test_expired_hold_frees_its_place(self):
        Business.objects.filter(pk=self.business.pk).update(slot_capacity=2)
        self.business.refresh_from_db()
        held = self.book(requires_payment=True).appointment
        self.assertIsNone(self.book().failure)
        self.assertEqual(self.book().failure, BookingFailure.CONFLICT)
        Appointment.objects.filter(pk=held.pk).update(expires_at=timezone.now() - timedelta(seconds=1))
        self.assertIsNone(find_conflict(self.business.id, self.when, 30))

    def test_public_slots_send_only_full_stretches(self):
        Business.objects.filter(pk=self.business.pk).update(slot_capacity=2)
        self.business.refresh_from_db()
        url = reverse('client-public-slots', args=[self.business.id])
        date = self.when.date().isoformat()
        self.book()
        cache.clear()
        self.assertEqual(self.client.get(url, {'date': date}).json()['occupied_slots'], [])
        self.book()
        cache.clear()
        occupied = self.client.get(url, {'date': date}).json()['occupied_slots']
        self.assertEqual(len(occupied), 1)
        self.assertEqual(occupied[0]['status'], 'FULL')


class HoldMinutesTests(Base):
    def test_default_hold_is_five_minutes(self):
        before = timezone.now()
        held = self.book(requires_payment=True).appointment
        self.assertEqual(held.status, 'LOCKED')
        self.assertAlmostEqual((held.expires_at - before).total_seconds(), 5 * 60, delta=5)

    def test_owner_hold_is_used(self):
        Business.objects.filter(pk=self.business.pk).update(hold_minutes=2)
        self.business.refresh_from_db()
        before = timezone.now()
        held = self.book(requires_payment=True).appointment
        self.assertAlmostEqual((held.expires_at - before).total_seconds(), 2 * 60, delta=5)

    def test_gateway_stretches_a_short_hold(self):
        Business.objects.filter(pk=self.business.pk).update(hold_minutes=2)
        self.business.refresh_from_db()
        held = self.book(requires_payment=True).appointment
        before = timezone.now()
        self.assertTrue(appointment_store_gateway_track_id(appointment=held, track_id='123'))
        held.refresh_from_db()
        self.assertAlmostEqual((held.expires_at - before).total_seconds(), GATEWAY_HOLD_MINUTES * 60, delta=5)
        self.assertEqual(held.payment_reference, '123')

    def test_gateway_never_shortens_a_long_hold(self):
        Business.objects.filter(pk=self.business.pk).update(hold_minutes=15)
        self.business.refresh_from_db()
        held = self.book(requires_payment=True).appointment
        expires = held.expires_at
        appointment_store_gateway_track_id(appointment=held, track_id='9')
        held.refresh_from_db()
        self.assertEqual(held.expires_at, expires)


class SettingsValidationTests(Base):
    def check(self, **data):
        return BusinessSerializer(self.business, data=data, partial=True).is_valid()

    def test_ranges(self):
        self.assertTrue(self.check(slot_capacity=1))
        self.assertTrue(self.check(slot_capacity=500))
        self.assertFalse(self.check(slot_capacity=0))
        self.assertFalse(self.check(slot_capacity=501))
        self.assertTrue(self.check(hold_minutes=2))
        self.assertTrue(self.check(hold_minutes=15))
        self.assertFalse(self.check(hold_minutes=1))
        self.assertFalse(self.check(hold_minutes=16))

    def test_more_than_one_per_slot_is_the_capacity_feature(self):
        from accounting import entitlements
        from accounting.permissions import _gated_features
        feats = lambda d: _gated_features(d.get, lambda k: k in d)  # noqa: E731
        self.assertIn(entitlements.FEATURE_CAPACITY_CONTROL, feats({'slot_capacity': 3}))
        self.assertNotIn(entitlements.FEATURE_CAPACITY_CONTROL, feats({'slot_capacity': 1}))
        self.assertNotIn(entitlements.FEATURE_CAPACITY_CONTROL, feats({'hold_minutes': 10}))


class OwnerEndpointTests(Base):
    def setUp(self):
        super().setUp()
        from rest_framework.test import APIClient
        self.api = APIClient()
        self.api.force_authenticate(self.owner)
        self.url = reverse('business-booking-capacity', args=[self.business.id])

    def test_get_defaults_and_bounds(self):
        data = self.api.get(self.url).json()['data']
        self.assertEqual((data['slot_capacity'], data['hold_minutes']), (1, 5))
        self.assertEqual((data['max_slot_capacity'], data['min_hold_minutes'], data['max_hold_minutes']), (500, 2, 15))

    def test_hold_is_free_to_set_and_bounded(self):
        self.assertEqual(self.api.put(self.url, {'hold_minutes': 12}, format='json').status_code, 200)
        self.business.refresh_from_db()
        self.assertEqual(self.business.hold_minutes, 12)
        self.assertEqual(self.api.put(self.url, {'hold_minutes': 1}, format='json').status_code, 400)
        self.assertEqual(self.api.put(self.url, {'hold_minutes': 16}, format='json').status_code, 400)
        self.assertEqual(self.api.put(self.url, {'slot_capacity': 501}, format='json').status_code, 400)
        self.assertEqual(self.api.put(self.url, {}, format='json').status_code, 400)

    def test_more_than_one_needs_the_plan_feature(self):
        r = self.api.put(self.url, {'slot_capacity': 3}, format='json')
        self.assertEqual(r.status_code, 403)
        self.business.refresh_from_db()
        self.assertEqual(self.business.slot_capacity, 1)
        with patch('accounting.permissions.validate_business_settings', return_value=None):
            self.assertEqual(self.api.put(self.url, {'slot_capacity': 3}, format='json').status_code, 200)
        self.business.refresh_from_db()
        self.assertEqual(self.business.slot_capacity, 3)

    def test_other_owner_gets_404(self):
        from rest_framework.test import APIClient
        other = User.objects.create_user(phone='09120000772', password='x', name='o2')
        api = APIClient()
        api.force_authenticate(other)
        self.assertEqual(api.get(self.url).status_code, 404)
        self.assertEqual(api.put(self.url, {'hold_minutes': 10}, format='json').status_code, 404)
