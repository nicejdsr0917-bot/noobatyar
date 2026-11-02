"""
Tests for claiming imported directory listings (business/claims.py,
business/claim_views.py) and the importer's data-quality rules.
"""

import json
import os
import tempfile

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.core.management import call_command
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from business.claims import ClaimError, apply_claim, mask_phone
from business.models import Business, BusinessClaimRequest

User = get_user_model()


def _listing(system_user, **kwargs):
    defaults = dict(
        user=system_user,
        title='مطب دکتر نمونه',
        category='DENTIST',
        phone='09121112233',
        address='تهران، ونک',
        city='تهران',
        is_claimed=False,
        booking_enabled=False,
        moderation_status=Business.MODERATION_APPROVED,
        first_approved_at=timezone.now(),
        default_service_duration=30,
        work_start_hour=9,
        work_end_hour=18,
    )
    defaults.update(kwargs)
    return Business.objects.create(**defaults)


@override_settings(OTP_DEV_CODE='12345')
class ClaimFlowTests(TestCase):
    def setUp(self):
        cache.clear()
        self.system_user = User.objects.create_user(phone='09000000000', name='سیستم', role='ADMIN')
        self.owner = User.objects.create_user(phone='09125556677', name='دکتر', role='BUSINESS_OWNER')
        self.other = User.objects.create_user(phone='09125556688', name='دیگری', role='BUSINESS_OWNER')
        self.listing = _listing(self.system_user)
        self.client = APIClient()
        self.client.force_authenticate(self.owner)

    def test_claimable_search_finds_only_unclaimed(self):
        _listing(self.owner, title='مطب دکتر نمونه ثبت‌شده', is_claimed=True, phone='09121112299')
        res = self.client.get(reverse('business-claimable'), {'search': 'مطب دکتر'})
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        ids = [b['id'] for b in res.data['data']['results']]
        self.assertEqual(ids, [self.listing.id])

    def test_claimable_search_requires_query(self):
        res = self.client.get(reverse('business-claimable'), {'search': 'م'})
        self.assertEqual(res.status_code, status.HTTP_400_BAD_REQUEST)

    def test_otp_claim_transfers_ownership(self):
        start = self.client.post(reverse('business-claim-start', kwargs={'business_id': self.listing.id}))
        self.assertEqual(start.status_code, status.HTTP_200_OK)
        self.assertEqual(start.data['data']['method'], BusinessClaimRequest.METHOD_OTP)
        self.assertEqual(start.data['data']['masked_phone'], '0912***2233')

        bad = self.client.post(
            reverse('business-claim-verify', kwargs={'business_id': self.listing.id}), {'code': '00000'},
        )
        self.assertEqual(bad.status_code, status.HTTP_400_BAD_REQUEST)

        ok = self.client.post(
            reverse('business-claim-verify', kwargs={'business_id': self.listing.id}), {'code': '12345'},
        )
        self.assertEqual(ok.status_code, status.HTTP_200_OK)
        self.listing.refresh_from_db()
        self.assertTrue(self.listing.is_claimed)
        self.assertEqual(self.listing.user, self.owner)

    def test_landline_listing_goes_to_manual_review(self):
        landline = _listing(self.system_user, title='کلینیک تلفن ثابت', phone='02188776655')
        res = self.client.post(
            reverse('business-claim-start', kwargs={'business_id': landline.id}), {'note': 'نظام پزشکی ۱۲۳'},
        )
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertEqual(res.data['data']['method'], BusinessClaimRequest.METHOD_MANUAL)
        claim = BusinessClaimRequest.objects.get(business=landline)
        self.assertEqual(claim.status, BusinessClaimRequest.STATUS_PENDING)
        self.assertEqual(claim.note, 'نظام پزشکی ۱۲۳')
        landline.refresh_from_db()
        self.assertFalse(landline.is_claimed)

    def test_listing_without_phone_goes_to_manual_review(self):
        nophone = _listing(self.system_user, title='مطب بدون شماره', phone='')
        res = self.client.post(reverse('business-claim-start', kwargs={'business_id': nophone.id}))
        self.assertEqual(res.data['data']['method'], BusinessClaimRequest.METHOD_MANUAL)

    def test_cannot_claim_already_claimed(self):
        claimed = _listing(self.other, title='ثبت شده', is_claimed=True)
        res = self.client.post(reverse('business-claim-start', kwargs={'business_id': claimed.id}))
        self.assertEqual(res.status_code, status.HTTP_400_BAD_REQUEST)

    def test_apply_claim_rejects_competing_requests(self):
        mine = BusinessClaimRequest.objects.create(
            business=self.listing, user=self.owner, method=BusinessClaimRequest.METHOD_MANUAL,
        )
        theirs = BusinessClaimRequest.objects.create(
            business=self.listing, user=self.other, method=BusinessClaimRequest.METHOD_MANUAL,
        )
        apply_claim(mine)
        theirs.refresh_from_db()
        self.assertEqual(theirs.status, BusinessClaimRequest.STATUS_REJECTED)
        with self.assertRaises(ClaimError):
            apply_claim(theirs)

    def test_claim_requires_authentication(self):
        anon = APIClient()
        res = anon.post(reverse('business-claim-start', kwargs={'business_id': self.listing.id}))
        self.assertEqual(res.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_mask_phone(self):
        self.assertEqual(mask_phone('09121234567'), '0912***4567')
        self.assertEqual(mask_phone(''), '')


class ImporterDataQualityTests(TestCase):
    def _run(self, rows, suffix='.json'):
        with tempfile.NamedTemporaryFile(mode='w', suffix=suffix, delete=False, encoding='utf-8') as tf:
            json.dump(rows, tf, ensure_ascii=False)
            path = tf.name
        try:
            call_command('import_directory', file=path, source='quality')
        finally:
            os.remove(path)

    def test_missing_phone_is_stored_empty_not_fake(self):
        self._run([{'title': 'بی‌شماره', 'city': 'تهران', 'address': 'خ ۱'}])
        self.assertEqual(Business.objects.get(title='بی‌شماره').phone, '')

    def test_invalid_phone_is_dropped(self):
        self._run([{'title': 'شماره خراب', 'city': 'تهران', 'phone': '12'}])
        self.assertEqual(Business.objects.get(title='شماره خراب').phone, '')

    def test_same_name_without_phone_different_address_both_imported(self):
        self._run([
            {'title': 'داروخانه شبانه‌روزی', 'city': 'تهران', 'address': 'ونک'},
            {'title': 'داروخانه شبانه‌روزی', 'city': 'تهران', 'address': 'تجریش'},
            {'title': 'داروخانه شبانه‌روزی', 'city': 'تهران', 'address': 'ونک'},
        ])
        self.assertEqual(Business.objects.filter(title='داروخانه شبانه‌روزی').count(), 2)

    def test_out_of_range_coords_and_bad_ints_are_ignored(self):
        self._run([{
            'title': 'مختصات خراب', 'city': 'تهران', 'latitude': 351.7, 'longitude': 51.4,
            'work_start_hour': 'ده', 'default_service_duration': '',
        }])
        b = Business.objects.get(title='مختصات خراب')
        self.assertIsNone(b.latitude)
        self.assertIsNone(b.longitude)
        self.assertEqual(b.work_start_hour, 9)
        self.assertEqual(b.default_service_duration, 30)

    def test_large_json_array_streams_across_chunks(self):
        rows = [{'title': f'مرکز {i}', 'city': 'تهران', 'address': f'پلاک {i}', 'bio': 'x' * 3000}
                for i in range(600)]
        self._run(rows)
        self.assertEqual(Business.objects.filter(external_source='quality').count(), 600)
