"""
visitor/tests_cross_business.py

A person is one Visitor row platform-wide (phone_number is globally unique),
but "being someone's contact" is not platform-wide — it is per owner. These
tests cover the case where those two facts collide: a customer already known
to business BA, whom the owner of an unrelated business BB now wants to add
and book for.

Reported from the field: BB's owner could neither add the person ("this
number is already registered") nor pick them from their own list, so there
was no way at all to book an appointment for a walk-in who happened to have
used another business on the platform before.
"""

from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from accounting.models import Plan, Subscription
from appointment.models import Appointment
from business.models import Business
from visitor.models import Visitor

User = get_user_model()


class BookingForAnotherBusinessCustomerTests(TestCase):
    def setUp(self):
        # Owner A, who already has this customer in their contacts.
        self.owner_a = User.objects.create_user(
            phone='09120000020', password='x', name='مالک الف',
        )
        # Owner B, a completely unrelated business.
        self.owner_b = User.objects.create_user(
            phone='09120000021', password='x', name='مالک ب',
        )
        self.business_b = Business.objects.create(
            user=self.owner_b,
            title='کسب‌وکار ب',
            phone='02100000002',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )
        plan = Plan.objects.create(
            name='تست', price=0, duration_value=1, duration_unit='month',
            features={'monthly_appointments': -1},
        )
        Subscription.objects.create(
            user=self.owner_b, plan=plan, status='active',
            ends_at=timezone.now() + timedelta(days=30),
        )

        # The customer, added by owner A first — so `user` points at A.
        self.customer = Visitor.objects.create(
            full_name='مشتری مشترک',
            phone_number='09390000020',
            user=self.owner_a,
        )

        self.client = APIClient()
        self.client.force_authenticate(user=self.owner_b)

    def test_owner_b_can_add_a_customer_who_already_exists_under_owner_a(self):
        """Adding by phone must attach, not be refused as a duplicate."""
        response = self.client.post(
            reverse('visitor:visitor-list-create'),
            data={'full_name': 'مشتری مشترک', 'phone_number': '09390000020'},
            format='json',
        )
        self.assertIn(
            response.status_code, (200, 201),
            f"owner B was refused: {response.status_code} {response.data}",
        )
        # And exactly one row still exists for that phone.
        self.assertEqual(Visitor.objects.filter(phone_number='09390000020').count(), 1)

    def test_owner_b_can_then_book_for_that_customer(self):
        """The whole point of adding them: booking the appointment."""
        self.client.post(
            reverse('visitor:visitor-list-create'),
            data={'full_name': 'مشتری مشترک', 'phone_number': '09390000020'},
            format='json',
        )

        when = (timezone.now() + timedelta(days=1)).replace(
            hour=10, minute=0, second=0, microsecond=0,
        )
        response = self.client.post(
            reverse('appointments:appointment-create'),
            data={
                'business_id': self.business_b.id,
                'visitor_id': self.customer.id,
                'appointment_date': int(when.timestamp() * 1000),
            },
            format='json',
        )
        self.assertIn(
            response.status_code, (200, 201),
            f"owner B could not book: {response.status_code} {response.data}",
        )
        self.assertTrue(
            Appointment.objects.filter(
                business=self.business_b, visitor=self.customer,
            ).exists()
        )

    def test_owner_b_sees_the_customer_in_their_own_list_afterwards(self):
        self.client.post(
            reverse('visitor:visitor-list-create'),
            data={'full_name': 'مشتری مشترک', 'phone_number': '09390000020'},
            format='json',
        )
        response = self.client.get(reverse('visitor:visitor-list-create'))
        phones = [v['phone_number'] for v in response.data['data']['results']]
        self.assertIn('09390000020', phones)

    def test_owner_b_still_cannot_rename_owner_a_s_customer(self):
        """Attaching is not ownership: the shared row stays protected."""
        response = self.client.put(
            reverse('visitor:visitor-detail', args=[self.customer.id]),
            data={'full_name': 'اسم عوض شده', 'phone_number': '09390000020'},
            format='json',
        )
        self.customer.refresh_from_db()
        self.assertEqual(self.customer.full_name, 'مشتری مشترک')
        self.assertEqual(response.status_code, 404)
