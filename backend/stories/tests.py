"""Business stories: owner create/delete, 24-hour expiry, the button rules,
what customers see, and the once-a-day push to followers."""
import io
import shutil
import tempfile
from datetime import timedelta
from unittest import mock

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.test import APIClient

from api.models import User
from business.models import Business, BusinessFollow
from core.outbox.models import OutboxMessage
from core.outbox.service import get_handler
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

from .models import Story

MEDIA = tempfile.mkdtemp()


def photo(w=1200, h=2000):
    buf = io.BytesIO()
    Image.new('RGB', (w, h), (200, 100, 50)).save(buf, format='JPEG')
    return SimpleUploadedFile('s.jpg', buf.getvalue(), content_type='image/jpeg')


@override_settings(MEDIA_ROOT=MEDIA)
class StoryTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA, ignore_errors=True)

    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000700', password='x', name='o')
        self.biz = Business.objects.create(
            user=self.owner, title='سالن', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )
        self.api = APIClient()
        self.api.force_authenticate(self.owner)
        self.visitor = Visitor.objects.create(full_name='علی', phone_number='09350000701')

    def post(self, **fields):
        data = {'business_id': self.biz.id, 'image': photo(), **fields}
        return self.api.post(reverse('owner-stories'), data, format='multipart')

    def test_owner_posts_a_story_scaled_to_the_screen(self):
        r = self.post(caption='سلام')
        self.assertEqual(r.status_code, 201, r.content)
        story = Story.objects.get()
        self.assertLessEqual(story.image.height, 1920)
        self.assertAlmostEqual(story.expires_at - story.created_at, timedelta(hours=24), delta=timedelta(seconds=5))

    def test_caption_is_at_most_100_chars(self):
        self.assertEqual(self.post(caption='x' * 101).status_code, 400)

    def test_discount_button_needs_a_valid_type_and_value(self):
        self.assertEqual(self.post(button='discount').status_code, 400)
        self.assertEqual(self.post(button='discount', discount_type='percent', discount_value=120).status_code, 400)
        self.assertEqual(self.post(button='discount', discount_type='amount', discount_value=500).status_code, 400)
        r = self.post(button='discount', discount_type='percent', discount_value=20)
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.json()['data']['discount_value'], 20)
        self.assertEqual(self.post(button='discount', discount_type='amount', discount_value=50000).status_code, 201)

    def test_only_the_owner_can_post_or_delete(self):
        other = User.objects.create_user(phone='09120000701', password='x', name='x')
        self.post()
        api = APIClient()
        api.force_authenticate(other)
        r = api.post(reverse('owner-stories'), {'business_id': self.biz.id, 'image': photo()}, format='multipart')
        self.assertEqual(r.status_code, 404)
        self.assertEqual(api.delete(reverse('owner-story-detail', args=[Story.objects.get().id])).status_code, 404)
        self.assertEqual(self.api.delete(reverse('owner-story-detail', args=[Story.objects.get().id])).status_code, 200)
        self.assertFalse(Story.objects.exists())

    def test_expired_stories_are_hidden_and_purged(self):
        self.post()
        Story.objects.update(expires_at=timezone.now() - timedelta(minutes=1))
        r = self.client.get(reverse('client-business-stories', args=[self.biz.id]))
        self.assertEqual(r.json()['data']['stories'], [])
        self.assertEqual(self.client.get(reverse('client-stories-active')).json()['data']['business_ids'], [])
        self.post()
        self.assertEqual(Story.objects.count(), 1)  # the expired one was deleted

    def test_customer_sees_stories_ring_and_follow_state(self):
        self.post(caption='تازه')
        self.post(caption='دوم')  # two stories, one id
        self.assertEqual(self.client.get(reverse('client-stories-active')).json()['data']['business_ids'], [self.biz.id])
        r = self.client.get(reverse('client-business-stories', args=[self.biz.id]))
        self.assertNotIn('is_following', r.json()['data'])
        auth = f'Visitor {sign_visitor_token(self.visitor.id)}'
        r = self.client.get(reverse('client-business-stories', args=[self.biz.id]), HTTP_AUTHORIZATION=auth)
        self.assertEqual(r.json()['data']['is_following'], False)
        self.assertEqual(r.json()['data']['stories'][0]['caption'], 'تازه')

    def test_followers_are_pushed_once_a_day(self):
        BusinessFollow.objects.create(business=self.biz, visitor=self.visitor)
        self.post(caption='تخفیف ویژه')
        self.post()
        msgs = OutboxMessage.objects.filter(kind='stories.notify_followers')
        self.assertEqual(msgs.count(), 1)
        with mock.patch('api.services.push.send_to_visitor') as send:
            get_handler('stories.notify_followers')(msgs.get().payload)
        visitor_id, title, body, data = send.call_args.args
        self.assertEqual((visitor_id, title, body, data['type']), (self.visitor.id, 'سالن', 'تخفیف ویژه', 'NEW_STORY'))
