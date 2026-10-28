# accounting/tests_payment_callback.py
"""Where the gateway sends a payer back to: the panel's own page for payments
started in the owner web panel, the app-link page for the native apps."""

from django.test import RequestFactory, SimpleTestCase, override_settings

from accounting.views import _from_web_panel, _payment_callback


@override_settings(PANEL_WEB_URL='https://panel.noobatyar.ir', CLIENT_WEB_URL='https://app.noobatyar.ir')
class PaymentCallbackTests(SimpleTestCase):
    def request(self, **headers):
        return RequestFactory().post('/api/accounting/pay/', **headers)

    def test_panel_origin_gets_the_web_page(self):
        req = self.request(HTTP_ORIGIN='https://panel.noobatyar.ir')
        self.assertTrue(_from_web_panel(req))
        self.assertEqual(
            _payment_callback(req, '/home/payment-result'),
            'https://app.noobatyar.ir/home/payment-result-web',
        )
        self.assertEqual(
            _payment_callback(req, '/home/payment-result-addon'),
            'https://app.noobatyar.ir/home/payment-result-addon-web',
        )

    def test_native_apps_send_no_origin_and_keep_the_app_link_page(self):
        req = self.request()
        self.assertFalse(_from_web_panel(req))
        self.assertEqual(
            _payment_callback(req, '/home/payment-result'),
            'https://app.noobatyar.ir/home/payment-result',
        )

    def test_other_origins_are_not_the_panel(self):
        self.assertFalse(_from_web_panel(self.request(HTTP_ORIGIN='https://evil.example')))
        self.assertFalse(_from_web_panel(self.request(HTTP_ORIGIN='https://app.noobatyar.ir')))

    def test_local_dev_panel_counts(self):
        self.assertTrue(_from_web_panel(self.request(HTTP_ORIGIN='http://127.0.0.1:8080')))
