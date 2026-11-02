"""Query-count guards for the public business directory and the entitlement
cache. The public list/detail must cost a constant number of queries no matter
how many rows a page holds."""
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from accounting import entitlements, usage
from accounting.models import (
    AddOnPack, AddOnPurchase, CreditBalance, Plan, Subscription,
)
from business.models import Business, Insurance

User = get_user_model()


def _mk_plan(**features):
    return Plan.objects.create(
        name='p', price=100000, duration_value=1, duration_unit='month',
        features={'monthly_appointments': 10, **features},
    )


class DirectoryFixtureMixin:
    def setUp(self):
        cache.clear()
        self.client = APIClient()
        self.plan = _mk_plan()
        self.ins = [
            Insurance.objects.create(name=f'ins{i}', slug=f'ins{i}', insurance_type='BASIC')
            for i in range(2)
        ]

    def make_businesses(self, n, start=0):
        now = timezone.now()
        out = []
        for i in range(start, start + n):
            owner = User.objects.create_user(phone=f'0912{i:07d}', name=f'o{i}', role='BUSINESS_OWNER')
            # Mix of owners: active subscription (even), none (odd).
            if i % 2 == 0:
                Subscription.objects.create(
                    user=owner, plan=self.plan, status='active',
                    ends_at=now + timedelta(days=20),
                )
            if i % 3 == 0:
                CreditBalance.objects.create(
                    user=owner, metric='appointment_monthly',
                    period=usage._period(), balance=10,
                )
            b = Business.objects.create(
                user=owner, title=f'biz{i}', category='DENTIST', unique_code=f'BZ{i:05d}',
                phone='02100000000', address=f'addr {i}', city='تهران',
                latitude=35.70 + i * 0.001, longitude=51.40 + i * 0.001,
                is_claimed=True, booking_enabled=True, default_service_duration=30,
                work_start_hour=9, work_end_hour=18,
                moderation_status=Business.MODERATION_APPROVED, first_approved_at=now,
            )
            b.insurances.set(self.ins)
            out.append(b)
        return out

    def count_queries(self, url, params=None):
        with CaptureQueriesContext(connection) as ctx:
            resp = self.client.get(url, params or {})
        self.assertEqual(resp.status_code, 200, resp.content)
        self.last_sql = [q['sql'][:110] for q in ctx.captured_queries]
        return len(ctx), resp.json()


# Upper bound of the constant per-request cost: COUNT, SELECT, insurances
# prefetch, subscriptions, add-ons, monthly balances (+2 ledger fallbacks),
# wallet balances (+2 ledger fallbacks), contact policy, and the «اولین نوبت
# خالی» batch (businesses + their appointments, business/first_free.py).
# Before the batching this was 38 / 123 / 604 queries for page sizes 5 / 20 / 100.
MAX_LIST_QUERIES = 16


class PublicListQueryCountTests(DirectoryFixtureMixin, TestCase):
    def test_list_query_count_is_constant(self):
        url = reverse('client-business-list')
        self.make_businesses(100)
        q5, _ = self.count_queries(url, {'page_size': 5})
        q20, d20 = self.count_queries(url, {'page_size': 20})
        q100, d100 = self.count_queries(url, {'page_size': 100})
        self.assertEqual(len(d20['data']['results']), 20)
        self.assertEqual(len(d100['data']['results']), 100)
        for q in (q5, q20, q100):
            self.assertLessEqual(q, MAX_LIST_QUERIES)
        # Never grows with the page size.
        self.assertLessEqual(q100, q5)

    def test_geo_list_query_count_is_constant(self):
        url = reverse('client-business-list')
        self.make_businesses(60)
        p = {'lat': 35.70, 'lng': 51.40, 'radius_km': 50}
        q5, _ = self.count_queries(url, {**p, 'page_size': 5})
        q50, d = self.count_queries(url, {**p, 'page_size': 50})
        self.assertEqual(len(d['data']['results']), 50)
        self.assertLessEqual(q5, MAX_LIST_QUERIES)
        self.assertLessEqual(q50, q5)

    def test_insurance_and_search_filters_stay_constant(self):
        url = reverse('client-business-list')
        self.make_businesses(30)
        q, d = self.count_queries(url, {'insurance': 'ins0', 'search': 'biz', 'page_size': 30})
        self.assertEqual(d['data']['count'], 30)  # two insurances per business, no duplicates
        self.assertLessEqual(q, MAX_LIST_QUERIES)
        q, d = self.count_queries(url, {'insurance': str(self.ins[1].id), 'page_size': 30})
        self.assertEqual(d['data']['count'], 30)

    def test_detail_query_count(self):
        biz = self.make_businesses(2)[0]
        q, _ = self.count_queries(reverse('client-business-detail', kwargs={'business_id': biz.id}))
        self.assertLessEqual(q, 10)  # was 8 + 6 per extra serializer lookup

    def test_detail_404(self):
        resp = self.client.get(reverse('client-business-detail', kwargs={'business_id': 999999}))
        self.assertEqual(resp.status_code, 404)

    def test_batched_payload_matches_per_object_path(self):
        """The annotated (batched) path and the legacy per-object fallback must
        produce byte-identical payloads, including the booking-block notice."""
        from business.serializers import PublicBusinessSerializer
        bizs = self.make_businesses(12)
        # Exhaust one owner's quota so the booking block shows up too.
        CreditBalance.objects.update_or_create(
            user=bizs[2].user, metric='appointment_monthly', period=usage._period(),
            defaults={'balance': 10},
        )
        resp = self.client.get(reverse('client-business-list'), {'page_size': 50})
        batched = {r['id']: r for r in resp.json()['data']['results']}
        self.assertEqual(len(batched), 12)
        legacy = {}
        for b in Business.objects.prefetch_related('insurances'):
            b.distance_km = None
            legacy[b.id] = PublicBusinessSerializer(b, context={'request': None}).data
        for bid, row in batched.items():
            # View-level field, not part of the serializer (business/first_free.py).
            row = {k: v for k, v in row.items() if k != 'first_free_slot'}
            self.assertEqual(row, dict(legacy[bid]), bid)
        flags = {(r['is_expired'], r['booking_enabled']) for r in batched.values()}
        self.assertGreater(len(flags), 1)  # fixture really covers several states


class GeoSqlTests(DirectoryFixtureMixin, TestCase):
    def test_radius_filter_and_distance_order_in_sql(self):
        near, mid, far = self.make_businesses(3)
        Business.objects.filter(pk=near.pk).update(latitude=35.700, longitude=51.400)
        Business.objects.filter(pk=mid.pk).update(latitude=35.750, longitude=51.400)   # ~5.5 km
        Business.objects.filter(pk=far.pk).update(latitude=36.500, longitude=51.400)   # ~89 km
        url = reverse('client-business-list')
        d = self.client.get(url, {'lat': 35.70, 'lng': 51.40, 'radius_km': 20, 'sort': 'distance'}).json()['data']
        self.assertEqual([r['id'] for r in d['results']], [near.id, mid.id])
        self.assertEqual(d['count'], 2)
        self.assertEqual(d['results'][0]['distance_km'], 0.0)
        self.assertAlmostEqual(d['results'][1]['distance_km'], 5.56, places=1)  # exact haversine

        d = self.client.get(url, {'lat': 35.70, 'lng': 51.40, 'radius_km': 100, 'sort': 'distance'}).json()['data']
        self.assertEqual([r['id'] for r in d['results']], [near.id, mid.id, far.id])

    def test_unlocated_and_claimed_ranking(self):
        a, b, c = self.make_businesses(3)
        Business.objects.filter(pk=a.pk).update(latitude=35.70, longitude=51.40, is_claimed=False)
        Business.objects.filter(pk=b.pk).update(latitude=35.75, longitude=51.40)
        Business.objects.filter(pk=c.pk).update(latitude=None, longitude=None)
        url = reverse('client-business-list')
        base = {'lat': 35.70, 'lng': 51.40, 'radius_km': 50}
        d = self.client.get(url, base).json()['data']
        self.assertEqual({r['id'] for r in d['results']}, {a.id, b.id})
        d = self.client.get(url, {**base, 'include_unlocated': 'true'}).json()['data']
        # recommended: claimed first, then nearest, unlocated last among equals
        self.assertEqual([r['id'] for r in d['results']], [b.id, c.id, a.id])
        self.assertIsNone(d['results'][1]['distance_km'])
        d = self.client.get(url, {**base, 'include_unlocated': 'true', 'sort': 'distance'}).json()['data']
        self.assertEqual([r['id'] for r in d['results']], [a.id, b.id, c.id])

    def test_geo_pagination_in_db(self):
        self.make_businesses(25)
        url = reverse('client-business-list')
        p = {'lat': 35.70, 'lng': 51.40, 'radius_km': 50, 'sort': 'distance', 'page_size': 10}
        seen = []
        for page in (1, 2, 3):
            d = self.client.get(url, {**p, 'page': page}).json()['data']
            self.assertEqual(d['count'], 25)
            self.assertEqual(d['total_pages'], 3)
            seen += [r['id'] for r in d['results']]
        self.assertEqual(len(set(seen)), 25)
        dists = [r['distance_km'] for r in self.client.get(url, p).json()['data']['results']]
        self.assertEqual(dists, sorted(dists))


class EntitlementCacheTests(DirectoryFixtureMixin, TestCase):
    def setUp(self):
        super().setUp()
        self.user = User.objects.create_user(phone='09129999999', name='x', role='BUSINESS_OWNER')

    def _grant(self, plan=None, days=20):
        return Subscription.objects.create(
            user=self.user, plan=plan or self.plan, status='active',
            ends_at=timezone.now() + timedelta(days=days),
        )

    def test_second_call_hits_cache(self):
        self._grant()
        entitlements.get_entitlements(self.user)
        with self.assertNumQueries(0):
            ent = entitlements.get_entitlements(self.user.id)
        self.assertEqual(ent['monthly_appointments'], 10)

    def test_returned_dict_is_a_copy(self):
        self._grant()
        entitlements.get_entitlements(self.user)['monthly_appointments'] = 999
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 10)

    def test_new_subscription_visible_immediately(self):
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 0)  # cached baseline
        self._grant()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 10)

    def test_subscription_invalidation_also_runs_on_commit(self):
        entitlements.get_entitlements(self.user)
        with self.captureOnCommitCallbacks(execute=False) as callbacks:
            self._grant()
        self.assertTrue(callbacks)  # re-invalidates after commit (race with concurrent readers)
        # Simulate a concurrent reader re-caching the stale pre-commit state
        # between the immediate drop and the commit.
        cache.set(entitlements._cache_key(self.user.id), dict(entitlements.DEFAULT_ENTITLEMENTS), 60)
        for cb in callbacks:
            cb()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 10)

    def test_subscription_change_and_delete_invalidate(self):
        sub = self._grant()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 10)
        sub.status = 'cancelled'
        sub.save()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 0)
        sub.status = 'active'
        sub.save()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 10)
        sub.delete()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 0)

    def test_plan_feature_edit_reaches_every_subscriber(self):
        self._grant()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 10)
        self.plan.features = {'monthly_appointments': 77}
        self.plan.save()
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 77)

    def test_feature_addon_purchase_invalidates(self):
        self.assertFalse(entitlements.has_feature(self.user, entitlements.FEATURE_DEPOSIT))
        pack = AddOnPack.objects.create(
            name='f', price=100000, kind=AddOnPack.KIND_FEATURE,
            feature_key=entitlements.FEATURE_DEPOSIT,
        )
        AddOnPurchase.objects.create(
            user=self.user, pack=pack, amount=1, track_id='t1', order_id='o1',
            status='success', expires_at=timezone.now() + timedelta(days=5),
        )
        self.assertTrue(entitlements.has_feature(self.user, entitlements.FEATURE_DEPOSIT))

    def test_cache_never_outlives_subscription(self):
        sub = self._grant(days=0)
        Subscription.objects.filter(pk=sub.pk).update(ends_at=timezone.now() + timedelta(seconds=1))
        entitlements.invalidate_entitlements(self.user)
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 10)
        ttl_cap = entitlements.ENTITLEMENTS_CACHE_TTL
        self.assertLess(1, ttl_cap)
        Subscription.objects.filter(pk=sub.pk).update(ends_at=timezone.now() - timedelta(seconds=1))
        cache.delete(entitlements._cache_key(self.user.id))
        self.assertEqual(entitlements.get_quota(self.user, 'monthly_appointments'), 0)


class OwnerBusinessViewTests(DirectoryFixtureMixin, TestCase):
    def test_owner_list_paginates_in_db_with_same_shape(self):
        bizs = self.make_businesses(7)
        owner = bizs[0].user
        for b in bizs[1:]:
            Business.objects.filter(pk=b.pk).update(user=owner)
        self.client.force_authenticate(owner)
        url = '/api/business/'
        resp = self.client.get(url, {'page': 2, 'page_size': 3})
        self.assertEqual(resp.status_code, 200, resp.content)
        d = resp.json()['data']
        self.assertEqual(
            set(d), {'count', 'total_pages', 'current_page', 'next', 'previous', 'results'})
        self.assertEqual((d['count'], d['total_pages'], d['current_page']), (7, 3, 2))
        self.assertEqual((d['next'], d['previous']), (3, 1))
        self.assertEqual(len(d['results']), 3)
        self.assertEqual(len(d['results'][0]['insurances']), 2)

    def test_owner_list_query_count_constant(self):
        bizs = self.make_businesses(30)
        owner = bizs[0].user
        Business.objects.update(user=owner)
        self.client.force_authenticate(owner)
        with CaptureQueriesContext(connection) as c5:
            self.client.get('/api/business/', {'page_size': 5})
        with CaptureQueriesContext(connection) as c30:
            self.client.get('/api/business/', {'page_size': 30})
        self.assertEqual(len(c5), len(c30))

    def test_owner_detail_and_no_thread_pool_fallback(self):
        import business.serializers as ser
        self.assertFalse(hasattr(ser, 'ThreadPoolExecutor'))
        biz = self.make_businesses(1)[0]
        self.client.force_authenticate(biz.user)
        resp = self.client.get(f'/api/business/{biz.id}/')
        self.assertEqual(resp.status_code, 200, resp.content)
        self.assertEqual(len(resp.json()['data']['insurances']), 2)
        self.assertEqual(self.client.get('/api/business/999999/').status_code, 404)
