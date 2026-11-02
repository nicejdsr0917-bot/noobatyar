import io
import shutil
import tempfile

from django.test import TestCase, override_settings
from django.urls import reverse
from PIL import Image
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework.test import APIClient

from visitor.auth import sign_visitor_token
from visitor.models import Visitor

MEDIA = tempfile.mkdtemp()


def jpeg(w, h):
    buf = io.BytesIO()
    Image.new('RGB', (w, h), (120, 80, 200)).save(buf, format='JPEG')
    return SimpleUploadedFile('a.jpg', buf.getvalue(), content_type='image/jpeg')


@override_settings(MEDIA_ROOT=MEDIA)
class ClientProfileTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA, ignore_errors=True)

    def setUp(self):
        self.visitor = Visitor.objects.create(full_name='مریم', phone_number='09120000001')
        self.client = APIClient()
        self.client.credentials(HTTP_AUTHORIZATION=f'Visitor {sign_visitor_token(self.visitor.id)}')

    def test_me_returns_profile_without_avatar(self):
        res = self.client.get(reverse('client-me'))
        self.assertEqual(res.status_code, 200)
        data = res.json()['data']
        self.assertEqual(data['full_name'], 'مریم')
        self.assertIsNone(data['avatar'])

    def test_rename_trims_and_saves(self):
        res = self.client.patch(reverse('client-me'), {'full_name': '  مریم   کریمی '}, format='json')
        self.assertEqual(res.status_code, 200)
        self.visitor.refresh_from_db()
        self.assertEqual(self.visitor.full_name, 'مریم کریمی')

    def test_rename_rejects_one_letter(self):
        res = self.client.patch(reverse('client-me'), {'full_name': 'م'}, format='json')
        self.assertEqual(res.status_code, 400)
        self.visitor.refresh_from_db()
        self.assertEqual(self.visitor.full_name, 'مریم')

    def test_rename_cannot_touch_phone(self):
        self.client.patch(reverse('client-me'), {'full_name': 'مریم', 'phone_number': '09129999999'}, format='json')
        self.visitor.refresh_from_db()
        self.assertEqual(self.visitor.phone_number, '09120000001')

    def test_avatar_is_cropped_square_and_replaced(self):
        res = self.client.post(reverse('client-me-avatar'), {'avatar': jpeg(1200, 800)}, format='multipart')
        self.assertEqual(res.status_code, 200)
        self.visitor.refresh_from_db()
        first = self.visitor.avatar.name
        with Image.open(self.visitor.avatar.path) as img:
            self.assertEqual(img.size, (512, 512))
        self.assertTrue(res.json()['data']['avatar'].startswith('http'))

        self.client.post(reverse('client-me-avatar'), {'avatar': jpeg(800, 800)}, format='multipart')
        self.visitor.refresh_from_db()
        self.assertNotEqual(self.visitor.avatar.name, first)
        self.assertFalse(self.visitor.avatar.storage.exists(first))

    def test_avatar_rejects_non_image(self):
        bad = SimpleUploadedFile('a.jpg', b'not an image', content_type='image/jpeg')
        res = self.client.post(reverse('client-me-avatar'), {'avatar': bad}, format='multipart')
        self.assertEqual(res.status_code, 400)

    def test_avatar_delete(self):
        self.client.post(reverse('client-me-avatar'), {'avatar': jpeg(600, 600)}, format='multipart')
        res = self.client.delete(reverse('client-me-avatar'))
        self.assertEqual(res.status_code, 200)
        self.visitor.refresh_from_db()
        self.assertFalse(self.visitor.avatar)

    def test_owner_serializer_does_not_expose_avatar(self):
        from visitor.serializers import VisitorSerializer
        self.assertNotIn('avatar', VisitorSerializer(self.visitor).data)

    def test_requires_sign_in(self):
        res = APIClient().get(reverse('client-me'))
        self.assertIn(res.status_code, (401, 403))
