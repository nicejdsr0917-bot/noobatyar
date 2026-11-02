"""Logo is required on business CREATE only for clients that opt in
(`logo_required=1` field or `X-Logo-Required: 1` header); old apps unaffected."""
import io
import shutil
import tempfile

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from PIL import Image
from rest_framework.test import APIClient

User = get_user_model()
MEDIA = tempfile.mkdtemp()


def logo():
    buf = io.BytesIO()
    Image.new('RGB', (64, 64), (1, 2, 3)).save(buf, 'PNG')
    return SimpleUploadedFile('l.png', buf.getvalue(), content_type='image/png')


@override_settings(MEDIA_ROOT=MEDIA)
class LogoRequiredTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA, ignore_errors=True)

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000701', password='x', name='O')
        self.c = APIClient()
        self.c.force_authenticate(self.user)

    def body(self, **extra):
        return {'title': 'سالن', 'phone': '02100000000', 'address': 'تهران',
                'default_service_duration': 30, 'work_start_hour': 9, 'work_end_hour': 17, **extra}

    def post(self, headers=None, **extra):
        return self.c.post('/api/business/', self.body(**extra), format='multipart', **(headers or {}))

    def test_old_clients_can_still_create_without_logo(self):
        self.assertEqual(self.post().status_code, 201)

    def test_opt_in_field_requires_logo(self):
        r = self.post(logo_required='1')
        self.assertEqual(r.status_code, 400)
        self.assertEqual(self.post(logo_required='1', logo=logo()).status_code, 201)

    def test_opt_in_header_requires_logo(self):
        self.assertEqual(self.post(headers={'HTTP_X_LOGO_REQUIRED': '1'}).status_code, 400)

    def test_updates_never_enforced(self):
        bid = self.post().json()['data']['id']
        r = self.c.put(f'/api/business/{bid}/', {'title': 'جدید', 'logo_required': '1'}, format='multipart')
        self.assertEqual(r.status_code, 200, r.content)
