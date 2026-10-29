"""Zibal verification is idempotent and crash-safe for plans and add-ons."""
import threading
from unittest import skipUnless
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.db import connection
from django.test import TestCase, TransactionTestCase

from accounting import usage
from accounting.models import AddOnPack, AddOnPurchase, Plan, Subscription, StorePurchase, Transaction
from accounting.payment import myket, zibal_client
from accounting.payment.addon_payment import AddOnVerificationService, grant_addon_benefit
from accounting.payment.zibal_payment_verification import PaymentVerificationService

User = get_user_model()
Outcome = zibal_client.Outcome


def result(outcome, code=None, data=None):
    # amount: Zibal reports Rial; every fixture row below costs 1000 Toman.
    return zibal_client.VerifyResult(
        outcome, code, "m", data or {"result": code, "refNumber": "R1", "amount": 10000})


OK = result(Outcome.SUCCESS, 100)
DUP = result(Outcome.ALREADY_VERIFIED, 201)
NOPAY = result(Outcome.FAILED, 202)
TIMEOUT = result(Outcome.TRANSPORT_ERROR, None, {"error": "timeout"})


def patch_verify(*results):
    return patch.object(zibal_client, "verify", side_effect=list(results))


class Base:
    def setup_data(self, phone):
        self.user = User.objects.create_user(phone=phone, password="x", name="u")
        Plan.objects.get_or_create(id=1, defaults=dict(name="آزمایشی", price=0, duration_value=7, duration_unit="day"))
        self.plan = Plan.objects.create(name="پرو", price=1000, duration_value=1, duration_unit="month")
        self.txn = Transaction.objects.create(user=self.user, plan=self.plan, amount=1000,
                                              track_id=phone, order_id="o" + phone, status="pending")
        self.pack = AddOnPack.objects.create(name="sms", price=1000, kind=AddOnPack.KIND_SMS, sms_amount=50)
        self.purchase = AddOnPurchase.objects.create(user=self.user, pack=self.pack, amount=1000,
                                                     track_id="a" + phone, order_id="ao" + phone, status="pending")

    def subs(self):
        return Subscription.objects.filter(user=self.user, plan=self.plan).count()


class PlanVerificationTests(Base, TestCase):
    def setUp(self):
        self.setup_data("09120000101")

    def run_cb(self):
        return PaymentVerificationService(self.txn.track_id).verify_and_activate()

    def test_replay_returns_success_and_grants_once(self):
        with patch_verify(OK) as v:
            first = self.run_cb()
            second = self.run_cb()
        self.assertTrue(first["success"])
        self.assertTrue(second["success"])
        self.assertEqual(second["status_code"], first["status_code"])
        self.assertEqual(second["data"]["subscription_id"], first["data"]["subscription_id"])
        self.assertEqual(v.call_count, 1)  # replay needs no second Zibal call
        self.assertEqual(self.subs(), 1)

    def test_201_already_verified_is_success(self):
        with patch_verify(DUP):
            r = self.run_cb()
        self.assertTrue(r["success"])
        self.txn.refresh_from_db()
        self.assertEqual(self.txn.status, "success")
        self.assertEqual(self.subs(), 1)

    def test_timeout_leaves_pending_and_retry_succeeds(self):
        with patch_verify(TIMEOUT, OK):
            r1 = self.run_cb()
            self.txn.refresh_from_db()
            self.assertFalse(r1["success"])
            self.assertEqual(r1["status_code"], 503)
            self.assertEqual(self.txn.status, "pending")
            r2 = self.run_cb()
        self.assertTrue(r2["success"])
        self.assertEqual(self.subs(), 1)

    def test_exception_leaves_pending(self):
        with patch.object(zibal_client, "verify", side_effect=RuntimeError("boom")):
            r = self.run_cb()
        self.assertEqual(r["status_code"], 500)
        self.txn.refresh_from_db()
        self.assertEqual(self.txn.status, "pending")

    def test_definitive_failure_marks_failed(self):
        with patch_verify(NOPAY):
            r = self.run_cb()
        self.assertFalse(r["success"])
        self.txn.refresh_from_db()
        self.assertEqual(self.txn.status, "failed")

    def test_failure_after_success_cannot_downgrade(self):
        with patch_verify(OK):
            self.run_cb()
        with patch_verify(NOPAY) as v:
            r = self.run_cb()
        self.assertTrue(r["success"])
        self.assertEqual(v.call_count, 0)
        self.txn.refresh_from_db()
        self.assertEqual(self.txn.status, "success")

    def test_failure_racing_a_success_cannot_downgrade(self):
        # Our copy was loaded as pending, but another callback granted meanwhile.
        def verify_then_other_wins(track_id):
            Transaction.objects.filter(pk=self.txn.pk).update(status="success")
            return NOPAY
        with patch.object(zibal_client, "verify", side_effect=verify_then_other_wins):
            self.run_cb()
        self.txn.refresh_from_db()
        self.assertEqual(self.txn.status, "success")

    def test_row_wrongly_failed_by_old_code_is_rescued(self):
        Transaction.objects.filter(pk=self.txn.pk).update(status="failed")
        with patch_verify(DUP):
            r = self.run_cb()
        self.assertTrue(r["success"])
        self.assertEqual(self.subs(), 1)

    def test_amount_mismatch_grants_nothing_and_stays_pending(self):
        wrong = result(Outcome.SUCCESS, 100, {"result": 100, "refNumber": "R1", "amount": 1000})
        with patch_verify(wrong), self.assertLogs("accounting.payment.zibal_payment_verification", "ERROR"):
            r = self.run_cb()
        self.assertFalse(r["success"])
        self.assertEqual(r["status_code"], 503)
        self.txn.refresh_from_db()
        self.assertEqual(self.txn.status, "pending")
        self.assertEqual(self.subs(), 0)

    def test_missing_amount_is_a_mismatch(self):
        nothing = result(Outcome.SUCCESS, 100, {"result": 100})
        with patch_verify(nothing), self.assertLogs("accounting.payment.zibal_payment_verification", "ERROR"):
            self.assertFalse(self.run_cb()["success"])
        self.assertEqual(self.subs(), 0)

    def test_unknown_track_id(self):
        self.assertEqual(PaymentVerificationService("nope").verify_and_activate()["status_code"], 404)


class AddOnVerificationTests(Base, TestCase):
    def setUp(self):
        self.setup_data("09120000102")
        usage.add_wallet(self.user.id, -usage.get_wallet(self.user.id))

    def run_cb(self):
        return AddOnVerificationService(self.purchase.track_id).verify_and_grant()

    def test_replay_returns_success_and_credits_once(self):
        with patch_verify(OK) as v:
            first = self.run_cb()
        with patch_verify(OK) as v2:
            second = self.run_cb()
        self.assertTrue(first["success"] and second["success"])
        self.assertEqual(second["data"], first["data"])
        self.assertEqual(v2.call_count, 0)
        self.assertEqual(usage.get_wallet(self.user.id), 50)

    def test_201_is_success(self):
        with patch_verify(DUP):
            r = self.run_cb()
        self.assertTrue(r["success"])
        self.assertEqual(usage.get_wallet(self.user.id), 50)

    def test_timeout_leaves_pending_then_retry_succeeds(self):
        with patch_verify(TIMEOUT, OK):
            r1 = self.run_cb()
            self.purchase.refresh_from_db()
            self.assertFalse(r1["success"])
            self.assertEqual(self.purchase.status, "pending")
            r2 = self.run_cb()
        self.assertTrue(r2["success"])
        self.assertEqual(usage.get_wallet(self.user.id), 50)

    def test_definitive_failure_and_no_downgrade(self):
        with patch_verify(NOPAY):
            self.assertFalse(self.run_cb()["success"])
        self.purchase.refresh_from_db()
        self.assertEqual(self.purchase.status, "failed")
        AddOnPurchase.objects.filter(pk=self.purchase.pk).update(status="success")
        with patch_verify(NOPAY):
            self.assertTrue(self.run_cb()["success"])
        self.purchase.refresh_from_db()
        self.assertEqual(self.purchase.status, "success")

    def test_amount_mismatch_grants_nothing_and_stays_pending(self):
        wrong = result(Outcome.SUCCESS, 100, {"result": 100, "refNumber": "R1", "amount": 1000})
        with patch_verify(wrong), self.assertLogs("accounting.payment.addon_payment", "ERROR"):
            r = self.run_cb()
        self.assertFalse(r["success"])
        self.purchase.refresh_from_db()
        self.assertEqual(self.purchase.status, "pending")
        self.assertEqual(usage.get_wallet(self.user.id), 0)

    def test_credit_applied_in_the_same_transaction(self):
        grant_addon_benefit(self.purchase)  # no on-commit callbacks involved
        self.assertEqual(usage.get_wallet(self.user.id), 50)

    def test_credit_rolls_back_with_the_transaction(self):
        class Boom(Exception):
            pass
        from django.db import transaction
        with self.assertRaises(Boom):
            with transaction.atomic():
                grant_addon_benefit(self.purchase)
                raise Boom()
        self.assertEqual(usage.get_wallet(self.user.id), 0)

    def test_failing_credit_leaves_purchase_retryable(self):
        with patch_verify(OK, OK), patch("accounting.usage.add_wallet", side_effect=RuntimeError("db")):
            r1 = self.run_cb()
        self.assertFalse(r1["success"])
        self.purchase.refresh_from_db()
        self.assertEqual(self.purchase.status, "pending")
        self.assertIsNone(self.purchase.activated_at)
        self.assertEqual(usage.get_wallet(self.user.id), 0)
        with patch_verify(OK):
            r2 = self.run_cb()
        self.assertTrue(r2["success"])
        self.assertEqual(usage.get_wallet(self.user.id), 50)


class MyketFailTests(Base, TestCase):
    def test_fail_cannot_downgrade_success(self):
        self.setup_data("09120000103")
        sp = StorePurchase.objects.create(user=self.user, kind=StorePurchase.KIND_PLAN, plan=self.plan,
                                          sku="x", payload="p1", status="success")
        stale = StorePurchase.objects.get(pk=sp.pk)
        stale.status = "pending"
        with self.assertRaises(myket.StoreError):
            myket._fail(stale, "nope")
        sp.refresh_from_db()
        self.assertEqual(sp.status, "success")

    def test_fail_marks_pending_failed(self):
        self.setup_data("09120000104")
        sp = StorePurchase.objects.create(user=self.user, kind=StorePurchase.KIND_PLAN, plan=self.plan,
                                          sku="x", payload="p2", status="pending")
        with self.assertRaises(myket.StoreError):
            myket._fail(sp, "nope")
        sp.refresh_from_db()
        self.assertEqual((sp.status, sp.error_detail), ("failed", "nope"))


@skipUnless(connection.vendor in ("postgresql", "mysql"), "needs row locks")
class ConcurrentCallbackTests(Base, TransactionTestCase):
    def test_two_concurrent_callbacks_grant_once(self):
        self.setup_data("09120000105")
        barrier = threading.Barrier(2)
        out = []

        def fake_verify(track_id):
            barrier.wait(timeout=10)
            return OK

        def worker():
            from django.db import connection as c
            try:
                out.append(PaymentVerificationService(self.txn.track_id).verify_and_activate())
            finally:
                c.close()

        with patch.object(zibal_client, "verify", side_effect=fake_verify):
            ts = [threading.Thread(target=worker) for _ in range(2)]
            [t.start() for t in ts]
            [t.join() for t in ts]
        self.assertTrue(all(r["success"] for r in out), out)
        self.assertEqual(self.subs(), 1)
