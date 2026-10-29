"""Owner/client transitions act on the LOCKED row, never a stale instance."""

import threading
from datetime import timedelta
from unittest import skipUnless
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.contrib.admin.sites import site
from django.core.cache import cache
from django.db import connection
from django.test import RequestFactory, TestCase, TransactionTestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from accounting.payment import zibal_client
from business.models import Business
from visitor.models import Visitor

from .admin import PaidWithoutSlotFilter
from .models import Appointment
from .payment.zibal_deposit import verify_deposit_payment
from .services.booking import appointment_delete_for_owner
from .services.transitions import (
    StatusChangeFailure, appointment_cancel_by_client,
    appointment_change_status_by_owner, appointment_expire_lock_if_due,
)

User = get_user_model()
RELEASE = 'accounting.usage.release_appointment'
NOTIFY = 'appointment.services.transitions.notify_client_of_decision'


class Base:
    def setUp(self):
        super().setUp()
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000099', password='x', name='o')
        self.business = Business.objects.create(
            user=self.owner, title='b', phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
            deposit_mode='MANDATORY', deposit_amount=50000, merchant_id='zibal-m',
        )
        self.visitor = Visitor.objects.create(full_name='v1', phone_number='09350000099')
        self.when = timezone.now() + timedelta(days=1)

    def make(self, **kw):
        d = dict(
            business=self.business, visitor=self.visitor,
            appointment_date=self.when, status='WAITING', quota_source='monthly',
        )
        d.update(kw)
        return Appointment.objects.create(**d)

    def stale(self, a):
        return Appointment.objects.select_related('business', 'visitor').get(pk=a.pk)


class OwnerStatusStaleTests(Base, TestCase):
    def test_owner_cancel_after_client_cancel_refunds_once(self):
        a = self.make()
        stale = self.stale(a)
        with patch(RELEASE) as rel, patch(NOTIFY):
            self.assertTrue(appointment_cancel_by_client(self.stale(a), 'WAITING'))
            appointment_change_status_by_owner(appointment=stale, new_status='CANCELLED')
        self.assertEqual(rel.call_count, 1)

    def test_owner_cancel_twice_refunds_once(self):
        a = self.make()
        s1, s2 = self.stale(a), self.stale(a)
        with patch(RELEASE) as rel, patch(NOTIFY):
            appointment_change_status_by_owner(appointment=s1, new_status='CANCELLED')
            appointment_change_status_by_owner(appointment=s2, new_status='CANCELLED')
        self.assertEqual(rel.call_count, 1)

    def test_owner_waiting_after_client_cancel_does_not_resurrect(self):
        a = self.make()
        stale = self.stale(a)
        with patch(RELEASE), patch(NOTIFY) as notify:
            appointment_cancel_by_client(self.stale(a), 'WAITING')
            res = appointment_change_status_by_owner(appointment=stale, new_status='WAITING')
        self.assertIs(res.failure, StatusChangeFailure.INVALID_TRANSITION)
        self.assertEqual(res.old_status, 'CANCELLED')
        notify.assert_not_called()
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')


class OwnerDeleteStaleTests(Base, TestCase):
    def test_delete_twice_refunds_once_and_second_is_noop(self):
        a = self.make()
        s1, s2 = self.stale(a), self.stale(a)
        with patch(RELEASE) as rel:
            appointment_delete_for_owner(appointment=s1)
            appointment_delete_for_owner(appointment=s2)
        self.assertEqual(rel.call_count, 1)
        self.assertFalse(Appointment.objects.filter(pk=a.pk).exists())

    def test_delete_after_client_cancel_does_not_refund_again(self):
        a = self.make()
        stale = self.stale(a)
        with patch(RELEASE) as rel:
            appointment_cancel_by_client(self.stale(a), 'WAITING')
            appointment_delete_for_owner(appointment=stale)
        self.assertEqual(rel.call_count, 1)


class PatchStatusWithDetailsTests(Base, TestCase):
    def setUp(self):
        super().setUp()
        self.api = APIClient()
        self.api.force_authenticate(user=self.owner)

    def patch_it(self, a, **data):
        return self.api.patch(reverse('appointments:appointment-update', args=[a.id]),
                              data=data, format='json')

    def test_status_plus_details_cancels_refunds_and_notifies(self):
        a = self.make()
        with patch(RELEASE) as rel, patch(NOTIFY) as notify:
            r = self.patch_it(a, status='CANCELLED', description='x')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['data']['status'], 'CANCELLED')
        self.assertEqual(rel.call_count, 1)
        notify.assert_called_once()
        a.refresh_from_db()
        self.assertEqual((a.status, a.description), ('CANCELLED', 'x'))

    def test_invalid_transition_with_details_applies_nothing(self):
        a = self.make(status='COMPLETED', description='old')
        with patch(RELEASE) as rel, patch(NOTIFY) as notify:
            r = self.patch_it(a, status='WAITING', description='new')
        self.assertEqual(r.status_code, 400)
        a.refresh_from_db()
        self.assertEqual((a.status, a.description), ('COMPLETED', 'old'))
        rel.assert_not_called()
        notify.assert_not_called()

    def test_status_only_on_vanished_row_is_404(self):
        a = self.make()
        pk = a.pk
        with patch('appointment.views.views.selectors.appointment_get_for_owner',
                   return_value=self.stale(a)):
            Appointment.objects.filter(pk=pk).delete()
            r = self.patch_it(a, status='CANCELLED')
        self.assertEqual(r.status_code, 404)


def _result(outcome, **data):
    return zibal_client.VerifyResult(outcome, data.get('result'), '', data)


class UnderpaidDepositTests(Base, TestCase):
    def test_underpayment_fails_but_is_stamped_and_listed_after_expiry(self):
        a = self.make(status='LOCKED', quota_source='', payment_reference='111',
                      locked_at=timezone.now(),
                      expires_at=timezone.now() + timedelta(minutes=5))
        low = _result(zibal_client.Outcome.SUCCESS, result=100, amount=1000)
        with patch('appointment.payment.zibal_deposit.zibal_client.verify', return_value=low):
            self.assertFalse(verify_deposit_payment(a, '111')['success'])
        a.refresh_from_db()
        self.assertIsNotNone(a.deposit_verified_at)

        def ids():
            f = PaidWithoutSlotFilter(
                RequestFactory().get('/', {'paid_without_slot': '1'}),
                {'paid_without_slot': '1'}, Appointment, site._registry[Appointment],
            )
            return set(f.queryset(None, Appointment.objects.all()).values_list('id', flat=True))

        self.assertNotIn(a.id, ids())  # still holding
        Appointment.objects.filter(pk=a.pk).update(expires_at=timezone.now() - timedelta(minutes=1))
        self.assertIn(a.id, ids())


class AtomicRefundTests(Base, TestCase):
    def test_client_cancel_rolls_back_when_refund_fails(self):
        a = self.make()
        with patch('appointment.services.transitions.refund_quota', side_effect=RuntimeError):
            with self.assertRaises(RuntimeError):
                appointment_cancel_by_client(self.stale(a), 'WAITING')
        a.refresh_from_db()
        self.assertEqual((a.status, a.quota_source), ('WAITING', 'monthly'))

    def test_expiry_rolls_back_when_refund_fails(self):
        a = self.make(status='LOCKED', locked_at=timezone.now(),
                      expires_at=timezone.now() - timedelta(minutes=1))
        with patch('appointment.services.transitions.refund_quota', side_effect=RuntimeError):
            with self.assertRaises(RuntimeError):
                appointment_expire_lock_if_due(self.stale(a))
        a.refresh_from_db()
        self.assertEqual((a.status, a.quota_source), ('LOCKED', 'monthly'))


@skipUnless(connection.vendor == 'postgresql', 'needs real row locks')
class OwnerStatusRaceTests(Base, TransactionTestCase):
    def test_parallel_owner_and_client_cancel_refund_once(self):
        a = self.make()
        barrier = threading.Barrier(2)
        stale = [self.stale(a), self.stale(a)]

        def owner():
            try:
                barrier.wait()
                appointment_change_status_by_owner(appointment=stale[0], new_status='CANCELLED')
            finally:
                connection.close()

        def client():
            try:
                barrier.wait()
                appointment_cancel_by_client(stale[1], 'WAITING')
            finally:
                connection.close()

        with patch(RELEASE) as rel, patch(NOTIFY):
            ts = [threading.Thread(target=owner), threading.Thread(target=client)]
            [t.start() for t in ts]
            [t.join() for t in ts]
        self.assertEqual(rel.call_count, 1)
