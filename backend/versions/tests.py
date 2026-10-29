from django.test import TestCase
from rest_framework.test import APIClient

from .models import AppVersion


class VersionCheckTests(TestCase):
    def setUp(self):
        self.api = APIClient()
        AppVersion.objects.create(version_name='1.9.8', version_code=100, force_update=True)
        AppVersion.objects.create(version_name='0.1.0', version_code=100, app='CLIENT',
                                  force_update=False)
        AppVersion.objects.create(version_name='0.2.0', version_code=200, app='CLIENT')

    def _check(self, **body):
        return self.api.post('/api/version/check/', body, format='json').json()['data']

    def test_default_app_is_owner_for_old_requests(self):
        self.assertEqual(AppVersion._meta.get_field('app').default, 'OWNER')
        d = self._check(version_code=50)
        self.assertEqual(d['latest_version']['version_name'], '1.9.8')
        self.assertTrue(d['is_outdated'])
        self.assertTrue(d['force_update'])

    def test_client_app_returns_client_rows(self):
        d = self._check(version_code=50, app='CLIENT')
        self.assertEqual(d['latest_version']['version_code'], 200)
        self.assertTrue(d['is_outdated'])
        self.assertFalse(d['force_update'])

    def test_force_update_independent_per_app(self):
        self.assertTrue(self._check(version_code=50, app='OWNER')['force_update'])
        self.assertFalse(self._check(version_code=50, app='CLIENT')['force_update'])

    def test_invalid_app_rejected(self):
        r = self.api.post('/api/version/check/', {'version_code': 1, 'app': 'x'}, format='json')
        self.assertEqual(r.status_code, 400)

    def test_changelog_filters_by_app(self):
        r = self.api.get('/api/version/changelog/?app=CLIENT').json()['data']
        self.assertEqual({v['version_code'] for v in r}, {100, 200})
        r = self.api.get('/api/version/changelog/').json()['data']
        self.assertEqual([v['version_name'] for v in r], ['1.9.8'])
