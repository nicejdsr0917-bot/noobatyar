from django.test import TestCase

# Create your tests here.


class ClientBioTests(TestCase):
    def setUp(self):
        from visitor.auth import sign_visitor_token
        from visitor.models import Visitor
        self.v = Visitor.objects.create(full_name='علی رضایی', phone_number='09350000777')
        self.auth = f'Visitor {sign_visitor_token(self.v.id)}'

    def patch(self, body):
        return self.client.patch('/api/client/auth/me/', body, content_type='application/json',
                                 HTTP_AUTHORIZATION=self.auth)

    def test_bio_alone_is_saved_and_returned(self):
        r = self.patch({'bio': '  عاشق کوتاهی مو   هستم  '})
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.json()['data']['bio'], 'عاشق کوتاهی مو هستم')
        self.assertEqual(r.json()['data']['full_name'], 'علی رضایی')
        self.v.refresh_from_db()
        self.assertEqual(self.v.bio, 'عاشق کوتاهی مو هستم')

    def test_150_characters_is_the_limit(self):
        self.assertEqual(self.patch({'bio': 'ا' * 150}).status_code, 200)
        r = self.patch({'bio': 'ا' * 151})
        self.assertEqual(r.status_code, 400)
        self.v.refresh_from_db()
        self.assertEqual(len(self.v.bio), 150)

    def test_bio_can_be_cleared_and_name_still_saves_alone(self):
        self.patch({'bio': 'سلام'})
        self.assertEqual(self.patch({'bio': ''}).json()['data']['bio'], '')
        self.assertEqual(self.patch({'full_name': 'علی'}).json()['data']['full_name'], 'علی')

    def test_empty_patch_is_refused(self):
        self.assertEqual(self.patch({}).status_code, 400)
