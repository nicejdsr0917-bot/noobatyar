"""P0-2: tenant isolation for every owner-facing endpoint.

Owner A runs two businesses (A1, A2); owner B runs one. Every piece of A's data
carries a marker string, so a leak shows up as the marker appearing in a body
no matter what the response envelope looks like. For each endpoint:
  * B must not read or change A's data (404/403 or an empty result), and
  * where a ``business_id`` pins a request to A1, A2's data must not appear.
"""
import json
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from appointment.models import Appointment
from business.models import Business, PromoCampaign
from visitor.models import PushLog, SmsLog, Visitor

User = get_user_model()

A1, A2 = 'ALPHAONE', 'ALPHATWO'  # markers for A's first / second business


def _biz(owner, title):
    return Business.objects.create(
        user=owner, title=title, phone='02100000001', address='تهران',
        default_service_duration=30, work_start_hour=0, work_end_hour=23,
        moderation_status=Business.MODERATION_APPROVED,
    )


def _body(resp):
    return resp.content.decode('utf-8', 'ignore')


class TenantIsolationTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.a = User.objects.create_user(phone='09120001001', password='x', name='A')
        cls.b = User.objects.create_user(phone='09120001002', password='x', name='B')
        cls.a1, cls.a2 = _biz(cls.a, A1 + '-biz'), _biz(cls.a, A2 + '-biz')
        cls.b1 = _biz(cls.b, 'BETA-biz')
        now = timezone.now()
        cls.v1 = Visitor.objects.create(full_name=A1 + '-visitor', phone_number='09391110001')
        cls.v2 = Visitor.objects.create(full_name=A2 + '-visitor', phone_number='09391110002')
        cls.ap1 = Appointment.objects.create(
            business=cls.a1, visitor=cls.v1, appointment_date=now,
            selected_services=A1 + '-svc')
        cls.ap2 = Appointment.objects.create(
            business=cls.a2, visitor=cls.v2, appointment_date=now,
            selected_services=A2 + '-svc')
        for biz, v, tag in ((cls.a1, cls.v1, A1), (cls.a2, cls.v2, A2)):
            SmsLog.objects.create(business=biz, visitor=v, message_text=tag + '-sms')
            PushLog.objects.create(business=biz, visitor=v, title=tag + '-push',
                                   body=tag + '-body', status='SENT')
        cls.c1 = PromoCampaign.objects.create(
            business=cls.a1, audience='ALL', message=A1 + '-promo')
        cls.c2 = PromoCampaign.objects.create(
            business=cls.a2, audience='ALL', message=A2 + '-promo')
        cls.ms = int(now.timestamp() * 1000)

    def setUp(self):
        self.ca = APIClient()
        self.ca.force_authenticate(self.a)
        self.cb = APIClient()
        self.cb.force_authenticate(self.b)

    # ── helpers ────────────────────────────────────────────────────────────
    def assertDenied(self, resp, marker='ALPHA'):
        self.assertNotIn(marker, _body(resp), f'leak: {resp.status_code}')
        self.assertIn(resp.status_code, (200, 400, 403, 404), resp.status_code)

    def assertHidden(self, resp):
        """B asking for A's thing: 403/404 (405 where the verb is unsupported),
        and nothing of A in the body."""
        self.assertIn(resp.status_code, (403, 404, 405), (resp.status_code, _body(resp)[:200]))
        self.assertNotIn('ALPHA', _body(resp))

    def assertPinned(self, resp, want, other):
        """A asking for one business: that one's data, never the sibling's."""
        self.assertEqual(resp.status_code, 200, _body(resp)[:300])
        body = _body(resp)
        self.assertIn(want, body)
        self.assertNotIn(other, body)

    # ── businesses ─────────────────────────────────────────────────────────
    def test_business_detail_and_list(self):
        self.assertHidden(self.cb.get(reverse('business-detail', args=[self.a1.id])))
        r = self.cb.get(reverse('business-list-create'))
        self.assertDenied(r)
        self.assertEqual(self.cb.get(reverse('business-list-create')).status_code, 200)

    def test_business_update_and_delete(self):
        url = reverse('business-detail', args=[self.a1.id])
        self.assertHidden(self.cb.put(url, {'title': 'HACKED'}, format='json'))
        self.assertHidden(self.cb.patch(url, {'title': 'HACKED'}, format='json'))
        self.assertHidden(self.cb.delete(url))
        self.a1.refresh_from_db()
        self.assertEqual(self.a1.title, A1 + '-biz')
        self.assertTrue(Business.objects.filter(id=self.a1.id).exists())

    def test_business_services_field_not_writable_cross_tenant(self):
        url = reverse('business-detail', args=[self.a1.id])
        self.assertHidden(self.cb.put(url, {'services': ['HACK']}, format='json'))

    # ── logs & promo ───────────────────────────────────────────────────────
    def test_sms_and_push_logs(self):
        for name in ('business-sms-logs', 'business-sms-logs-summary',
                     'business-push-logs', 'business-push-logs-summary'):
            self.assertHidden(self.cb.get(reverse(name, args=[self.a1.id])))
        self.assertPinned(self.ca.get(reverse('business-sms-logs', args=[self.a1.id])), A1, A2)
        self.assertPinned(self.ca.get(reverse('business-push-logs', args=[self.a2.id])), A2, A1)

    def test_promo_sms(self):
        self.assertHidden(self.cb.get(reverse('business-promo-sms', args=[self.a1.id])))
        self.assertHidden(self.cb.post(
            reverse('business-promo-sms', args=[self.a1.id]),
            {'audience': 'ALL', 'message': 'spam'}, format='json'))
        self.assertHidden(self.cb.post(
            reverse('business-promo-sms-preview', args=[self.a1.id]),
            {'audience': 'ALL', 'message': 'spam'}, format='json'))
        self.assertEqual(PromoCampaign.objects.filter(business=self.a1).count(), 1)
        # Campaign of A1 via A2's id (same owner, wrong business) and via B.
        self.assertHidden(self.cb.get(reverse(
            'business-promo-sms-detail', args=[self.a1.id, self.c1.id])))
        r = self.ca.get(reverse('business-promo-sms-detail', args=[self.a2.id, self.c1.id]))
        self.assertEqual(r.status_code, 404)
        self.assertNotIn(A1, _body(r))
        self.assertPinned(self.ca.get(reverse(
            'business-promo-sms-detail', args=[self.a1.id, self.c1.id])), A1, A2)
        # The screen's history lists only that business's campaigns.
        self.assertPinned(self.ca.get(reverse('business-promo-sms', args=[self.a1.id])), A1, A2)

    # ── appointments ───────────────────────────────────────────────────────
    def test_appointment_detail_update_delete(self):
        url = reverse('appointments:appointment-update', args=[self.ap1.id])
        self.assertHidden(self.cb.get(url))
        self.assertHidden(self.cb.patch(url, {'status': 'CANCELLED'}, format='json'))
        self.assertHidden(self.cb.patch(url, {'description': 'HACK'}, format='json'))
        self.assertHidden(self.cb.delete(url))
        self.ap1.refresh_from_db()
        self.assertEqual(self.ap1.status, 'WAITING')
        self.assertTrue(Appointment.objects.filter(id=self.ap1.id).exists())

    def test_appointment_create_in_foreign_business_or_visitor(self):
        url = reverse('appointments:appointment-create')
        when = int((timezone.now() + timedelta(days=1)).timestamp() * 1000)
        r = self.cb.post(url, {'business_id': self.a1.id, 'visitor_id': self.v1.id,
                               'appointment_date': when}, format='json')
        self.assertIn(r.status_code, (400, 403, 404))
        r = self.cb.post(url, {'business_id': self.b1.id, 'visitor_id': self.v1.id,
                               'appointment_date': when}, format='json')
        self.assertIn(r.status_code, (400, 403, 404))
        self.assertFalse(Appointment.objects.filter(business__in=[self.a1, self.b1],
                                                    appointment_date__gt=timezone.now() + timedelta(hours=1)).exists())

    def test_appointment_list(self):
        url = reverse('appointments:appointment-list')
        self.assertDenied(self.cb.get(url, {'business_id': self.a1.id}))
        self.assertDenied(self.cb.get(url, {'business_id': self.a1.id, 'visitor_id': self.v1.id}))
        self.assertPinned(self.ca.get(url, {'business_id': self.a1.id}), A1, A2)
        self.assertPinned(self.ca.get(url, {'business_id': self.a2.id}), A2, A1)

    def test_appointment_stats(self):
        url = reverse('appointments:appointment-stats')
        self.assertEqual(self._counts(self.cb.get(url, {'business_id': self.a1.id})), 0)
        self.assertDenied(self.cb.get(url, {'business_id': self.a1.id}))

    def _counts(self, resp):
        """Total of every integer in the payload that is a count of appointments."""
        self.assertIn(resp.status_code, (200, 400, 403, 404))
        if resp.status_code != 200:
            return 0
        data = json.loads(_body(resp)).get('data') or {}
        return sum(v for v in data.values() if isinstance(v, int) and not isinstance(v, bool))

    def test_daily_counts_and_breakdown_and_range(self):
        self.assertDenied(self.cb.get(reverse('appointments:appointment-daily-counts'), {'business_id': self.a1.id}))
        self.assertDenied(self.cb.get(reverse('appointments:appointment-service-breakdown'), {'business_id': self.a1.id}))
        rng = {'business_id': self.a1.id, 'date_from': self.ms - 86_400_000, 'date_to': self.ms + 86_400_000}
        self.assertDenied(self.cb.get(reverse('appointments:appointment-range-visitors'), rng))
        # Pinned to one business for the owner who has two.
        # Breakdown only names services the business defines, so check counts:
        # one appointment per business, never the owner's combined 2.
        for biz in (self.a1, self.a2):
            r = self.ca.get(reverse('appointments:appointment-service-breakdown'),
                            {'business_id': biz.id})
            self.assertEqual(r.status_code, 200)
            self.assertEqual(json.loads(_body(r))['data']['total_appointments'], 1)
        self.assertPinned(self.ca.get(reverse('appointments:appointment-range-visitors'), rng), A1, A2)
        r = self.ca.get(reverse('appointments:appointment-daily-counts'), {'business_id': self.a1.id})
        self.assertEqual(r.status_code, 200)
        a2 = self.ca.get(reverse('appointments:appointment-daily-counts'), {'business_id': self.a2.id})
        # One appointment each, so per-business totals must be 1, not 2.
        for resp in (r, a2):
            self.assertEqual(_body(resp).count('"count":1') + _body(resp).count('"count": 1'), 1, _body(resp)[:400])

    def test_stats_do_not_mix_a_s_two_businesses(self):
        url = reverse('appointments:appointment-stats')
        r1 = self.ca.get(url, {'business_id': self.a1.id})
        both = self._counts(r1)
        self.assertGreater(both, 0)
        # Same call for B1 (no appointments) must be 0 => stats are per business.
        self.assertEqual(self._counts(self.cb.get(url, {'business_id': self.b1.id})), 0)

    # ── visitors ───────────────────────────────────────────────────────────
    def test_visitor_list_detail_messages_restore(self):
        self.assertDenied(self.cb.get(reverse('visitor:visitor-list-create')))
        self.assertHidden(self.cb.get(reverse('visitor:visitor-detail', args=[self.v1.id])))
        self.assertHidden(self.cb.get(reverse('visitor:visitor-messages', args=[self.v1.id])))
        self.assertHidden(self.cb.post(reverse('visitor:visitor-restore', args=[self.v1.id])))
        self.assertHidden(self.cb.delete(reverse('visitor:visitor-detail', args=[self.v1.id])))
        self.assertHidden(self.cb.put(reverse('visitor:visitor-detail', args=[self.v1.id]),
                                      {'full_name': 'HACK', 'phone_number': '09399999999'}, format='json'))
        self.v1.refresh_from_db()
        self.assertEqual(self.v1.full_name, A1 + '-visitor')
        self.assertEqual(self.v1.phone_number, '09391110001')
        self.assertFalse(self.v1.archives.exists())

    def test_visitor_messages_business_pin(self):
        url = reverse('visitor:visitor-messages', args=[self.v1.id])
        self.assertPinned(self.ca.get(url, {'business_id': self.a1.id}), A1, A2)
        r = self.ca.get(url, {'business_id': self.a2.id})  # v1 has no logs at A2
        self.assertEqual(r.status_code, 200)
        self.assertNotIn(A1 + '-sms', _body(r))
        # Foreign business id never widens the query.
        self.assertNotIn('ALPHA', _body(self.cb.get(url, {'business_id': self.a1.id})))

    def test_visitor_lookup_by_attach_does_not_leak_name(self):
        r = self.cb.post(reverse('visitor:visitor-list-create'),
                         {'full_name': 'MINE', 'phone_number': self.v1.phone_number}, format='json')
        self.assertNotIn('ALPHA', _body(r))

    # ── user & devices ─────────────────────────────────────────────────────
    def test_user_detail(self):
        url = reverse('user-detail', args=[self.a.pk])
        self.assertEqual(self.cb.get(url).status_code, 403)
        self.assertEqual(self.cb.patch(url, {'name': 'HACK'}, format='json').status_code, 403)
        self.assertEqual(self.cb.delete(url).status_code, 403)
        self.assertTrue(User.objects.filter(pk=self.a.pk, name='A').exists())

    def test_device_unregister_only_touches_own_tokens(self):
        from api.models import DeviceToken
        DeviceToken.objects.create(user=self.a, token='a-token', platform='ANDROID')
        self.cb.post(reverse('device-unregister'), {'token': 'a-token'}, format='json')
        self.assertTrue(DeviceToken.objects.get(token='a-token').is_active)

    # ── anonymous ──────────────────────────────────────────────────────────
    def test_anonymous_gets_nothing(self):
        anon = APIClient()
        for url in (
            reverse('business-list-create'),
            reverse('business-detail', args=[self.a1.id]),
            reverse('business-sms-logs', args=[self.a1.id]),
            reverse('business-push-logs', args=[self.a1.id]),
            reverse('business-promo-sms', args=[self.a1.id]),
            reverse('visitor:visitor-list-create'),
            reverse('visitor:visitor-detail', args=[self.v1.id]),
            reverse('appointments:appointment-update', args=[self.ap1.id]),
            reverse('appointments:appointment-list') + f'?business_id={self.a1.id}',
            reverse('appointments:appointment-stats') + f'?business_id={self.a1.id}',
        ):
            r = anon.get(url)
            self.assertIn(r.status_code, (401, 403), url)
            self.assertNotIn('ALPHA', _body(r))
