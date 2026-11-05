"""Customers following business pages: follow/unfollow, the count, the list,
and who can see what."""
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient

from api.models import User
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

from .models import Business, BusinessFollow


def make_business(owner, title='سالن', **extra):
    fields = dict(
        user=owner, title=title, phone='02100000000', address='تهران',
        default_service_duration=30, work_start_hour=9, work_end_hour=17,
        moderation_status=Business.MODERATION_APPROVED,
    )
    fields.update(extra)
    return Business.objects.create(**fields)


class FollowTests(TestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000900', password='x', name='o')
        self.biz = make_business(self.owner)
        self.v1 = Visitor.objects.create(full_name='علی', phone_number='09350000901')
        self.v2 = Visitor.objects.create(full_name='سارا', phone_number='09350000902')
        self.a1 = f'Visitor {sign_visitor_token(self.v1.id)}'
        self.a2 = f'Visitor {sign_visitor_token(self.v2.id)}'

    def follow(self, auth, biz=None, method='post'):
        url = reverse('client-business-follow', args=[(biz or self.biz).id])
        kw = {'HTTP_AUTHORIZATION': auth} if auth else {}
        return getattr(self.client, method)(url, **kw)

    def detail(self, auth=None):
        kw = {'HTTP_AUTHORIZATION': auth} if auth else {}
        return self.client.get(reverse('client-business-detail', args=[self.biz.id]), **kw).json()['data']

    def test_needs_a_signed_in_customer(self):
        self.assertIn(self.follow(None).status_code, (401, 403))
        api = APIClient()
        api.force_authenticate(self.owner)  # an owner's JWT is not a customer
        self.assertIn(api.post(reverse('client-business-follow', args=[self.biz.id])).status_code, (401, 403))

    def test_follow_and_unfollow_are_idempotent_and_counted(self):
        r = self.follow(self.a1).json()['data']
        self.assertEqual((r['is_following'], r['followers_count']), (True, 1))
        self.assertEqual(self.follow(self.a1).json()['data']['followers_count'], 1)
        self.assertEqual(self.follow(self.a2).json()['data']['followers_count'], 2)
        r = self.follow(self.a1, method='delete').json()['data']
        self.assertEqual((r['is_following'], r['followers_count']), (False, 1))
        self.assertEqual(self.follow(self.a1, method='delete').json()['data']['followers_count'], 1)
        self.biz.refresh_from_db()
        self.assertEqual(self.biz.followers_count, BusinessFollow.objects.filter(business=self.biz).count())

    def test_no_limit_on_how_many_a_customer_follows(self):
        for i in range(30):
            self.assertEqual(self.follow(self.a1, make_business(self.owner, f'ب{i}')).status_code, 200)
        self.assertEqual(BusinessFollow.objects.filter(visitor=self.v1).count(), 30)

    def test_hidden_or_unknown_business_is_404(self):
        hidden = make_business(self.owner, 'پنهان', moderation_status=Business.MODERATION_REJECTED)
        self.assertEqual(self.follow(self.a1, hidden).status_code, 404)
        self.assertEqual(self.client.post(reverse('client-business-follow', args=[999999]),
                                          HTTP_AUTHORIZATION=self.a1).status_code, 404)

    def test_detail_shows_count_publicly_and_is_following_only_to_the_customer(self):
        self.follow(self.a1)
        anon = self.detail()
        self.assertEqual(anon['followers_count'], 1)
        self.assertNotIn('is_following', anon)
        self.assertTrue(self.detail(self.a1)['is_following'])
        self.assertFalse(self.detail(self.a2)['is_following'])

    def test_count_on_the_cached_detail_updates_after_a_follow(self):
        self.assertEqual(self.detail()['followers_count'], 0)  # now cached
        self.follow(self.a1)
        self.assertEqual(self.detail()['followers_count'], 1)

    def test_following_list_is_own_newest_first_and_skips_hidden(self):
        second = make_business(self.owner, 'دوم')
        self.follow(self.a1)
        self.follow(self.a1, second)
        self.follow(self.a2)
        Business.objects.filter(pk=self.biz.pk).update(moderation_status=Business.MODERATION_REJECTED)
        r = self.client.get(reverse('client-business-following'), HTTP_AUTHORIZATION=self.a1)
        self.assertEqual(r.status_code, 200)
        rows = r.json()['data']['results']
        self.assertEqual([x['id'] for x in rows], [second.id])
        self.assertTrue(rows[0]['is_following'])
        self.assertIn(self.client.get(reverse('client-business-following')).status_code, (401, 403))

    def test_business_detail_never_names_followers(self):
        self.follow(self.a1)
        api = APIClient()
        api.force_authenticate(self.owner)
        r = api.get(f'/api/business/{self.biz.id}/')
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.json()['data']['followers_count'], 1)
        self.assertNotIn('09350000901', r.content.decode())
        self.assertNotIn('علی', r.content.decode())

    def test_owner_follower_list_is_first_name_only_newest_first(self):
        self.v1.full_name = 'علی رضایی'
        self.v1.save()
        self.follow(self.a1)
        self.follow(self.a2)
        api = APIClient()
        api.force_authenticate(self.owner)
        r = api.get(reverse('business-followers', args=[self.biz.id]))
        self.assertEqual(r.status_code, 200, r.content)
        data = r.json()['data']
        self.assertEqual(data['count'], 2)
        self.assertEqual([f['name'] for f in data['results']], ['سارا', 'علی'])
        self.assertNotIn('0935', r.content.decode())
        self.assertNotIn('رضایی', r.content.decode())

    def test_owner_follower_list_is_only_for_the_owner(self):
        other = User.objects.create_user(phone='09120000999', password='x', name='x')
        api = APIClient()
        api.force_authenticate(other)
        self.assertEqual(api.get(reverse('business-followers', args=[self.biz.id])).status_code, 404)

    def test_follow_tells_the_owner_once(self):
        from unittest import mock

        from core.outbox.models import OutboxMessage
        from core.outbox.service import get_handler
        self.follow(self.a1)
        self.follow(self.a1, method='delete')
        self.follow(self.a1)
        msgs = OutboxMessage.objects.filter(kind='business.new_follower_push')
        self.assertEqual(msgs.count(), 1)
        with mock.patch('api.services.push.send_to_user') as send:
            get_handler('business.new_follower_push')(msgs.get().payload)
        user_id, title, body, data = send.call_args.args
        self.assertEqual(user_id, self.owner.id)
        self.assertIn('علی', body)
        self.assertEqual(data['type'], 'NEW_FOLLOWER')
