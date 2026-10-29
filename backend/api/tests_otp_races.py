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

    def test_verify_per_phone_ip_429(self):
        codes = [self.c.post("/api/auth/otp/verify/", {"phone": PHONE, "code": "123456"},
                             format="json", REMOTE_ADDR="10.0.0.1").status_code for i in range(12)]
        self.assertEqual(codes[-1], 429)
        self.assertNotIn(429, codes[:10])

    def test_verify_per_ip_429(self):
        codes = [self.c.post("/api/auth/otp/verify/", {"phone": f"0912000{i:04d}", "code": "123456"},
                             format="json", REMOTE_ADDR="9.9.9.9").status_code for i in range(32)]
        self.assertNotIn(429, codes[:30])
        self.assertEqual(codes[-1], 429)

    def test_failed_sends_do_not_count_toward_hourly_quota(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value=None):
            codes = [self.c.post("/api/auth/otp/send/", {"phone": PHONE}, format="json",
                                 REMOTE_ADDR="10.1.0.1").status_code for _ in range(4)]
        self.assertNotIn(429, codes)  # ip throttle 5/min is the only limit here

    def test_send_per_phone_ip_hourly_429_after_successes(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="111111"):
            for _ in range(5):
                cache.delete(otp._key_rate(PHONE))  # skip the 3-min cooldown
                self.assertEqual(self.c.post("/api/auth/otp/send/", {"phone": PHONE},
                                             format="json", REMOTE_ADDR="10.1.0.1").status_code, 200)
            cache.delete(otp._key_rate(PHONE))
            r = self.c.post("/api/auth/otp/send/", {"phone": PHONE}, format="json", REMOTE_ADDR="10.1.0.1")
        self.assertEqual(r.status_code, 429)


@override_settings(OTP_DEV_CODE="")
class OtpLockoutResistanceTests(TestCase):
    """A third party must not be able to lock a victim out."""
    def setUp(self):
        cache.clear()
        self.c = APIClient()

    def _post(self, path, ip, **data):
        return self.c.post(path, {"phone": PHONE, **data}, format="json", REMOTE_ADDR=ip)

    def test_attacker_cannot_block_victim_verify(self):
        for _ in range(15):
            self._post("/api/auth/otp/verify/", "6.6.6.6", code="000000")
        self.assertEqual(self._post("/api/auth/otp/verify/", "6.6.6.6", code="000000").status_code, 429)
        self.assertNotEqual(self._post("/api/auth/otp/verify/", "7.7.7.7", code="000000").status_code, 429)

    def test_attacker_cannot_block_victim_send(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="111111"):
            for _ in range(5):
                cache.delete(otp._key_rate(PHONE))
                self._post("/api/auth/otp/send/", "6.6.6.6")
            cache.delete(otp._key_rate(PHONE))
            self.assertEqual(self._post("/api/auth/otp/send/", "6.6.6.6").status_code, 429)
            self.assertEqual(self._post("/api/auth/otp/send/", "7.7.7.7").status_code, 200)

    def test_daily_cap_counts_only_successful_sends(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value=None):
            for _ in range(20):
                self.assertFalse(send_otp(PHONE, ip="1.1.1.1")["success"])
        self.assertEqual(cache.get(otp._key_sent_day(PHONE), 0), 0)
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="111111"):
            self.assertTrue(send_otp(PHONE, ip="1.1.1.1")["success"])
        self.assertEqual(cache.get(otp._key_sent_day(PHONE)), 1)

    def test_daily_cap_blocks_after_limit_across_ips(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="111111"):
            for i in range(otp.OTP_MAX_DAILY_SENDS):
                cache.delete(otp._key_rate(PHONE))
                self.assertTrue(send_otp(PHONE, ip=f"2.2.2.{i}")["success"])
            cache.delete(otp._key_rate(PHONE))
            res = send_otp(PHONE, ip="3.3.3.3")
        self.assertFalse(res["success"])
        self.assertIn("سقف", res["error"])

    def test_no_24h_ban_after_wrong_guesses(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="654321"):
            self.assertTrue(send_otp(PHONE)["success"])
            for _ in range(6):
                verify_otp(PHONE, "000000")
            # code is dead but the cooldown stays: must wait the normal 3 min
            res = send_otp(PHONE)
            self.assertFalse(res["success"])
            self.assertIn("۳ دقیقه", res["error"])
            self.assertFalse(verify_otp(PHONE, "654321")["success"])
            cache.delete(otp._key_rate(PHONE))  # cooldown elapsed
            self.assertTrue(send_otp(PHONE)["success"])
            self.assertTrue(verify_otp(PHONE, "654321")["success"])

    def test_forgot_password_endpoints_throttled(self):
        from django.contrib.auth import get_user_model
        get_user_model().objects.create_user(phone=PHONE, password="x12345678") \
            if hasattr(get_user_model().objects, "create_user") else None
        codes = [self._post("/api/auth/forgot-password/verify/", "9.9.9.9", code="123456").status_code
                 for _ in range(12)]
        self.assertEqual(codes[-1], 429)
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="111111"):
            for _ in range(5):
                cache.delete(otp._key_rate(PHONE))
                self._post("/api/auth/forgot-password/send/", "8.8.8.8")
            cache.delete(otp._key_rate(PHONE))
            self.assertEqual(self._post("/api/auth/forgot-password/send/", "8.8.8.8").status_code, 429)


@override_settings(OTP_DEV_CODE="")
class OtpCacheOutageTests(SimpleTestCase):
    def setUp(self):
        cache.clear()

    def test_send_outage_distinct_error_and_logged(self):
        with mock.patch.object(otp.cache, "add", return_value=None), \
             mock.patch.object(otp, "_send_via_melipayamak") as m, \
             self.assertLogs("api.services.otp", level="ERROR") as cm:
            res = send_otp(PHONE)
        self.assertFalse(res["success"])
        self.assertIn("در دسترس نیست", res["error"])
        self.assertNotIn("۳ دقیقه", res["error"])
        m.assert_not_called()
        self.assertEqual(len(cm.output), 1)

    def test_verify_incr_outage_no_exception(self):
        with mock.patch.object(otp, "_send_via_melipayamak", return_value="654321"):
            send_otp(PHONE)
        with mock.patch.object(otp.cache, "incr", return_value=None), \
             self.assertLogs("api.services.otp", level="ERROR"):
            res = verify_otp(PHONE, "654321")
        self.assertFalse(res["success"])


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
