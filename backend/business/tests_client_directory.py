# backend/business/tests_client_directory.py
"""
Unit and integration tests for the Consumer Discovery & Directory Marketplace features:
- Insurance model and serializers
- Business model directory fields
- Client discovery endpoints (search, category groups, insurance, location, radius, ranking)
- Insurance list endpoint
- Online booking lead-gen request endpoint
- Directory demo seeder command
- Bulk directory importer command (JSON, JSONL, CSV)
"""

import json
import os
import tempfile
from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from business.client_views import haversine_km
from business.models import Business, Insurance
from business.serializers import (
    BusinessSerializer,
    ClientBusinessSerializer,
    InsuranceSerializer,
    PublicBusinessSerializer,
)

User = get_user_model()


class InsuranceModelAndSerializerTests(TestCase):
    def setUp(self):
        self.dana = Insurance.objects.create(
            name='بیمه دانا',
            slug='dana',
            insurance_type='SUPPLEMENTARY',
            is_active=True,
            display_order=10,
        )
        self.tamin = Insurance.objects.create(
            name='تامین اجتماعی',
            slug='tamin',
            insurance_type='BASIC',
            is_active=True,
            display_order=20,
        )

    def test_insurance_str_and_ordering(self):
        self.assertEqual(str(self.dana), 'بیمه دانا')
        insurances = list(Insurance.objects.all())
        self.assertEqual(insurances[0].slug, 'dana')
        self.assertEqual(insurances[1].slug, 'tamin')

    def test_insurance_serializer(self):
        serializer = InsuranceSerializer(self.dana)
        self.assertEqual(
            serializer.data,
            {
                'id': self.dana.id,
                'name': 'بیمه دانا',
                'slug': 'dana',
                'insurance_type': 'SUPPLEMENTARY',
            }
        )


class BusinessDirectoryFieldsAndSerializersTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(
            phone='09121111111',
            name='دکتر حسینی',
            role='BUSINESS_OWNER',
        )
        self.dana = Insurance.objects.create(
            name='بیمه دانا',
            slug='dana',
            insurance_type='SUPPLEMENTARY',
        )
        self.saman = Insurance.objects.create(
            name='بیمه سامان',
            slug='saman',
            insurance_type='SUPPLEMENTARY',
        )
        self.business = Business.objects.create(
            user=self.owner,
            title='مطب دندان‌پزشکی دکتر حسینی',
            category='DENTIST',
            unique_code='HOSSEIND',
            phone='02188889999',
            address='تهران، ونک، خیابان خدامی، پلاک ۱۰',
            province='تهران',
            city='تهران',
            neighborhood='ونک',
            latitude=35.7580,
            longitude=51.4105,
            is_claimed=True,
            external_source='',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=18,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=timezone.now(),
        )
        self.business.insurances.add(self.dana, self.saman)

    def test_business_serializer_includes_directory_fields(self):
        serializer = BusinessSerializer(self.business)
        data = serializer.data
        self.assertEqual(data['latitude'], 35.7580)
        self.assertEqual(data['longitude'], 51.4105)
        self.assertEqual(data['province'], 'تهران')
        self.assertEqual(data['city'], 'تهران')
        self.assertEqual(data['neighborhood'], 'ونک')
        self.assertTrue(data['is_claimed'])
        self.assertEqual(data['online_booking_requests_count'], 0)
        self.assertEqual(len(data['insurances']), 2)
        ins_slugs = [i['slug'] for i in data['insurances']]
        self.assertIn('dana', ins_slugs)
        self.assertIn('saman', ins_slugs)

    def test_business_serializer_update_insurance_ids(self):
        # Update insurances via insurance_ids
        serializer = BusinessSerializer(
            self.business,
            data={'insurance_ids': [self.dana.id]},
            partial=True,
        )
        self.assertTrue(serializer.is_valid(), serializer.errors)
        serializer.save()
        self.business.refresh_from_db()
        self.assertEqual(self.business.insurances.count(), 1)
        self.assertEqual(self.business.insurances.first().slug, 'dana')

    def test_business_serializer_read_only_is_claimed_and_request_count(self):
        # Attempting to modify is_claimed or online_booking_requests_count should be ignored
        serializer = BusinessSerializer(
            self.business,
            data={'is_claimed': False, 'online_booking_requests_count': 99},
            partial=True,
        )
        self.assertTrue(serializer.is_valid(), serializer.errors)
        serializer.save()
        self.business.refresh_from_db()
        self.assertTrue(self.business.is_claimed)
        self.assertEqual(self.business.online_booking_requests_count, 0)

    def test_public_and_client_serializer_notice_rules_unclaimed(self):
        unclaimed = Business.objects.create(
            user=self.owner,
            title='درمانگاه نامعلوم',
            category='DOCTOR',
            unique_code='UNCLAIM1',
            phone='02111112222',
            address='تهران، آزادی',
            is_claimed=False,
            booking_enabled=True,
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=timezone.now(),
        )
        # Even though booking_enabled=True in DB, apply_notice_rules forces False for unclaimed
        pub_data = PublicBusinessSerializer(unclaimed).data
        self.assertFalse(pub_data['booking_enabled'])
        self.assertFalse(pub_data['is_claimed'])

        client_data = ClientBusinessSerializer(unclaimed).data
        self.assertFalse(client_data['booking_enabled'])
        self.assertFalse(client_data['is_claimed'])

    def test_distance_km_serializer_method_field(self):
        self.business.distance_km = 4.238
        data = PublicBusinessSerializer(self.business).data
        self.assertEqual(data['distance_km'], 4.24)

        self.business.distance_km = None
        data = PublicBusinessSerializer(self.business).data
        self.assertIsNone(data['distance_km'])


class HaversineDistanceTests(TestCase):
    def test_haversine_calculation(self):
        # Vanak (35.7575, 51.4099) to Saadat Abad (35.7780, 51.3690) is ~4.3 km
        dist = haversine_km(35.7575, 51.4099, 35.7780, 51.3690)
        self.assertAlmostEqual(dist, 4.34, delta=0.5)

        # Same point should be 0.0
        dist_zero = haversine_km(35.7575, 51.4099, 35.7575, 51.4099)
        self.assertAlmostEqual(dist_zero, 0.0, places=5)


class ClientDiscoveryEndpointTests(TestCase):
    def setUp(self):
        from django.core.cache import cache
        cache.clear()
        self.client = APIClient()
        self.owner = User.objects.create_user(
            phone='09122222222',
            name='دکتر شریفی',
            role='BUSINESS_OWNER',
        )
        self.dana = Insurance.objects.create(name='بیمه دانا', slug='dana', insurance_type='SUPPLEMENTARY')
        self.asia = Insurance.objects.create(name='بیمه آسیا', slug='asia', insurance_type='SUPPLEMENTARY')

        now = timezone.now()
        # 1. Claimed Dentist in Vanak
        self.b1 = Business.objects.create(
            user=self.owner,
            title='دندان‌پزشکی ونک',
            category='DENTIST',
            unique_code='DENTVNK',
            phone='02188770001',
            address='تهران، ونک، خ ملاصدرا',
            city='تهران',
            neighborhood='ونک',
            latitude=35.7580,
            longitude=51.4100,
            is_claimed=True,
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=18,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=now,
        )
        self.b1.insurances.add(self.dana)

        # 2. Claimed Doctor (Cardiology) in Valiasr
        self.b2 = Business.objects.create(
            user=self.owner,
            title='کلینیک قلب ولیعصر',
            category='CARDIOLOGY',
            unique_code='CARDVAL',
            phone='02188770002',
            address='تهران، ولیعصر، بالاتر از توانیر',
            city='تهران',
            neighborhood='ولیعصر',
            latitude=35.7310,
            longitude=51.4080,
            is_claimed=True,
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=18,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=now,
        )
        self.b2.insurances.add(self.asia)

        # 3. Unclaimed Dermatologist in Vanak
        self.b3 = Business.objects.create(
            user=self.owner,
            title='مطب پوست ونک دکتر کیانی',
            category='DERMATOLOGY',
            unique_code='DERMVNK',
            phone='02188770003',
            address='تهران، میدان ونک',
            city='تهران',
            neighborhood='ونک',
            latitude=35.7590,
            longitude=51.4110,
            is_claimed=False,
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=18,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=now,
        )
        self.b3.insurances.add(self.dana, self.asia)

        # 4. Unclaimed Clinic in Isfahan (different city, far away)
        self.b4 = Business.objects.create(
            user=self.owner,
            title='کلینیک اصفهان مهر',
            category='GENERAL_PRACTITIONER',
            unique_code='ISFCLIN',
            phone='03133330004',
            address='اصفهان، خیابان چهارباغ',
            city='اصفهان',
            neighborhood='چهارباغ',
            latitude=32.6546,
            longitude=51.6680,
            is_claimed=False,
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=18,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=now,
        )

        # 5. Unlocated Claimed Business (no lat/lng)
        self.b5 = Business.objects.create(
            user=self.owner,
            title='مرکز مشاوره تهران',
            category='PSYCHOLOGY',
            unique_code='CONSTEH',
            phone='02188770005',
            address='تهران، نامعلوم',
            city='تهران',
            neighborhood='مرکز',
            latitude=None,
            longitude=None,
            is_claimed=True,
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=18,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=now,
        )

    def test_list_businesses_basic(self):
        url = reverse('client-business-list')
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['data']['count'], 5)

    def test_search_filtering(self):
        url = reverse('client-business-list')
        # Search by title
        res = self.client.get(url, {'search': 'قلب'})
        self.assertEqual(res.data['data']['count'], 1)
        self.assertEqual(res.data['data']['results'][0]['unique_code'], 'CARDVAL')

        # Search by neighborhood
        res2 = self.client.get(url, {'search': 'ونک'})
        self.assertEqual(res2.data['data']['count'], 2)

        # Search by unique code
        res3 = self.client.get(url, {'search': 'ISFCLIN'})
        self.assertEqual(res3.data['data']['count'], 1)
        self.assertEqual(res3.data['data']['results'][0]['title'], 'کلینیک اصفهان مهر')

    def test_category_and_category_group_filtering(self):
        url = reverse('client-business-list')
        # Exact category
        res = self.client.get(url, {'category': 'DENTIST'})
        self.assertEqual(res.data['data']['count'], 1)
        self.assertEqual(res.data['data']['results'][0]['unique_code'], 'DENTVNK')

        # Category group 'MEDICAL' covers CARDIOLOGY, DERMATOLOGY, GENERAL_PRACTITIONER
        res_group = self.client.get(url, {'category': 'MEDICAL'})
        # b2 (CARDIOLOGY), b3 (DERMATOLOGY), b4 (GENERAL_PRACTITIONER)
        self.assertEqual(res_group.data['data']['count'], 3)
        codes = [b['unique_code'] for b in res_group.data['data']['results']]
        self.assertIn('CARDVAL', codes)
        self.assertIn('DERMVNK', codes)
        self.assertIn('ISFCLIN', codes)

    def test_insurance_filtering(self):
        url = reverse('client-business-list')
        # Filter by slug
        res = self.client.get(url, {'insurance': 'dana'})
        self.assertEqual(res.data['data']['count'], 2)
        codes = [b['unique_code'] for b in res.data['data']['results']]
        self.assertIn('DENTVNK', codes)
        self.assertIn('DERMVNK', codes)

        # Filter by ID
        res_id = self.client.get(url, {'insurance': str(self.asia.id)})
        self.assertEqual(res_id.data['data']['count'], 2)
        codes_asia = [b['unique_code'] for b in res_id.data['data']['results']]
        self.assertIn('CARDVAL', codes_asia)
        self.assertIn('DERMVNK', codes_asia)

    def test_city_and_only_claimed_filtering(self):
        url = reverse('client-business-list')
        # City filter
        res = self.client.get(url, {'city': 'اصفهان'})
        self.assertEqual(res.data['data']['count'], 1)
        self.assertEqual(res.data['data']['results'][0]['city'], 'اصفهان')

        # only_claimed filter
        res_claimed = self.client.get(url, {'only_claimed': 'true'})
        self.assertEqual(res_claimed.data['data']['count'], 3)
        for b in res_claimed.data['data']['results']:
            self.assertTrue(b['is_claimed'])

    def test_location_and_radius_filtering(self):
        url = reverse('client-business-list')
        # Center in Vanak (35.7580, 51.4100)
        # b1 is at distance ~0 km
        # b3 is at distance ~0.14 km
        # b2 (Valiasr) is at distance ~3.0 km
        # b4 (Isfahan) is at ~340 km
        # b5 has no lat/lng

        # Radius 5km around Vanak
        res = self.client.get(url, {
            'lat': '35.7580',
            'lng': '51.4100',
            'radius_km': '5.0',
        })
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        # Should include b1, b3, b2 (within 5km). Should exclude b4 (Isfahan) and b5 (unlocated)
        self.assertEqual(res.data['data']['count'], 3)
        codes = [b['unique_code'] for b in res.data['data']['results']]
        self.assertIn('DENTVNK', codes)
        self.assertIn('DERMVNK', codes)
        self.assertIn('CARDVAL', codes)
        self.assertNotIn('ISFCLIN', codes)
        self.assertNotIn('CONSTEH', codes)

        # Distance should be set and rounded
        first = res.data['data']['results'][0]
        self.assertIsNotNone(first['distance_km'])

    def test_location_include_unlocated(self):
        url = reverse('client-business-list')
        res = self.client.get(url, {
            'lat': '35.7580',
            'lng': '51.4100',
            'radius_km': '5.0',
            'include_unlocated': 'true',
        })
        # Should now include b1, b3, b2, and b5 (unlocated)
        self.assertEqual(res.data['data']['count'], 4)
        codes = [b['unique_code'] for b in res.data['data']['results']]
        self.assertIn('CONSTEH', codes)

    def test_location_ranking_recommended_vs_distance(self):
        url = reverse('client-business-list')
        # Recommended: claimed first sorted by distance, then unclaimed sorted by distance
        res_rec = self.client.get(url, {
            'lat': '35.7580',
            'lng': '51.4100',
            'radius_km': '10.0',
            'sort': 'recommended',
        })
        results_rec = res_rec.data['data']['results']
        # b1 is claimed (~0 km), b2 is claimed (~3 km), b3 is unclaimed (~0.14 km)
        # So order must be b1 (claimed, 0km) -> b2 (claimed, 3km) -> b3 (unclaimed, 0.14km)
        self.assertEqual(results_rec[0]['unique_code'], 'DENTVNK')
        self.assertEqual(results_rec[1]['unique_code'], 'CARDVAL')
        self.assertEqual(results_rec[2]['unique_code'], 'DERMVNK')

        # Distance sort: pure distance regardless of claim state
        # Order must be b1 (0 km) -> b3 (0.14 km) -> b2 (3 km)
        res_dist = self.client.get(url, {
            'lat': '35.7580',
            'lng': '51.4100',
            'radius_km': '10.0',
            'sort': 'distance',
        })
        results_dist = res_dist.data['data']['results']
        self.assertEqual(results_dist[0]['unique_code'], 'DENTVNK')
        self.assertEqual(results_dist[1]['unique_code'], 'DERMVNK')
        self.assertEqual(results_dist[2]['unique_code'], 'CARDVAL')

    def test_client_insurance_list_view(self):
        url = reverse('client-insurance-list')
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.data['data']
        self.assertEqual(len(data), 2)
        slugs = [item['slug'] for item in data]
        self.assertIn('dana', slugs)
        self.assertIn('asia', slugs)

    def test_client_request_online_booking(self):
        url = reverse('client-business-request-booking', kwargs={'business_id': self.b3.id})
        # First request
        res1 = self.client.post(url)
        self.assertEqual(res1.status_code, status.HTTP_200_OK)
        self.assertEqual(res1.data['data']['online_booking_requests_count'], 1)
        self.assertIn('ثبت شد', res1.data['message'])

        # Second request increments
        res2 = self.client.post(url)
        self.assertEqual(res2.status_code, status.HTTP_200_OK)
        self.assertEqual(res2.data['data']['online_booking_requests_count'], 2)

        self.b3.refresh_from_db()
        self.assertEqual(self.b3.online_booking_requests_count, 2)

    def test_client_request_online_booking_404(self):
        url = reverse('client-business-request-booking', kwargs={'business_id': 999999})
        res = self.client.post(url)
        self.assertEqual(res.status_code, status.HTTP_404_NOT_FOUND)

    def test_client_request_online_booking_throttled(self):
        url = reverse('client-business-request-booking', kwargs={'business_id': self.b3.id})
        for _ in range(10):
            res = self.client.post(url)
            self.assertEqual(res.status_code, status.HTTP_200_OK)
        res_throttled = self.client.post(url)
        self.assertEqual(res_throttled.status_code, status.HTTP_429_TOO_MANY_REQUESTS)


class ManagementCommandTests(TestCase):
    def test_seed_directory_demo_command(self):
        # Run seeder command
        call_command('seed_directory_demo')

        # Check insurances
        self.assertTrue(Insurance.objects.filter(slug='dana').exists())
        self.assertTrue(Insurance.objects.filter(slug='tamin').exists())
        self.assertGreaterEqual(Insurance.objects.count(), 17)

        # Check businesses
        self.assertGreaterEqual(Business.objects.count(), 15)
        # Check system user owns unclaimed
        system_user = User.objects.get(phone='09000000000')
        unclaimed = Business.objects.filter(is_claimed=False)
        self.assertGreaterEqual(unclaimed.count(), 7)
        for b in unclaimed:
            self.assertEqual(b.user, system_user)
            self.assertEqual(b.moderation_status, Business.MODERATION_APPROVED)

    def test_import_directory_json(self):
        dana = Insurance.objects.create(name='بیمه دانا', slug='dana')
        sample_data = [
            {
                'title': 'مطب نمونه تستی یک',
                'phone': '02188991122',
                'city': 'تهران',
                'province': 'تهران',
                'neighborhood': 'ونک',
                'address': 'تهران، میدان ونک، برج نگار',
                'category': 'DENTIST',
                'latitude': 35.7580,
                'longitude': 51.4105,
                'services': ['جرم‌گیری', 'پرکردن'],
                'insurances': ['dana'],
            },
            {
                'title': 'سالن زیبایی آزمایشی دو',
                'phone': '02122334455',
                'city': 'تهران',
                'province': 'تهران',
                'neighborhood': 'سعادت‌آباد',
                'category': 'آرایشگاه و سالن زیبایی',
                'latitude': 35.7800,
                'longitude': 51.3700,
            },
            # Duplicate of row 1
            {
                'title': 'مطب نمونه تستی یک',
                'phone': '02188991122',
                'city': 'تهران',
            }
        ]

        with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False, encoding='utf-8') as tf:
            json.dump(sample_data, tf, ensure_ascii=False)
            temp_path = tf.name

        try:
            call_command(
                'import_directory',
                file=temp_path,
                source='test_import',
                batch_size=2,
            )

            # Two valid businesses should be created, third is a duplicate
            biz1 = Business.objects.get(title='مطب نمونه تستی یک')
            self.assertFalse(biz1.is_claimed)
            self.assertEqual(biz1.category, 'DENTIST')
            self.assertEqual(biz1.external_source, 'test_import')
            self.assertEqual(biz1.insurances.count(), 1)
            self.assertEqual(biz1.insurances.first().slug, 'dana')

            biz2 = Business.objects.get(title='سالن زیبایی آزمایشی دو')
            self.assertFalse(biz2.is_claimed)
            self.assertEqual(biz2.category, 'BEAUTY_SALON')

        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_import_directory_csv(self):
        csv_content = """title,phone,city,province,neighborhood,address,category,latitude,longitude,insurances
کلینیک ارتوپدی تستی,02188223344,تهران,تهران,پاسداران,تهران خ دولت,ارتوپدی,35.7600,51.4600,dana
"""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', delete=False, encoding='utf-8') as tf:
            tf.write(csv_content)
            temp_path = tf.name

        try:
            call_command('import_directory', file=temp_path, source='csv_test')
            biz = Business.objects.get(title='کلینیک ارتوپدی تستی')
            self.assertEqual(biz.category, 'ORTHOPEDICS')
            self.assertEqual(biz.city, 'تهران')
            self.assertFalse(biz.is_claimed)
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)
