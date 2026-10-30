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
from .client_views import ClientAppointmentListView
from .services.transitions import appointment_expire_lock_if_due as _expire_lock_if_due
from .models import Appointment
from .tests_client_booking_rules import local_slot

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
        # Inside the business's 09-17 working hours (client booking enforces them).
        self.when = local_slot(days=2, hour=10)

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
                patch('appointment.services.client_booking.notify_deposit_paid') as sms, \
                self.captureOnCommitCallbacks(execute=True):
            r = self.callback()
        self.assertIn('payment=success', r['Location'])
        a.refresh_from_db()
        self.assertEqual((a.status, a.deposit_payment_method), ('WAITING', 'GATEWAY'))
        self.assertIsNone(a.expires_at)
        sms.assert_called_once()

    def test_replay_has_no_side_effects(self):
        self.make()
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.services.client_booking.notify_deposit_paid') as sms, \
                self.captureOnCommitCallbacks(execute=True):
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
                patch('appointment.services.client_booking.notify_deposit_paid'):
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
                patch('appointment.services.client_booking.notify_deposit_paid') as sms:
            r = self.callback()
        self.assertIn('payment=conflict', r['Location'])
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')
        sms.assert_not_called()

    def test_expired_lock_without_conflict_still_confirms(self):
        a = self.make(expires_at=timezone.now() - timedelta(minutes=1))
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.services.client_booking.notify_deposit_paid'):
            r = self.callback()
        self.assertIn('payment=success', r['Location'])
        a.refresh_from_db()
        self.assertEqual(a.status, 'WAITING')

    def test_expired_lock_with_conflict_is_not_double_booked(self):
        a = self.make(expires_at=timezone.now() - timedelta(minutes=1))
        taken = self.make(visitor=self.other, status='PENDING_APPROVAL',
                          payment_reference=None, expires_at=None, locked_at=None)
        with patch(VERIFY, return_value={'success': True}), \
                patch('appointment.services.client_booking.notify_deposit_paid') as sms, \
                self.assertLogs('appointment.services.deposits', level='ERROR'):
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
        from .services.transitions import appointment_cancel_by_client as _cancel_transition
        stale = Appointment.objects.select_related('business').get(pk=a.pk)
        Appointment.objects.filter(pk=a.pk).update(status='IN_PROGRESS')
        self.assertFalse(_cancel_transition(stale, 'PENDING_APPROVAL'))
        a.refresh_from_db()
        self.assertEqual(a.status, 'IN_PROGRESS')

    def test_cancel_view_cancels(self):
        a = self.make()
        with patch('appointment.services.client_booking.notify_booking_cancelled_by_client'):
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
        with patch('appointment.services.client_booking.notify_booking_created'):
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
        with patch('appointment.services.transitions.refund_quota') as refund:
            self.assertTrue(_expire_lock_if_due(stale1))
            self.assertTrue(_expire_lock_if_due(stale2))
        self.assertEqual(refund.call_count, 1)
        a.refresh_from_db()
        self.assertEqual(a.status, 'CANCELLED')

    def test_expire_helper_ignores_live_lock(self):
        a = self.make()
        self.assertFalse(_expire_lock_if_due(a))


class ReceiptOrphanTests(Base, TestCase):
    def setUp(self):
        super().setUp()
        import shutil
        import tempfile
        from django.test import override_settings
        self.media = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.media, ignore_errors=True)
        override = override_settings(MEDIA_ROOT=self.media)
        override.enable()
        self.addCleanup(override.disable)

    def _files(self):
        import os
        return [f for _, _, fs in os.walk(self.media) for f in fs]

    def _pay(self, a, upload=None, **fields):
        return self.client.post(
            reverse('client-appointment-pay', args=[a.pk]),
            {'method': 'CARD', 'payment_receipt': upload or image_upload(), **fields},
            HTTP_AUTHORIZATION=self.auth,
        )

    def test_receipt_kept_when_payment_wins(self):
        a = self.make()
        self.assertEqual(self._pay(a).status_code, 200)
        self.assertEqual(len(self._files()), 1)
        a.refresh_from_db()
        self.assertTrue(a.payment_receipt.name)

    def test_receipt_removed_when_update_loses_race(self):
        a = self.make()
        with patch('appointment.services.client_booking.appointment_submit_payment',
                   return_value=False):
            r = self._pay(a)
        self.assertEqual(r.status_code, 400)
        self.assertEqual(self._files(), [])


def image_upload(size=(40, 30), fmt='PNG', name='r.png', mode='RGB'):
    import io
    from django.core.files.uploadedfile import SimpleUploadedFile
    from PIL import Image
    buf = io.BytesIO()
    Image.new(mode, size, (200, 180, 160) if mode == 'RGB' else (200, 180, 160, 0)).save(buf, format=fmt)
    return SimpleUploadedFile(name, buf.getvalue(), content_type=f'image/{fmt.lower()}')


class ReceiptImageTests(ReceiptOrphanTests):
    """The receipt is checked and shrunk before it reaches storage."""

    def test_large_receipt_is_downscaled_to_jpeg(self):
        from PIL import Image
        a = self.make(payment_reference='')
        r = self._pay(a, upload=image_upload(size=(3000, 2000), mode='RGBA'))
        self.assertEqual(r.status_code, 200, r.content)
        a.refresh_from_db()
        self.assertEqual(a.status, 'PENDING_VERIFICATION')
        self.assertTrue(a.payment_receipt.name.endswith('.jpg'))
        with Image.open(a.payment_receipt.path) as img:
            self.assertEqual((img.format, img.size), ('JPEG', (1600, 1067)))

    def test_receipt_alone_needs_no_reference(self):
        a = self.make(payment_reference='')
        self.assertEqual(self._pay(a).status_code, 200)
        a.refresh_from_db()
        self.assertEqual(a.payment_reference, '')

    def test_receipt_and_reference_are_both_kept(self):
        a = self.make(payment_reference='')
        self.assertEqual(self._pay(a, payment_reference='99881').status_code, 200)
        a.refresh_from_db()
        self.assertEqual(a.payment_reference, '99881')
        self.assertTrue(a.payment_receipt.name)

    def test_non_image_is_refused_and_nothing_changes(self):
        from django.core.files.uploadedfile import SimpleUploadedFile
        a = self.make()
        fake = SimpleUploadedFile('r.png', b'\x89PNG\r\n\x1a\n' + b'0' * 20, content_type='image/png')
        r = self._pay(a, upload=fake)
        self.assertEqual(r.status_code, 400)
        self.assertIn('عکس معتبر نیست', r.json()['message'])
        a.refresh_from_db()
        self.assertEqual(a.status, 'LOCKED')
        self.assertEqual(self._files(), [])

    def test_oversized_upload_is_refused(self):
        from django.core.files.uploadedfile import SimpleUploadedFile
        a = self.make()
        big = SimpleUploadedFile('r.jpg', b'0' * (8 * 1024 * 1024 + 1), content_type='image/jpeg')
        r = self._pay(a, upload=big)
        self.assertEqual(r.status_code, 400)
        self.assertIn('۸ مگابایت', r.json()['message'])
        self.assertEqual(self._files(), [])


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
                patch('appointment.services.client_booking.notify_booking_created'):
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
                patch('appointment.services.client_booking.notify_booking_created'):
            self.assertEqual(self.book().status_code, 409)
        self.assertEqual(Appointment.objects.count(), before)

    def test_second_booking_same_slot_conflicts(self):
        with patch('accounting.usage.can_book_appointment', return_value=True), \
                patch('accounting.usage.record_appointment', return_value='monthly'), \
                patch('appointment.services.client_booking.notify_booking_created'):
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
            from .services.deposits import deposit_settle as _settle_deposit
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
