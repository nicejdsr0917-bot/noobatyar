from django.core.cache import cache
from django.test import SimpleTestCase, override_settings

from api.services.otp import verify_otp

PHONE = "989120003344"


class OtpDevBypassTests(SimpleTestCase):
    def setUp(self):
        cache.clear()

    @override_settings(OTP_DEV_CODE="123456")
    def test_dev_code_works_without_a_send(self):
        self.assertEqual(verify_otp(PHONE, "123456"), {"success": True})

    @override_settings(OTP_DEV_CODE="123456")
    def test_wrong_code_never_counts_towards_a_ban(self):
        for _ in range(10):
            self.assertFalse(verify_otp(PHONE, "000000")["success"])
        self.assertEqual(verify_otp(PHONE, "123456"), {"success": True})

    @override_settings(OTP_DEV_CODE="")
    def test_no_bypass_when_dev_code_is_off(self):
        # Production: settings forces OTP_DEV_CODE empty whenever DEBUG is off.
        self.assertFalse(verify_otp(PHONE, "123456")["success"])
