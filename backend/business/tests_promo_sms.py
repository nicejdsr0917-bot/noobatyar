"""
Owner promo-SMS campaigns (business/promo_sms.py, business/promo_views.py).

What matters here is money and consent: a campaign must only reach the owner's
own un-archived customers, cost exactly what the preview said, refund what the
provider rejected, never go out without the plan feature or outside the
line's delivery hours, and never text anyone twice when resumed.
"""

from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from appointment.models import Appointment
from visitor.models import SmsLog, Visitor, VisitorArchive

from . import promo_sms
from .models import Business, PromoCampaign, PromoCampaignRecipient

User = get_user_model()


def _feature(on=True):
    return patch('accounting.entitlements.has_feature', return_value=on)


def _window(open_=True):
    return patch('business.promo_sms.within_send_window', return_value=open_)


def _credit(value):
    return patch('business.promo_sms.available_credit', return_value=value)


class PromoSmsTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000011', password='x', name='مالک')
        self.other_owner = User.objects.create_user(phone='09120000012', password='x', name='دیگری')
        self.business = Business.objects.create(
            user=self.owner, title='سالن رز', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
        )
        other_business = Business.objects.create(
            user=self.other_owner, title='جای دیگر', phone='02100000001', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
        )
        now = timezone.now()
        self.recent = Visitor.objects.create(full_name='سارا', phone_number='09350000001')
        self.inactive = Visitor.objects.create(full_name='نیلوفر', phone_number='09350000002')
        self.archived = Visitor.objects.create(full_name='بایگانی', phone_number='09350000003')
        self.stranger = Visitor.objects.create(full_name='غریبه', phone_number='09350000004')
        for visitor, days_ago in ((self.recent, 5), (self.inactive, 90), (self.archived, 5)):
            Appointment.objects.create(
                business=self.business, visitor=visitor,
                appointment_date=now - timedelta(days=days_ago), status='COMPLETED',
            )
        Appointment.objects.create(
            business=other_business, visitor=self.stranger,
            appointment_date=now - timedelta(days=5), status='COMPLETED',
        )
        VisitorArchive.objects.create(owner=self.owner, visitor=self.archived)

    def phones(self, audience):
        return {r[1] for r in promo_sms._recipients(self.business, audience, 'سلام {name}')}

    # ── audience ──

    def test_audiences_cover_only_own_unarchived_customers(self):
        self.assertEqual(self.phones('all'), {'09350000001', '09350000002'})
        self.assertEqual(self.phones('recent'), {'09350000001'})
        self.assertEqual(self.phones('inactive'), {'09350000002'})

    # ── text & cost ──

    def test_render_fills_tokens_and_appends_optout(self):
        text = promo_sms.render('{name} عزیز، {business} منتظر شماست.', name='سارا', business_title='سالن رز')
        self.assertTrue(text.startswith('سارا عزیز، سالن رز منتظر شماست'))
        self.assertNotIn('.', text)  # the line reads a full stop as a link
        self.assertTrue(text.endswith('لغو11'))

    def test_sms_parts_uses_ucs2_limits_for_persian(self):
        self.assertEqual(promo_sms.sms_parts('س' * 70), 1)
        self.assertEqual(promo_sms.sms_parts('س' * 71), 2)
        self.assertEqual(promo_sms.sms_parts('س' * 134), 2)
        self.assertEqual(promo_sms.sms_parts('س' * 135), 3)
        self.assertEqual(promo_sms.sms_parts('a' * 160), 1)

    def test_preview_totals_real_recipients(self):
        with _window(), _credit(100):
            data = promo_sms.preview(self.business, 'all', 'سلام {name}')
        self.assertEqual(data['recipient_count'], 2)
        self.assertEqual(data['total_parts'], 2)
        self.assertTrue(data['enough_credit'])

    # ── refusals ──

    def test_refused_without_plan_feature(self):
        with _feature(False), _window(), _credit(100):
            with self.assertRaises(promo_sms.CampaignError) as ctx:
                promo_sms.create_campaign(self.business, 'all', 'سلام', self.owner)
        self.assertEqual(ctx.exception.code, 'feature')

    def test_refused_outside_delivery_hours(self):
        with _feature(), _window(False), _credit(100):
            with self.assertRaises(promo_sms.CampaignError) as ctx:
                promo_sms.create_campaign(self.business, 'all', 'سلام', self.owner)
        self.assertEqual(ctx.exception.code, 'window')

    def test_refused_when_credit_does_not_cover_everyone(self):
        with _feature(), _window(), _credit(1):
            with self.assertRaises(promo_sms.CampaignError) as ctx:
                promo_sms.create_campaign(self.business, 'all', 'سلام', self.owner)
        self.assertEqual(ctx.exception.code, 'credit')
        self.assertFalse(PromoCampaign.objects.exists())

    # ── sending ──

    def create(self):
        with _feature(), _window(), _credit(None):
            return promo_sms.create_campaign(self.business, 'all', 'سلام {name}', self.owner)

    def test_send_bills_logs_and_refunds_rejections(self):
        campaign = self.create()
        results = {'09350000001': (True, ''), '09350000002': (False, 'rejected')}
        with patch('business.promo_sms.send_sms', side_effect=lambda phone, text: results[phone]) as send, \
                patch('accounting.usage.consume_sms', return_value={'owner': self.owner.id}) as consume, \
                patch('accounting.usage.refund_sms') as refund:
            promo_sms.process_campaign(campaign.id)

        self.assertEqual(send.call_count, 2)
        self.assertEqual(consume.call_count, 2)
        self.assertEqual(refund.call_count, 1)
        campaign.refresh_from_db()
        self.assertEqual((campaign.status, campaign.sent_count, campaign.failed_count), ('DONE', 1, 1))
        self.assertEqual(SmsLog.objects.filter(business=self.business).count(), 2)

    def test_out_of_credit_mid_send_is_skipped_not_sent(self):
        campaign = self.create()
        with patch('business.promo_sms.send_sms', return_value=(True, '')) as send, \
                patch('accounting.usage.consume_sms', return_value=None):
            promo_sms.process_campaign(campaign.id)
        self.assertEqual(send.call_count, 0)
        campaign.refresh_from_db()
        self.assertEqual(campaign.skipped_count, 2)

    def test_resume_never_resends_what_was_already_sent(self):
        campaign = self.create()
        first = campaign.recipients.order_by('id').first()
        first.status = 'SENT'
        first.save(update_fields=['status'])
        PromoCampaign.objects.filter(id=campaign.id).update(
            heartbeat_at=timezone.now() - timedelta(hours=1)
        )
        with patch('business.promo_sms.send_sms', return_value=(True, '')) as send, \
                patch('accounting.usage.consume_sms', return_value={'owner': self.owner.id}):
            self.assertEqual(promo_sms.resume_stalled(), 1)
        self.assertEqual(send.call_count, 1)
        self.assertEqual(
            PromoCampaignRecipient.objects.filter(campaign=campaign, status='SENT').count(), 2
        )

    def test_live_campaign_is_not_resumed(self):
        campaign = self.create()
        PromoCampaign.objects.filter(id=campaign.id).update(heartbeat_at=timezone.now())
        with patch('business.promo_sms.send_sms') as send:
            self.assertEqual(promo_sms.resume_stalled(), 0)
        send.assert_not_called()

    # ── API ──

    def test_api_is_owner_only_and_reports_refusal_reason(self):
        client = APIClient()
        client.force_authenticate(self.other_owner)
        self.assertEqual(client.get(f'/api/business/{self.business.id}/promo-sms/').status_code, 404)

        client.force_authenticate(self.owner)
        with _feature(False):
            response = client.post(
                f'/api/business/{self.business.id}/promo-sms/',
                {'audience': 'all', 'message': 'سلام'}, format='json',
            )
        self.assertEqual(response.status_code, 403)
        self.assertIn('message', response.json())

    def test_api_overview_lists_audience_counts(self):
        client = APIClient()
        client.force_authenticate(self.owner)
        response = client.get(f'/api/business/{self.business.id}/promo-sms/')
        self.assertEqual(response.status_code, 200)
        counts = {a['key']: a['count'] for a in response.json()['audiences']}
        self.assertEqual(counts, {'all': 2, 'recent': 1, 'inactive': 1})
