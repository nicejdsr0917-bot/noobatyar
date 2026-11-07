from django.test import SimpleTestCase, TestCase
from rest_framework.test import APIClient

from accounting import pricing


class QuoteTests(SimpleTestCase):
    PRICES = (2_500, 17_500, 199_000, 949_000, 1_790_000, 5_000_000)

    def test_zibal_leaves_price_plus_vat_after_fee(self):
        for base in self.PRICES:
            q = pricing.quote(base, pricing.CHANNEL_ZIBAL)
            self.assertEqual(q["base"] + q["vat"] + q["fee"], q["total"])
            self.assertEqual(q["vat"], round(base * pricing.VAT_RATE))
            self.assertGreaterEqual(q["total"] - pricing.zibal_fee(q["total"]), base + q["vat"])
            self.assertEqual(q["total"] % pricing.ROUND_TO, 0)

    def test_myket_settles_at_least_the_price(self):
        for base in self.PRICES:
            q = pricing.quote(base, pricing.CHANNEL_MYKET)
            self.assertEqual(q["base"] + q["vat"] + q["fee"], q["total"])
            self.assertGreaterEqual(pricing.myket_net(q["total"]), base)
            self.assertEqual(q["total"] % pricing.ROUND_TO, 0)

    def test_myket_matches_published_example(self):
        # myket.ir/kb/pages/developers-share-fa: 1,000,000 → 772,557 at 85%.
        self.assertEqual(round(pricing.myket_net(1_000_000)), 772_557)

    def test_free_item_costs_nothing(self):
        self.assertEqual(pricing.quote(0, pricing.CHANNEL_ZIBAL)["total"], 0)


class MinimumPriceTests(SimpleTestCase):
    def test_cheap_active_pack_is_rejected(self):
        from django.core.exceptions import ValidationError
        from accounting.models import AddOnPack
        with self.assertRaises(ValidationError):
            AddOnPack(name="x", price=5_000, is_active=True).clean()
        AddOnPack(name="x", price=5_000, is_active=False).clean()
        AddOnPack(name="x", price=10_000, is_active=True).clean()

    def test_paid_plan_has_a_floor_but_trial_is_free(self):
        from django.core.exceptions import ValidationError
        from accounting.models import Plan
        Plan(name="trial", price=0).clean()
        with self.assertRaises(ValidationError):
            Plan(name="x", price=9_000).clean()
        with self.assertRaises(ValidationError):
            Plan(name="x", price=199_000, discount_price=5_000).clean()


class StorePriceDisplayTests(TestCase):
    """The Myket build must show what Myket charges; everyone else the plain price."""

    def setUp(self):
        from accounting.models import AddOnPack, Plan
        self.plan = Plan.objects.create(name="پایه", price=199_000, duration_value=1, duration_unit="month")
        self.sale = Plan.objects.create(name="ویژه", price=300_000, discount_price=199_000,
                                        duration_value=1, duration_unit="month")
        self.pack = AddOnPack.objects.create(name="بسته", price=50_000, kind="sms", sms_amount=100)
        self.client = APIClient()

    def _display(self, url, **headers):
        rows = self.client.get(url, **headers).json()["data"]
        return {r["name"]: r["price_display"] for r in rows}

    def _plan_url(self):
        return "/api/accounting/plans/"

    def test_default_shows_plain_price(self):
        got = self._display(self._plan_url())
        self.assertEqual(got["پایه"], "199,000 تومان")

    def test_myket_shows_store_total(self):
        total = pricing.quote(199_000, pricing.CHANNEL_MYKET)["total"]
        got = self._display(self._plan_url(), HTTP_X_STORE="myket")
        self.assertEqual(got["پایه"], f"{total:,} تومان")
        self.assertEqual(got["ویژه"], f"{total:,} تومان")  # discount price is the base

    def test_myket_pack_and_raw_fields_unchanged(self):
        total = pricing.quote(50_000, pricing.CHANNEL_MYKET)["total"]
        got = self._display("/api/accounting/addons/", HTTP_X_STORE="myket")
        self.assertEqual(got["بسته"], f"{total:,} تومان")
        rows = self.client.get(self._plan_url(), HTTP_X_STORE="myket").json()["data"]
        self.assertEqual({r["price"] for r in rows}, {199_000, 300_000})

    def test_unknown_store_ignored(self):
        got = self._display(self._plan_url(), HTTP_X_STORE="bazaar")
        self.assertEqual(got["پایه"], "199,000 تومان")
