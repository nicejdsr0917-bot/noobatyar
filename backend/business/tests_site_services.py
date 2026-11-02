from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.utils import timezone

from accounting.models import Plan, Subscription
from business.models import Business

User = get_user_model()
ACTIVE = '/api/public/site/active-customers/'
NEWEST = '/api/public/site/newest-businesses/'


class SiteServicesTests(TestCase):
    def setUp(self):
        cache.clear()
        self.paid = Plan.objects.create(name='p', price=100, duration_value=1, duration_unit='month')
        self.free = Plan.objects.create(name='f', price=0, duration_value=1, duration_unit='month')

    def owner(self, i):
        return User.objects.create_user(phone=f'0912000070{i}', password='x', name=f'o{i}')

    def biz(self, owner, title, **kw):
        kw.setdefault('moderation_status', Business.MODERATION_APPROVED)
        return Business.objects.create(
            user=owner, title=title, phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17, **kw)

    def sub(self, owner, plan, days=30, status='active'):
        Subscription.objects.create(user=owner, plan=plan, status=status,
                                    ends_at=timezone.now() + timedelta(days=days))

    def test_active_customers_filters(self):
        a, b, c, d, e = (self.owner(i) for i in range(5))
        self.biz(a, 'paid'); self.sub(a, self.paid)
        self.biz(b, 'free'); self.sub(b, self.free)
        self.biz(c, 'expired'); self.sub(c, self.paid, days=-1)
        self.biz(d, 'cancelled'); self.sub(d, self.paid, status='cancelled')
        self.biz(e, 'pending', moderation_status=Business.MODERATION_REJECTED); self.sub(e, self.paid)
        r = self.client.get(ACTIVE)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r['Access-Control-Allow-Origin'], '*')
        rows = r.json()['results']
        self.assertEqual([x['name'] for x in rows], ['paid'])
        self.assertEqual(set(rows[0]), {'name', 'category', 'category_display', 'city', 'slug', 'logo', 'url'})

    def test_newest_limit_and_order(self):
        o = self.owner(0)
        for i in range(15):
            self.biz(o, f'b{i}')
        rows = self.client.get(NEWEST).json()['results']
        self.assertEqual(len(rows), 12)
        self.assertEqual(rows[0]['name'], 'b14')

    def test_html_fragment_escaped(self):
        o = self.owner(0)
        self.biz(o, '<script>alert(1)</script>')
        r = self.client.get(NEWEST, {'format': 'html'})
        self.assertEqual(r.status_code, 200)
        self.assertIn('text/html', r['Content-Type'])
        body = r.content.decode()
        self.assertNotIn('<script>', body)
        self.assertIn('&lt;script&gt;', body)

    def test_cached_and_read_only(self):
        o = self.owner(0)
        self.biz(o, 'one')
        self.client.get(NEWEST)
        self.biz(o, 'two')
        self.assertEqual(self.client.get(NEWEST).json()['count'], 1)  # cached 10 min
        self.assertEqual(self.client.post(NEWEST).status_code, 405)

    def test_throttled(self):
        from unittest.mock import patch
        from business.site_views import SitePublicThrottle
        with patch.object(SitePublicThrottle, 'rate', '2/min'):
            codes = [self.client.get(NEWEST).status_code for _ in range(3)]
        self.assertEqual(codes[-1], 429)
