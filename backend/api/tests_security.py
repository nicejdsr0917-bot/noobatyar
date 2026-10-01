"""
api/tests_security.py

Account security around the owner panel:

* password login: per-IP throttle (settings) + per-phone failure lockout;
* forgot-password: one change per PASSWORD_CHANGE_COOLDOWN_HOURS, and a
  change signs out every other session;
* logout really blacklists the refresh token it is given;
* user_detail: someone else's account needs the matching model permission,
  not just is_staff;
* a user can't change their own role.
"""

from datetime import timedelta
from unittest import mock

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import RefreshToken

from api.views import LOGIN_MAX_FAILURES

User = get_user_model()

PHONE = '09120000070'
PASSWORD = 'correct-horse-1'


class LoginLockoutTests(TestCase):
    def setUp(self):
        cache.clear()
        self.user = User.objects.create_user(phone=PHONE, password=PASSWORD, name='مالک')
        self.client = APIClient()

    def _login(self, password, phone=PHONE):
        return self.client.post(reverse('login'), {'phone': phone, 'password': password}, format='json')

    def test_correct_password_logs_in(self):
        self.assertEqual(self._login(PASSWORD).status_code, 200)

    def test_locked_after_repeated_failures_even_with_the_right_password(self):
        for _ in range(LOGIN_MAX_FAILURES):
            self.assertEqual(self._login('wrong').status_code, 401)
        response = self._login(PASSWORD)
        self.assertEqual(response.status_code, 429)

    def test_a_success_resets_the_count(self):
        for _ in range(LOGIN_MAX_FAILURES - 1):
            self._login('wrong')
        self.assertEqual(self._login(PASSWORD).status_code, 200)
        for _ in range(LOGIN_MAX_FAILURES - 1):
            self._login('wrong')
        self.assertEqual(self._login(PASSWORD).status_code, 200)

    def test_unknown_numbers_lock_the_same_way(self):
        """Otherwise the lockout itself would reveal which numbers exist."""
        for _ in range(LOGIN_MAX_FAILURES):
            self.assertEqual(self._login('x', phone='09120000099').status_code, 401)
        self.assertEqual(self._login('x', phone='09120000099').status_code, 429)

    def test_inactive_account_cannot_log_in(self):
        self.user.is_active = False
        self.user.save()
        self.assertEqual(self._login(PASSWORD).status_code, 401)


class PasswordChangeCooldownTests(TestCase):
    def setUp(self):
        cache.clear()
        self.user = User.objects.create_user(phone=PHONE, password=PASSWORD, name='مالک')
        self.client = APIClient()
        self.verify_url = reverse('login').replace('login/', 'forgot-password/verify/')
        self.reset_url = reverse('login').replace('login/', 'forgot-password/reset/')

    def _verify(self):
        with mock.patch('api.views.otp_verify', return_value={'success': True}):
            return self.client.post(self.verify_url, {'phone': PHONE, 'code': '123456'}, format='json')

    def _reset(self, new_password):
        token = self._verify().json()['data']['reset_token']
        return self.client.post(
            self.reset_url,
            {'phone': PHONE, 'reset_token': token, 'new_password': new_password},
            format='json',
        )

    def test_first_change_is_allowed_and_stamped(self):
        response = self._reset('new-password-1')
        self.assertEqual(response.status_code, 200, response.content)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password('new-password-1'))
        self.assertIsNotNone(self.user.password_changed_at)

    def test_second_change_within_the_cooldown_is_refused(self):
        self._reset('new-password-1')
        response = self._verify()
        self.assertEqual(response.status_code, 429)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password('new-password-1'))

    def test_reset_step_enforces_it_too(self):
        """A reset token issued before the change can't be used to change again."""
        early_token = self._verify().json()['data']['reset_token']
        self._reset('new-password-1')
        cache.set(f'reset_token:{PHONE}', early_token, timeout=300)
        response = self.client.post(
            self.reset_url,
            {'phone': PHONE, 'reset_token': early_token, 'new_password': 'new-password-2'},
            format='json',
        )
        self.assertEqual(response.status_code, 429)

    def test_allowed_again_after_the_cooldown(self):
        self._reset('new-password-1')
        User.objects.filter(pk=self.user.pk).update(
            password_changed_at=timezone.now() - timedelta(hours=25),
        )
        self.assertEqual(self._reset('new-password-2').status_code, 200)

    def test_change_signs_out_other_sessions(self):
        other_session = RefreshToken.for_user(self.user)
        self._reset('new-password-1')
        response = self.client.post(
            reverse('token-refresh'), {'refresh': str(other_session)}, format='json',
        )
        self.assertEqual(response.status_code, 401)


class LogoutTests(TestCase):
    def test_logout_blacklists_the_refresh_token(self):
        user = User.objects.create_user(phone=PHONE, password=PASSWORD, name='مالک')
        refresh = RefreshToken.for_user(user)
        client = APIClient()
        client.credentials(HTTP_AUTHORIZATION=f'Bearer {refresh.access_token}')
        response = client.post(reverse('logout'), {'refresh': str(refresh)}, format='json')
        self.assertEqual(response.status_code, 200, response.content)
        response = APIClient().post(reverse('token-refresh'), {'refresh': str(refresh)}, format='json')
        self.assertEqual(response.status_code, 401)


class UserDetailAccessTests(TestCase):
    def setUp(self):
        self.target = User.objects.create_user(phone='09120000071', password=PASSWORD, name='کاربر')
        self.staff = User.objects.create_user(phone='09120000072', password=PASSWORD, name='پشتیبان')
        self.staff.is_staff = True
        self.staff.save()
        self.client = APIClient()
        self.client.force_authenticate(user=self.staff)
        self.url = reverse('user-detail', args=[self.target.pk])

    def test_staff_without_permissions_gets_nothing(self):
        self.assertEqual(self.client.get(self.url).status_code, 403)
        self.assertEqual(self.client.patch(self.url, {'name': 'تغییر'}, format='json').status_code, 403)
        self.assertEqual(self.client.delete(self.url).status_code, 403)
        self.assertTrue(User.objects.filter(pk=self.target.pk).exists())

    def test_view_permission_allows_reading_only(self):
        self.staff.user_permissions.add(Permission.objects.get(codename='view_user'))
        self.staff = User.objects.get(pk=self.staff.pk)  # drop the permission cache
        self.client.force_authenticate(user=self.staff)
        self.assertEqual(self.client.get(self.url).status_code, 200)
        self.assertEqual(self.client.delete(self.url).status_code, 403)

    def test_own_account_needs_no_permission(self):
        self.client.force_authenticate(user=self.target)
        self.assertEqual(self.client.get(self.url).status_code, 200)
        self.assertEqual(self.client.patch(self.url, {'name': 'نام تازه'}, format='json').status_code, 200)

    def test_role_cannot_be_self_assigned(self):
        self.client.force_authenticate(user=self.target)
        response = self.client.patch(self.url, {'name': 'نام تازه', 'role': 'ADMIN'}, format='json')
        self.assertEqual(response.status_code, 200)
        self.target.refresh_from_db()
        self.assertEqual(self.target.role, 'CLIENT')
        self.assertEqual(self.target.name, 'نام تازه')
