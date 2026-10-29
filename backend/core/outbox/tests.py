"""Outbox: enqueue semantics, worker retry/backoff, and the migrated call sites."""

from datetime import timedelta
from io import StringIO
from types import SimpleNamespace
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.db import transaction
from django.test import TestCase
from django.utils import timezone

from business.models import Business
from core.outbox import service
from core.outbox.models import OutboxMessage
from core.outbox.service import enqueue, register

User = get_user_model()

CALLS = []


@register('test.ok')
def _ok(payload):
    CALLS.append(payload)


@register('test.boom')
def _boom(payload):
    raise RuntimeError('provider down')


@register('test.flaky')
def _flaky(payload):
    CALLS.append(payload)
    if len(CALLS) < 3:
        raise RuntimeError('try again')


def drain():
    while service.run_batch():
        pass


def make_due(msg):
    OutboxMessage.objects.filter(pk=msg.pk).update(next_attempt_at=timezone.now() - timedelta(seconds=1))


class EnqueueTests(TestCase):
    def setUp(self):
        CALLS.clear()

    def test_enqueue_then_once_sends_exactly_once(self):
        enqueue('test.ok', {'n': 1})
        call_command('run_outbox', '--once', stdout=StringIO())
        call_command('run_outbox', '--once', stdout=StringIO())
        self.assertEqual(CALLS, [{'n': 1}])
        msg = OutboxMessage.objects.get()
        self.assertEqual(msg.status, OutboxMessage.DONE)
        self.assertIsNotNone(msg.processed_at)

    def test_dedupe_key_is_idempotent(self):
        self.assertIsNotNone(enqueue('test.ok', {'n': 1}, dedupe_key='k1'))
        self.assertIsNone(enqueue('test.ok', {'n': 2}, dedupe_key='k1'))
        drain()
        self.assertEqual(CALLS, [{'n': 1}])
        self.assertEqual(OutboxMessage.objects.count(), 1)

    def test_dedupe_inside_a_transaction_does_not_poison_it(self):
        with transaction.atomic():
            enqueue('test.ok', {}, dedupe_key='k')
            enqueue('test.ok', {}, dedupe_key='k')
            enqueue('test.ok', {}, dedupe_key='other')
        self.assertEqual(OutboxMessage.objects.count(), 2)

    def test_rollback_of_outer_transaction_enqueues_nothing(self):
        try:
            with transaction.atomic():
                enqueue('test.ok', {'n': 1})
                raise RuntimeError('rollback')
        except RuntimeError:
            pass
        self.assertEqual(OutboxMessage.objects.count(), 0)

    def test_future_rows_are_not_picked_up(self):
        enqueue('test.ok', {}, delay_seconds=3600)
        drain()
        self.assertEqual(CALLS, [])

    def test_retry_with_backoff_then_success(self):
        msg = enqueue('test.flaky', {'n': 1})
        drain()
        msg.refresh_from_db()
        self.assertEqual((msg.status, msg.attempts), (OutboxMessage.PENDING, 1))
        self.assertIn('try again', msg.last_error)
        self.assertGreater(msg.next_attempt_at, timezone.now() + timedelta(seconds=15))
        drain()  # not due yet: nothing happens
        self.assertEqual(len(CALLS), 1)

        make_due(msg)
        drain()
        make_due(msg)
        drain()
        msg.refresh_from_db()
        self.assertEqual((msg.status, msg.attempts), (OutboxMessage.DONE, 2))
        self.assertEqual(len(CALLS), 3)

    def test_backoff_grows_and_is_capped(self):
        a, b, c = (service.backoff_seconds(n) for n in (1, 2, 3))
        self.assertTrue(24 <= a <= 36)
        self.assertTrue(48 <= b <= 72)
        self.assertTrue(96 <= c <= 144)
        self.assertLessEqual(service.backoff_seconds(50), service.BACKOFF_CAP_SECONDS * 1.2)

    def test_fails_permanently_after_max_attempts(self):
        msg = enqueue('test.boom', {})
        for _ in range(service.MAX_ATTEMPTS):
            make_due(msg)
            drain()
        msg.refresh_from_db()
        self.assertEqual(msg.status, OutboxMessage.FAILED)
        self.assertEqual(msg.attempts, service.MAX_ATTEMPTS)
        self.assertIn('provider down', msg.last_error)
        make_due(msg)
        drain()
        msg.refresh_from_db()
        self.assertEqual(msg.attempts, service.MAX_ATTEMPTS)

    def test_unknown_kind_fails_instead_of_blocking_the_queue(self):
        enqueue('nope.nothing', {})
        enqueue('test.ok', {'n': 1})
        drain()
        self.assertEqual(CALLS, [{'n': 1}])

    def test_stuck_processing_rows_are_recovered(self):
        msg = enqueue('test.ok', {})
        OutboxMessage.objects.filter(pk=msg.pk).update(
            status=OutboxMessage.PROCESSING, locked_at=timezone.now() - timedelta(minutes=11),
        )
        fresh = enqueue('test.ok', {'fresh': 1})
        OutboxMessage.objects.filter(pk=fresh.pk).update(
            status=OutboxMessage.PROCESSING, locked_at=timezone.now(),
        )
        self.assertEqual(service.recover_stuck(), 1)
        msg.refresh_from_db()
        fresh.refresh_from_db()
        self.assertEqual(msg.status, OutboxMessage.PENDING)
        self.assertEqual(fresh.status, OutboxMessage.PROCESSING)

    def test_safe_enqueue_never_raises(self):
        with patch('core.outbox.service.enqueue', side_effect=RuntimeError('db down')):
            self.assertIsNone(service.safe_enqueue('test.ok', {}))


def _appointment():
    from appointment.models import Appointment
    from visitor.models import Visitor

    owner = User.objects.create_user(phone='09120000990', password='x', name='o')
    business = Business.objects.create(
        user=owner, title='سالن', phone='02100000000', address='t',
        default_service_duration=30, work_start_hour=9, work_end_hour=17,
        moderation_status=Business.MODERATION_APPROVED,
    )
    visitor = Visitor.objects.create(full_name='مشتری', phone_number='09350000990')
    appt = Appointment.objects.create(
        business=business, visitor=visitor,
        appointment_date=timezone.now() + timedelta(days=1), status='PENDING_APPROVAL',
    )
    appt.business, appt.visitor = business, visitor
    return appt


class CallSiteTests(TestCase):
    """Each migrated call site queues the right kind, and the handler runs the old sender."""

    def test_booking_sms_queued_then_sent_once_by_worker(self):
        from appointment.client_views import _fire_booking_sms
        from visitor.models import SmsLog

        appt = _appointment()
        with patch('api.sms.send_sms', return_value=(True, '')) as send, \
                patch('accounting.usage.consume_sms', return_value=object()):
            _fire_booking_sms(appt, appt.visitor.phone_number)
            _fire_booking_sms(appt, appt.visitor.phone_number)  # double submit
            send.assert_not_called()
            msg = OutboxMessage.objects.get(kind='appointment.booking_sms')
            self.assertEqual(msg.dedupe_key, f'booking_sms:{appt.id}')
            drain()
            drain()
        client_calls = [c for c in send.call_args_list if c[0][0] == appt.visitor.phone_number]
        self.assertEqual(len(client_calls), 1)
        self.assertIn('در انتظار تایید کسب‌وکار', client_calls[0][0][1])
        self.assertTrue(SmsLog.objects.filter(business=appt.business, status='SENT').exists())

    def test_cancellation_and_deposit_use_their_own_keys(self):
        from appointment.client_views import _fire_cancellation_sms, _fire_deposit_paid_sms

        appt = _appointment()
        _fire_cancellation_sms(appt)
        _fire_deposit_paid_sms(appt)
        keys = set(OutboxMessage.objects.filter(kind='appointment.booking_sms').values_list('dedupe_key', flat=True))
        self.assertEqual(keys, {f'cancel_sms:{appt.id}', f'deposit_sms:{appt.id}'})
        cancel = OutboxMessage.objects.get(dedupe_key=f'cancel_sms:{appt.id}')
        self.assertIsNone(cancel.payload['client_phone'])  # client cancelled: no client SMS

    def test_owner_decision_queues_client_sms(self):
        from appointment.views.views import _notify_client_of_decision

        appt = _appointment()
        _notify_client_of_decision(appt, 'WAITING', 'IN_PROGRESS')  # not a decision
        self.assertEqual(OutboxMessage.objects.count(), 0)
        _notify_client_of_decision(appt, 'PENDING_APPROVAL', 'WAITING')
        msg = OutboxMessage.objects.get(kind='appointment.client_sms')
        self.assertEqual(msg.payload['phone'], appt.visitor.phone_number)
        with patch('api.sms.send_sms', return_value=(True, '')) as send, \
                patch('accounting.usage.consume_sms', return_value=object()):
            drain()
        send.assert_called_once()
        self.assertIn('تایید شد', send.call_args[0][1])

    def test_decision_rolls_back_with_its_transaction(self):
        from appointment.views.views import _notify_client_of_decision

        appt = _appointment()
        try:
            with transaction.atomic():
                _notify_client_of_decision(appt, 'PENDING_APPROVAL', 'WAITING')
                raise RuntimeError
        except RuntimeError:
            pass
        self.assertEqual(OutboxMessage.objects.count(), 0)

    def test_bale_review_card_is_queued_in_the_callers_transaction(self):
        from bale.notify import notify_review_queued

        appt = _appointment()
        try:
            with transaction.atomic():
                notify_review_queued(appt.business)
                raise RuntimeError
        except RuntimeError:
            pass
        self.assertEqual(OutboxMessage.objects.count(), 0)

        with transaction.atomic():
            notify_review_queued(appt.business, kind='edit', changed=['title'])
        msg = OutboxMessage.objects.get(kind='bale.review_card')
        self.assertEqual(msg.payload, {'business_id': appt.business.pk, 'kind': 'edit', 'changed': ['title']})
        with patch('bale.notify._send') as send:
            drain()
        send.assert_called_once_with(appt.business.pk, 'edit', ['title'])

    def test_owner_moderation_notice_is_queued(self):
        from bale.notify import fire_owner_sms

        fire_owner_sms(7, 'APPROVED')
        msg = OutboxMessage.objects.get(kind='bale.owner_moderation_notify')
        self.assertEqual(msg.payload, {'business_id': 7, 'to_status': 'APPROVED'})

    def test_promo_campaign_is_one_deduped_message(self):
        from business import promo_sms

        promo_sms.start_background_send(41)
        promo_sms.start_background_send(41)
        self.assertEqual(OutboxMessage.objects.filter(kind='promo.campaign').count(), 1)
        with patch('business.promo_sms.process_campaign') as process:
            drain()
        process.assert_called_once_with(41)


class AdminBulkSendTests(TestCase):
    def test_push_campaign_fans_out_in_chunks_and_counts_accumulate(self):
        from api.services.push import SendResult
        from core import messaging
        from core.campaigns import PushCampaign
        from core.models import MarketingPushLog

        reachable = [(i, f'n{i}') for i in range(1, 251)]
        campaign = PushCampaign.create(title='t', body='سلام {full_name}')
        with patch.dict(messaging._AUDIENCE_DISPATCH, {'visitor': lambda f, e: (300, reachable)}), \
                patch('api.services.push.is_configured', return_value=True), \
                patch.object(messaging, '_deliver', return_value=SendResult(delivered=1)) as deliver:
            log = messaging.send_push_campaign(campaign, 'visitor', {}, {})
            self.assertEqual((log.recipient_count, log.reachable_count, log.delivered_count), (300, 250, 0))
            self.assertEqual(OutboxMessage.objects.filter(kind='core.push_campaign_chunk').count(), 3)
            deliver.assert_not_called()
            drain()
        self.assertEqual(deliver.call_count, 250)
        self.assertEqual(deliver.call_args_list[0][0][2].body, 'سلام n1')
        self.assertEqual(MarketingPushLog.objects.get(pk=log.pk).delivered_count, 250)

    def test_admin_message_queues_and_logs_on_delivery(self):
        from core.messaging import send_admin_message
        from core.models import AdminMessageLog

        owner = User.objects.create_user(phone='09120000991', password='x', name='o')
        business = Business.objects.create(
            user=owner, title='سالن', phone='0', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
        )
        with patch('api.services.push.is_configured', return_value=False):
            logs = send_admin_message(business, 'T', 'سلام {business_name}', ['PUSH', 'SMS'])
        # PUSH fails up front (FCM unconfigured); SMS is queued.
        self.assertEqual([l.channel for l in logs], ['PUSH'])
        self.assertEqual(AdminMessageLog.objects.count(), 1)
        with patch('api.sms.send_sms', return_value=(True, '')) as send:
            drain()
        send.assert_called_once()
        self.assertEqual(AdminMessageLog.objects.filter(channel='SMS', status='SENT').count(), 1)


BULK_CALLS = []


@register('test.campaign_chunk')
def _bulk(payload):
    BULK_CALLS.append(payload)


class LaneTests(TestCase):
    def setUp(self):
        CALLS.clear()
        BULK_CALLS.clear()

    def test_lane_of_routes_kinds(self):
        for kind in ('promo.campaign', 'core.push_campaign_chunk', 'core.expiry_sms_chunk',
                     'x.some_campaign', 'test.campaign_chunk'):
            self.assertEqual(service.lane_of(kind), 'bulk', kind)
        for kind in ('appointment.booking_sms', 'appointment.client_sms', 'bale.review_card',
                     'core.admin_message'):
            self.assertEqual(service.lane_of(kind), 'default', kind)

    def test_each_lane_only_claims_its_kinds(self):
        enqueue('test.campaign_chunk', {'b': 1})
        enqueue('test.ok', {'d': 1})
        d = service.claim_one('default')
        self.assertEqual(d.kind, 'test.ok')
        self.assertIsNone(service.claim_one('default'))
        b = service.claim_one('bulk')
        self.assertEqual(b.kind, 'test.campaign_chunk')
        self.assertIsNone(service.claim_one('bulk'))

    def test_bulk_backlog_does_not_delay_default_lane(self):
        for i in range(30):
            enqueue('test.campaign_chunk', {'i': i})
        enqueue('test.ok', {'sms': 1})
        call_command('run_outbox', '--once', '--lane', 'default', stdout=StringIO())
        self.assertEqual(CALLS, [{'sms': 1}])
        self.assertEqual(BULK_CALLS, [])
        self.assertEqual(OutboxMessage.objects.filter(status=OutboxMessage.PENDING).count(), 30)
        call_command('run_outbox', '--once', '--lane', 'bulk', stdout=StringIO())
        self.assertEqual(len(BULK_CALLS), 30)

    def test_all_lane_is_backward_compatible(self):
        enqueue('test.campaign_chunk', {})
        enqueue('test.ok', {})
        call_command('run_outbox', '--once', stdout=StringIO())
        self.assertEqual((len(CALLS), len(BULK_CALLS)), (1, 1))


class ClaimAndRecoveryTests(TestCase):
    def setUp(self):
        CALLS.clear()

    def test_locked_at_is_stamped_per_message_at_claim_time(self):
        a, b = enqueue('test.ok', {'n': 1}), enqueue('test.ok', {'n': 2})
        first = service.claim_one()
        OutboxMessage.objects.filter(pk=first.pk).update(locked_at=timezone.now() - timedelta(minutes=9))
        second = service.claim_one()
        second.refresh_from_db()
        self.assertGreater(second.locked_at, timezone.now() - timedelta(seconds=30))
        self.assertEqual(service.recover_stuck(), 0)  # 9 min < 10 min threshold
        OutboxMessage.objects.filter(pk=first.pk).update(locked_at=timezone.now() - timedelta(minutes=11))
        self.assertEqual(service.recover_stuck(), 1)  # truly dead row
        self.assertEqual(OutboxMessage.objects.get(pk=first.pk).status, OutboxMessage.PENDING)
        self.assertEqual(OutboxMessage.objects.get(pk=second.pk).status, OutboxMessage.PROCESSING)

    def test_heartbeat_prevents_recovery(self):
        enqueue('test.ok', {})
        msg = service.claim_one()
        OutboxMessage.objects.filter(pk=msg.pk).update(locked_at=timezone.now() - timedelta(minutes=11))
        self.assertEqual(service.heartbeat(msg.pk), 1)
        self.assertEqual(service.recover_stuck(), 0)
        self.assertEqual(OutboxMessage.objects.get(pk=msg.pk).status, OutboxMessage.PROCESSING)

    def test_heartbeat_ignores_finished_rows(self):
        m = enqueue('test.ok', {})
        drain()
        self.assertEqual(service.heartbeat(m.pk), 0)

    def test_beat_loop_refreshes_while_handler_runs(self):
        import threading
        enqueue('test.ok', {})
        msg = service.claim_one()
        old = timezone.now() - timedelta(minutes=11)
        OutboxMessage.objects.filter(pk=msg.pk).update(locked_at=old)
        stop = threading.Event()
        with patch('core.outbox.service.connection.close'):
            with patch.object(stop, 'wait', side_effect=[False, True]):
                service._beat_loop(msg.pk, stop, 0)
        self.assertGreater(OutboxMessage.objects.get(pk=msg.pk).locked_at, old)


class SmsIdempotencyTests(TestCase):
    def _run(self, **kw):
        with patch('api.sms.send_sms', return_value=(True, '')) as send, \
                patch('accounting.usage.consume_sms', return_value=object()) as consume:
            drain()
        return send, consume

    def test_retry_after_crash_post_send_does_not_send_or_charge_twice(self):
        from appointment.client_views import _fire_booking_sms

        appt = _appointment()
        _fire_booking_sms(appt, appt.visitor.phone_number)
        msg = OutboxMessage.objects.get(kind='appointment.booking_sms')
        with patch('api.sms.send_sms', return_value=(True, '')) as send, \
                patch('accounting.usage.consume_sms', return_value=object()) as consume:
            # Worker dies right after the provider accepted the SMS: the row
            # stays 'processing', never marked done.
            claimed = service.claim_one()
            service._current.message_id = claimed.id
            try:
                service.get_handler(claimed.kind)(claimed.payload)
            finally:
                service._current.message_id = None
            self.assertEqual(send.call_count, 1)
            self.assertEqual(consume.call_count, 1)
            msg.refresh_from_db()
            self.assertIn('client_sms', msg.done_steps)
            self.assertEqual(msg.status, OutboxMessage.PROCESSING)
            # Recovery resets it (dead worker) and the next worker retries.
            OutboxMessage.objects.filter(pk=msg.pk).update(locked_at=timezone.now() - timedelta(minutes=11))
            service.recover_stuck()
            drain()
        self.assertEqual(send.call_count, 1)
        self.assertEqual(consume.call_count, 1)
        msg.refresh_from_db()
        self.assertEqual(msg.status, OutboxMessage.DONE)

    def test_client_sms_retry_does_not_resend(self):
        from appointment.views.views import _notify_client_of_decision

        appt = _appointment()
        _notify_client_of_decision(appt, 'PENDING_APPROVAL', 'WAITING')
        msg = OutboxMessage.objects.get(kind='appointment.client_sms')
        with patch('api.sms.send_sms', return_value=(True, '')) as send, \
                patch('accounting.usage.consume_sms', return_value=object()) as consume:
            for _ in range(2):  # second pass simulates a recovered row
                claimed = service.claim_one()
                self.assertIsNotNone(claimed)
                service.process_message(claimed)
                OutboxMessage.objects.filter(pk=msg.pk).update(
                    status=OutboxMessage.PENDING, next_attempt_at=timezone.now() - timedelta(seconds=1))
        self.assertEqual((send.call_count, consume.call_count), (1, 1))

    def test_failed_send_is_not_marked_and_is_refunded(self):
        from appointment.views.views import _notify_client_of_decision

        appt = _appointment()
        _notify_client_of_decision(appt, 'PENDING_APPROVAL', 'WAITING')
        with patch('api.sms.send_sms', return_value=(False, 'down')), \
                patch('accounting.usage.consume_sms', return_value=object()), \
                patch('accounting.usage.refund_sms') as refund:
            drain()
        refund.assert_called_once()
        self.assertEqual(OutboxMessage.objects.get(kind='appointment.client_sms').done_steps, [])
