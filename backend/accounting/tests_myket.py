"""
Myket in-app billing (accounting/payment/myket.py).

The failure modes that cost money: granting without Myket's confirmation,
granting twice for one token, granting a purchase to the wrong account, and
granting a different product than the one paid for.
"""

from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from accounting.models import AddOnPack, AddOnPurchase, Plan, StorePurchase, Subscription, Transaction
from accounting.payment import myket

User = get_user_model()

OK = {"kind": "androidpublisher#productPurchase", "purchaseTime": 1, "developerPayload": "",
      "purchaseState": 0, "consumptionState": 0}


@override_settings(MYKET_ACCESS_TOKEN="test-token", MYKET_PACKAGE_NAME="xyz.sattar.javid.proqueue")
class MyketBillingTests(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(phone="09120000031", password="x", name="مالک")
        self.other = User.objects.create_user(phone="09120000032", password="x", name="دیگری")
        Plan.objects.create(id=1, name="آزمایشی", price=0, duration_value=7, duration_unit="day")
        self.plan = Plan.objects.create(name="پرو", price=500000, duration_value=1, duration_unit="month",
                                        myket_sku="plan_pro")
        self.unlisted = Plan.objects.create(name="بدون مایکت", price=1, duration_value=1, duration_unit="month")
        self.pack = AddOnPack.objects.create(name="۱۰۰ پیامک", price=90000, kind=AddOnPack.KIND_SMS,
                                             sms_amount=100, myket_sku="sms_100")

    def verify(self, verdict=OK, consume=True):
        return patch.multiple(
            "accounting.payment.myket",
            myket_verify=lambda sku, token: dict(verdict),
            myket_consume=lambda sku, token: consume,
        )

    def test_plan_purchase_grants_subscription_and_is_consumed(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        self.assertEqual(intent["sku"], "plan_pro")
        with self.verify():
            result = myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-1", "ord-1")
        self.assertEqual(result["status"], "success")
        self.assertTrue(Subscription.objects.filter(user=self.user, plan=self.plan, status="active").exists())
        self.assertEqual(Transaction.objects.get(user=self.user).status, "success")
        self.assertIsNotNone(StorePurchase.objects.get(purchase_token="tok-1").consumed_at)

    def subscribe(self):
        from datetime import timedelta
        from django.utils import timezone
        Subscription.objects.create(user=self.user, plan=self.plan, status="active",
                                    ends_at=timezone.now() + timedelta(days=30))

    def test_addon_needs_an_active_subscription(self):
        with self.assertRaises(myket.StoreError) as ctx:
            myket.create_intent(self.user, StorePurchase.KIND_ADDON, self.pack.id)
        self.assertEqual(ctx.exception.status, 403)
        self.assertFalse(StorePurchase.objects.exists())

    def test_zibal_addon_needs_an_active_subscription(self):
        client = APIClient()
        client.force_authenticate(self.user)
        with patch("accounting.views.AddOnPaymentService") as service:
            r = client.post("/api/accounting/addons/buy/", {"pack_id": self.pack.id}, format="json")
        self.assertEqual(r.status_code, 403, r.content)
        service.assert_not_called()

    def test_addon_purchase_uses_the_zibal_grant_path(self):
        self.subscribe()
        intent = myket.create_intent(self.user, StorePurchase.KIND_ADDON, self.pack.id)
        with self.verify(), patch("accounting.usage.add_wallet") as add_wallet:
            myket.verify_and_grant(self.user, intent["payload"], "sms_100", "tok-2")
        add_wallet.assert_called_once()
        self.assertEqual(AddOnPurchase.objects.get(user=self.user).status, "success")

    def test_same_token_twice_grants_once(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.verify():
            myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-3")
            myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-3")
        self.assertEqual(Subscription.objects.filter(user=self.user, plan=self.plan).count(), 1)

    def test_another_account_cannot_claim_a_token(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.verify():
            myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-4")
            with self.assertRaises(myket.StoreError):
                myket.verify_and_grant(self.other, intent["payload"], "plan_pro", "tok-4")

    def test_unpaid_purchase_grants_nothing(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.verify({**OK, "purchaseState": 1}):
            with self.assertRaises(myket.StoreError):
                myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-5")
        self.assertFalse(Subscription.objects.filter(user=self.user, plan=self.plan).exists())

    def test_payload_of_another_order_is_rejected(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.verify({**OK, "developerPayload": "someone-elses"}):
            with self.assertRaises(myket.StoreError):
                myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-6")

    def test_already_consumed_token_is_rejected(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.verify({**OK, "consumptionState": 1}):
            with self.assertRaises(myket.StoreError):
                myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-7")

    def test_sku_must_match_the_order(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.verify():
            with self.assertRaises(myket.StoreError):
                myket.verify_and_grant(self.user, intent["payload"], "sms_100", "tok-8")

    def test_products_without_a_myket_id_and_the_trial_are_not_sold(self):
        with self.assertRaises(myket.StoreError):
            myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.unlisted.id)
        with self.assertRaises(myket.StoreError):
            myket.create_intent(self.user, StorePurchase.KIND_PLAN, 1)

    def test_failed_consume_is_retried_on_the_next_verify(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.verify(consume=False):
            myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-9")
        self.assertIsNone(StorePurchase.objects.get(purchase_token="tok-9").consumed_at)
        with self.verify(consume=True):
            myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-9")
        self.assertIsNotNone(StorePurchase.objects.get(purchase_token="tok-9").consumed_at)

    @override_settings(MYKET_ACCESS_TOKEN="")
    def test_unconfigured_server_refuses_clearly(self):
        intent = myket.create_intent(self.user, StorePurchase.KIND_PLAN, self.plan.id)
        with self.assertRaises(myket.StoreError) as ctx:
            myket.verify_and_grant(self.user, intent["payload"], "plan_pro", "tok-10")
        self.assertEqual(ctx.exception.status, 503)

    def test_api_round_trip(self):
        client = APIClient()
        client.force_authenticate(self.user)
        r = client.post("/api/accounting/store/myket/intent/", {"kind": "plan", "id": self.plan.id}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        payload = r.json()["data"]["payload"]
        with self.verify():
            r = client.post("/api/accounting/store/myket/verify/",
                            {"payload": payload, "sku": "plan_pro", "token": "tok-11"}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.json()["status"], "success")
