import io
import shutil
import tempfile
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.test import APIClient

from business.models import Business

from .models import Banner, BannerCategoryGroup, SiteSetting, validate_banner_size

User = get_user_model()
MEDIA = tempfile.mkdtemp()


def img(w=1200, h=400, name='b.png'):
    buf = io.BytesIO()
    Image.new('RGB', (w, h), (10, 20, 200)).save(buf, 'PNG')
    return SimpleUploadedFile(name, buf.getvalue(), content_type='image/png')


def biz(user, category):
    return Business.objects.create(
        user=user, title='x', category=category, phone='02100000000', address='t',
        default_service_duration=30, work_start_hour=9, work_end_hour=17)


@override_settings(MEDIA_ROOT=MEDIA)
class BannerTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA, ignore_errors=True)

    def setUp(self):
        self.a = User.objects.create_user(phone='09120000501', password='x', name='A')
        self.b = User.objects.create_user(phone='09120000502', password='x', name='B')
        self.doc = biz(self.a, 'DOCTOR')
        self.salon = biz(self.b, 'BEAUTY_SALON')
        self.group = BannerCategoryGroup.objects.create(key='MEDICAL')
        self.c = APIClient()
        self.c.force_authenticate(self.a)

    def test_size_validator_is_exact(self):
        validate_banner_size(img())
        for size in [(1201, 400), (1200, 399), (600, 200), (2400, 800)]:
            with self.assertRaises(ValidationError):
                validate_banner_size(img(*size))
        with self.assertRaises(ValidationError):
            validate_banner_size(SimpleUploadedFile('x.png', b'nope'))

    def test_clean_needs_exactly_one_target(self):
        with self.assertRaises(ValidationError):
            Banner(image='x.png').clean()
        with self.assertRaises(ValidationError):
            Banner(image='x.png', group=self.group, category='DOCTOR').clean()
        Banner(image='x.png', group=self.group).clean()

    def test_owner_sees_group_and_category_banners_with_link(self):
        Banner.objects.create(group=self.group, image='g.png', order=2)
        Banner.objects.create(category='DOCTOR', image='c.png', order=1)
        Banner.objects.create(category='BEAUTY_SALON', image='s.png')
        Banner.objects.create(group=self.group, image='off.png', is_active=False)
        now = timezone.now()
        Banner.objects.create(group=self.group, image='future.png', starts_at=now + timedelta(days=1))
        Banner.objects.create(group=self.group, image='past.png', ends_at=now - timedelta(days=1))
        s = SiteSetting.load()
        s.banner_link_url = 'https://noobatyar.ir/plans'
        s.save()
        r = self.c.get('/api/owner/banners/', {'business_id': self.doc.id})
        self.assertEqual(r.status_code, 200)
        data = r.json()['data']
        self.assertEqual([x['image'].rsplit('/', 1)[-1] for x in data['banners']], ['c.png', 'g.png'])
        self.assertEqual(data['link_url'], 'https://noobatyar.ir/plans')

    def test_auth_and_tenant_isolation_and_params(self):
        self.assertEqual(APIClient().get('/api/owner/banners/', {'business_id': self.doc.id}).status_code, 401)
        self.assertEqual(self.c.get('/api/owner/banners/', {'business_id': self.salon.id}).status_code, 404)
        self.assertEqual(self.c.get('/api/owner/banners/').status_code, 400)
        self.assertEqual(self.c.get('/api/owner/banners/', {'business_id': 'x'}).status_code, 400)

    def test_admin_rejects_wrong_size_and_shows_owner_counts(self):
        staff = User.objects.create_superuser(phone='09120000510', password='x', name='S')
        self.client.force_login(staff)
        url = reverse('admin:banners_banner_add')
        bad = self.client.post(url, {'group': self.group.id, 'image': img(1000, 400), 'order': 0, 'is_active': 'on'})
        self.assertEqual(bad.status_code, 200)
        self.assertEqual(Banner.objects.count(), 0)
        self.assertContains(bad, '1200')
        ok = self.client.post(url, {'group': self.group.id, 'image': img(), 'order': 0, 'is_active': 'on'})
        self.assertEqual(ok.status_code, 302, getattr(ok, 'context', None) and ok.context['adminform'].form.errors)
        self.assertEqual(Banner.objects.count(), 1)
        r = self.client.get(reverse('admin:banners_bannercategorygroup_changelist'))
        self.assertEqual(r.status_code, 200)
        self.assertEqual(self.group.owner_count(), 1)
        for name in ('banners_banner_changelist', 'banners_sitesetting_changelist'):
            self.assertEqual(self.client.get(reverse(f'admin:{name}')).status_code, 200)
        self.assertEqual(SiteSetting.objects.count(), 1)


class ClientPromoTests(TestCase):
    url = '/api/client/promos/'

    def promo(self, **kw):
        from .models import ClientPromo
        defaults = dict(text='۳۰٪ تخفیف تا امشب', placement=ClientPromo.PLACEMENT_ALL)
        defaults.update(kw)
        return ClientPromo.objects.create(**defaults)

    def get(self, placement):
        return self.client.get(self.url, {'placement': placement})

    def test_placement_filters_and_all_shows_everywhere(self):
        from .models import ClientPromo
        both = self.promo(order=2)
        explore = self.promo(text='کاوش', placement=ClientPromo.PLACEMENT_EXPLORE, order=1)
        self.promo(text='نقشه', placement=ClientPromo.PLACEMENT_BUSINESS_MAP)
        ids = [p['id'] for p in self.get('EXPLORE').json()['data']['promos']]
        self.assertEqual(ids, [explore.id, both.id])

    def test_inactive_and_out_of_window_are_hidden(self):
        from datetime import timedelta
        from django.utils import timezone
        now = timezone.now()
        self.promo(is_active=False)
        self.promo(starts_at=now + timedelta(hours=1))
        self.promo(ends_at=now - timedelta(hours=1))
        live = self.promo(starts_at=now - timedelta(hours=1), ends_at=now + timedelta(hours=1))
        promos = self.get('BUSINESS_MAP').json()['data']['promos']
        self.assertEqual([p['id'] for p in promos], [live.id])

    def test_shape_and_public_access(self):
        self.promo(action_label='مشاهده', link_url='https://noobatyar.ir/off')
        r = self.get('explore')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(
            r.json()['data']['promos'][0],
            {'id': r.json()['data']['promos'][0]['id'], 'text': '۳۰٪ تخفیف تا امشب',
             'action_label': 'مشاهده', 'business_id': None, 'link_url': 'https://noobatyar.ir/off'},
        )

    def test_unknown_placement_is_refused(self):
        self.assertEqual(self.get('HOME').status_code, 400)
        self.assertEqual(self.client.get(self.url).status_code, 400)
