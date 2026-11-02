"""
Pins the charge -> send -> SmsLog -> refund behaviour of every metered SMS path
against the REAL usage/ledger code (only the provider call is stubbed):

* appointment outbox SMS  (appointment.services.notifications)
* appointment reminders   (appointment.services.reminders / send_appointment_reminders)
* owner promo campaigns   (business.promo_sms)

All three go through accounting.sms_billing.send_metered_sms; these tests are
written to hold identically before and after that consolidation.
"""

from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone

from accounting import entitlements, usage
from accounting.models import CreditBalance, CreditLedger, Plan, Subscription
from appointment.models import Appointment
from appointment.services import notifications
from business import promo_sms
from business.models import Business, PromoCampaign, PromoCampaignRecipient
from visitor.models import SmsLog, Visitor

User = get_user_model()

SEND = 'api.sms.send_sms'


class _Base(TestCase):
    def setUp(self):
        cache.clear()
        self.addCleanup(cache.clear)
        self.owner = User.objects.create_user(phone='09120000101', password='x', name='مالک')
        self.business = Business.objects.create(
            user=self.owner, title='سالن', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            notification_enabled=True, notification_minutes_before=30,
            enable_reminder_sms=True, reminder_delivery='PANEL',
        )
        self.visitor = Visitor.objects.create(full_name='مشتری', phone_number='09350000101')

    def subscribe(self, monthly_sms=5, wallet=0):
        plan = Plan.objects.create(
            name='پلن', price=1000, description=[], duration_value=1, duration_unit='month',
            features={entitlements.QUOTA_MAX_BUSINESSES: 1,
                      entitlements.QUOTA_MONTHLY_APPOINTMENTS: 10,
                      entitlements.QUOTA_MONTHLY_SMS: monthly_sms},
        )
        Subscription.objects.create(
            user=self.owner, plan=plan, status='active', ends_at=timezone.now() + timedelta(days=30),
        )
        if wallet:
            usage.add_wallet(self.owner.id, wallet)

    def ledger(self, metric=None):
        qs = CreditLedger.objects.filter(user_id=self.owner.id)
        if metric:
            qs = qs.filter(metric=metric)
        return [(r.metric, r.delta, r.reason) for r in qs.order_by('id')]

    def sms_used(self):
        return usage.get_usage(self.owner.id, usage.METRIC_SMS)


class OutboxSmsTests(_Base):
    def send(self, ok=True, err='', step=None, owner_id='owner'):
        owner = self.owner.id if owner_id == 'owner' else owner_id
        with patch(SEND, return_value=(ok, err)) as send:
            notifications._send_metered_sms('client', owner, self.business.id, self.visitor.id,
                                            '09350000101', 'سلام', step=step)
        return send

    def test_success_charges_once_and_logs_sent(self):
        self.subscribe()
        send = self.send()
        self.assertEqual(send.call_count, 1)
        self.assertEqual(self.sms_used(), 1)
        self.assertEqual(self.ledger(), [('sms_monthly', 1, usage.REASON_SMS_SEND)])
        log = SmsLog.objects.get()
        self.assertEqual((log.status, log.error_detail, log.visitor_id), ('SENT', '', self.visitor.id))

    def test_provider_failure_refunds_and_logs_failed(self):
        self.subscribe()
        self.send(ok=False, err='rejected')
        self.assertEqual(self.sms_used(), 0)
        self.assertEqual(self.ledger(), [('sms_monthly', 1, usage.REASON_SMS_SEND),
                                         ('sms_monthly', -1, usage.REASON_SMS_REFUND)])
        log = SmsLog.objects.get()
        self.assertEqual((log.status, log.error_detail), ('FAILED', 'rejected'))

    def test_provider_failure_refunds_wallet_portion(self):
        self.subscribe(monthly_sms=0, wallet=3)
        self.send(ok=False, err='x')
        self.assertEqual(usage.get_wallet(self.owner.id), 3)
        self.assertEqual(self.ledger('sms_wallet')[-2:], [('sms_wallet', -1, usage.REASON_SMS_SEND),
                                                          ('sms_wallet', 1, usage.REASON_SMS_REFUND)])

    def test_quota_exhausted_skips_send_and_logs(self):
        self.subscribe(monthly_sms=0)
        send = self.send()
        self.assertEqual(send.call_count, 0)
        self.assertEqual(self.ledger(), [])
        log = SmsLog.objects.get()
        self.assertEqual((log.status, log.error_detail), ('SKIPPED_QUOTA', "اعتبار پیامک این ماه تمام شده است"))

    def test_no_owner_sends_without_charging(self):
        send = self.send(owner_id=None)
        self.assertEqual(send.call_count, 1)
        self.assertEqual(self.ledger(), [])
        self.assertEqual(SmsLog.objects.get().status, 'SENT')

    def test_step_already_done_neither_sends_nor_charges(self):
        self.subscribe()
        with patch('core.outbox.service.step_done', return_value=True), \
                patch('core.outbox.service.mark_step') as mark:
            send = self.send(step='client_sms')
        self.assertEqual(send.call_count, 0)
        self.assertEqual(self.ledger(), [])
        self.assertEqual(SmsLog.objects.count(), 0)
        mark.assert_not_called()

    def test_step_marked_on_success_and_on_quota_skip_but_not_on_failure(self):
        self.subscribe()
        with patch('core.outbox.service.step_done', return_value=False), \
                patch('core.outbox.service.mark_step') as mark:
            self.send(step='s1')
            self.assertEqual(mark.call_count, 1)
            self.send(ok=False, err='x', step='s2')
            self.assertEqual(mark.call_count, 1)
        Subscription.objects.all().delete()
        cache.clear()
        with patch('core.outbox.service.step_done', return_value=False), \
                patch('core.outbox.service.mark_step') as mark:
            self.send(step='s3')  # no subscription -> no quota
            self.assertEqual(mark.call_count, 1)

    def test_send_raising_is_swallowed(self):
        self.subscribe()
        with patch(SEND, side_effect=RuntimeError('boom')):
            notifications._send_metered_sms('client', self.owner.id, self.business.id, None, '0935', 'x')
        # A provider exception is a rejection: credit goes back, failure is logged.
        self.assertEqual(self.sms_used(), 0)
        self.assertEqual(SmsLog.objects.get().status, 'FAILED')


class ReminderSmsTests(_Base):
    def make_appointment(self):
        return Appointment.objects.create(
            business=self.business, visitor=self.visitor,
            appointment_date=timezone.now() + timedelta(minutes=10), status='WAITING',
        )

    def run_job(self, ok=True, err=''):
        with patch(SEND, return_value=(ok, err)) as send, \
                patch('api.services.push.is_configured', return_value=False), \
                patch('accounting.entitlements.has_feature', return_value=False):
            call_command('send_appointment_reminders')
        return send

    def test_success_charges_and_stamps(self):
        self.subscribe()
        appt = self.make_appointment()
        self.run_job()
        appt.refresh_from_db()
        self.assertIsNotNone(appt.reminder_sent_at)
        self.assertEqual(self.sms_used(), 1)
        self.assertEqual(self.ledger(), [('sms_monthly', 1, usage.REASON_SMS_SEND)])
        self.assertEqual(SmsLog.objects.get().status, 'SENT')

    def test_provider_failure_refunds_keeps_claim(self):
        self.subscribe()
        appt = self.make_appointment()
        self.run_job(ok=False, err='rejected')
        appt.refresh_from_db()
        self.assertIsNotNone(appt.reminder_sent_at)  # never retried every run
        self.assertEqual(self.sms_used(), 0)
        self.assertEqual(self.ledger(), [('sms_monthly', 1, usage.REASON_SMS_SEND),
                                         ('sms_monthly', -1, usage.REASON_SMS_REFUND)])
        log = SmsLog.objects.get()
        self.assertEqual((log.status, log.error_detail), ('FAILED', 'rejected'))

    def test_quota_exhausted_releases_claim_and_logs_skip(self):
        self.subscribe(monthly_sms=0)
        appt = self.make_appointment()
        send = self.run_job()
        appt.refresh_from_db()
        self.assertEqual(send.call_count, 0)
        self.assertIsNone(appt.reminder_sent_at)
        self.assertEqual(self.ledger(), [])
        log = SmsLog.objects.get()
        self.assertEqual((log.status, log.error_detail), ('SKIPPED_QUOTA', "اعتبار پیامک این ماه تمام شده است"))


class PromoSmsBillingTests(_Base):
    def setUp(self):
        super().setUp()
        self.visitor2 = Visitor.objects.create(full_name='دو', phone_number='09350000102')
        for v in (self.visitor, self.visitor2):
            Appointment.objects.create(
                business=self.business, visitor=v,
                appointment_date=timezone.now() - timedelta(days=3), status='COMPLETED',
            )

    def create(self):
        with patch('accounting.entitlements.has_feature', return_value=True), \
                patch('business.promo_sms.within_send_window', return_value=True), \
                patch('business.promo_sms.available_credit', return_value=None):
            return promo_sms.create_campaign(self.business, 'all', 'سلام {name}', self.owner)

    def process(self, campaign, results):
        with patch(SEND, side_effect=lambda phone, text: results[phone]):
            promo_sms.process_campaign(campaign.id)

    def test_charges_per_part_refunds_rejections_and_logs(self):
        self.subscribe(monthly_sms=10)
        campaign = self.create()
        parts = {r.phone: r.parts for r in campaign.recipients.all()}
        self.process(campaign, {'09350000101': (True, ''), '09350000102': (False, 'rejected')})
        self.assertEqual(self.sms_used(), parts['09350000101'])
        self.assertEqual(
            sorted(r.status for r in campaign.recipients.all()), ['FAILED', 'SENT'])
        failed = campaign.recipients.get(status='FAILED')
        self.assertEqual(failed.error_detail, 'rejected')
        self.assertIsNotNone(failed.sent_at)
        # one send row per recipient + one refund row for the rejected one
        reasons = [r[2] for r in self.ledger('sms_monthly')]
        self.assertEqual(reasons.count(usage.REASON_SMS_SEND), 2)
        self.assertEqual(reasons.count(usage.REASON_SMS_REFUND), 1)
        self.assertEqual(
            sorted(SmsLog.objects.filter(business=self.business).values_list('status', flat=True)),
            ['FAILED', 'SENT'])

    def test_provider_exception_is_a_failure_with_refund(self):
        self.subscribe(monthly_sms=10)
        campaign = self.create()
        with patch(SEND, side_effect=RuntimeError('boom')):
            promo_sms.process_campaign(campaign.id)
        self.assertEqual(self.sms_used(), 0)
        rec = campaign.recipients.first()
        self.assertEqual((rec.status, rec.error_detail), ('FAILED', 'boom'))

    def test_out_of_credit_skips_with_promo_wording(self):
        self.subscribe(monthly_sms=0)
        campaign = self.create()
        self.process(campaign, {})
        self.assertEqual(self.ledger(), [])
        self.assertEqual(
            set(campaign.recipients.values_list('status', 'error_detail')),
            {('SKIPPED_QUOTA', 'اعتبار پیامک تمام شده است')})
        self.assertEqual(
            set(SmsLog.objects.values_list('status', 'error_detail')),
            {('SKIPPED_QUOTA', 'اعتبار پیامک تمام شده است')})


class SendMeteredSmsApiTests(_Base):
    def test_returns_status_strings(self):
        from accounting import sms_billing
        self.subscribe(monthly_sms=1)
        args = (self.owner.id, self.business.id, self.visitor.id, '09350000101', 'x')
        with patch(SEND, return_value=(True, '')):
            self.assertEqual(sms_billing.send_metered_sms(*args), 'SENT')
            self.assertEqual(sms_billing.send_metered_sms(*args), 'SKIPPED_QUOTA')
        with patch(SEND, return_value=(False, 'no')):
            usage.add_wallet(self.owner.id, 1)
            self.assertEqual(sms_billing.send_metered_sms(*args), 'FAILED')
        self.assertEqual(usage.get_wallet(self.owner.id), 1)
