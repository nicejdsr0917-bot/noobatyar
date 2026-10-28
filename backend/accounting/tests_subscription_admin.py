# accounting/tests_subscription_admin.py
"""
The subscription list in the admin: telling an owner's history from where they
stand now (an owner who renewed must not read as «منقضی» off their old trial
row), and the «ارسال پیامک» action that messages owners picked off that list.
"""

from datetime import timedelta
from unittest import mock

from django.contrib.admin.helpers import ACTION_CHECKBOX_NAME
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from accounting.models import Plan, Subscription
from core.messaging import send_sms_to_subscriptions
from core.models import ExpirySmsLog

User = get_user_model()


def make_user(phone, name='کاربر', **kwargs):
    return User.objects.create_user(phone=phone, name=name, **kwargs)


def make_plan(name):
    return Plan.objects.create(name=name, price=1000, duration_value=1, duration_unit='month')


def make_sub(user, plan, days_left, **kwargs):
    return Subscription.objects.create(
        user=user, plan=plan, status=kwargs.pop('status', 'active'),
        ends_at=timezone.now() + timedelta(days=days_left, hours=1), **kwargs,
    )


class ListFixture(TestCase):
    def setUp(self):
        self.trial, self.eco = make_plan('آزمایشی'), make_plan('اکو')
        # Renewed: trial ran out, then bought اکو — the case that read as both.
        self.renewed = make_user('09120000501', name='مهری')
        self.old_trial = make_sub(self.renewed, self.trial, -5, status='expired')
        self.eco_sub = make_sub(self.renewed, self.eco, 85)
        # Lapsed: trial ran out, nothing after it.
        self.lapsed_owner = make_user('09120000502', name='سارا')
        self.lapsed = make_sub(self.lapsed_owner, self.trial, -3, status='expired')
        # Running.
        self.running = make_sub(make_user('09120000503', name='رضا'), self.eco, 10)

        self.staff = make_user('09120000500', name='ادمین', is_staff=True)
        for codename in ('view_subscription', 'change_subscription'):
            self.staff.user_permissions.add(Permission.objects.get(codename=codename))
        self.client.force_login(self.staff)
        self.url = reverse('admin:accounting_subscription_changelist')

    def grant_send(self):
        self.staff.user_permissions.add(Permission.objects.get(codename='send_expiry_sms'))

    def listed(self, **params):
        response = self.client.get(self.url, params)
        self.assertEqual(response.status_code, 200)
        return {obj.pk for obj in response.context['cl'].result_list}


class RenewalStateFilterTests(ListFixture):
    def test_current_leaves_out_the_renewed_owners_old_trial(self):
        self.assertEqual(
            self.listed(renewal='current'), {self.eco_sub.pk, self.lapsed.pk, self.running.pk},
        )

    def test_lapsed_is_only_owners_who_did_not_renew(self):
        self.assertEqual(self.listed(renewal='lapsed'), {self.lapsed.pk})

    def test_superseded_is_the_history(self):
        self.assertEqual(self.listed(renewal='superseded'), {self.old_trial.pk})

    def test_cancelled_later_row_is_not_a_renewal(self):
        make_sub(self.lapsed_owner, self.eco, 30, status='cancelled')
        self.assertIn(self.lapsed.pk, self.listed(renewal='lapsed'))

    def test_old_row_names_the_current_plan(self):
        response = self.client.get(self.url)
        self.assertContains(response, 'سابقه — الان: «اکو»')
        self.assertContains(response, 'تمام‌شده، تمدید نکرده')


class SendSmsToSubscriptionsTests(ListFixture):
    @mock.patch('api.sms.send_sms', return_value=(True, ''))
    def test_one_sms_per_owner_and_each_row_flagged_by_its_state(self, send_sms):
        subs = Subscription.objects.filter(pk__in=[self.old_trial.pk, self.eco_sub.pk, self.lapsed.pk])
        log = send_sms_to_subscriptions(subs, '{full_name} {days} {plan}', actor=self.staff)

        self.assertEqual((log.matched_count, log.sent_count), (2, 2))
        self.assertEqual(log.filters['mode'], 'selection')
        sent = ' | '.join(call[0][1] for call in send_sms.call_args_list)
        self.assertIn('مهری 85 اکو', sent)    # the later sub speaks for the owner
        self.assertIn('سارا 2 آزمایشی', sent)  # days since the end, like expired mode
        self.eco_sub.refresh_from_db()
        self.lapsed.refresh_from_db()
        self.assertTrue(self.eco_sub.reminder_sent)
        self.assertTrue(self.lapsed.expired_reminder_sent)


class SendSmsActionTests(ListFixture):
    def post_action(self, pks, **extra):
        data = {'action': 'send_sms_action', ACTION_CHECKBOX_NAME: [str(pk) for pk in pks], **extra}
        return self.client.post(self.url, data)

    def actions_offered(self):
        form = self.client.get(self.url).context['action_form']  # None when no action is allowed
        return [name for name, _ in form.fields['action'].choices] if form else []

    def test_action_needs_the_send_permission(self):
        self.assertNotIn('send_sms_action', self.actions_offered())
        self.grant_send()
        self.assertIn('send_sms_action', self.actions_offered())

    @mock.patch('api.sms.within_send_window', return_value=True)
    @mock.patch('api.sms.send_sms', return_value=(True, ''))
    def test_confirm_then_send(self, send_sms, _window):
        self.grant_send()
        page = self.post_action([self.old_trial.pk, self.eco_sub.pk, self.lapsed.pk])
        self.assertEqual(page.status_code, 200)
        self.assertEqual(len(page.context['recipients']), 2)
        send_sms.assert_not_called()

        done = self.post_action(
            [self.old_trial.pk, self.eco_sub.pk, self.lapsed.pk], apply='1', sms_message='سلام {full_name}',
        )
        self.assertEqual(done.status_code, 302)
        self.assertEqual(send_sms.call_count, 2)
        self.assertEqual(ExpirySmsLog.objects.get().sent_count, 2)

    @mock.patch('api.sms.within_send_window', return_value=False)
    @mock.patch('api.sms.send_sms', return_value=(True, ''))
    def test_outside_the_window_nothing_is_sent_or_logged(self, send_sms, _window):
        self.grant_send()
        page = self.post_action([self.lapsed.pk], apply='1', sms_message='سلام')
        self.assertEqual(page.status_code, 200)
        self.assertFalse(page.context['window_open'])
        self.assertTrue(page.context['error'])
        send_sms.assert_not_called()
        self.assertFalse(ExpirySmsLog.objects.exists())


class CampaignSendWindowTests(TestCase):
    def setUp(self):
        self.staff = make_user('09120000600', name='ادمین', is_staff=True)
        self.staff.user_permissions.add(Permission.objects.get(codename='send_expiry_sms'))
        self.client.force_login(self.staff)
        make_sub(make_user('09120000601'), make_plan('پرو'), 2)

    @mock.patch('core.admin_site.within_send_window', return_value=False)
    @mock.patch('api.sms.send_sms', return_value=(True, ''))
    def test_manual_send_outside_the_window_is_refused_up_front(self, send_sms, _window):
        response = self.client.post(
            reverse('admin:core_expiry_sms_send'), {'expiry_message': 'سلام', 'days_before': '3'},
        )
        self.assertEqual(response.status_code, 302)
        send_sms.assert_not_called()
        self.assertFalse(ExpirySmsLog.objects.exists())
