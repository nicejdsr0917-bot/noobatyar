"""Appointment.source: set by each creation path, exposed to owners, counted in stats."""
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from accounting.models import Plan, Subscription
from business.models import Business
from visitor.models import Visitor

from .models import Appointment
from .tests_client_booking_rules import _Fixture, local_slot

User = get_user_model()


class ClientSourceTests(_Fixture):
    def test_default_is_web(self):
        self.assertEqual(self.book().status_code, 200)
        self.assertEqual(Appointment.objects.get().source, 'WEB')

    def test_app_source(self):
        self.assertEqual(self.book(source='APP').status_code, 200)
        self.assertEqual(Appointment.objects.get().source, 'APP')

    def test_client_cannot_claim_owner(self):
        r = self.book(source='OWNER')
        self.assertEqual(r.status_code, 400)
        self.assertEqual(Appointment.objects.count(), 0)


class OwnerSourceTests(_Fixture):
    def setUp(self):
        super().setUp()
        self.owner = self.business.user
        plan = Plan.objects.create(
            name='t', price=0, duration_value=1, duration_unit='month',
            features={'monthly_appointments': -1},
        )
        Subscription.objects.create(
            user=self.owner, plan=plan, status='active',
            ends_at=timezone.now() + timedelta(days=30),
        )
        self.visitor = Visitor.objects.create(full_name='x', phone_number='09390000077', user=self.owner)
        self.api = APIClient()
        self.api.force_authenticate(user=self.owner)

    def test_owner_create_sets_owner_and_stats_count(self):
        when = local_slot(days=1, hour=10)
        r = self.api.post(reverse('appointments:appointment-create'), {
            'business_id': self.business.id, 'visitor_id': self.visitor.id,
            'appointment_date': int(when.timestamp() * 1000),
        }, format='json')
        self.assertIn(r.status_code, (200, 201), r.content)
        self.assertEqual(Appointment.objects.get().source, 'OWNER')
        self.assertEqual(r.json()['data'].get('source'), 'OWNER')
        # one APP + one legacy row
        for src in ('APP', 'UNKNOWN'):
            Appointment.objects.create(
                business=self.business, visitor=self.visitor, source=src,
                appointment_date=when + timedelta(hours=2),
            )
        ms = int(when.timestamp() * 1000)
        s = self.api.get('/api/appointment/stats/', {
            'business_id': self.business.id, 'date_from': ms - 86400000, 'date_to': ms + 86400000,
        })
        self.assertEqual(s.status_code, 200)
        sc = s.json()['data']['source_counts']
        self.assertEqual(sc, {'WEB': 0, 'APP': 1, 'OWNER': 1, 'UNKNOWN': 1})

    def test_stats_tenant_isolation_and_auth(self):
        other = User.objects.create_user(phone='09120000099', password='x', name='o2')
        api = APIClient()
        api.force_authenticate(user=other)
        r = api.get('/api/appointment/stats/', {'business_id': self.business.id})
        self.assertEqual(r.json()['data']['source_counts']['OWNER'], 0)
        self.assertIn(APIClient().get('/api/appointment/stats/').status_code, (401, 403))
