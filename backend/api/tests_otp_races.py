from unittest import mock

from django.core.cache import cache
from django.test import RequestFactory, SimpleTestCase, TestCase, override_settings
from rest_framework.test import APIClient

from api.services import otp
from api.services.otp import send_otp, verify_otp
from api.throttles import client_ip

PHONE = "09120003344"


@override_settings(OTP_DEV_CODE="")
class OtpAtomicityTests(SimpleTestCase):
    def setUp(self):
        cache.clear()

    def _issue(self, code="654321"):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value=code):
            self.assertTrue(send_otp(PHONE)["success"])

    def test_locked_after_five_wrong_guesses_even_with_right_code(self):
        self._issue()
        for _ in range(5):
            self.assertFalse(verify_otp(PHONE, "000000")["success"])
        res = verify_otp(PHONE, "654321")
        self.assertFalse(res["success"])
        self.assertIn("تعداد تلاش", res["error"])

    def test_remaining_counter(self):
        self._issue()
        self.assertIn("4 تلاش", verify_otp(PHONE, "1")["error"])

    def test_double_send_within_cooldown_sends_one_sms(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="111111") as m:
            self.assertTrue(send_otp(PHONE)["success"])
            self.assertFalse(send_otp(PHONE)["success"])
        self.assertEqual(m.call_count, 1)

    def test_claim_blocks_parallel_send_while_sms_in_flight(self):
        def inner(_):
            # a second request arriving mid-send must hit the cooldown
            self.assertFalse(send_otp(PHONE)["success"])
            return "222222"
        with mock.patch.object(otp, "_send_via_melipayamak", side_effect=inner) as m:
            self.assertTrue(send_otp(PHONE)["success"])
        self.assertEqual(m.call_count, 1)

    def test_failed_send_releases_claim(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value=None):
            self.assertFalse(send_otp(PHONE)["success"])
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="333333") as m:
            self.assertTrue(send_otp(PHONE)["success"])
        self.assertEqual(m.call_count, 1)

    def test_codes_not_logged(self):
        self._issue("987654")
        with self.assertLogs("api.services.otp", level="DEBUG") as cm:
            verify_otp(PHONE, "123123")
            verify_otp(PHONE, "987654")
        out = "\n".join(cm.output)
        self.assertNotIn("987654", out)
        self.assertNotIn("123123", out)
        with self.assertLogs("api.services.otp", level="DEBUG") as cm:
            verify_otp(PHONE, "555555")  # no active code now
        self.assertNotIn("555555", "\n".join(cm.output))


@override_settings(OTP_DEV_CODE="")
class OtpThrottleTests(TestCase):
    def setUp(self):
        cache.clear()
        self.c = APIClient()

    def test_verify_per_phone_429(self):
        codes = []
        for i in range(12):
            r = self.c.post("/api/auth/otp/verify/", {"phone": PHONE, "code": "123456"},
                            format="json", REMOTE_ADDR=f"10.0.0.{i}")
            codes.append(r.status_code)
        self.assertEqual(codes[-1], 429)
        self.assertNotIn(429, codes[:10])

    def test_verify_per_ip_429(self):
        codes = [self.c.post("/api/auth/otp/verify/", {"phone": f"0912000{i:04d}", "code": "123456"},
                             format="json", REMOTE_ADDR="9.9.9.9").status_code for i in range(32)]
        self.assertNotIn(429, codes[:30])
        self.assertEqual(codes[-1], 429)

    def test_send_per_phone_429(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value=None):
            codes = [self.c.post("/api/auth/otp/send/", {"phone": PHONE}, format="json",
                                 REMOTE_ADDR=f"10.1.0.{i}").status_code for i in range(7)]
        self.assertNotIn(429, codes[:5])
        self.assertEqual(codes[-1], 429)


class ClientIpTests(SimpleTestCase):
    def _req(self, **meta):
        return RequestFactory().get("/", **meta)

    def test_ignores_spoofed_xff_by_default(self):
        r = self._req(REMOTE_ADDR="1.1.1.1", HTTP_X_FORWARDED_FOR="6.6.6.6, 2.2.2.2")
        self.assertEqual(client_ip(r), "1.1.1.1")

    def test_trusted_proxy_count(self):
        r = self._req(REMOTE_ADDR="10.0.0.1", HTTP_X_FORWARDED_FOR="6.6.6.6, 2.2.2.2")
        with mock.patch("api.throttles.api_settings") as s:
            s.NUM_PROXIES = 1
            self.assertEqual(client_ip(r), "2.2.2.2")

    def test_fingerprint_ignores_spoofed_xff(self):
        from business.client_views import request_fingerprint
        a = request_fingerprint(self._req(REMOTE_ADDR="1.1.1.1", HTTP_X_FORWARDED_FOR="6.6.6.6"))
        b = request_fingerprint(self._req(REMOTE_ADDR="1.1.1.1", HTTP_X_FORWARDED_FOR="7.7.7.7"))
        self.assertEqual(a, b)
