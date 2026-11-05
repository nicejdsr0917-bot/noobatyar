from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from api.models import DeviceToken
from business.models import Business, ContentReport
from core.outbox.models import OutboxMessage
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

from .models import Review

User = get_user_model()


def make_business(owner, title='سالن'):
    return Business.objects.create(
        user=owner, title=title, phone='02100000000', address='t',
        default_service_duration=30, work_start_hour=9, work_end_hour=17,
        moderation_status=Business.MODERATION_APPROVED,
    )


class ReviewBase(TestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000500', password='x', name='o')
        self.other_owner = User.objects.create_user(phone='09120000501', password='x', name='o2')
        self.biz = make_business(self.owner)
        self.other_biz = make_business(self.other_owner, 'دیگر')
        self.v1 = Visitor.objects.create(full_name='علی رضایی', phone_number='09121234567')
        self.v2 = Visitor.objects.create(full_name='سارا', phone_number='09351112222')
        self.a1 = f'Visitor {sign_visitor_token(self.v1.id)}'
        self.a2 = f'Visitor {sign_visitor_token(self.v2.id)}'

    def mine(self, bid=None):
        return reverse('review-mine', args=[bid or self.biz.id])

    def post(self, auth, rating=5, text='عالی', bid=None):
        kw = {'HTTP_AUTHORIZATION': auth} if auth else {}
        return self.client.post(self.mine(bid), {'rating': rating, 'text': text},
                                content_type='application/json', **kw)


class ReviewWriteTests(ReviewBase):
    def test_auth_required(self):
        self.assertEqual(self.post(None).status_code, 401)
        self.assertEqual(Review.objects.count(), 0)

    def test_create_publishes_and_updates_summary_and_pushes(self):
        r = self.post(self.a1, 4, 'خوب')
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(self.post(self.a2, 5).status_code, 201)
        self.biz.refresh_from_db()
        self.assertEqual(self.biz.rating_count, 2)
        self.assertEqual(self.biz.rating_avg, 4.5)
        self.assertEqual(self.biz.rating_histogram['5'], 1)
        self.assertEqual(OutboxMessage.objects.filter(kind='reviews.new_review_push').count(), 2)

    def test_one_per_visitor(self):
        self.post(self.a1)
        self.assertEqual(self.post(self.a1).status_code, 409)
        # but another business is fine
        self.assertEqual(self.post(self.a1, bid=self.other_biz.id).status_code, 201)

    def test_validation(self):
        self.assertEqual(self.post(self.a1, 6).status_code, 400)
        self.assertEqual(self.post(self.a1, 0).status_code, 400)
        self.assertEqual(self.post(self.a1, 'x').status_code, 400)
        self.assertEqual(self.post(self.a1, 5, 'a' * 1001).status_code, 400)
        self.assertEqual(Review.objects.count(), 0)

    def test_unknown_business_404(self):
        self.assertEqual(self.post(self.a1, bid=99999).status_code, 404)

    def test_edit_within_window_and_after(self):
        self.post(self.a1, 2)
        kw = {'HTTP_AUTHORIZATION': self.a1}
        r = self.client.put(self.mine(), {'rating': 5, 'text': 'ok'}, content_type='application/json', **kw)
        self.assertEqual(r.status_code, 200, r.content)
        self.biz.refresh_from_db()
        self.assertEqual(self.biz.rating_avg, 5)
        Review.objects.update(created_at=timezone.now() - timedelta(days=8))
        r = self.client.put(self.mine(), {'rating': 1}, content_type='application/json', **kw)
        self.assertEqual(r.status_code, 403)

    def test_cannot_touch_someone_elses(self):
        self.post(self.a1, 3)
        kw = {'HTTP_AUTHORIZATION': self.a2}
        self.assertEqual(self.client.put(self.mine(), {'rating': 1}, content_type='application/json', **kw).status_code, 404)
        self.assertEqual(self.client.delete(self.mine(), **kw).status_code, 404)
        self.assertEqual(Review.objects.get().rating, 3)

    def test_delete_updates_summary(self):
        self.post(self.a1, 3)
        r = self.client.delete(self.mine(), HTTP_AUTHORIZATION=self.a1)
        self.assertEqual(r.status_code, 200)
        self.biz.refresh_from_db()
        self.assertEqual((self.biz.rating_count, self.biz.rating_avg), (0, 0))

    def test_get_mine(self):
        self.assertEqual(self.client.get(self.mine(), HTTP_AUTHORIZATION=self.a1).status_code, 404)
        self.post(self.a1, 3)
        r = self.client.get(self.mine(), HTTP_AUTHORIZATION=self.a1)
        self.assertEqual(r.json()['data']['rating'], 3)

    def test_push_sent_to_owner(self):
        from unittest.mock import patch
        r = Review.objects.create(business=self.biz, visitor=self.v1, rating=5)
        with patch('api.services.push.send_to_user') as m:
            from reviews.services import send_new_review_push
            send_new_review_push(r.id)
        self.assertEqual(m.call_args[0][0], self.owner.id)
        self.assertEqual(m.call_args[0][1], 'نظر جدید')

    def test_throttle(self):
        from reviews.client_views import ReviewCreateThrottle
        from unittest.mock import patch
        with patch.object(ReviewCreateThrottle, 'rate', '2/hour'), \
                patch.object(ReviewCreateThrottle, 'THROTTLE_RATES', {}, create=True):
            codes = [self.client.put(self.mine(), {'rating': 4}, content_type='application/json',
                                     HTTP_AUTHORIZATION=self.a1).status_code for _ in range(3)]
        self.assertEqual(codes[-1], 429)


class ReviewReadTests(ReviewBase):
    def test_public_list_masks_and_hides(self):
        Review.objects.create(business=self.biz, visitor=self.v1, rating=5, text='a')
        Review.objects.create(business=self.biz, visitor=self.v2, rating=1, text='b', is_hidden=True)
        r = self.client.get(reverse('review-public-list', args=[self.biz.id]))
        self.assertEqual(r.status_code, 200)
        data = r.json()['data']
        self.assertEqual(data['count'], 1)
        row = data['results'][0]
        self.assertEqual(row['visitor_name'], 'علی')
        # Nothing a stranger could contact the reviewer with, not even masked.
        self.assertEqual(row['visitor_phone'], '')
        self.assertNotIn('0912', str(row))
        self.assertNotIn('4567', str(row))
        self.assertIsNone(row['visitor_avatar'])

    def test_public_list_shows_the_reviewers_photo(self):
        from django.core.files.base import ContentFile
        self.v1.avatar.save('a.jpg', ContentFile(b'x'), save=True)
        Review.objects.create(business=self.biz, visitor=self.v1, rating=5, text='a')
        row = self.client.get(reverse('review-public-list', args=[self.biz.id])).json()['data']['results'][0]
        self.assertTrue(row['visitor_avatar'].startswith('http://testserver/'))
        self.assertIn('visitor_avatars/', row['visitor_avatar'])

    def test_public_list_newest_first_and_isolation(self):
        Review.objects.create(business=self.biz, visitor=self.v1, rating=5, text='old')
        Review.objects.create(business=self.biz, visitor=self.v2, rating=4, text='new')
        Review.objects.create(business=self.other_biz, visitor=self.v1, rating=1, text='other')
        r = self.client.get(reverse('review-public-list', args=[self.biz.id]))
        self.assertEqual([x['text'] for x in r.json()['data']['results']], ['new', 'old'])

    def test_unpublished_business_404(self):
        Business.objects.filter(pk=self.biz.pk).update(is_locked=True)
        self.assertEqual(self.client.get(reverse('review-public-list', args=[self.biz.id])).status_code, 404)

    def test_summary_and_detail_key_and_cache_bump(self):
        url = reverse('client-business-detail', args=[self.biz.id])
        self.assertEqual(self.client.get(url).json()['data']['rating']['count'], 0)
        self.post(self.a1, 4)
        self.post(self.a2, 2)
        d = self.client.get(url).json()['data']['rating']
        self.assertEqual((d['avg'], d['count']), (3.0, 2))
        self.assertEqual(d['histogram'], {'1': 0, '2': 1, '3': 0, '4': 1, '5': 0})
        s = self.client.get(reverse('review-summary', args=[self.biz.id])).json()['data']
        self.assertEqual(s, d)

    def test_owner_list_scoping_and_auth(self):
        Review.objects.create(business=self.biz, visitor=self.v1, rating=5, is_hidden=True)
        url = reverse('review-owner-list')
        self.assertIn(self.client.get(url, {'business_id': self.biz.id}).status_code, (401, 403))
        api = APIClient()
        api.force_authenticate(self.owner)
        r = api.get(url, {'business_id': self.biz.id})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['data']['count'], 1)
        self.assertTrue(r.json()['data']['results'][0]['is_hidden'])
        # The owner keeps the masked number they always saw.
        self.assertEqual(r.json()['data']['results'][0]['visitor_phone'], '0912***4567')
        self.assertEqual(api.get(url, {'business_id': self.other_biz.id}).status_code, 404)
        self.assertEqual(api.post(url).status_code, 405)


class ReviewReportAndAdminTests(ReviewBase):
    def test_report_review(self):
        rv = Review.objects.create(business=self.biz, visitor=self.v1, rating=1, text='bad')
        url = reverse('review-report', args=[rv.id])
        r = self.client.post(url, {'reason': 'SPAM'}, content_type='application/json',
                             HTTP_AUTHORIZATION=self.a2)
        self.assertEqual(r.status_code, 201, r.content)
        rep = ContentReport.objects.get()
        self.assertEqual((rep.review_id, rep.business_id, rep.reporter_visitor_id),
                         (rv.id, self.biz.id, self.v2.id))
        self.assertEqual(self.client.post(url, {'reason': 'nope'}, content_type='application/json').status_code, 400)
        self.assertEqual(self.client.post(reverse('review-report', args=[9999]), {'reason': 'SPAM'},
                                          content_type='application/json').status_code, 404)

    def test_admin_hide_unhide(self):
        from django.contrib.admin.sites import site
        from django.test import RequestFactory
        from reviews.admin import ReviewAdmin
        Review.objects.create(business=self.biz, visitor=self.v1, rating=5)
        adm = ReviewAdmin(Review, site)
        req = RequestFactory().get('/')
        adm.message_user = lambda *a, **k: None
        adm.hide_reviews(req, Review.objects.all())
        self.biz.refresh_from_db()
        self.assertEqual(self.biz.rating_count, 0)
        adm.unhide_reviews(req, Review.objects.all())
        self.biz.refresh_from_db()
        self.assertEqual(self.biz.rating_count, 1)


class ReviewRequestTests(ReviewBase):
    """After a COMPLETED appointment the client is asked, by push and in the app, to rate it."""

    def _appointment(self, visitor=None, business=None, status='WAITING'):
        from appointment.models import Appointment
        return Appointment.objects.create(
            business=business or self.biz, visitor=visitor or self.v1, source='APP',
            status=status, appointment_date=timezone.now() + timedelta(hours=1),
        )

    def _complete(self, appt):
        from appointment.services.transitions import appointment_change_status_by_owner
        appointment_change_status_by_owner(appointment=appt, new_status='COMPLETED')

    def pending(self, auth):
        return self.client.get(reverse('review-pending'), HTTP_AUTHORIZATION=auth)

    def test_completing_queues_one_request(self):
        appt = self._appointment()
        self._complete(appt)
        self._complete(appt)  # repeated status write: still one request
        self.assertEqual(OutboxMessage.objects.filter(kind='reviews.review_request_push').count(), 1)

    def test_no_request_when_already_reviewed(self):
        self.post(self.a1)
        self._complete(self._appointment())
        self.assertFalse(OutboxMessage.objects.filter(kind='reviews.review_request_push').exists())

    def test_other_statuses_do_not_ask(self):
        from appointment.services.transitions import appointment_change_status_by_owner
        appointment_change_status_by_owner(appointment=self._appointment(), new_status='CANCELLED')
        self.assertFalse(OutboxMessage.objects.filter(kind='reviews.review_request_push').exists())

    def test_push_handler_skips_when_reviewed_meanwhile(self):
        from unittest import mock
        from reviews.services import send_review_request_push
        appt = self._appointment()
        self._complete(appt)
        self.post(self.a1)
        with mock.patch('api.services.push.deliver_to_visitor') as deliver:
            send_review_request_push(appt.id)
        deliver.assert_not_called()

    def test_push_handler_links_to_review_form(self):
        from unittest import mock
        from api.services.push import SendResult
        from reviews.services import send_review_request_push
        appt = self._appointment()
        self._complete(appt)
        with mock.patch('api.services.push.deliver_to_visitor', return_value=SendResult()) as deliver:
            send_review_request_push(appt.id)
        args, kwargs = deliver.call_args
        self.assertEqual(args[0], self.v1.id)
        self.assertEqual(args[3]['type'], 'REVIEW_REQUEST')
        self.assertTrue(kwargs['link'].endswith(f'/b/Noobatyar-{self.biz.unique_code}?review=1'))

    def test_pending_lists_unreviewed_finished_visits(self):
        self.assertEqual(self.pending(self.a1).json()['data'], [])
        self._complete(self._appointment())
        self._complete(self._appointment())  # second visit, same business: one row
        self._complete(self._appointment(business=self.other_biz))
        self._appointment(status='WAITING')  # not finished
        rows = self.pending(self.a1).json()['data']
        self.assertEqual(sorted(r['business_id'] for r in rows), sorted([self.biz.id, self.other_biz.id]))
        self.post(self.a1)
        rows = self.pending(self.a1).json()['data']
        self.assertEqual([r['business_id'] for r in rows], [self.other_biz.id])
        # nobody else's visits
        self.assertEqual(self.pending(self.a2).json()['data'], [])

    def test_pending_needs_sign_in(self):
        self.assertEqual(self.client.get(reverse('review-pending')).status_code, 401)
