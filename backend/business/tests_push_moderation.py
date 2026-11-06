"""
business/tests_push_moderation.py

Two owner-facing pushes added alongside the existing SMS/Bale notices:

* A moderation decision (approve/reject/suspend) now pushes the owner too,
  not just texts them — see push_moderation.notify_moderation_decision_push,
  wired into services.apply_moderation_decision.
* The moment an edit re-enters the review queue, the owner gets an immediate
  "in the queue, this can take 10 minutes to 2 hours" push — sms_moderation
  deliberately skips PENDING as noise, but that left the owner with nothing
  to see right after saving an edit that can sit under review for hours.
  See push_moderation.notify_review_queued_push, wired into both re-queue
  paths in services.py.
"""

from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase

from . import push_moderation, services
from .models import Business
from .tests_moderation_api import make_business

User = get_user_model()


class ModerationDecisionPushTests(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(phone='09120000021', name='مالک')
        self.business = make_business(self.user, title='سالن آفتاب')

    def test_approved_push_names_the_business(self):
        with patch('api.services.push.send_to_user', return_value=1) as send:
            ok = push_moderation.notify_moderation_decision_push(
                self.business, Business.MODERATION_APPROVED,
            )
        self.assertTrue(ok)
        args, kwargs = send.call_args
        self.assertEqual(args[0], self.user.id)
        self.assertIn('سالن آفتاب', kwargs['body'])
        self.assertIn('تایید', kwargs['title'])

    def test_rejected_push_includes_the_reviewer_note(self):
        self.business.moderation_note = 'تصویر نامناسب است'
        with patch('api.services.push.send_to_user', return_value=1) as send:
            push_moderation.notify_moderation_decision_push(
                self.business, Business.MODERATION_REJECTED,
            )
        self.assertIn('تصویر نامناسب است', send.call_args.kwargs['body'])

    def test_pending_has_no_decision_push_builder(self):
        """PENDING is a queue event, not a decision — it goes through
        notify_review_queued_push instead, never this function."""
        with patch('api.services.push.send_to_user') as send:
            sent = push_moderation.notify_moderation_decision_push(
                self.business, Business.MODERATION_PENDING,
            )
        self.assertFalse(sent)
        send.assert_not_called()

    def test_provider_failure_never_raises(self):
        with patch(
            'api.services.push.send_to_user', side_effect=RuntimeError('boom')
        ), self.assertLogs('business.push_moderation', level='ERROR'):
            ok = push_moderation.notify_moderation_decision_push(
                self.business, Business.MODERATION_APPROVED,
            )
        self.assertFalse(ok)

    def test_apply_moderation_decision_sends_both_push_and_sms(self):
        staff = User.objects.create_user(
            phone='09120000022', name='ناظر', is_staff=True,
        )
        with patch('api.services.push.send_to_user', return_value=1) as push_send, \
                patch('business.sms_moderation.send_sms', return_value=(True, '')), \
                patch('business.sms_moderation.within_send_window', return_value=True):
            notified = services.apply_moderation_decision(
                self.business, Business.MODERATION_APPROVED, staff,
            )

        self.assertTrue(notified)
        push_send.assert_called_once()

    def test_dead_push_provider_does_not_block_the_sms_result(self):
        """apply_moderation_decision's return value is the SMS outcome — a
        push failure must not change it, since a caller checking "was the
        owner notified" already gets the SMS answer."""
        staff = User.objects.create_user(
            phone='09120000023', name='ناظر', is_staff=True,
        )
        with patch(
            'api.services.push.send_to_user', side_effect=RuntimeError('boom')
        ), patch('business.sms_moderation.send_sms', return_value=(True, '')), \
                patch('business.sms_moderation.within_send_window', return_value=True):
            notified = services.apply_moderation_decision(
                self.business, Business.MODERATION_APPROVED, staff,
            )
        self.assertTrue(notified)


class ReviewQueuedPushTests(TestCase):
    """The "you're in the queue" push, fired the moment an edit re-enters
    review — independent of whatever the eventual decision turns out to be.
    """

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000024', name='مالک')

    def test_push_names_the_business_and_gives_a_time_estimate(self):
        business = make_business(self.user, title='سالن آفتاب')
        with patch('api.services.push.send_to_user', return_value=1) as send:
            ok = push_moderation.notify_review_queued_push(business)
        self.assertTrue(ok)
        body = send.call_args.kwargs['body']
        self.assertIn('سالن آفتاب', body)
        self.assertIn('۱۰ دقیقه', body)
        self.assertIn('۲ ساعت', body)

    def test_first_time_submission_requeue_sends_the_queued_push(self):
        """resubmit_if_content_changed's path: a REJECTED business editing
        the offending copy is its only route back into the queue — a fresh
        PENDING business is not re-queued by an edit, it's already queued."""
        business = make_business(self.user, title='سالن آفتاب')
        business.moderation_status = Business.MODERATION_REJECTED
        business.save(update_fields=['moderation_status'])
        before = services.moderated_snapshot(business)
        business.title = 'سالن آفتاب طلایی'
        business.save(update_fields=['title'])

        with patch('api.services.push.send_to_user', return_value=1) as send, \
                patch('business.services.notify_review_queued'):
            requeued = services.resubmit_if_content_changed(business, before)

        self.assertTrue(requeued)
        send.assert_called_once()
        self.assertIn('سالن آفتاب طلایی', send.call_args.kwargs['body'])

    def test_staged_edit_after_approval_sends_the_queued_push(self):
        """save_with_moderation's path: an already-approved business whose
        edit gets staged onto pending_* instead of overwriting the live
        columns."""
        from django.utils import timezone

        from .serializers import BusinessSerializer

        business = make_business(self.user, title='سالن آفتاب')
        business.moderation_status = Business.MODERATION_APPROVED
        business.first_approved_at = timezone.now()
        business.save(update_fields=['moderation_status', 'first_approved_at'])

        before = services.moderated_snapshot(business)
        serializer = BusinessSerializer(business, data={'title': 'سالن آفتاب نو'}, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)

        with patch('api.services.push.send_to_user', return_value=1) as send, \
                patch('business.services.notify_review_queued'):
            requeued = services.save_with_moderation(serializer, business, before)

        self.assertTrue(requeued)
        send.assert_called_once()

    def test_no_change_sends_no_queued_push(self):
        """A save that doesn't touch a moderated field is not an edit — see
        resubmit_if_content_changed's own docstring — so nothing should push
        the owner about a queue they never entered."""
        business = make_business(self.user, title='سالن آفتاب')
        before = services.moderated_snapshot(business)

        with patch('api.services.push.send_to_user') as send:
            requeued = services.resubmit_if_content_changed(business, before)

        self.assertFalse(requeued)
        send.assert_not_called()

    def test_provider_failure_never_breaks_the_requeue(self):
        business = make_business(self.user, title='سالن آفتاب')
        business.moderation_status = Business.MODERATION_REJECTED
        business.save(update_fields=['moderation_status'])
        before = services.moderated_snapshot(business)
        business.title = 'سالن آفتاب طلایی'
        business.save(update_fields=['title'])

        with patch(
            'api.services.push.send_to_user', side_effect=RuntimeError('boom')
        ), patch('business.services.notify_review_queued'), \
                self.assertLogs('business.push_moderation', level='ERROR'):
            requeued = services.resubmit_if_content_changed(business, before)

        self.assertTrue(requeued)
        business.refresh_from_db()
        self.assertEqual(business.moderation_status, Business.MODERATION_PENDING)
