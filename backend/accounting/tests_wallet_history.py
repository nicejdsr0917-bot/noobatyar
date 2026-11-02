# accounting/tests_wallet_history.py
"""GET /api/accounting/wallet/history/ — the owner's add-on credit balances and history."""

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from rest_framework.test import APIClient

from accounting import entitlements, usage
from accounting.models import CreditLedger, Plan, Subscription

User = get_user_model()
URL = '/api/accounting/wallet/history/'


def make_user(phone):
    return User.objects.create_user(phone=phone, name='کاربر')


def subscribe(user, monthly_appointments=1, monthly_sms=1):
    from datetime import timedelta
    from django.utils import timezone
    plan = Plan.objects.create(
        name='پلن تست', price=10000, description=[], duration_value=1, duration_unit='month',
        features={
            entitlements.QUOTA_MAX_BUSINESSES: 1,
            entitlements.QUOTA_MONTHLY_APPOINTMENTS: monthly_appointments,
            entitlements.QUOTA_MONTHLY_SMS: monthly_sms,
        },
    )
    Subscription.objects.create(user=user, plan=plan, status='active', ends_at=timezone.now() + timedelta(days=30))


class WalletHistoryTests(TestCase):
    def setUp(self):
        cache.clear()
        self.addCleanup(cache.clear)
        self.user = make_user('09120001001')
        self.client = APIClient()
        self.client.force_authenticate(self.user)

    def get(self, **params):
        r = self.client.get(URL, params)
        self.assertEqual(r.status_code, 200, r.content)
        return r.json()['data']

    def test_requires_authentication(self):
        r = APIClient().get(URL)
        self.assertIn(r.status_code, (401, 403))

    def test_lists_only_wallet_rows_newest_first_with_labels(self):
        subscribe(self.user, monthly_appointments=1)
        usage.add_appt_wallet(self.user, 10, reason=usage.REASON_ADDON_PURCHASE, ref_type='AddOnPurchase', ref_id=1)
        usage.record_appointment(self.user)   # monthly allowance: monthly row only
        usage.record_appointment(self.user)   # allowance spent → wallet
        usage.add_wallet(self.user, 50, reason=usage.REASON_ADDON_PURCHASE)

        data = self.get()
        rows = data['results']
        self.assertEqual(data['count'], 3)
        self.assertEqual([r['metric'] for r in rows], ['sms_wallet', 'appointment_wallet', 'appointment_wallet'])
        self.assertEqual(rows[0]['delta'], 50)
        self.assertEqual(rows[0]['reason_label'], 'خرید بسته افزودنی')
        self.assertEqual(rows[1]['delta'], -1)
        self.assertEqual(rows[1]['balance_after'], 9)
        self.assertEqual(rows[1]['reason'], 'booking')
        self.assertEqual(rows[1]['reason_label'], 'ثبت نوبت')
        self.assertIn('created_at', rows[0])
        self.assertEqual(data['balances']['appointment_wallet'], 9)
        self.assertEqual(data['balances']['sms_wallet'], 50)
        self.assertEqual(data['balances']['appointment_quota'], 1)

    def test_filter_by_metric(self):
        usage.add_appt_wallet(self.user, 3)
        usage.add_wallet(self.user, 4)
        rows = self.get(metric='sms_wallet')['results']
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['metric'], 'sms_wallet')

    def test_rejects_monthly_metric_filter(self):
        r = self.client.get(URL, {'metric': 'sms_monthly'})
        self.assertEqual(r.status_code, 400)

    def test_never_shows_other_users_rows(self):
        other = make_user('09120001002')
        usage.add_wallet(other, 99)
        data = self.get()
        self.assertEqual(data['count'], 0)
        self.assertEqual(data['balances']['sms_wallet'], 0)

    def test_paginates(self):
        for _ in range(12):
            usage.add_wallet(self.user, 1)
        first = self.get(page_size=5)
        self.assertEqual(first['count'], 12)
        self.assertEqual(first['total_pages'], 3)
        self.assertEqual(len(first['results']), 5)
        self.assertEqual(first['results'][0]['balance_after'], 12)
        last = self.get(page_size=5, page=3)
        self.assertEqual(len(last['results']), 2)
        self.assertIsNone(last['next'])

    def test_unknown_reason_falls_back_to_generic_label(self):
        CreditLedger.objects.create(user=self.user, metric='sms_wallet', delta=-2, balance_after=0, reason='mystery')
        self.assertEqual(self.get()['results'][0]['reason_label'], 'کسر اعتبار')
