# accounting/tests_toman_wallet.py
"""The owner's toman wallet: balance/ledger, paying plans and packs with it
(fully or partly), giving the held part back, and converting it to credit."""

from datetime import timedelta
from io import StringIO
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from accounting import entitlements, pricing, toman_wallet, usage
from accounting.models import (
    AddOnPack, AddOnPurchase, CreditLedger, Plan, Subscription, TomanWalletSettings, Transaction,
)
from integrations import zibal

User = get_user_model()

PAID = zibal.VerifyResult(zibal.Outcome.SUCCESS, 100, "m", {"result": 100, "refNumber": "R1"})
NOPAY = zibal.VerifyResult(zibal.Outcome.FAILED, 202, "پرداخت نشد", {"result": 202})


def gateway_ok(track_id=777):
    return patch.object(zibal, "request_payment", return_value=zibal.RequestResult(
        success=True, track_id=track_id, payment_url=f"https://gateway/start/{track_id}"))


def gateway_down():
    return patch.object(zibal, "request_payment", return_value=zibal.RequestResult(
        success=False, transport_error=True))


class Base(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(phone="09120002001", password="x", name="مالک")
        Plan.objects.get_or_create(id=1, defaults=dict(name="آزمایشی", price=0, duration_value=7, duration_unit="day"))
        self.plan = Plan.objects.create(
            name="پرو", price=100_000, description=[], duration_value=1, duration_unit="month",
            features={entitlements.QUOTA_MAX_BUSINESSES: 1},
        )
        Subscription.objects.create(user=self.user, plan=self.plan, status="active",
                                    ends_at=timezone.now() + timedelta(days=30))
        self.pack = AddOnPack.objects.create(name="۵۰ پیامک", price=50_000, kind=AddOnPack.KIND_SMS, sms_amount=50)
        self.client = APIClient()
        self.client.force_authenticate(self.user)

    def balance(self):
        return toman_wallet.get_balance(self.user)


class BalanceTests(Base):
    def test_credit_and_spend_write_ledger_rows(self):
        self.assertEqual(toman_wallet.credit(self.user, 10_000, toman_wallet.REASON_DISCOUNT_REIMBURSEMENT,
                                             ref_type="Appointment", ref_id=9), 10_000)
        self.assertEqual(toman_wallet.spend(self.user, 4_000, toman_wallet.REASON_PLAN_PURCHASE), 6_000)
        rows = list(CreditLedger.objects.filter(user=self.user, metric="toman_wallet").order_by("id"))
        self.assertEqual([(r.delta, r.balance_after, r.reason) for r in rows],
                         [(10_000, 10_000, "discount_reimbursement"), (-4_000, 6_000, "plan_purchase")])

    def test_spend_never_overdraws(self):
        toman_wallet.credit(self.user, 3_000)
        self.assertIsNone(toman_wallet.spend(self.user, 5_000, toman_wallet.REASON_PLAN_PURCHASE))
        self.assertEqual(self.balance(), 3_000)

    def test_wallet_part_leaves_a_sellable_remainder_or_covers_everything(self):
        self.assertEqual(toman_wallet.wallet_part(50_000, 60_000), 50_000)
        self.assertEqual(toman_wallet.wallet_part(50_000, 10_000), 10_000)
        # 45k of 50k would leave 5k for the gateway: capped so 10k is left.
        self.assertEqual(toman_wallet.wallet_part(50_000, 45_000), 50_000 - pricing.MIN_SELLABLE_PRICE)
        self.assertEqual(toman_wallet.wallet_part(50_000, 0), 0)
        # Store prices are fixed per SKU: all or nothing.
        self.assertEqual(toman_wallet.wallet_part(50_000, 10_000, pricing.CHANNEL_MYKET), 0)
        self.assertEqual(toman_wallet.wallet_part(50_000, 50_000, pricing.CHANNEL_MYKET), 50_000)


class QuoteTests(Base):
    def test_quote_offers_the_wallet_and_takes_it_off_before_tax(self):
        toman_wallet.credit(self.user, 10_000)
        r = self.client.get("/api/accounting/checkout/quote/", {"kind": "addon", "id": self.pack.id})
        plain = r.json()["data"]
        self.assertEqual(plain["wallet_balance"], 10_000)
        self.assertEqual(plain["wallet_available"], 10_000)
        self.assertEqual(plain["wallet"], 0)

        r = self.client.get("/api/accounting/checkout/quote/",
                            {"kind": "addon", "id": self.pack.id, "use_wallet": "true"})
        used = r.json()["data"]
        self.assertEqual(used["base"], 50_000)
        self.assertEqual(used["wallet"], 10_000)
        self.assertEqual(used["total"], pricing.quote(40_000, "zibal")["total"])
        self.assertLess(used["total"], plain["total"])


class FullWalletPurchaseTests(Base):
    def test_pack_paid_entirely_from_wallet_skips_the_gateway(self):
        toman_wallet.credit(self.user, 60_000)
        with patch.object(zibal, "request_payment") as gateway:
            r = self.client.post("/api/accounting/addons/buy/", {"pack_id": self.pack.id, "use_wallet": True}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertTrue(r.json()["data"]["paid_with_wallet"])
        gateway.assert_not_called()
        self.assertEqual(self.balance(), 10_000)
        self.assertEqual(usage.get_wallet(self.user), 50)
        purchase = AddOnPurchase.objects.get(user=self.user)
        self.assertEqual((purchase.status, purchase.amount, purchase.wallet_amount), ("success", 0, 50_000))

    def test_plan_paid_entirely_from_wallet_activates_it(self):
        toman_wallet.credit(self.user, 100_000)
        with patch.object(zibal, "request_payment") as gateway:
            r = self.client.post("/api/accounting/plans/payment/", {"plan_id": self.plan.id, "use_wallet": True}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertTrue(r.json()["data"]["paid_with_wallet"])
        gateway.assert_not_called()
        self.assertEqual(self.balance(), 0)
        self.assertEqual(Transaction.objects.get(user=self.user).status, "success")
        self.assertEqual(Subscription.objects.filter(user=self.user, plan=self.plan).count(), 2)

    def test_without_use_wallet_the_balance_is_untouched(self):
        toman_wallet.credit(self.user, 60_000)
        with gateway_ok():
            r = self.client.post("/api/accounting/addons/buy/", {"pack_id": self.pack.id}, format="json")
        self.assertIn("payment_url", r.json()["data"])
        self.assertEqual(self.balance(), 60_000)
        self.assertEqual(AddOnPurchase.objects.get().wallet_amount, 0)


class PartialWalletPurchaseTests(Base):
    def buy(self):
        with gateway_ok(888) as gateway:
            r = self.client.post("/api/accounting/addons/buy/", {"pack_id": self.pack.id, "use_wallet": True}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        return gateway, AddOnPurchase.objects.get(user=self.user)

    def test_gateway_is_asked_only_for_the_rest(self):
        toman_wallet.credit(self.user, 10_000)
        gateway, purchase = self.buy()
        expected = pricing.quote(40_000, "zibal")["total"]
        self.assertEqual(gateway.call_args.args[1], expected * 10)  # rial
        self.assertEqual((purchase.track_id, purchase.amount, purchase.wallet_amount), ("888", expected, 10_000))
        self.assertEqual(self.balance(), 0)

    def test_paid_rest_grants_the_pack_and_keeps_the_wallet_part(self):
        from accounting.payment.addon_payment import AddOnVerificationService

        toman_wallet.credit(self.user, 10_000)
        _, purchase = self.buy()
        paid = zibal.VerifyResult(zibal.Outcome.SUCCESS, 100, "m", {"result": 100, "amount": purchase.amount * 10})
        with patch.object(zibal, "verify", return_value=paid):
            self.assertTrue(AddOnVerificationService("888").verify_and_grant()["success"])
        self.assertEqual(usage.get_wallet(self.user), 50)
        self.assertEqual(self.balance(), 0)

    def test_failed_payment_gives_the_wallet_part_back_once(self):
        from accounting.payment.addon_payment import AddOnVerificationService

        toman_wallet.credit(self.user, 10_000)
        self.buy()
        with patch.object(zibal, "verify", return_value=NOPAY):
            AddOnVerificationService("888").verify_and_grant()
            AddOnVerificationService("888").verify_and_grant()
        self.assertEqual(self.balance(), 10_000)
        self.assertEqual(AddOnPurchase.objects.get().status, "cancelled")
        self.assertEqual(usage.get_wallet(self.user), 0)

    def test_failed_plan_payment_gives_the_wallet_part_back(self):
        from accounting.payment.zibal_payment_verification import PaymentVerificationService

        toman_wallet.credit(self.user, 20_000)
        with gateway_ok(999):
            self.client.post("/api/accounting/plans/payment/", {"plan_id": self.plan.id, "use_wallet": True}, format="json")
        self.assertEqual(self.balance(), 0)
        with patch.object(zibal, "verify", return_value=NOPAY):
            PaymentVerificationService("999").verify_and_activate()
        self.assertEqual(self.balance(), 20_000)
        self.assertEqual(Transaction.objects.get(user=self.user).status, "cancelled")

    def test_unreachable_gateway_gives_it_back_at_once(self):
        toman_wallet.credit(self.user, 10_000)
        with gateway_down():
            r = self.client.post("/api/accounting/addons/buy/", {"pack_id": self.pack.id, "use_wallet": True}, format="json")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(self.balance(), 10_000)
        self.assertEqual(AddOnPurchase.objects.get().status, "cancelled")

    def test_abandoned_payment_is_released_by_the_lifecycle_job(self):
        toman_wallet.credit(self.user, 10_000)
        _, purchase = self.buy()
        AddOnPurchase.objects.filter(pk=purchase.pk).update(created_at=timezone.now() - timedelta(days=2))
        call_command("run_subscription_lifecycle", stdout=StringIO())
        self.assertEqual(self.balance(), 10_000)
        self.assertEqual(AddOnPurchase.objects.get().status, "cancelled")
        # A fresh pending one is left alone.
        toman_wallet.credit(self.user, 10_000)
        with gateway_ok(889):
            self.client.post("/api/accounting/addons/buy/", {"pack_id": self.pack.id, "use_wallet": True}, format="json")
        self.assertEqual(toman_wallet.release_stale_holds(), 0)


class ConvertTests(Base):
    def setUp(self):
        super().setUp()
        settings = TomanWalletSettings.load()
        settings.sms_unit_price, settings.appointment_unit_price = 200, 500
        settings.save()

    def test_convert_to_sms_and_bookings(self):
        toman_wallet.credit(self.user, 10_000)
        r = self.client.post("/api/accounting/wallet/convert/", {"kind": "sms", "count": 10}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.json()["data"]["cost"], 2_000)
        r = self.client.post("/api/accounting/wallet/convert/", {"kind": "appointment", "count": 16}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(self.balance(), 0)
        self.assertEqual(usage.get_wallet(self.user), 10)
        self.assertEqual(usage.get_appt_wallet(self.user), 16)

    def test_cannot_convert_more_than_the_balance(self):
        toman_wallet.credit(self.user, 1_000)
        r = self.client.post("/api/accounting/wallet/convert/", {"kind": "sms", "count": 6}, format="json")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(self.balance(), 1_000)
        self.assertEqual(usage.get_wallet(self.user), 0)

    def test_history_shows_toman_rows_and_balances(self):
        toman_wallet.credit(self.user, 10_000, toman_wallet.REASON_DISCOUNT_REIMBURSEMENT)
        self.client.post("/api/accounting/wallet/convert/", {"kind": "sms", "count": 5}, format="json")
        data = self.client.get("/api/accounting/wallet/history/").json()["data"]
        self.assertEqual(data["balances"]["toman_wallet"], 9_000)
        self.assertEqual(data["balances"]["sms_unit_price"], 200)
        rows = [(r["metric"], r["delta"], r["reason_label"]) for r in data["results"]]
        self.assertIn(("toman_wallet", -1_000, "تبدیل به اعتبار"), rows)
        self.assertIn(("sms_wallet", 5, "تبدیل از کیف‌پول"), rows)
        self.assertIn(("toman_wallet", 10_000, "بازگشت تخفیف نوبت"), rows)
        ent = self.client.get("/api/accounting/my-entitlements/").json()["data"]
        self.assertEqual(ent["usage"]["toman_wallet"], 9_000)


class CommandTests(Base):
    def test_credit_and_debit_by_phone(self):
        call_command("toman_wallet_credit", self.user.phone, "15000", stdout=StringIO())
        call_command("toman_wallet_credit", self.user.phone, "5000", "--debit", stdout=StringIO())
        self.assertEqual(self.balance(), 10_000)
