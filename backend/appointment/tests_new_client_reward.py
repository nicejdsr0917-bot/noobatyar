"""The owner's toman wallet earns TomanWalletSettings.new_client_reward when a
client's first self-made booking at the business is marked COMPLETED."""
from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone

from accounting import toman_wallet
from accounting.models import CreditLedger, TomanWalletSettings
from business.models import Business
from visitor.models import Visitor

from .models import Appointment
from .services.transitions import appointment_change_status_by_owner

User = get_user_model()


class NewClientRewardTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000500', password='x', name='مالک')
        self.business = self._business(self.owner)
        self.visitor = Visitor.objects.create(full_name='مشتری', phone_number='09390000500')
        s = TomanWalletSettings.load()
        s.new_client_reward = 20_000
        s.save()

    def _business(self, owner):
        return Business.objects.create(
            user=owner, title='کسب‌وکار', phone='02100000000', address='تهران', default_service_duration=30,
            work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )

    def _book(self, visitor=None, source='APP', business=None, status='WAITING', hours=1):
        return Appointment.objects.create(
            business=business or self.business, visitor=visitor or self.visitor,
            source=source, status=status,
            appointment_date=timezone.now() + timedelta(hours=hours),
        )

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
        me = Visitor.objects.create(full_name='خودم', phone_number='+989120000500')
        self._complete(self._book(visitor=me))
        self.assertEqual(self.balance(), 0)

    def test_monthly_cap_gives_the_remainder_then_stops(self):
        TomanWalletSettings.objects.update(new_client_reward_monthly_cap=50_000)
        for i in range(4):
            v = Visitor.objects.create(full_name=f'م{i}', phone_number=f'0939000060{i}')
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
