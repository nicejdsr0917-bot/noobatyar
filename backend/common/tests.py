"""Tests for the shared helpers: pagination envelope and strict page params."""

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient

from business.tests_moderation_api import make_business
from common.params import positive_int
from common.timeutils import local_date_midnight, utc_date_tehran_midnight

User = get_user_model()


class BadPageParamsTests(TestCase):
    """Garbage page/page_size is a 400 in the standard envelope, not a 500."""

    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000077', name='مالک')
        make_business(self.owner)
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)
        self.urls = [reverse('business-list-create'), reverse('visitor:visitor-list-create')]

    def test_non_integer_page_is_400(self):
        for url in self.urls:
            for params in ({'page': 'abc'}, {'page_size': 'x'}, {'page_size': '0'}, {'page_size': '-3'}):
                with self.subTest(url=url, params=params):
                    resp = self.client.get(url, params)
                    self.assertEqual(resp.status_code, 400)
                    self.assertEqual(resp.json()['status'], 'error')
                    self.assertEqual(resp.json()['code'], 400)

    def test_valid_params_and_page_size_cap(self):
        for url in self.urls:
            with self.subTest(url=url):
                resp = self.client.get(url, {'page': '1', 'page_size': '500'})
                self.assertEqual(resp.status_code, 200)
                self.assertEqual(
                    set(resp.json()['data']),
                    {'count', 'total_pages', 'current_page', 'next', 'previous', 'results'},
                )


class HelperSemanticsTests(TestCase):
    def test_positive_int(self):
        self.assertEqual(positive_int('3', 1), 3)
        for bad in (None, '', 'x', '0', '-2'):
            self.assertEqual(positive_int(bad, 7), 7)

    def test_two_day_readings_differ_near_midnight(self):
        # 2026-01-10 22:00 UTC = 2026-01-11 01:30 Tehran.
        ms = 1768082400000
        self.assertEqual(utc_date_tehran_midnight(ms).date().isoformat(), '2026-01-10')
        self.assertEqual(local_date_midnight(ms).date().isoformat(), '2026-01-11')
