"""A6: owner registration is atomic and the exists()->create race is a clean error."""
from unittest import mock

from django.contrib.auth import get_user_model
from django.db import IntegrityError
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient

from accounting.models import Plan, Subscription

User = get_user_model()
PHONE = '09123334455'


class RegisterTests(TestCase):
    def setUp(self):
        self.api = APIClient()
        self.plan = Plan.objects.create(
            name='trial', price=0, duration_value=1, duration_unit='month',
            features={}, is_active=True)

    def _post(self):
        return self.api.post(reverse('register'), {
            'phone': PHONE, 'name': 'علی', 'password': 'Str0ng-pass-123'}, format='json')

    def test_register_grants_trial(self):
        r = self._post()
        self.assertEqual(r.status_code, 201, r.data)
        u = User.objects.get(phone=PHONE)
        self.assertTrue(Subscription.objects.filter(user=u, plan=self.plan).exists())

    def test_race_on_create_returns_already_registered_not_500(self):
        with mock.patch('api.views.User.objects.create_user', side_effect=IntegrityError):
            r = self._post()
        self.assertEqual(r.status_code, 400)
        self.assertIn('قبلاً ثبت شده', r.data['message'])

    def test_trial_failure_rolls_back_user(self):
        api = APIClient(raise_request_exception=False)
        with mock.patch('api.views.grant_trial', side_effect=RuntimeError('boom')):
            r = api.post(reverse('register'), {
                'phone': PHONE, 'name': 'علی', 'password': 'Str0ng-pass-123'}, format='json')
        self.assertEqual(r.status_code, 500)
        self.assertFalse(User.objects.filter(phone=PHONE).exists())

    def test_trial_integrity_error_is_not_reported_as_duplicate_phone(self):
        api = APIClient(raise_request_exception=False)
        with mock.patch('api.views.grant_trial', side_effect=IntegrityError):
            r = api.post(reverse('register'), {
                'phone': PHONE, 'name': 'علی', 'password': 'Str0ng-pass-123'}, format='json')
        self.assertEqual(r.status_code, 500)
        self.assertFalse(User.objects.filter(phone=PHONE).exists())
