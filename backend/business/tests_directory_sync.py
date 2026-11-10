"""n8n doctor-list refresh: the sync service and its token-guarded endpoint."""
import json

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings

from business import directory_sync
from business.models import Business, Insurance

User = get_user_model()
URL = '/api/internal/directory-sync/'
TOKEN = 'unit-test-token'


def row(**extra):
    base = {'title': 'دکتر الف', 'city': 'تهران', 'phone': '02188000000', 'insurances': 'بیمه دانا'}
    base.update(extra)
    return base


class SyncServiceTests(TestCase):
    def setUp(self):
        self.dana = Insurance.objects.create(name='بیمه دانا', slug='dana')
        self.asia = Insurance.objects.create(name='بیمه آسیا', slug='asia')

    def test_creates_new_listing_with_insurance(self):
        report = directory_sync.sync_listings([row()], 'n8n')
        self.assertEqual(report.created, 1)
        self.assertEqual(list(Business.objects.get().insurances.all()), [self.dana])

    def test_existing_unclaimed_listing_only_gains_insurances(self):
        directory_sync.sync_listings([row()], 'n8n')
        report = directory_sync.sync_listings([row(insurances='بیمه دانا، بیمه آسیا')], 'n8n')
        self.assertEqual((report.created, report.duplicates, report.linked), (0, 1, 1))
        self.assertEqual(Business.objects.get().insurances.count(), 2)
        # idempotent: a second identical run adds nothing
        self.assertEqual(directory_sync.sync_listings([row(insurances='بیمه دانا، بیمه آسیا')], 'n8n').linked, 0)

    def test_never_removes_insurances(self):
        directory_sync.sync_listings([row(insurances='بیمه دانا، بیمه آسیا')], 'n8n')
        directory_sync.sync_listings([row(insurances='بیمه دانا')], 'n8n')
        self.assertEqual(Business.objects.get().insurances.count(), 2)

    def test_claimed_business_is_left_alone(self):
        directory_sync.sync_listings([row()], 'n8n')
        owner = User.objects.create(phone='09120000001', name='Owner')
        Business.objects.update(is_claimed=True, user=owner)
        report = directory_sync.sync_listings([row(insurances='بیمه آسیا')], 'n8n')
        self.assertEqual((report.linked, report.claimed_skipped), (0, 1))
        self.assertEqual(Business.objects.get().insurances.count(), 1)

    def test_dry_run_saves_nothing(self):
        report = directory_sync.sync_listings([row()], 'n8n', dry_run=True)
        self.assertEqual(report.created, 1)
        self.assertEqual(Business.objects.count(), 0)


@override_settings(DIRECTORY_SYNC_TOKEN=TOKEN)
class SyncEndpointTests(TestCase):
    def setUp(self):
        Insurance.objects.create(name='بیمه دانا', slug='dana')

    def post(self, body, token=TOKEN):
        headers = {'HTTP_AUTHORIZATION': f'Bearer {token}'} if token else {}
        return self.client.post(URL, json.dumps(body), content_type='application/json', **headers)

    def test_wrong_or_missing_token_is_404(self):
        self.assertEqual(self.post({'rows': [row()]}, token='nope').status_code, 404)
        self.assertEqual(self.post({'rows': [row()]}, token=None).status_code, 404)
        self.assertEqual(Business.objects.count(), 0)

    @override_settings(DIRECTORY_SYNC_TOKEN='')
    def test_unset_token_disables_endpoint(self):
        self.assertEqual(self.post({'rows': [row()]}, token='').status_code, 404)

    def test_get_is_not_allowed(self):
        self.assertEqual(self.client.get(URL, HTTP_AUTHORIZATION=f'Bearer {TOKEN}').status_code, 405)

    def test_valid_call_creates_listing(self):
        response = self.post({'source': 'n8n-dana', 'rows': [row()]})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['created'], 1)
        self.assertEqual(Business.objects.get().external_source, 'n8n-dana')

    def test_dry_run_flag(self):
        response = self.post({'dry_run': True, 'rows': [row()]})
        self.assertTrue(response.json()['dry_run'])
        self.assertEqual(Business.objects.count(), 0)

    def test_bad_bodies_are_400_and_too_many_rows_413(self):
        self.assertEqual(self.post({'rows': 'x'}).status_code, 400)
        self.assertEqual(self.post({'rows': ['x']}).status_code, 400)
        self.assertEqual(self.post({'rows': [row()] * (directory_sync.MAX_ROWS + 1)}).status_code, 413)
