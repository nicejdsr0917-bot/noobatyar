"""Pins the dicts create_deposit_payment returns, with Zibal mocked at the httpx layer."""

from datetime import timedelta
from unittest.mock import MagicMock, patch

import httpx
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone

from appointment.models import Appointment
from appointment.payment import zibal_deposit
from business.models import Business
from visitor.models import Visitor

User = get_user_model()


def _resp(payload):
    r = MagicMock()
    r.json.return_value = payload
    r.raise_for_status.return_value = None
    return r


class CreateDepositPaymentTests(TestCase):
    def setUp(self):
        owner = User.objects.create_user(phone='09120000201', password='x', name='م')
        self.business = Business.objects.create(
            user=owner, title='سالن', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            deposit_mode='MANDATORY', deposit_amount=50000, merchant_id='m-1',
        )
        visitor = Visitor.objects.create(full_name='م', phone_number='09350000201')
        self.appt = Appointment.objects.create(
            business=self.business, visitor=visitor,
            appointment_date=timezone.now() + timedelta(days=1), status='WAITING',
        )

    def create(self, **post):
        with patch.object(httpx.Client, 'post', **post) as m:
            return zibal_deposit.create_deposit_payment(self.appt, 'https://cb'), m

    def test_success(self):
        out, m = self.create(return_value=_resp({'result': 100, 'trackId': 777}))
        self.assertEqual(out, {'success': True, 'payment_url': 'https://gateway.zibal.ir/start/777',
                               'track_id': '777'})
        sent = m.call_args.kwargs['json']
        self.assertEqual((sent['merchant'], sent['amount'], sent['callbackUrl']), ('m-1', 500000, 'https://cb'))
        self.assertTrue(sent['orderId'].startswith(f'APT-{self.appt.id}-'))
        self.assertIn('بیعانه نوبت', sent['description'])

    def test_rejected_by_zibal(self):
        out, _ = self.create(return_value=_resp({'result': 102, 'message': 'merchant not found'}))
        self.assertEqual(out, {'success': False, 'error': 'درگاه پرداخت این کسب‌وکار در دسترس نیست'})

    def test_transport_error(self):
        out, _ = self.create(side_effect=httpx.ConnectError('down'))
        self.assertEqual(out, {'success': False, 'error': 'خطا در اتصال به درگاه پرداخت'})

    def test_missing_merchant_or_amount_never_calls_zibal(self):
        Business.objects.filter(pk=self.business.pk).update(merchant_id='')
        self.appt.refresh_from_db()
        out, m = self.create(return_value=_resp({}))
        self.assertEqual(out['success'], False)
        m.assert_not_called()
