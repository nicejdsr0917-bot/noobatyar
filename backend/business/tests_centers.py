"""«مراکز»: origin of a business, the centers admin and call tracking, and the center flags in the API."""
from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from business import directory_import
from business.claims import apply_claim
from business.models import Business, BusinessClaimRequest, CenterOutreach
from business.serializers import ClientBusinessSerializer, PublicBusinessSerializer

User = get_user_model()


def make(user, **kwargs):
    defaults = dict(
        user=user, title='مرکز نمونه', category='DENTIST', phone='02112345678', address='تهران، ونک',
        city='تهران', is_claimed=False, booking_enabled=False, default_service_duration=30,
        work_start_hour=9, work_end_hour=18, moderation_status=Business.MODERATION_APPROVED,
        first_approved_at=timezone.now(),
    )
    defaults.update(kwargs)
    return Business.objects.create(**defaults)


class OriginTests(TestCase):
    def test_import_marks_rows_as_directory(self):
        directory_import.run_import([{'title': 'کلینیک الف', 'city': 'تهران'}], 'src')
        self.assertEqual(Business.objects.get().origin, Business.ORIGIN_DIRECTORY)

    def test_claim_marks_converted_and_closes_outreach(self):
        system = User.objects.create_user(phone='09000000000', name='سیستم', role='ADMIN')
        owner = User.objects.create_user(phone='09125556677', name='دکتر', role='BUSINESS_OWNER')
        center = make(system, origin=Business.ORIGIN_DIRECTORY)
        CenterOutreach.objects.create(business=center, status=CenterOutreach.STATUS_INTERESTED)
        claim = BusinessClaimRequest.objects.create(business=center, user=owner, method=BusinessClaimRequest.METHOD_OTP)
        apply_claim(claim)
        center.refresh_from_db()
        self.assertTrue(center.is_claimed)
        self.assertEqual(center.origin, Business.ORIGIN_CONVERTED)
        self.assertEqual(center.outreach.status, CenterOutreach.STATUS_CONVERTED)


class CenterApiFlagsTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09125556677', name='دکتر', role='BUSINESS_OWNER')

    def test_center_is_not_noobatyar_and_has_no_booking_extras(self):
        center = make(self.owner, booking_enabled=True, deposit_mode='MANDATORY', deposit_amount=50000,
                      first_visit_discount=20, payment_link='https://pay.example/x',
                      notice_enabled=True, notice_message='x')
        for data in (ClientBusinessSerializer(center).data, PublicBusinessSerializer(center).data):
            self.assertFalse(data['is_noobatyar'])
            self.assertEqual(data['kind'], 'center')
            self.assertFalse(data['booking_available'])
            self.assertFalse(data['booking_enabled'])
            if 'deposit_mode' in data:
                self.assertEqual((data['deposit_mode'], data['deposit_amount']), ('NONE', 0))
                self.assertEqual(data['first_visit_discount'], 0)
                self.assertEqual(data['payment_link'], '')
                self.assertFalse(data['notice_enabled'])

    def test_claimed_business_is_flagged_as_noobatyar(self):
        business = make(self.owner, is_claimed=True, booking_enabled=True, origin=Business.ORIGIN_SELF)
        data = ClientBusinessSerializer(business).data
        self.assertTrue(data['is_noobatyar'])
        self.assertEqual(data['kind'], 'business')
        self.assertEqual(data['booking_available'], bool(data['booking_enabled']))


class CenterAdminTests(TestCase):
    def setUp(self):
        self.admin = User.objects.create_superuser(phone='09120000111', password='pw-123456')
        self.client.force_login(self.admin)
        system = directory_import.get_system_user()
        self.center = make(system, title='مرکز بدون صاحب', origin=Business.ORIGIN_DIRECTORY)
        self.owner = User.objects.create_user(phone='09125556677', name='دکتر', role='BUSINESS_OWNER')
        self.business = make(self.owner, title='کسب‌وکار با تیک', is_claimed=True, booking_enabled=True,
                             origin=Business.ORIGIN_SELF, phone='02187654321')

    def test_lists_are_separated(self):
        centers = self.client.get(reverse('admin:business_centerlisting_changelist'))
        businesses = self.client.get(reverse('admin:business_business_changelist'))
        self.assertContains(centers, 'مرکز بدون صاحب')
        self.assertNotContains(centers, 'کسب‌وکار با تیک')
        self.assertContains(businesses, 'کسب‌وکار با تیک')
        self.assertNotContains(businesses, 'مرکز بدون صاحب')

    def test_center_change_page_still_opens_from_business_admin(self):
        res = self.client.get(reverse('admin:business_business_change', args=[self.center.pk]))
        self.assertEqual(res.status_code, 200)

    def test_status_filter_and_actions(self):
        url = reverse('admin:business_centerlisting_changelist')
        self.assertContains(self.client.get(url, {'outreach': 'NEW'}), 'مرکز بدون صاحب')
        self.client.post(url, {'action': 'action_mark_called', '_selected_action': [self.center.pk]})
        outreach = CenterOutreach.objects.get(business=self.center)
        self.assertEqual(outreach.status, CenterOutreach.STATUS_CALLED)
        self.assertIsNotNone(outreach.contacted_at)
        self.assertNotContains(self.client.get(url, {'outreach': 'NEW'}), 'مرکز بدون صاحب')
        self.assertContains(self.client.get(url, {'outreach': 'CALLED'}), 'مرکز بدون صاحب')

    def test_call_list_csv(self):
        res = self.client.post(
            reverse('admin:business_centerlisting_changelist'),
            {'action': 'action_export_call_list', '_selected_action': [self.center.pk]},
        )
        self.assertEqual(res['Content-Type'], 'text/csv; charset=utf-8')
        body = res.content.decode('utf-8')
        self.assertIn('مرکز بدون صاحب', body)
        self.assertIn('02112345678', body)

    def test_origin_backfill_logic_marks_centers_only(self):
        Business.objects.update(origin='')
        Business.objects.filter(is_claimed=False, origin='').update(origin=Business.ORIGIN_DIRECTORY)
        self.assertEqual(Business.objects.get(pk=self.center.pk).origin, Business.ORIGIN_DIRECTORY)
        self.assertEqual(Business.objects.get(pk=self.business.pk).origin, '')
