from django.test import SimpleTestCase

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
