"""The owner's toman wallet earns TomanWalletSettings.new_client_reward when a
client's first self-made booking at the business is marked COMPLETED."""
from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.utils import timezone

from accounting import toman_wallet
from accounting.models import CreditLedger, NewClientReward, TomanWalletSettings
from api.authentication import record_ip_sighting
from api.models import UserIpSighting
from business.models import Business
from visitor.models import Visitor

from .models import Appointment
from .services.transitions import appointment_change_status_by_owner
from .tests_client_booking_rules import _Fixture

User = get_user_model()


class NewClientRewardTests(TestCase):
    def setUp(self):
        cache.clear()  # IP sightings are rate-limited through the cache
        self.owner = User.objects.create_user(phone='09120000500', password='x', name='مالک')
        self.business = self._business(self.owner)
        self.visitor = self._visitor('مشتری', '09390000500')
        s = TomanWalletSettings.load()
        s.new_client_reward = 20_000
        s.save()

    def _business(self, owner):
        return Business.objects.create(
            user=owner, title='کسب‌وکار', phone='02100000000', address='تهران', default_service_duration=30,
            work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )

    def _visitor(self, name, phone, age=timedelta(days=30)):
        """A client account that is not brand new (see the new_account flag)."""
        v = Visitor.objects.create(full_name=name, phone_number=phone)
        Visitor.objects.filter(pk=v.pk).update(created_at=timezone.now() - age)
        v.refresh_from_db()
        return v

    def _book(self, visitor=None, source='APP', business=None, status='WAITING', hours=1,
              booked_ago=timedelta(days=2), ip=None):
        """A booking made ``booked_ago`` (not just now: see quick_complete)."""
        a = Appointment.objects.create(
            business=business or self.business, visitor=visitor or self.visitor,
            source=source, status=status,
            appointment_date=timezone.now() + timedelta(hours=hours),
        )
        Appointment.objects.filter(pk=a.pk).update(created_at=timezone.now() - booked_ago, booking_ip=ip)
        a.refresh_from_db()
        return a

    def _complete(self, appointment):
        appointment_change_status_by_owner(appointment=appointment, new_status='COMPLETED')

    def balance(self):
        return toman_wallet.get_balance(self.owner)

    def test_first_completed_app_booking_rewards_owner(self):
        self._complete(self._book())
        self.assertEqual(self.balance(), 20_000)
        row = CreditLedger.objects.get(user=self.owner, metric='toman_wallet')
        self.assertEqual((row.reason, row.ref_type, row.ref_id),
                         ('new_client_reward', f'business:{self.business.pk}', self.visitor.pk))

    def test_web_booking_counts(self):
        self._complete(self._book(source='WEB'))
        self.assertEqual(self.balance(), 20_000)

    def test_owner_entered_and_unknown_bookings_do_not(self):
        self._complete(self._book(source='OWNER'))
        self._complete(self._book(source='UNKNOWN', hours=2))
        self.assertEqual(self.balance(), 0)

    def test_off_when_amount_is_zero(self):
        TomanWalletSettings.objects.update(new_client_reward=0)
        self._complete(self._book())
        self.assertEqual(self.balance(), 0)

    def test_only_on_completed(self):
        for status in ('CONFIRMED', 'NO_SHOW'):
            appointment_change_status_by_owner(appointment=self._book(), new_status=status)
        appointment_change_status_by_owner(appointment=self._book(hours=3), new_status='CANCELLED')
        self.assertEqual(self.balance(), 0)

    def test_returning_client_earns_nothing(self):
        self._book(source='OWNER', status='COMPLETED', hours=-48)
        self._complete(self._book())
        self.assertEqual(self.balance(), 0)

    def test_once_per_visitor_per_business_even_after_delete(self):
        first = self._book()
        self._complete(first)
        first.delete()
        self._complete(self._book(hours=2))
        self.assertEqual(self.balance(), 20_000)

    def test_same_visitor_at_another_business_of_the_owner_counts(self):
        other = self._business(self.owner)
        self._complete(self._book())
        self._complete(self._book(business=other))
        self.assertEqual(self.balance(), 40_000)

    def test_owner_booking_themself_earns_nothing(self):
        me = self._visitor('خودم', '+989120000500')
        self._complete(self._book(visitor=me))
        self.assertEqual(self.balance(), 0)

    def test_monthly_cap_gives_the_remainder_then_stops(self):
        TomanWalletSettings.objects.update(new_client_reward_monthly_cap=50_000)
        for i in range(4):
            v = self._visitor(f'م{i}', f'0939000060{i}')
            self._complete(self._book(visitor=v, hours=i + 1))
        self.assertEqual(self.balance(), 50_000)
        deltas = list(CreditLedger.objects.filter(user=self.owner, metric='toman_wallet')
                      .order_by('id').values_list('delta', flat=True))
        self.assertEqual(deltas, [20_000, 20_000, 10_000])

    def test_history_label(self):
        self._complete(self._book())
        from accounting.serializers import WalletLedgerEntrySerializer
        row = CreditLedger.objects.get(user=self.owner, metric='toman_wallet')
        self.assertEqual(WalletLedgerEntrySerializer(row).data['reason_label'], 'پاداش مشتری تازه')

    def test_reward_failure_still_completes_the_booking(self):
        a = self._book()
        with patch.object(toman_wallet, 'credit', side_effect=RuntimeError('boom')):
            self._complete(a)
        a.refresh_from_db()
        self.assertEqual(a.status, 'COMPLETED')
        self.assertEqual(self.balance(), 0)

    def test_business_cap_is_a_lifetime_total_per_business(self):
        TomanWalletSettings.objects.update(new_client_reward_business_cap=50_000)
        for i in range(4):
            v = self._visitor(f'ک{i}', f'0939000070{i}')
            self._complete(self._book(visitor=v, hours=i + 1))
        self.assertEqual(self.balance(), 50_000)
        # Another business of the same owner has its own cap.
        other = self._business(self.owner)
        self._complete(self._book(business=other, hours=9))
        self.assertEqual(self.balance(), 70_000)

    def test_default_business_cap_is_500k(self):
        self.assertEqual(TomanWalletSettings.load().new_client_reward_business_cap, 500_000)


class RiskFlagTests(NewClientRewardTests):
    """Bookings that look like the owner made them are logged, not paid."""

    def _reward(self):
        return NewClientReward.objects.get(business=self.business, visitor=self.visitor)

    def test_clean_booking_is_paid_and_logged(self):
        self._complete(self._book(ip='5.5.5.5'))
        self.assertEqual(self.balance(), 20_000)
        r = self._reward()
        self.assertEqual((r.status, r.amount, r.flags), ('granted', 20_000, []))

    def test_booked_from_the_owners_ip_is_blocked(self):
        record_ip_sighting(self.owner.pk, '5.5.5.5')
        self._complete(self._book(ip='5.5.5.5'))
        self.assertEqual(self.balance(), 0)
        r = self._reward()
        self.assertEqual((r.status, r.amount, r.flags), ('blocked', 20_000, ['same_ip']))

    def test_an_old_owner_ip_does_not_count(self):
        record_ip_sighting(self.owner.pk, '5.5.5.5')
        UserIpSighting.objects.update(last_seen=timezone.now() - timedelta(days=60))
        self._complete(self._book(ip='5.5.5.5'))
        self.assertEqual(self.balance(), 20_000)

    def test_one_weak_flag_alone_still_pays(self):
        self._complete(self._book(booked_ago=timedelta(minutes=5)))
        self.assertEqual(self.balance(), 20_000)
        self.assertEqual(self._reward().flags, ['quick_complete'])

    def test_brand_new_account_completed_at_once_is_blocked(self):
        fresh = self._visitor('تازه', '09390000555', age=timedelta(minutes=10))
        self._complete(self._book(visitor=fresh, booked_ago=timedelta(minutes=5)))
        self.assertEqual(self.balance(), 0)
        r = NewClientReward.objects.get(visitor=fresh)
        self.assertEqual((r.status, sorted(r.flags)), ('blocked', ['new_account', 'quick_complete']))

    def test_new_account_that_booked_elsewhere_is_not_flagged(self):
        fresh = self._visitor('تازه', '09390000556', age=timedelta(hours=1))
        other_owner = User.objects.create_user(phone='09120000599', password='x', name='دیگری')
        self._book(visitor=fresh, business=self._business(other_owner))
        self._complete(self._book(visitor=fresh, booked_ago=timedelta(minutes=5)))
        self.assertEqual(NewClientReward.objects.get(visitor=fresh, business=self.business).flags,
                         ['quick_complete'])

    def test_burst_plus_another_flag_blocks(self):
        for i in range(5):
            self._complete(self._book(visitor=self._visitor(f'ب{i}', f'0939000080{i}'), hours=i + 1))
        self.assertEqual(self.balance(), 100_000)
        v = self._visitor('ششم', '09390000810')
        self._complete(self._book(visitor=v, hours=9, booked_ago=timedelta(minutes=5)))
        self.assertEqual(self.balance(), 100_000)
        self.assertEqual(sorted(NewClientReward.objects.get(visitor=v).flags), ['burst', 'quick_complete'])

    def test_blocked_visitor_cannot_retry_with_a_new_booking(self):
        record_ip_sighting(self.owner.pk, '5.5.5.5')
        self._complete(self._book(ip='5.5.5.5'))
        self._complete(self._book(hours=3, ip='6.6.6.6'))
        self.assertEqual(self.balance(), 0)

    def test_staff_can_pay_a_blocked_reward_once(self):
        from accounting.toman_wallet import grant_blocked_reward
        record_ip_sighting(self.owner.pk, '5.5.5.5')
        self._complete(self._book(ip='5.5.5.5'))
        r = self._reward()
        self.assertEqual(grant_blocked_reward(r, 'admin'), 20_000)
        self.assertEqual(grant_blocked_reward(r, 'admin'), 0)
        self.assertEqual(self.balance(), 20_000)
        r.refresh_from_db()
        self.assertEqual((r.status, r.decided_by), ('granted', 'admin'))

    def test_capped_rewards_are_logged(self):
        TomanWalletSettings.objects.update(new_client_reward_business_cap=20_000)
        self._complete(self._book())
        v = self._visitor('دوم', '09390000820')
        self._complete(self._book(visitor=v, hours=2))
        self.assertEqual(NewClientReward.objects.get(visitor=v).status, 'capped')


class BookingIpTests(_Fixture):
    def test_client_booking_stores_the_request_ip(self):
        self.assertEqual(self.book().status_code, 200)
        self.assertEqual(Appointment.objects.get().booking_ip, '127.0.0.1')


class OwnerIpSightingTests(TestCase):
    def setUp(self):
        cache.clear()

    def test_owner_requests_record_their_ip_once_an_hour(self):
        from rest_framework.test import APIClient
        from rest_framework_simplejwt.tokens import RefreshToken
        owner = User.objects.create_user(phone='09120000700', password='x', name='م')
        api = APIClient(REMOTE_ADDR='7.7.7.7')
        api.credentials(HTTP_AUTHORIZATION=f'Bearer {RefreshToken.for_user(owner).access_token}')
        api.get('/api/accounting/my-subscription/')
        api.get('/api/accounting/my-subscription/')
        self.assertEqual(list(UserIpSighting.objects.values_list('user_id', 'ip')), [(owner.pk, '7.7.7.7')])
