"""Tests for the DB-authoritative CreditBalance (accounting/usage.py, Phase 2)."""

import threading
from io import StringIO
from unittest import skipUnless

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.core.management import call_command
from django.db import connection
from django.test import TestCase, TransactionTestCase

from accounting import entitlements, usage
from accounting.models import CreditBalance, CreditLedger
from accounting.tests_ledger import make_plan, make_user, subscribe

User = get_user_model()


class Base(TestCase):
    def setUp(self):
        cache.clear()
        self.addCleanup(cache.clear)


class AtomicSpendTests(Base):
    def test_wallet_second_spend_fails_when_balance_is_one(self):
        u = make_user('09130000001')
        usage.add_appt_wallet(u.id, 1)
        self.assertTrue(usage._spend_wallet(u.id, 'appointment_wallet', 1, 'booking'))
        self.assertFalse(usage._spend_wallet(u.id, 'appointment_wallet', 1, 'booking'))
        self.assertEqual(usage.get_appt_wallet(u.id), 0)

    def test_record_appointment_only_allowed_number_even_if_all_passed_can_book(self):
        u = make_user('09130000002')
        subscribe(u, make_plan(monthly_appointments=1))
        usage.add_appt_wallet(u.id, 1)
        self.assertTrue(usage.can_book_appointment(u.id))
        results = [usage.record_appointment(u.id) for _ in range(3)]
        self.assertEqual(results, [usage.SOURCE_MONTHLY, usage.SOURCE_WALLET, ''])
        self.assertEqual(usage.get_usage(u.id, usage.METRIC_APPOINTMENTS), 1)
        self.assertEqual(usage.get_appt_wallet(u.id), 0)

    def test_sms_quota_cap_respected_and_all_or_nothing(self):
        u = make_user('09130000003')
        subscribe(u, make_plan(monthly_sms=2))
        usage.add_wallet(u.id, 1)
        r = usage.consume_sms(u.id, 2)
        self.assertEqual((r['monthly'], r['wallet']), (2, 0))
        # quota full; 2 wanted, wallet has 1 -> nothing consumed
        self.assertIsNone(usage.consume_sms(u.id, 2))
        self.assertEqual(usage.get_wallet(u.id), 1)
        self.assertEqual(usage.get_usage(u.id, usage.METRIC_SMS), 2)
        self.assertEqual(usage.consume_sms(u.id, 1)['wallet'], 1)
        self.assertIsNone(usage.consume_sms(u.id, 1))

    def test_partial_monthly_plus_wallet_and_rollback_on_shortfall(self):
        u = make_user('09130000004')
        subscribe(u, make_plan(monthly_sms=3))
        usage.consume_sms(u.id, 2)           # 1 monthly left
        self.assertIsNone(usage.consume_sms(u.id, 3))   # needs 2 from empty wallet
        self.assertEqual(usage.get_usage(u.id, usage.METRIC_SMS), 2)  # rolled back
        usage.add_wallet(u.id, 2)
        r = usage.consume_sms(u.id, 3)
        self.assertEqual((r['monthly'], r['wallet']), (1, 2))
        self.assertTrue(usage.refund_sms(r))
        self.assertEqual(usage.get_wallet(u.id), 2)
        self.assertEqual(usage.get_usage(u.id, usage.METRIC_SMS), 2)


class DurabilityTests(Base):
    def test_topup_survives_cache_clear(self):
        u = make_user('09130000010')
        usage.add_wallet(u.id, 7)
        usage.add_appt_wallet(u.id, 3)
        cache.clear()
        self.assertEqual(usage.get_wallet(u.id), 7)
        self.assertEqual(usage.get_appt_wallet(u.id), 3)
        self.assertTrue(CreditLedger.objects.filter(user_id=u.id, metric='sms_wallet').exists())

    def test_lazy_init_from_redis_value(self):
        u = make_user('09130000011')
        cache.set(usage._wallet_key(u.id), 12, timeout=None)
        cache.set(usage._month_key(u.id, usage.METRIC_SMS), 4, timeout=None)
        self.assertEqual(usage.get_wallet(u.id), 12)      # read, no row yet
        self.assertFalse(CreditBalance.objects.filter(user_id=u.id).exists())
        self.assertEqual(usage.add_wallet(u.id, 1), 13)   # seeds from Redis then adds
        self.assertEqual(usage.add_usage(u.id, usage.METRIC_SMS), 5)
        cache.clear()
        self.assertEqual(usage.get_wallet(u.id), 13)

    def test_lazy_init_falls_back_to_ledger(self):
        u = make_user('09130000012')
        CreditLedger.objects.create(user_id=u.id, metric='appointment_wallet', delta=5,
                                    balance_after=5, reason='wallet_credit')
        self.assertEqual(usage.get_appt_wallet(u.id), 5)
        self.assertEqual(usage.add_appt_wallet(u.id, 1), 6)

    def test_ledger_failure_logged_at_error_but_balance_changes(self):
        from unittest.mock import patch
        u = make_user('09130000013')
        with patch('accounting.models.CreditLedger.objects.create', side_effect=RuntimeError('x')):
            with self.assertLogs('accounting.usage', level='ERROR'):
                usage.add_wallet(u.id, 4)
        self.assertEqual(usage.get_wallet(u.id), 4)

    def test_rollback_of_outer_transaction_returns_credit(self):
        from django.db import transaction
        u = make_user('09130000014')
        usage.add_appt_wallet(u.id, 1)
        try:
            with transaction.atomic():
                self.assertEqual(usage.record_appointment(u.id), usage.SOURCE_WALLET)
                raise RuntimeError
        except RuntimeError:
            pass
        self.assertEqual(usage.get_appt_wallet(u.id), 1)


class BackfillCommandTests(Base):
    def _run(self, *a):
        out = StringIO()
        call_command('migrate_credits_to_db', *a, stdout=out)
        return out.getvalue()

    def test_idempotent_dry_run_and_no_stale_overwrite(self):
        u = make_user('09130000020')
        cache.set(usage._wallet_key(u.id), 9, timeout=None)
        cache.set(usage._month_key(u.id, usage.METRIC_APPOINTMENTS), 2, timeout=None)
        self._run('--dry-run')
        self.assertEqual(CreditBalance.objects.count(), 0)
        self._run()
        self.assertEqual(CreditBalance.objects.get(user=u, metric='sms_wallet').balance, 9)
        self.assertEqual(CreditBalance.objects.get(user=u, metric='appointment_monthly',
                                                   period=usage._period()).balance, 2)
        n = CreditBalance.objects.count()
        usage.add_wallet(u.id, 1)          # DB now ahead of stale Redis
        self._run()
        self.assertEqual(CreditBalance.objects.count(), n)
        self.assertEqual(usage.get_wallet(u.id), 10)
        self._run('--overwrite')           # explicit force: DB := Redis (stale 9 in tests)
        self.assertEqual(usage.get_wallet(u.id), 9)

    def test_ledger_fallback(self):
        u = make_user('09130000021')
        CreditLedger.objects.create(user_id=u.id, metric='sms_wallet', delta=8,
                                    balance_after=8, reason='wallet_credit')
        self._run()
        self.assertEqual(CreditBalance.objects.get(user=u, metric='sms_wallet').balance, 8)


@skipUnless(connection.vendor in ('postgresql', 'mysql'), 'needs row locks')
class ConcurrencyTests(TransactionTestCase):
    def test_parallel_record_appointment_respects_limits(self):
        u = make_user('09130000030')
        subscribe(u, make_plan(monthly_appointments=2))
        usage.add_appt_wallet(u.id, 3)
        results, lock = [], threading.Lock()

        def work():
            try:
                r = usage.record_appointment(u.id)
            finally:
                connection.close()
            with lock:
                results.append(r)

        threads = [threading.Thread(target=work) for _ in range(12)]
        [t.start() for t in threads]
        [t.join() for t in threads]
        self.assertEqual(results.count(usage.SOURCE_MONTHLY), 2)
        self.assertEqual(results.count(usage.SOURCE_WALLET), 3)
        self.assertEqual(results.count(''), 7)
        self.assertEqual(usage.get_appt_wallet(u.id), 0)
        self.assertEqual(usage.get_usage(u.id, usage.METRIC_APPOINTMENTS), 2)

    def test_parallel_consume_sms_never_overdraws(self):
        u = make_user('09130000031')
        subscribe(u, make_plan(monthly_sms=1))
        usage.add_wallet(u.id, 2)
        results, lock = [], threading.Lock()

        def work():
            try:
                r = usage.consume_sms(u.id)
            finally:
                connection.close()
            with lock:
                results.append(r)

        threads = [threading.Thread(target=work) for _ in range(10)]
        [t.start() for t in threads]
        [t.join() for t in threads]
        self.assertEqual(sum(1 for r in results if r), 3)
        self.assertEqual(usage.get_wallet(u.id), 0)
