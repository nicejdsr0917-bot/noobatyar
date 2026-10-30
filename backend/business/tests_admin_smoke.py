"""Every business admin renders: changelist and change form return 200 for a superuser."""

from django.contrib import admin
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse

from accounting.models import Plan, Subscription  # noqa: F401  (keeps app registry warm)
from business import admin as business_admin  # noqa: F401
from business.models import (
    BannedKeyword, Business, BusinessClaimRequest, BusinessModerationLog, ContentReport, Insurance,
)

User = get_user_model()


class BusinessAdminSmokeTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.staff = User.objects.create_superuser(phone='09120000300', password='x', name='ادمین')
        owner = User.objects.create_user(phone='09120000301', password='x', name='مالک')
        cls.business = Business.objects.create(
            user=owner, title='سالن', phone='02100000000', address='تهران',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
        )
        cls.objs = [
            cls.business,
            Insurance.objects.create(name='تأمین', slug='tamin'),
            BannedKeyword.objects.create(term='ممنوع'),
            BusinessClaimRequest.objects.create(business=cls.business, user=owner),
            ContentReport.objects.create(business=cls.business),
            BusinessModerationLog.objects.create(
                business=cls.business, from_status='PENDING', to_status='APPROVED', note='x',
            ),
        ]

    def setUp(self):
        self.client.force_login(self.staff)

    def test_all_business_models_are_registered_with_unfold(self):
        from unfold.admin import ModelAdmin
        for obj in self.objs:
            model_admin = admin.site._registry[type(obj)]
            self.assertIsInstance(model_admin, ModelAdmin, type(obj).__name__)

    def test_changelist_and_changeform_render(self):
        for obj in self.objs:
            meta = type(obj)._meta
            with self.subTest(model=meta.label):
                response = self.client.get(reverse(f'admin:{meta.app_label}_{meta.model_name}_changelist'))
                self.assertEqual(response.status_code, 200)
                response = self.client.get(reverse(f'admin:{meta.app_label}_{meta.model_name}_change', args=[obj.pk]))
                self.assertEqual(response.status_code, 200)
