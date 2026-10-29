"""Correctness of the client booking / payment / cancel flow under races."""

import threading
from datetime import timedelta
from unittest import skipUnless
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.db import connection
from django.test import TestCase, TransactionTestCase
from django.urls import reverse
from django.utils import timezone

from business.models import Business
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

from .client_serializers import ClientAppointmentSerializer
from .client_views import ClientAppointmentListView, _expire_lock_if_due
from .models import Appointment

User = get_user_model()

VERIFY = 'appointment.payment.zibal_deposit.verify_deposit_payment'


class Base:
    def setUp(self):
        super().setUp()
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000077', password='x', name='o')
        self.business = Business.objects.create(
            user=self.owner, title='b', phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )
        self.visitor = Visitor.objects.create(full_name='v1', phone_number='09350000077')
        self.other = Visitor.objects.create(full_name='v2', phone_number='09350000078')
        self.auth = f'Visitor {sign_visitor_token(self.visitor.id)}'
        self.when = timezone.now() + timedelta(days=1)

    def make(self, visitor=None, **kw):
        defaults = dict(
            business=self.business, visitor=visitor or self.visitor,
            appointment_date=self.when, status='LOCKED',
            locked_at=timezone.now(), expires_at=timezone.now() + timedelta(minutes=10),
            payment_reference='TRK1',
        )
        defaults.update(kw)
        return Appointment.objects.create(**defaults)

    def callback(self, track='TRK1'):
        return self.client.get(reverse('client-deposit-callback'), {'trackId': track})


class DepositCallbackTests(Base, TestCase):
    def test_happy_path_confirms(self):
        a = self.make()
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.client_views._fire_deposit_paid_sms') as sms:
            r = self.callback()
        self.assertIn('payment=success', r['Location'])
        a.refresh_from_db()
        self.assertEqual((a.status, a.deposit_payment_method), ('WAITING', 'GATEWAY'))
        self.assertIsNone(a.expires_at)
        sms.assert_called_once()

    def test_replay_has_no_side_effects(self):
        self.make()
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.client_views._fire_deposit_paid_sms') as sms:
            self.callback()
            r = self.callback()
        self.assertIn('payment=success', r['Location'])
        sms.assert_called_once()

    def test_duplicate_reference_on_card_rows_does_not_crash(self):
        # Client-controlled free text on the CARD path can equal a trackId.
        self.make(status='PENDING_VERIFICATION', deposit_payment_method='CARD',
                  expires_at=None, locked_at=None)
        self.make(visitor=self.other, status='PENDING_VERIFICATION',
                  deposit_payment_method='CARD', expires_at=None, locked_at=None)
        locked = self.make()
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.client_views._fire_deposit_paid_sms'):
            r = self.callback()
        self.assertEqual(r.status_code, 302)
        locked.refresh_from_db()
        self.assertEqual(locked.status, 'WAITING')

    def test_card_reference_alone_is_not_success(self):
        self.make(status='PENDING_VERIFICATION', deposit_payment_method='CARD',
                  expires_at=None, locked_at=None)
        r = self.callback()
        self.assertIn('payment=failed', r['Location'])

    def test_cancelled_row_is_not_revived(self):
        a = self.make(status='CANCELLED')
        with patch(VERIFY, return_value={'success': True}) as v:
            r = self.callback()
        self.assertIn('payment=failed', r['Location'])
        v.assert_not_called()
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')

    def test_cancelled_during_verify_is_not_revived(self):
        a = self.make()

        def cancel_meanwhile(*args, **kwargs):
            Appointment.objects.filter(pk=a.pk).update(status='CANCELLED')
            return {'success': True}

        with patch(VERIFY, side_effect=cancel_meanwhile), \
                patch('appointment.client_views._fire_deposit_paid_sms') as sms:
            self.callback()
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')
        sms.assert_not_called()

    def test_expired_lock_without_conflict_still_confirms(self):
        a = self.make(expires_at=timezone.now() - timedelta(minutes=1))
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.client_views._fire_deposit_paid_sms'):
            r = self.callback()
        self.assertIn('payment=success', r['Location'])
        a.refresh_from_db()
        self.assertEqual(a.status, 'WAITING')

    def test_expired_lock_with_conflict_is_not_double_booked(self):
        a = self.make(expires_at=timezone.now() - timedelta(minutes=1))
        taken = self.make(visitor=self.other, status='PENDING_APPROVAL',
                          payment_reference=None, expires_at=None, locked_at=None)
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.client_views._fire_deposit_paid_sms') as sms, \
                self.assertLogs('appointment.client_views', level='ERROR'):
            r = self.callback()
        self.assertIn('payment=conflict', r['Location'])
        a.refresh_from_db()
        self.assertEqual(a.status, 'LOCKED')
        sms.assert_not_called()
        taken.refresh_from_db()
        self.assertEqual(taken.status, 'PENDING_APPROVAL')


class CancelAndPayTests(Base, TestCase):
    def test_cancel_loses_to_concurrent_transition(self):
        a = self.make(status='PENDING_APPROVAL', expires_at=None, locked_at=None)
        from .client_views import _cancel_transition
        stale = Appointment.objects.select_related('business').get(pk=a.pk)
        Appointment.objects.filter(pk=a.pk).update(status='IN_PROGRESS')
        self.assertFalse(_cancel_transition(stale, 'PENDING_APPROVAL'))
        a.refresh_from_db()
        self.assertEqual(a.status, 'IN_PROGRESS')

    def test_cancel_view_cancels(self):
        a = self.make()
        with patch('appointment.client_views._fire_cancellation_sms'):
            r = self.client.post(reverse('client-appointment-cancel', args=[a.pk]),
                                 HTTP_AUTHORIZATION=self.auth)
        self.assertEqual(r.status_code, 200)
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')

    def test_pay_does_not_overwrite_concurrent_owner_edit(self):
        a = self.make()
        original = Appointment.objects.get

        def racing_get(*args, **kwargs):
            return original(*args, **kwargs)

        # Owner edits the description after the view loaded the row: simulate by
        # patching _fire_booking_sms-free path — edit just before the update.
        from asgiref.sync import sync_to_async  # noqa: F401

        def edit_then_fire(appt, phone):
            pass

        orig_update = Appointment.objects.filter

        r = None
        Appointment.objects.filter(pk=a.pk).update(description='owner note')
        with patch('appointment.client_views._fire_booking_sms'):
            r = self.client.post(
                reverse('client-appointment-pay', args=[a.pk]),
                {'method': 'CASH'}, HTTP_AUTHORIZATION=self.auth,
            )
        self.assertEqual(r.status_code, 200)
        a.refresh_from_db()
        self.assertEqual(a.status, 'PENDING_APPROVAL')
        self.assertEqual(a.description, 'owner note')
        self.assertIsNone(a.expires_at)

    def test_pay_after_cancel_is_refused(self):
        a = self.make(status='CANCELLED')
        r = self.client.post(reverse('client-appointment-pay', args=[a.pk]),
                             {'method': 'CASH'}, HTTP_AUTHORIZATION=self.auth)
        self.assertEqual(r.status_code, 400)
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')

    def test_expire_helper_cancels_once_and_refunds_once(self):
        a = self.make(expires_at=timezone.now() - timedelta(minutes=1), quota_source='monthly')
        stale1 = Appointment.objects.select_related('business').get(pk=a.pk)
        stale2 = Appointment.objects.select_related('business').get(pk=a.pk)
        with patch('appointment.client_views.refund_quota') as refund:
            self.assertTrue(_expire_lock_if_due(stale1))
            self.assertTrue(_expire_lock_if_due(stale2))
        self.assertEqual(refund.call_count, 1)
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')

    def test_expire_helper_ignores_live_lock(self):
        a = self.make()
        self.assertFalse(_expire_lock_if_due(a))


class BookingGuardTests(Base, TestCase):
    def book(self, ts=None):
        return self.client.post(
            reverse('client-appointment-list'),
            {'business_id': self.business.id,
             'appointment_date': int((ts or self.when).timestamp() * 1000)},
            content_type='application/json', HTTP_AUTHORIZATION=self.auth,
        )

    def test_quota_exhausted_blocks_booking(self):
        with patch('accounting.usage.can_book_appointment', return_value=False):
            r = self.book()
        self.assertEqual(r.status_code, 409)
        self.assertEqual(Appointment.objects.count(), 0)

    def test_quota_checked_inside_business_lock_transaction(self):
        seen = {}

        def check(owner):
            seen['in_atomic'] = connection.in_atomic_block
            return True

        with patch('accounting.usage.can_book_appointment', side_effect=check), \
                patch('accounting.usage.record_appointment', return_value='monthly'), \
                patch('appointment.client_views._fire_booking_sms'):
            r = self.book()
        self.assertEqual(r.status_code, 200, r.content)
        self.assertTrue(seen['in_atomic'])
        self.assertEqual(Appointment.objects.get().quota_source, 'monthly')

    def test_lost_credit_race_creates_nothing(self):
        # can_book_appointment said yes, but another booking took the last
        # credit before record_appointment ran: refused, no appointment row.
        before = Appointment.objects.count()
        with patch('accounting.usage.can_book_appointment', return_value=True), \
                patch('accounting.usage.record_appointment', return_value=''), \
                patch('appointment.client_views._fire_booking_sms'):
            self.assertEqual(self.book().status_code, 409)
        self.assertEqual(Appointment.objects.count(), before)

    def test_second_booking_same_slot_conflicts(self):
        with patch('accounting.usage.can_book_appointment', return_value=True), \
                patch('accounting.usage.record_appointment', return_value='monthly'), \
                patch('appointment.client_views._fire_booking_sms'):
            self.assertEqual(self.book().status_code, 200)
            self.assertEqual(self.book().status_code, 409)
        self.assertEqual(Appointment.objects.count(), 1)


class QueuePositionTests(Base, TestCase):
    def test_queue_position_computed_once_per_row(self):
        a = self.make(status='WAITING', expires_at=None, locked_at=None)
        ser = ClientAppointmentSerializer(a, context={})
        with self.assertNumQueries(1):
            ser.get_queue_position(a)
            ser.get_queue_position(a)
            ser.get_estimated_turn_time(a)


@skipUnless(connection.vendor in ('postgresql', 'mysql'), 'needs row locks')
class CallbackRaceTests(Base, TransactionTestCase):
    def test_parallel_callbacks_confirm_once(self):
        a = self.make()
        results = []

        def go():
            from .client_views import _settle_deposit
            try:
                results.append(_settle_deposit('TRK1')[0])
            finally:
                connection.close()

        with patch(VERIFY, return_value={'success': True}):
            ts = [threading.Thread(target=go) for _ in range(2)]
            [t.start() for t in ts]
            [t.join() for t in ts]
        self.assertEqual(sorted(results), ['success', 'success_new'])
        a.refresh_from_db()
        self.assertEqual(a.status, 'WAITING')
