"""Card details are for the paying visitor only; Zibal-verified deposits are traceable."""

from datetime import timedelta
from unittest.mock import patch

from django.contrib.admin.sites import site
from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import RequestFactory, TestCase
from django.urls import reverse
from django.utils import timezone

from integrations import zibal
from business.models import Business
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

from .admin import PaidWithoutSlotFilter
from .models import Appointment
from .payment.zibal_deposit import verify_deposit_payment

User = get_user_model()
CARD = '6037991234567890'


class Base:
    def setUp(self):
        super().setUp()
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000088', password='x', name='o')
        self.business = Business.objects.create(
            user=self.owner, title='b', phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
            payment_method='CARD', accepted_payment_methods=['CARD'],
            card_number=CARD, card_owner_name='Ali Owner',
            deposit_mode='MANDATORY', deposit_amount=50000, merchant_id='zibal-m',
        )
        self.visitor = Visitor.objects.create(full_name='v1', phone_number='09350000088')
        self.other = Visitor.objects.create(full_name='v2', phone_number='09350000089')
        self.when = timezone.now() + timedelta(days=1)

    def auth(self, visitor):
        return {'HTTP_AUTHORIZATION': f'Visitor {sign_visitor_token(visitor.id)}'}

    def make(self, visitor=None, **kw):
        d = dict(
            business=self.business, visitor=visitor or self.visitor,
            appointment_date=self.when, status='LOCKED',
            locked_at=timezone.now(), expires_at=timezone.now() + timedelta(minutes=10),
        )
        d.update(kw)
        return Appointment.objects.create(**d)

    def my_appointments(self, visitor):
        r = self.client.get(reverse('client-appointment-list'), **self.auth(visitor))
        self.assertEqual(r.status_code, 200)
        return {a['id']: a for a in r.json()['data']['results']}


class CardExposureTests(Base, TestCase):
    def test_anonymous_business_list_and_detail_have_no_card(self):
        r = self.client.get(reverse('client-business-list'))
        self.assertEqual(r.status_code, 200)
        self.assertNotIn(CARD, r.content.decode())
        self.assertNotIn('card_owner_name', r.content.decode())
        r = self.client.get(reverse('client-business-detail', args=[self.business.id]))
        self.assertEqual(r.status_code, 200)
        body = r.content.decode()
        self.assertNotIn(CARD, body)
        self.assertNotIn('card_number', body)
        self.assertNotIn('card_owner_name', body)

    def test_own_locked_card_appointment_has_card(self):
        a = self.make()
        row = self.my_appointments(self.visitor)[a.id]
        self.assertEqual(row['card_number'], CARD)
        self.assertEqual(row['card_owner_name'], 'Ali Owner')

    def test_other_visitor_does_not_see_it(self):
        a = self.make()
        self.assertNotIn(a.id, self.my_appointments(self.other))

    def test_serializer_refuses_foreign_visitor(self):
        from .client_serializers import ClientAppointmentSerializer
        a = self.make()
        req = RequestFactory().get('/')
        req.user = self.other
        data = ClientAppointmentSerializer(a, context={'request': req}).data
        self.assertIsNone(data['card_number'])
        self.assertIsNone(data['card_owner_name'])

    def test_settled_appointment_hides_card(self):
        a = self.make(status='WAITING', locked_at=None, expires_at=None)
        row = self.my_appointments(self.visitor)[a.id]
        self.assertIsNone(row['card_number'])

    def test_non_card_business_has_no_card(self):
        self.business.payment_method = 'FREE'
        self.business.accepted_payment_methods = ['CASH']
        self.business.save()
        a = self.make()
        self.assertIsNone(self.my_appointments(self.visitor)[a.id]['card_number'])


def _result(outcome, **data):
    return zibal.VerifyResult(outcome, data.get('result'), '', data)


class VerifyDepositTests(Base, TestCase):
    def test_success_stamps_verified_at_and_uses_business_merchant(self):
        a = self.make(payment_reference='111')
        ok = _result(zibal.Outcome.SUCCESS, result=100, amount=500000)
        with patch('appointment.payment.zibal_deposit.zibal.verify', return_value=ok) as v:
            res = verify_deposit_payment(a, '111')
        self.assertTrue(res['success'])
        v.assert_called_once_with('111', merchant='zibal-m')
        a.refresh_from_db()
        self.assertIsNotNone(a.deposit_verified_at)

    def test_already_verified_is_success(self):
        a = self.make(payment_reference='111')
        dup = _result(zibal.Outcome.ALREADY_VERIFIED, result=201, amount=500000)
        with patch('appointment.payment.zibal_deposit.zibal.verify', return_value=dup):
            self.assertTrue(verify_deposit_payment(a, '111')['success'])

    def test_failed_and_transport_error_are_not_success(self):
        a = self.make(payment_reference='111')
        for outcome in (zibal.Outcome.FAILED, zibal.Outcome.TRANSPORT_ERROR):
            with patch('appointment.payment.zibal_deposit.zibal.verify',
                       return_value=_result(outcome, result=202)):
                self.assertFalse(verify_deposit_payment(a, '111')['success'])
        a.refresh_from_db()
        self.assertIsNone(a.deposit_verified_at)

    def test_underpayment_is_rejected_but_stamped_for_refund(self):
        a = self.make(payment_reference='111')
        low = _result(zibal.Outcome.SUCCESS, result=100, amount=1000)
        with patch('appointment.payment.zibal_deposit.zibal.verify', return_value=low):
            self.assertFalse(verify_deposit_payment(a, '111')['success'])
        a.refresh_from_db()
        self.assertIsNotNone(a.deposit_verified_at)


class PaidWithoutSlotFilterTests(Base, TestCase):
    def ids(self):
        f = PaidWithoutSlotFilter(
            RequestFactory().get('/', {'paid_without_slot': '1'}),
            {'paid_without_slot': '1'}, Appointment, site._registry[Appointment],
        )
        return set(f.queryset(None, Appointment.objects.all()).values_list('id', flat=True))

    def test_filter_selects_only_lost_paid_deposits(self):
        past, future = timezone.now() - timedelta(minutes=5), timezone.now() + timedelta(minutes=5)
        now = timezone.now()
        lost = self.make(payment_reference='1', expires_at=past, deposit_verified_at=now)
        self.make(payment_reference='2', expires_at=past)                         # never verified
        self.make(payment_reference='3', expires_at=future, deposit_verified_at=now)  # still holding
        self.make(payment_reference='4', expires_at=past, deposit_verified_at=now,
                  deposit_payment_method='GATEWAY')
        self.make(payment_reference='5', status='WAITING', expires_at=None, deposit_verified_at=now)
        self.assertEqual(self.ids(), {lost.id})
