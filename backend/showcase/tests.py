import io
import shutil
import tempfile
from unittest import mock

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from PIL import Image
from rest_framework.test import APIClient

from business.models import Business
from core.outbox.models import OutboxMessage

from .models import Certificate, ModerationStatus, PortfolioItem
from . import services

User = get_user_model()
MEDIA = tempfile.mkdtemp()


def png(w, h, name='a.png', fmt='PNG'):
    buf = io.BytesIO()
    Image.new('RGB', (w, h), (200, 30, 30)).save(buf, format=fmt)
    return SimpleUploadedFile(name, buf.getvalue(), content_type='image/png')


def make_business(user, title='سالن'):
    return Business.objects.create(
        user=user, title=title, phone='02100000000', address='تهران',
        default_service_duration=30, work_start_hour=9, work_end_hour=17,
        moderation_status=Business.MODERATION_APPROVED, first_approved_at='2024-01-01T00:00:00Z',
    )


@override_settings(MEDIA_ROOT=MEDIA)
class ShowcaseApiTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA, ignore_errors=True)

    def setUp(self):
        self.a = User.objects.create_user(phone='09120000401', password='x', name='A')
        self.b = User.objects.create_user(phone='09120000402', password='x', name='B')
        self.biz_a = make_business(self.a)
        self.biz_b = make_business(self.b, 'دیگری')
        self.c = APIClient()
        self.c.force_authenticate(self.a)

    def post_portfolio(self, w=1080, h=1080, **extra):
        return self.c.post('/api/owner/showcase/portfolio/', {
            'business_id': self.biz_a.id, 'image': png(w, h), 'caption': 'cap', **extra}, format='multipart')

    def test_auth_required(self):
        r = APIClient().get('/api/owner/showcase/portfolio/', {'business_id': self.biz_a.id})
        self.assertEqual(r.status_code, 401)

    def test_create_downscales_and_queues(self):
        r = self.post_portfolio(2000, 2000)
        self.assertEqual(r.status_code, 201, r.content)
        item = PortfolioItem.objects.get()
        self.assertEqual(item.status, ModerationStatus.PENDING)
        with Image.open(item.image.path) as im:
            self.assertEqual(im.size, (1080, 1080))
            self.assertEqual(im.format, 'JPEG')
        with Image.open(item.thumbnail.path) as im:
            self.assertEqual(im.width, 360)
        self.assertTrue(OutboxMessage.objects.filter(kind='bale.showcase_card').exists())
        self.assertEqual(r.json()['data']['status'], 'PENDING')

    def test_rejects_small_and_wrong_shape_and_garbage(self):
        self.assertEqual(self.post_portfolio(500, 500).status_code, 400)
        self.assertEqual(self.post_portfolio(1080, 540).status_code, 400)  # 2:1 is none of the shapes
        r = self.c.post('/api/owner/showcase/portfolio/', {
            'business_id': self.biz_a.id,
            'image': SimpleUploadedFile('x.png', b'not an image')}, format='multipart')
        self.assertEqual(r.status_code, 400)
        self.assertEqual(PortfolioItem.objects.count(), 0)

    def test_caption_limit_and_missing_image(self):
        self.assertEqual(self.post_portfolio(caption='x' * 301).status_code, 400)
        r = self.c.post('/api/owner/showcase/portfolio/', {'business_id': self.biz_a.id}, format='multipart')
        self.assertEqual(r.status_code, 400)

    def test_max_30(self):
        for _ in range(30):
            self.assertEqual(self.post_portfolio(600, 600).status_code, 201)
        self.assertEqual(self.post_portfolio(600, 600).status_code, 400)

    def test_tenant_isolation(self):
        item = PortfolioItem.objects.create(business=self.biz_b, image='x.jpg', caption='b')
        cert = Certificate.objects.create(business=self.biz_b, title='t', image='x.jpg')
        self.assertEqual(self.c.get('/api/owner/showcase/portfolio/', {'business_id': self.biz_b.id}).status_code, 404)
        self.assertEqual(self.c.patch(f'/api/owner/showcase/portfolio/{item.id}/', {'caption': 'h'}).status_code, 404)
        self.assertEqual(self.c.delete(f'/api/owner/showcase/portfolio/{item.id}/').status_code, 404)
        self.assertEqual(self.c.delete(f'/api/owner/showcase/certificates/{cert.id}/').status_code, 404)
        self.assertEqual(self.post_to(self.biz_b).status_code, 404)
        r = self.c.post('/api/owner/showcase/portfolio/reorder/', {'business_id': self.biz_b.id, 'ids': [item.id]}, format='json')
        self.assertEqual(r.status_code, 404)
        item.refresh_from_db()
        self.assertEqual(item.caption, 'b')

    def post_to(self, biz):
        return self.c.post('/api/owner/showcase/portfolio/', {
            'business_id': biz.id, 'image': png(1080, 1080)}, format='multipart')

    def test_edit_caption_requeues_but_order_does_not(self):
        item = PortfolioItem.objects.create(
            business=self.biz_a, image='x.jpg', caption='old', status=ModerationStatus.APPROVED)
        r = self.c.patch(f'/api/owner/showcase/portfolio/{item.id}/', {'order': 5}, format='json')
        self.assertEqual(r.status_code, 200)
        item.refresh_from_db()
        self.assertEqual((item.order, item.status), (5, ModerationStatus.APPROVED))
        self.c.patch(f'/api/owner/showcase/portfolio/{item.id}/', {'caption': 'new'}, format='json')
        item.refresh_from_db()
        self.assertEqual(item.status, ModerationStatus.PENDING)

    def test_reorder(self):
        items = [PortfolioItem.objects.create(business=self.biz_a, image='x.jpg', order=i) for i in range(3)]
        ids = [items[2].id, items[0].id, items[1].id]
        r = self.c.post('/api/owner/showcase/portfolio/reorder/',
                        {'business_id': self.biz_a.id, 'ids': ids}, format='json')
        self.assertEqual(r.status_code, 200)
        self.assertEqual([i['id'] for i in r.json()['data']], ids)
        bad = self.c.post('/api/owner/showcase/portfolio/reorder/',
                          {'business_id': self.biz_a.id, 'ids': ids[:2]}, format='json')
        self.assertEqual(bad.status_code, 400)

    def test_square_portrait_and_landscape_are_all_accepted(self):
        # The owner picks the frame; a landscape one is downscaled, not cropped.
        for w, h in [(1080, 1080), (1080, 1350), (2000, 1500)]:
            self.assertEqual(self.post_portfolio(w, h).status_code, 201, (w, h))
        with Image.open(PortfolioItem.objects.order_by('-id').first().image.path) as im:
            self.assertEqual(im.size, (1080, 810))

    def test_certificate_flow_and_ratio(self):
        url = '/api/owner/showcase/certificates/'
        bad = self.c.post(url, {'business_id': self.biz_a.id, 'title': 'T', 'image': png(1080, 540)}, format='multipart')
        self.assertEqual(bad.status_code, 400)
        landscape = self.c.post(url, {'business_id': self.biz_a.id, 'title': 'L', 'image': png(1080, 810)}, format='multipart')
        self.assertEqual(landscape.status_code, 201, landscape.content)
        ok = self.c.post(url, {'business_id': self.biz_a.id, 'title': 'T', 'issuer': 'I', 'year': 1402,
                               'image': png(1080, 1350)}, format='multipart')
        self.assertEqual(ok.status_code, 201, ok.content)
        self.assertEqual(ok.json()['data']['year'], 1402)
        no_title = self.c.post(url, {'business_id': self.biz_a.id, 'image': png(1080, 1350)}, format='multipart')
        self.assertEqual(no_title.status_code, 400)

    def test_public_shows_only_approved_ordered_and_hides_hidden_business(self):
        PortfolioItem.objects.create(business=self.biz_a, image='a.jpg', order=2, status=ModerationStatus.APPROVED)
        PortfolioItem.objects.create(business=self.biz_a, image='b.jpg', order=1, status=ModerationStatus.APPROVED)
        PortfolioItem.objects.create(business=self.biz_a, image='c.jpg', order=0, status=ModerationStatus.PENDING)
        PortfolioItem.objects.create(business=self.biz_a, image='d.jpg', order=0, status=ModerationStatus.REJECTED)
        Certificate.objects.create(business=self.biz_a, title='ok', image='a.jpg', status=ModerationStatus.APPROVED)
        Certificate.objects.create(business=self.biz_a, title='no', image='a.jpg')
        anon = APIClient()
        r = anon.get(f'/api/client/businesses/{self.biz_a.id}/showcase/')
        self.assertEqual(r.status_code, 200)
        data = r.json()['data']
        self.assertEqual([p['order'] for p in data['portfolio']], [1, 2])
        self.assertEqual([c['title'] for c in data['certificates']], ['ok'])
        self.assertNotIn('status', data['portfolio'][0])
        # approving a pending item shows up immediately (cache stamp bumped)
        pend = PortfolioItem.objects.get(image='c.jpg')
        services.decide(pend, 'portfolio', ModerationStatus.APPROVED)
        r = anon.get(f'/api/client/businesses/{self.biz_a.id}/showcase/')
        self.assertEqual(len(r.json()['data']['portfolio']), 3)
        Business.objects.filter(pk=self.biz_a.pk).update(moderation_status=Business.MODERATION_SUSPENDED)
        self.biz_a.refresh_from_db()
        self.biz_a.save()
        self.assertEqual(anon.get(f'/api/client/businesses/{self.biz_a.id}/showcase/').status_code, 404)

    def test_decide_requires_reason_and_replay_guard(self):
        item = PortfolioItem.objects.create(business=self.biz_a, image='a.jpg')
        with self.assertRaises(ValueError):
            services.decide(item, 'portfolio', ModerationStatus.REJECTED)
        self.assertTrue(services.decide(item, 'portfolio', ModerationStatus.REJECTED, reason='bad'))
        self.assertFalse(services.decide(item, 'portfolio', ModerationStatus.REJECTED, reason='bad'))
        self.assertEqual(item.reject_reason, 'bad')
        r = self.c.get('/api/owner/showcase/portfolio/', {'business_id': self.biz_a.id})
        self.assertEqual(r.json()['data'][0]['reject_reason'], 'bad')


@override_settings(MEDIA_ROOT=MEDIA)
class ShowcaseBaleTests(TestCase):
    def setUp(self):
        from bale.models import BaleSettings
        self.owner = User.objects.create_user(phone='09120000411', password='x', name='O')
        self.biz = make_business(self.owner)
        self.item = PortfolioItem.objects.create(business=self.biz, image='a.jpg', caption='c')
        self.config = BaleSettings.load()
        self.config.chat_id = '42'
        self.config.bot_token = 't'

    def cb(self, data, sender='42'):
        from bale import handlers
        with mock.patch.object(handlers, 'answer_callback_query'), \
                mock.patch.object(handlers, 'edit_message_text') as edit:
            handlers.handle(self.config, {'callback_query': {
                'id': '1', 'from': {'id': sender}, 'data': data, 'message': {'message_id': 9}}})
        return edit

    def test_parse(self):
        from bale.keyboards import parse_showcase_callback as p
        self.assertEqual(p('s:p:5:A')['action'], 'approve')
        self.assertEqual(p('s:c:5:R:1')['kind'], 'certificate')
        for bad in ('s:x:5:A', 's:p:z:A', 's:p:5:R:99', 's:p:5', 'm:d:1:A', None):
            self.assertIsNone(p(bad))

    def test_approve_and_reject_and_replay(self):
        edit = self.cb(f's:p:{self.item.id}:A')
        self.item.refresh_from_db()
        self.assertEqual(self.item.status, 'APPROVED')
        self.assertTrue(edit.called)
        self.cb(f's:p:{self.item.id}:R:0')  # replay-ish: different decision applies
        self.item.refresh_from_db()
        self.assertEqual(self.item.status, 'REJECTED')
        self.assertTrue(self.item.reject_reason)
        self.cb(f's:p:{self.item.id}:R:0')
        self.item.refresh_from_db()
        self.assertEqual(self.item.status, 'REJECTED')

    def test_unauthorised_sender_ignored(self):
        self.cb(f's:p:{self.item.id}:A', sender='99')
        self.item.refresh_from_db()
        self.assertEqual(self.item.status, 'PENDING')

    def test_menu_and_card_send(self):
        edit = self.cb(f's:p:{self.item.id}:Q')
        self.assertTrue(edit.called)
        from bale import notify
        with mock.patch.object(notify, 'send_message', return_value={'success': True}) as sm, \
                mock.patch.object(notify.BaleSettings, 'load', return_value=mock.Mock(
                    is_configured=True, bot_token='t', chat_id='42')):
            notify.send_showcase_card('portfolio', self.item.id)
        self.assertIn('s:p:%d:A' % self.item.id, str(sm.call_args))


@override_settings(MEDIA_ROOT=MEDIA)
class ShowcaseAdminTests(TestCase):
    def test_admin_pages_and_actions(self):
        from django.urls import reverse
        staff = User.objects.create_superuser(phone='09120000420', password='x', name='S')
        owner = User.objects.create_user(phone='09120000421', password='x', name='O')
        biz = make_business(owner)
        p = PortfolioItem.objects.create(business=biz, image='a.jpg')
        c = Certificate.objects.create(business=biz, title='T', image='a.jpg')
        self.client.force_login(staff)
        for obj in (p, c):
            m = type(obj)._meta
            self.assertEqual(self.client.get(reverse(f'admin:{m.app_label}_{m.model_name}_changelist')).status_code, 200)
            self.assertEqual(self.client.get(reverse(f'admin:{m.app_label}_{m.model_name}_change', args=[obj.pk])).status_code, 200)
        r = self.client.post(reverse('admin:showcase_portfolioitem_changelist'),
                             {'action': 'approve_selected', '_selected_action': [p.pk]})
        self.assertEqual(r.status_code, 302)
        p.refresh_from_db()
        self.assertEqual((p.status, p.reviewed_by), ('APPROVED', staff))
        self.client.post(reverse('admin:showcase_certificate_changelist'),
                         {'action': 'reject_selected', '_selected_action': [c.pk]})
        c.refresh_from_db()
        self.assertEqual(c.status, 'REJECTED')
        self.assertTrue(c.reject_reason)
