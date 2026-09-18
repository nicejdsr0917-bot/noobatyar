"""
bale/tests_notify.py

fire_owner_sms() itself, run for real rather than mocked at the call site the
way bale/tests.py's webhook tests mock it — the two are separate: the webhook
tests confirm a decision *fires* this function, this file confirms the
function actually notifies the owner on both channels.

The bug this exists for: apply_moderation_decision(notify=True) sends both a
push and an SMS, but bale/views.py always calls it with notify=False (see
that module's docstrings — SMS is a 10s-timeout round trip the webhook can't
afford to await) and fires this function instead. When the push half was
added to apply_moderation_decision, this off-thread replacement only grew a
matching SMS call — an operator approving a business through Bale kept
getting silence on the push channel specifically, with no error anywhere to
explain it, because the two notify paths had quietly drifted apart.

The real daemon thread fire_owner_sms starts is replaced with a synchronous
stand-in: a genuine background thread races Django's test-transaction
rollback and its own SQLite connection against the test's, which is a
threading/locking problem to test around, not something these tests are
about. Patching threading.Thread to run its target immediately keeps the
behaviour under test (does _run reach both notify functions, in what order,
surviving which failures) while making it deterministic.
"""

from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase

from bale.notify import fire_owner_sms
from business.models import Business
from business.tests_moderation_api import make_business

User = get_user_model()


class _ImmediateThread:
    """Stand-in for threading.Thread that runs its target synchronously."""

    def __init__(self, target=None, daemon=None, name=None, args=(), kwargs=None):
        self._target = target
        self._args = args
        self._kwargs = kwargs or {}

    def start(self):
        self._target(*self._args, **self._kwargs)


class FireOwnerSmsTests(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(phone='09120000031', name='مالک')
        self.business = make_business(self.user, title='سالن آفتاب')

    def test_fires_both_push_and_sms(self):
        with patch('bale.notify.threading.Thread', _ImmediateThread), \
                patch('api.services.push.send_to_user', return_value=1) as push_send, \
                patch('business.sms_moderation.send_sms', return_value=(True, '')) as sms_send, \
                patch('business.sms_moderation.within_send_window', return_value=True):
            fire_owner_sms(self.business.pk, Business.MODERATION_APPROVED)

        push_send.assert_called_once()
        sms_send.assert_called_once()

    def test_dead_push_does_not_stop_the_sms(self):
        with patch('bale.notify.threading.Thread', _ImmediateThread), \
                patch('api.services.push.send_to_user', side_effect=RuntimeError('boom')), \
                patch('business.sms_moderation.send_sms', return_value=(True, '')) as sms_send, \
                patch('business.sms_moderation.within_send_window', return_value=True), \
                self.assertLogs('business.push_moderation', level='ERROR'):
            fire_owner_sms(self.business.pk, Business.MODERATION_APPROVED)

        sms_send.assert_called_once()

    def test_unknown_business_notifies_nothing(self):
        with patch('bale.notify.threading.Thread', _ImmediateThread), \
                patch('api.services.push.send_to_user') as push_send, \
                patch('business.sms_moderation.send_sms') as sms_send:
            fire_owner_sms(999999, Business.MODERATION_APPROVED)

        push_send.assert_not_called()
        sms_send.assert_not_called()
