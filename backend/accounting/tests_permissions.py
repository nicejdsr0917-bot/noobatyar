from types import SimpleNamespace
from unittest.mock import patch

from django.http import QueryDict
from django.test import SimpleTestCase

from accounting import entitlements
from accounting.permissions import validate_business_settings

OWNER = SimpleNamespace(is_staff=False)


def stored_business(**overrides):
    fields = dict(
        payment_method="NONE",
        accepted_payment_methods=["CASH"],
        card_number="",
        deposit_mode="NONE",
        deposit_amount=0,
        enable_promotional_sms=False,
        reminder_delivery="MANUAL",
        max_appointments_per_hour=None,
    )
    fields.update(overrides)
    return SimpleNamespace(**fields)


def multipart(**fields):
    """A payload shaped like the owner app's multipart PUT."""
    data = QueryDict(mutable=True)
    for key, value in fields.items():
        data[key] = value
    return data


def plan_without(*missing):
    return patch.object(
        entitlements, "has_feature", side_effect=lambda user, key: key not in missing
    )


class ValidateBusinessSettingsTests(SimpleTestCase):
    def test_enabling_card_without_the_feature_is_refused(self):
        with plan_without(entitlements.FEATURE_DEPOSIT):
            error = validate_business_settings(
                OWNER, multipart(card_number="6037991234567890"), stored_business()
            )
        self.assertIn("دریافت بیعانه", error)

    def test_saved_card_does_not_block_an_unrelated_edit_after_downgrade(self):
        # The owner app posts the whole business back on every save.
        business = stored_business(card_number="6037991234567890", accepted_payment_methods=["CASH", "CARD"])
        payload = multipart(
            title="new name",
            card_number="6037991234567890",
            accepted_payment_methods="CASH,CARD",
            deposit_mode="NONE",
        )
        with plan_without(entitlements.FEATURE_DEPOSIT):
            self.assertIsNone(validate_business_settings(OWNER, payload, business))

    def test_saved_panel_reminders_do_not_block_an_edit_after_downgrade(self):
        business = stored_business(reminder_delivery="PANEL")
        with plan_without(entitlements.FEATURE_AUTO_REMINDER_SMS):
            self.assertIsNone(
                validate_business_settings(OWNER, multipart(reminder_delivery="PANEL"), business)
            )

    def test_a_saved_capability_does_not_unlock_a_different_one(self):
        business = stored_business(card_number="6037991234567890")
        with plan_without(entitlements.FEATURE_DEPOSIT, entitlements.FEATURE_ONLINE_GATEWAY):
            error = validate_business_settings(
                OWNER,
                multipart(card_number="6037991234567890", accepted_payment_methods="CARD,ONLINE"),
                business,
            )
        self.assertIn(entitlements.FEATURE_LABELS[entitlements.FEATURE_ONLINE_GATEWAY], error)

    def test_turning_a_capability_off_is_always_allowed(self):
        business = stored_business(card_number="6037991234567890", deposit_mode="MANDATORY", deposit_amount=50000)
        with plan_without(entitlements.FEATURE_DEPOSIT):
            self.assertIsNone(
                validate_business_settings(
                    OWNER, multipart(card_number="", deposit_mode="NONE"), business
                )
            )

    def test_create_still_checks_everything(self):
        with plan_without(entitlements.FEATURE_DEPOSIT):
            error = validate_business_settings(OWNER, multipart(deposit_mode="MANDATORY"))
        self.assertIn("دریافت بیعانه", error)

    def test_deposit_without_amount_is_refused(self):
        with plan_without():
            error = validate_business_settings(
                OWNER, multipart(deposit_mode="OPTIONAL", deposit_amount="0"), stored_business()
            )
        self.assertEqual(error, "برای فعال کردن بیعانه، مبلغ بیعانه را وارد کنید.")
