"""Query-count and tenant-isolation guards for owner-side visitor/SMS lists."""
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.utils import timezone
from rest_framework.test import APIClient

from appointment.models import Appointment
from business.models import Business
from visitor.models import PushLog, SmsLog, Visitor, VisitorArchive, VisitorContact

User = get_user_model()


def _biz(owner, title):
    return Business.objects.create(
        user=owner, title=title, phone='02100000000', address='x',
        default_service_duration=30, work_start_hour=9, work_end_hour=17,
    )


class Base(TestCase):
    def setUp(self):
        self.a = User.objects.create_user(phone='09120000101', password='x', name='A')
        self.b = User.objects.create_user(phone='09120000102', password='x', name='B')
        self.biz_a = _biz(self.a, 'A1')
        self.biz_a2 = _biz(self.a, 'A2')
        self.biz_b = _biz(self.b, 'B1')
        self.client = APIClient()
        self.client.force_authenticate(user=self.a)
        self._n = 0

    def visitor(self, **kw):
        self._n += 1
        return Visitor.objects.create(
            full_name=f'v{self._n}', phone_number=f'0935000{self._n:04d}', **kw)

    def appt(self, visitor, business, days=1):
        return Appointment.objects.create(
            business=business, visitor=visitor,
            appointment_date=timezone.now() + timedelta(days=days), status='WAITING')

    def count(self, url, params=None):
        with CaptureQueriesContext(connection) as ctx:
            r = self.client.get(url, params or {})
        self.assertEqual(r.status_code, 200, r.content)
        return len(ctx.captured_queries), r


class VisitorListTests(Base):
    URL = '/api/visitor/'

    def _populate(self, n):
        for _ in range(n):
            v = self.visitor()
            self.appt(v, self.biz_a)
            self.appt(v, self.biz_a2)      # join multiplication
            VisitorContact.objects.create(owner=self.a, visitor=v)

    def test_constant_queries_regardless_of_size(self):
        self._populate(3)
        small, _ = self.count(self.URL, {'page_size': 100})
        self._populate(25)
        large, _ = self.count(self.URL, {'page_size': 100})
        self.assertEqual(small, large)
        self.assertLessEqual(large, 3)

    def test_pagination_shape_and_no_duplicates(self):
        self._populate(7)
        seen = []
        for page in (1, 2, 3):
            _, r = self.count(self.URL, {'page': page, 'page_size': 3})
            d = r.json()['data']
            self.assertEqual(d['count'], 7)
            self.assertEqual(d['total_pages'], 3)
            self.assertEqual(d['current_page'], page)
            self.assertEqual(set(d), {'count', 'total_pages', 'current_page', 'next', 'previous', 'results'})
            seen += [x['id'] for x in d['results']]
        self.assertEqual(len(seen), 7)
        self.assertEqual(len(set(seen)), 7)
        # newest first
        self.assertEqual(seen, sorted(seen, reverse=True))

    def test_tenant_isolation_all_pages(self):
        self._populate(5)
        mine = set(Visitor.objects.values_list('id', flat=True))
        theirs = []
        for _ in range(6):
            v = self.visitor(user=self.b)
            self.appt(v, self.biz_b)
            theirs.append(v.id)
        got = []
        for page in (1, 2, 3, 4):
            _, r = self.count(self.URL, {'page': page, 'page_size': 2})
            got += [x['id'] for x in r.json()['data']['results']]
        self.assertEqual(set(got), mine)
        self.assertFalse(set(got) & set(theirs))

    def test_archived_hidden_and_other_owner_archive_irrelevant(self):
        v1, v2 = self.visitor(user=self.a), self.visitor(user=self.a)
        VisitorArchive.objects.create(owner=self.a, visitor=v1)
        VisitorArchive.objects.create(owner=self.b, visitor=v2)
        _, r = self.count(self.URL)
        self.assertEqual([x['id'] for x in r.json()['data']['results']], [v2.id])


class MessageHistoryTests(Base):
    def url(self, v):
        return f'/api/visitor/{v.id}/messages/'

    def test_other_owner_messages_never_appear(self):
        v = self.visitor(user=self.a)
        self.appt(v, self.biz_a)
        self.appt(v, self.biz_b)
        SmsLog.objects.create(business=self.biz_a, visitor=v, message_text='mine')
        SmsLog.objects.create(business=self.biz_b, visitor=v, message_text='theirs')
        _, r = self.count(self.url(v))
        texts = [m['message_text'] for m in r.json()['data']['results']]
        self.assertEqual(texts, ['mine'])

    def test_business_id_filters_same_owner_businesses(self):
        v = self.visitor(user=self.a)
        SmsLog.objects.create(business=self.biz_a, visitor=v, message_text='a1')
        SmsLog.objects.create(business=self.biz_a2, visitor=v, message_text='a2')
        _, r = self.count(self.url(v), {'business_id': self.biz_a2.id})
        self.assertEqual([m['message_text'] for m in r.json()['data']['results']], ['a2'])
        # old clients (no business_id): all of the owner's own businesses
        _, r = self.count(self.url(v))
        self.assertEqual(r.json()['data']['count'], 2)

    def test_foreign_or_bad_business_id_is_an_empty_page(self):
        # Never another owner's messages, and never an error the owner app
        # would show as a failure (it sends 0 before its business loads).
        v = self.visitor(user=self.a)
        self.appt(v, self.biz_b)
        SmsLog.objects.create(business=self.biz_b, visitor=v, message_text='theirs')
        for bid in (self.biz_b.id, 99999, 0, 'abc'):
            r = self.client.get(self.url(v), {'business_id': bid})
            self.assertEqual(r.status_code, 200, (bid, r.content))
            self.assertNotIn('theirs', r.content.decode(), bid)

    def test_constant_queries(self):
        v = self.visitor(user=self.a)
        for i in range(3):
            SmsLog.objects.create(business=self.biz_a, visitor=v, message_text=str(i))
        small, _ = self.count(self.url(v), {'page_size': 50})
        for i in range(20):
            SmsLog.objects.create(business=self.biz_a, visitor=v, message_text=str(i))
        large, _ = self.count(self.url(v), {'page_size': 50})
        self.assertEqual(small, large)


class SmsPushLogTests(Base):
    def test_sms_list_and_summary_constant(self):
        def mk(n):
            for i in range(n):
                v = self.visitor()
                SmsLog.objects.create(business=self.biz_a, visitor=v, message_text='m')
                PushLog.objects.create(business=self.biz_a, visitor=v, title='t', body='b')
        mk(3)
        base = {}
        urls = [f'/api/business/{self.biz_a.id}/{p}/' for p in
                ('sms-logs', 'sms-logs/summary', 'push-logs', 'push-logs/summary')]
        for u in urls:
            self.count(u, {'page_size': 100})  # warm the entitlements cache
            base[u] = self.count(u, {'page_size': 100})[0]
        mk(20)
        for u in urls:
            self.assertEqual(self.count(u, {'page_size': 100})[0], base[u], u)
        self.assertLessEqual(base[urls[0]], 4)
        self.assertLessEqual(base[urls[2]], 4)

    def test_sms_logs_scoped_to_owner(self):
        SmsLog.objects.create(business=self.biz_b, message_text='x')
        r = self.client.get(f'/api/business/{self.biz_b.id}/sms-logs/')
        self.assertEqual(r.status_code, 404)
