"""Redis cache of the anonymous public directory: hits, bypasses, invalidation."""
from datetime import timedelta

from django.core.cache import cache
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.utils import timezone
from rest_framework_simplejwt.tokens import RefreshToken

from accounting.models import ContactVisibilityPolicy, Subscription
from business import cache as public_cache
from business.models import Business, Insurance
from business.tests_query_counts import DirectoryFixtureMixin

LIST = '/api/client/business/'


class PublicCacheTests(DirectoryFixtureMixin, TestCase):
    def setUp(self):
        super().setUp()
        self.bizs = self.make_businesses(4)
        self.biz = self.bizs[0]
        self.detail = f'{LIST}{self.biz.id}/'

    def get(self, url, params=None, **extra):
        with CaptureQueriesContext(connection) as ctx:
            r = self.client.get(url, params or {}, **extra)
        self.assertEqual(r.status_code, 200, r.content)
        return len(ctx), r.json()['data']

    # ── hits ──────────────────────────────────────────────────────────────
    def test_list_hit_costs_no_queries_and_is_identical(self):
        q1, d1 = self.get(LIST, {'search': 'biz', 'page_size': 3})
        self.assertGreater(q1, 0)
        q2, d2 = self.get(LIST, {'page_size': 3, 'search': 'biz'})  # same params, other order
        self.assertEqual(q2, 0)
        self.assertEqual(d1, d2)

    def test_list_keys_differ_per_param(self):
        _, a = self.get(LIST, {'page_size': 2})
        _, b = self.get(LIST, {'page_size': 2, 'page': 2})
        _, c = self.get(LIST, {'page_size': 2, 'search': 'biz1'})
        self.assertNotEqual(a['results'], b['results'])
        self.assertEqual(c['count'], 1)

    def test_detail_hit_costs_no_queries(self):
        q1, d1 = self.get(self.detail)
        self.assertGreater(q1, 0)
        q2, d2 = self.get(self.detail)
        self.assertEqual(q2, 0)
        self.assertEqual(d1, d2)

    def test_detail_distance_is_per_request_but_served_from_cache(self):
        _, plain = self.get(self.detail)
        self.assertIsNone(plain['distance_km'])
        q, near = self.get(self.detail, {'lat': 35.70, 'lng': 51.40})
        self.assertEqual(q, 0)
        public_cache.bump_business(self.biz.id)
        cache.clear()
        _, uncached = self.get(self.detail, {'lat': 35.70, 'lng': 51.40})
        self.assertEqual(near, uncached)
        self.assertIsNotNone(near['distance_km'])
        _, far = self.get(self.detail, {'lat': 36.0, 'lng': 51.4})
        self.assertNotEqual(near['distance_km'], far['distance_km'])

    def test_geo_list_is_not_cached(self):
        p = {'lat': 35.70, 'lng': 51.40, 'radius_km': 20}
        self.get(LIST, p)
        q, _ = self.get(LIST, p)
        self.assertGreater(q, 0)

    def test_404_is_not_cached(self):
        r = self.client.get(f'{LIST}999999/')
        self.assertEqual(r.status_code, 404)
        self.assertEqual(self.client.get(f'{LIST}999999/').status_code, 404)

    # ── never shared with a caller who could see something else ───────────
    def test_authenticated_requests_bypass_the_cache(self):
        Business.objects.filter(pk=self.biz.pk).update(allow_anonymous_view=False)
        _, anon = self.get(self.detail)
        self.assertIsNone(anon['phone'])           # masked, and now cached
        self.get(self.detail)                      # served from cache
        token = RefreshToken.for_user(self.biz.user).access_token
        q, authed = self.get(self.detail, HTTP_AUTHORIZATION=f'Bearer {token}')
        self.assertGreater(q, 0)
        self.assertEqual(authed['phone'], '02100000000')
        # ...and the anonymous entry is still the masked one.
        _, anon2 = self.get(self.detail)
        self.assertIsNone(anon2['phone'])

    def test_authenticated_list_bypasses_the_cache(self):
        self.get(LIST)
        token = RefreshToken.for_user(self.biz.user).access_token
        q, _ = self.get(LIST, HTTP_AUTHORIZATION=f'Bearer {token}')
        self.assertGreater(q, 0)

    def test_cached_payload_has_no_owner_private_fields(self):
        self.get(self.detail)
        _, d = self.get(self.detail)
        for k in ('card_number', 'card_owner_name', 'merchant_id', 'moderation_status',
                  'moderation_note', 'user', 'owner'):
            self.assertNotIn(k, d)

    # ── invalidation ──────────────────────────────────────────────────────
    def test_business_save_invalidates_detail_and_lists(self):
        self.get(self.detail)
        self.get(LIST, {'page_size': 50})
        self.biz.title = 'renamed'
        self.biz.save()
        _, d = self.get(self.detail)
        self.assertEqual(d['title'], 'renamed')
        _, lst = self.get(LIST, {'page_size': 50})
        self.assertIn('renamed', [r['title'] for r in lst['results']])

    def test_other_business_save_keeps_detail_entry(self):
        self.get(self.detail)
        other = self.bizs[1]
        other.title = 'x'
        other.save()
        q, _ = self.get(self.detail)
        self.assertEqual(q, 0)

    def test_working_hours_logo_and_moderation_changes_invalidate(self):
        self.get(self.detail)
        self.biz.work_end_hour = 20
        self.biz.save(update_fields=['work_end_hour'])
        self.assertEqual(self.get(self.detail)[1]['work_end_hour'], 20)

        self.biz.logo = 'logos/new.png'
        self.biz.save(update_fields=['logo'])
        self.assertIn('new.png', self.get(self.detail)[1]['logo'])

        self.biz.moderation_status = Business.MODERATION_REJECTED
        self.biz.save(update_fields=['moderation_status'])
        self.assertEqual(self.client.get(self.detail).status_code, 404)
        _, lst = self.get(LIST, {'page_size': 50})
        self.assertNotIn(self.biz.id, [r['id'] for r in lst['results']])

    def test_locking_a_business_removes_it(self):
        self.get(self.detail)
        self.biz.is_locked = True
        self.biz.save(update_fields=['is_locked'])
        self.assertEqual(self.client.get(self.detail).status_code, 404)

    def test_services_change_invalidates(self):
        self.get(self.detail)
        self.biz.services = ['کاشت', 'ترمیم']
        self.biz.save(update_fields=['services'])
        self.assertEqual(self.get(self.detail)[1]['services'], ['کاشت', 'ترمیم'])

    def test_delete_invalidates(self):
        _, before = self.get(LIST, {'page_size': 50})
        self.biz.delete()
        _, after = self.get(LIST, {'page_size': 50})
        self.assertEqual(after['count'], before['count'] - 1)

    def test_insurance_changes_invalidate(self):
        self.get(self.detail)
        self.biz.insurances.set([self.ins[0]])
        self.assertEqual(len(self.get(self.detail)[1]['insurances']), 1)
        self.get(self.detail)
        Insurance.objects.filter(pk=self.ins[0].pk).first().save()  # name edit path
        q, _ = self.get(self.detail)
        self.assertGreater(q, 0)

    def test_subscription_change_invalidates_is_expired(self):
        sub = Subscription.objects.get(user=self.biz.user)
        self.assertFalse(self.get(self.detail)[1]['is_expired'])
        self.get(LIST, {'page_size': 50})
        sub.ends_at = timezone.now() - timedelta(days=1)
        sub.save()
        self.assertTrue(self.get(self.detail)[1]['is_expired'])
        _, lst = self.get(LIST, {'page_size': 50})
        self.assertTrue({r['id']: r for r in lst['results']}[self.biz.id]['is_expired'])

    def test_contact_policy_change_invalidates(self):
        Subscription.objects.filter(user=self.biz.user).update(ends_at=timezone.now() - timedelta(days=1))
        public_cache.bump_all()
        self.assertEqual(self.get(self.detail)[1]['phone'], '02100000000')
        policy = ContactVisibilityPolicy.load()
        policy.hide_when_expired = True
        policy.hide_phone = True
        policy.save()
        self.assertIsNone(self.get(self.detail)[1]['phone'])

    def test_invalidation_runs_again_on_commit(self):
        self.get(self.detail)
        with self.captureOnCommitCallbacks(execute=True) as callbacks:
            self.biz.title = 'tx'
            self.biz.save()
        self.assertTrue(callbacks)
        self.assertEqual(self.get(self.detail)[1]['title'], 'tx')

    def test_evicted_stamp_never_revives_old_entries(self):
        self.get(self.detail)
        old = public_cache.detail_key(self.biz.id)
        cache.delete(f'pubdir:v:biz:{self.biz.id}')   # simulate Redis eviction
        self.assertNotEqual(public_cache.detail_key(self.biz.id), old)

    def test_disabled_by_setting(self):
        with self.settings(PUBLIC_DIRECTORY_CACHE_ENABLED=False):
            self.get(self.detail)
            q, _ = self.get(self.detail)
            self.assertGreater(q, 0)
