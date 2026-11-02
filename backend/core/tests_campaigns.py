# core/tests_campaigns.py
"""
The renewal-reminder SMS on the campaigns page: who a manual send reaches,
that it and the automatic reminder never message the same owner twice, that
both fill the same tokens, that the cron's sends land in the same report —
and the page/permission plumbing around them.
"""

from datetime import timedelta
from unittest import mock

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from accounting.management.commands.run_subscription_lifecycle import Command as LifecycleCommand
from accounting.models import Plan, Subscription, SubscriptionReminderSettings, render_reminder_text
from core.outbox.service import run_batch
from core.messaging import expiry_sms_audience, send_expiry_sms, send_expiry_sms_test
from core.models import ExpirySmsLog

User = get_user_model()


def drain():
    """Run the outbox worker until nothing is due (what `run_outbox --once` does)."""
    while run_batch():
        pass


def make_user(phone, name='کاربر', **kwargs):
    return User.objects.create_user(phone=phone, name=name, **kwargs)


def make_plan(name):
    return Plan.objects.create(name=name, price=1000, duration_value=1, duration_unit='month')


def make_sub(user, plan, days_left, **kwargs):
    return Subscription.objects.create(
        user=user, plan=plan, status=kwargs.pop('status', 'active'),
        ends_at=timezone.now() + timedelta(days=days_left, hours=1), **kwargs,
    )


def grant(user, *labels):
    for label in labels:
        app_label, codename = label.split('.')
        user.user_permissions.add(
            Permission.objects.get(content_type__app_label=app_label, codename=codename)
        )


class RenderReminderTextTests(TestCase):
    def test_fills_every_token(self):
        text = render_reminder_text(
            '{full_name}، {days} روز تا پایان {plan}', owner_name='مریم', days_left=3, plan_name='پرو',
        )
        self.assertEqual(text, 'مریم، 3 روز تا پایان پرو')

    def test_unknown_tokens_and_braces_stay_as_typed(self):
        text = render_reminder_text('{name} {days} {', owner_name='x', days_left=2, plan_name='y')
        self.assertEqual(text, '{name} 2 {')


class ExpiryAudienceTests(TestCase):
    def setUp(self):
        self.pro = make_plan('پرو')
        self.basic = make_plan('پایه')
        self.soon = make_sub(make_user('09120000101'), self.pro, 2)
        self.later = make_sub(make_user('09120000102'), self.pro, 20)
        self.other_plan = make_sub(make_user('09120000103'), self.basic, 2)
        self.reminded = make_sub(make_user('09120000104'), self.pro, 2, reminder_sent=True)
        self.expired = make_sub(make_user('09120000105'), self.pro, 2, status='expired')

    def test_window_and_status(self):
        ids = set(expiry_sms_audience(3, skip_reminded=False).values_list('id', flat=True))
        self.assertEqual(ids, {self.soon.id, self.other_plan.id, self.reminded.id})

    def test_plan_filter(self):
        ids = set(expiry_sms_audience(3, plan_ids=[self.pro.id], skip_reminded=False).values_list('id', flat=True))
        self.assertEqual(ids, {self.soon.id, self.reminded.id})

    def test_skip_reminded(self):
        ids = set(expiry_sms_audience(3).values_list('id', flat=True))
        self.assertEqual(ids, {self.soon.id, self.other_plan.id})


class ExpiredAudienceTests(TestCase):
    """Owners whose plan already ended and who never came back."""

    def setUp(self):
        self.pro = make_plan('پرو')
        self.basic = make_plan('پایه')
        self.lapsed = make_sub(make_user('09120000301'), self.pro, -3, status='expired')
        # Ended, but the cron hasn't flipped it to 'expired' yet — still lapsed.
        self.unflipped = make_sub(make_user('09120000302'), self.basic, -2)
        self.too_old = make_sub(make_user('09120000303'), self.pro, -40, status='expired')
        self.still_active = make_sub(make_user('09120000304'), self.pro, 5)
        self.cancelled = make_sub(make_user('09120000305'), self.pro, -3, status='cancelled')
        renewed = make_user('09120000306')
        make_sub(renewed, self.pro, -3, status='expired')
        make_sub(renewed, self.pro, 20)  # paid again
        self.renewed_user = renewed
        # Lapsed twice: only the latest subscription counts, once.
        twice = make_user('09120000307')
        make_sub(twice, self.pro, -20, status='expired')
        self.twice = make_sub(twice, self.pro, -4, status='expired')

    def ids(self, **kwargs):
        return set(expiry_sms_audience(7, mode='expired', **kwargs).values_list('id', flat=True))

    def test_lapsed_and_not_renewed_within_window(self):
        self.assertEqual(self.ids(skip_reminded=False), {self.lapsed.id, self.unflipped.id, self.twice.id})

    def test_renewed_owner_is_never_in_it(self):
        users = set(expiry_sms_audience(30, mode='expired', skip_reminded=False).values_list('user_id', flat=True))
        self.assertNotIn(self.renewed_user.id, users)

    def test_plan_filter(self):
        self.assertEqual(self.ids(plan_ids=[self.basic.id], skip_reminded=False), {self.unflipped.id})

    def test_uses_its_own_reminded_flag(self):
        # The "ending soon" message already spent reminder_sent for these owners.
        Subscription.objects.update(reminder_sent=True)
        self.assertEqual(self.ids(), {self.lapsed.id, self.unflipped.id, self.twice.id})
        Subscription.objects.filter(id=self.lapsed.id).update(expired_reminder_sent=True)
        self.assertEqual(self.ids(), {self.unflipped.id, self.twice.id})


@mock.patch('api.sms.send_sms', return_value=(True, ''))
class SendExpiredSmsTests(TestCase):
    def setUp(self):
        self.plan = make_plan('پرو')
        self.owner = make_user('09120000401', name='مریم')
        self.sub = make_sub(self.owner, self.plan, -3, status='expired', reminder_sent=True)
        self.staff = make_user('09120000402', name='ادمین', is_staff=True)

    def test_days_is_days_since_the_end_and_flag_is_its_own(self, send_sms):
        log = send_expiry_sms('{full_name} {days} {plan}', 7, actor=self.staff, mode='expired')
        send_sms.assert_not_called()  # queued, not sent inside the call
        drain()
        log.refresh_from_db()

        self.assertEqual((log.matched_count, log.sent_count), (1, 1))
        self.assertIn('مریم 2 پرو', send_sms.call_args[0][1])
        self.assertEqual(log.filters['mode'], 'expired')
        self.sub.refresh_from_db()
        self.assertTrue(self.sub.expired_reminder_sent)

    def test_second_send_skips_and_expiring_mode_is_untouched(self, send_sms):
        send_expiry_sms('x', 7, actor=self.staff, mode='expired')
        drain()
        log = send_expiry_sms('x', 7, actor=self.staff, mode='expired')
        drain()
        self.assertEqual(log.matched_count, 0)
        self.assertEqual(send_sms.call_count, 1)
        # An expired send never marks the "ending soon" flag as sent.
        other = make_sub(make_user('09120000403'), self.plan, 2)
        send_expiry_sms('x', 7, actor=self.staff, mode='expired')
        drain()
        other.refresh_from_db()
        self.assertFalse(other.reminder_sent)


@mock.patch('api.sms.send_sms', return_value=(True, ''))
class SendExpirySmsTests(TestCase):
    def setUp(self):
        self.plan = make_plan('پرو')
        self.owner = make_user('09120000201', name='مریم')
        self.sub = make_sub(self.owner, self.plan, 2)
        self.staff = make_user('09120000202', name='ادمین', is_staff=True)

    def test_fills_plan_and_marks_reminded(self, send_sms):
        log = send_expiry_sms('{full_name} {plan}', 3, actor=self.staff)
        drain()
        log.refresh_from_db()

        self.assertEqual(log.source, ExpirySmsLog.SOURCE_MANUAL)
        self.assertEqual((log.matched_count, log.sent_count), (1, 1))
        self.assertIn('مریم پرو', send_sms.call_args[0][1])
        self.sub.refresh_from_db()
        self.assertTrue(self.sub.reminder_sent)

    def test_second_send_skips_already_reminded(self, send_sms):
        send_expiry_sms('یادآوری', 3, actor=self.staff)
        drain()
        log = send_expiry_sms('یادآوری', 3, actor=self.staff)
        drain()
        self.assertEqual(log.matched_count, 0)
        self.assertEqual(send_sms.call_count, 1)

    def test_records_plan_filter(self, send_sms):
        log = send_expiry_sms('یادآوری', 3, actor=self.staff, plan_ids=[self.plan.id])
        self.assertEqual(log.filters, {'plans': ['پرو'], 'skip_reminded': True, 'mode': 'expiring'})

    def test_test_send_goes_to_the_operator_only(self, send_sms):
        log = send_expiry_sms_test('{full_name} {days} {plan}', 5, actor=self.staff)

        self.assertEqual(log.source, ExpirySmsLog.SOURCE_TEST)
        self.assertEqual(send_sms.call_count, 1)
        self.assertEqual(send_sms.call_args[0][0], '09120000202')
        self.assertIn('ادمین 5 پلن نمونه', send_sms.call_args[0][1])
        self.sub.refresh_from_db()
        self.assertFalse(self.sub.reminder_sent)


@mock.patch('api.sms.send_sms', return_value=(True, ''))
class AutomaticReminderTests(TestCase):
    def setUp(self):
        self.plan = make_plan('پرو')
        self.sub = make_sub(make_user('09120000301', name='سارا'), self.plan, 2)
        self.settings = SubscriptionReminderSettings.load()
        self.settings.days_before = 3
        self.settings.message_template = '{full_name} {plan}'

    def run_job(self):
        LifecycleCommand()._send_renewal_reminders(timezone.now())

    def test_disabled_still_stamps_last_run(self, send_sms):
        self.settings.is_enabled = False
        self.settings.save()
        self.run_job()

        self.assertIsNotNone(SubscriptionReminderSettings.load().last_run_at)
        send_sms.assert_not_called()
        self.assertFalse(ExpirySmsLog.objects.exists())

    def test_sends_are_logged_as_automatic(self, send_sms):
        self.settings.is_enabled = True
        self.settings.save()
        self.run_job()

        log = ExpirySmsLog.objects.get()
        self.assertEqual(log.source, ExpirySmsLog.SOURCE_AUTO)
        self.assertEqual((log.matched_count, log.sent_count), (1, 1))
        self.assertIn('سارا پرو', send_sms.call_args[0][1])

    def test_skips_owners_a_manual_send_already_reached(self, send_sms):
        send_expiry_sms('دستی', 3)
        drain()
        self.settings.is_enabled = True
        self.settings.save()
        self.run_job()

        self.assertEqual(send_sms.call_count, 1)
        self.assertFalse(ExpirySmsLog.objects.filter(source=ExpirySmsLog.SOURCE_AUTO).exists())


class CampaignPageTests(TestCase):
    def setUp(self):
        self.staff = make_user('09120000401', name='ادمین', is_staff=True)
        self.client.force_login(self.staff)
        self.url = reverse('admin:core_campaigns')

    def test_needs_one_of_the_send_permissions(self):
        self.assertEqual(self.client.get(self.url).status_code, 403)

    def test_sms_only_operator_lands_on_the_sms_tab(self):
        grant(self.staff, 'core.send_expiry_sms')
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.context['tab'], 'sms')
        self.assertFalse(response.context['can_send_push'])

    def test_tabs_render(self):
        grant(self.staff, 'core.send_expiry_sms', 'core.send_marketing_push')
        for tab in ('push', 'sms', 'reports'):
            response = self.client.get(self.url, {'tab': tab})
            self.assertEqual(response.status_code, 200, tab)
            self.assertEqual(response.context['tab'], tab)

    def test_count_lists_recipients(self):
        grant(self.staff, 'core.send_expiry_sms')
        make_sub(make_user('09120000402', name='مریم'), make_plan('پرو'), 2)
        data = self.client.get(reverse('admin:core_expiry_sms_count'), {'days_before': 3}).json()
        self.assertEqual(data['matched'], 1)
        self.assertEqual(data['rows'][0]['name'], 'مریم')
        self.assertEqual(data['rows'][0]['plan'], 'پرو')

    def test_count_expired_mode_lists_lapsed_owners(self):
        grant(self.staff, 'core.send_expiry_sms')
        plan = make_plan('پرو')
        make_sub(make_user('09120000403', name='سارا'), plan, -3, status='expired')
        make_sub(make_user('09120000404', name='علی'), plan, 2)  # still active: not in this mode
        data = self.client.get(
            reverse('admin:core_expiry_sms_count'), {'days_before': 7, 'expiry_mode': 'expired'},
        ).json()
        self.assertEqual(data['matched'], 1)
        self.assertEqual(data['rows'][0]['name'], 'سارا')
        self.assertEqual(data['rows'][0]['days_left'], 2)  # days since it ended

    def test_page_offers_the_expired_audience(self):
        grant(self.staff, 'core.send_expiry_sms')
        response = self.client.get(self.url, {'tab': 'sms'})
        self.assertContains(response, 'name="expiry_mode" value="expired"')

    def test_autosend_saves_its_own_fields(self):
        grant(self.staff, 'core.send_expiry_sms')
        response = self.client.post(reverse('admin:core_expiry_sms_autosend'), {
            'auto_enabled': '1', 'auto_days_before': '5', 'auto_message': 'متن خودکار',
            # The manual box's text must no longer leak into the auto template.
            'expiry_message': 'متن دستی',
        })
        self.assertRedirects(response, self.url + '?tab=sms', fetch_redirect_response=False)
        settings_row = SubscriptionReminderSettings.load()
        self.assertTrue(settings_row.is_enabled)
        self.assertEqual(settings_row.days_before, 5)
        self.assertEqual(settings_row.message_template, 'متن خودکار')
