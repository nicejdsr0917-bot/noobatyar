"""A mandatory deposit with no card or online way to pay it must not strand
the client on a payment step with nothing to pay to."""

from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.utils import timezone

from business.models import Business
from business.serializers import BusinessSerializer
from visitor.models import Visitor

from . import selectors
from .models import Appointment
from .services.client_booking import ClientBookingError, client_submit_payment

User = get_user_model()


class UncollectableDepositTests(TestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000077', password='x', name='o')
        self.business = Business.objects.create(
            user=self.owner, title='b', phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
            accepted_payment_methods=['CASH'],
            deposit_mode='MANDATORY', deposit_amount=100000,
        )
        self.visitor = Visitor.objects.create(full_name='v', phone_number='09350000077')
        self.entitled = patch('accounting.entitlements.has_feature', return_value=True)
        self.entitled.start()
        self.addCleanup(self.entitled.stop)

    def test_cash_only_mandatory_deposit_is_not_collectable(self):
        self.assertFalse(selectors.deposit_collectable(self.business))
        self.assertFalse(selectors.business_requires_payment(self.business))

    def test_card_without_number_is_not_collectable(self):
        self.business.accepted_payment_methods = ['CARD']
        self.assertFalse(selectors.deposit_collectable(self.business))
        self.assertFalse(selectors.business_requires_payment(self.business))

    def test_card_with_number_requires_payment(self):
        self.business.accepted_payment_methods = ['CARD', 'CASH']
        self.business.card_number = '6037991234567890'
        self.assertTrue(selectors.deposit_collectable(self.business))
        self.assertTrue(selectors.business_requires_payment(self.business))

    def test_optional_deposit_still_takes_the_payment_step(self):
        self.business.deposit_mode = 'OPTIONAL'
        self.assertTrue(selectors.business_requires_payment(self.business))

    def test_existing_hold_can_be_settled_in_cash(self):
        a = Appointment.objects.create(
            business=self.business, visitor=self.visitor,
            appointment_date=timezone.now() + timedelta(days=1), status='LOCKED',
            locked_at=timezone.now(), expires_at=timezone.now() + timedelta(minutes=10),
        )
        with patch('appointment.services.client_booking._after_commit'):
            appointment, _ = client_submit_payment(
                visitor=self.visitor, appointment_id=a.id, method='CASH',
                payment_reference='', payment_receipt=None,
            )
        a.refresh_from_db()
        self.assertEqual(a.status, 'PENDING_APPROVAL')

    def test_cash_refused_when_a_card_is_available(self):
        self.business.accepted_payment_methods = ['CARD', 'CASH']
        self.business.card_number = '6037991234567890'
        self.business.save()
        a = Appointment.objects.create(
            business=self.business, visitor=self.visitor,
            appointment_date=timezone.now() + timedelta(days=1), status='LOCKED',
            locked_at=timezone.now(), expires_at=timezone.now() + timedelta(minutes=10),
        )
        with self.assertRaises(ClientBookingError):
            client_submit_payment(
                visitor=self.visitor, appointment_id=a.id, method='CASH',
                payment_reference='', payment_receipt=None,
            )


class MandatoryDepositValidationTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000076', password='x', name='o')
        self.business = Business.objects.create(
            user=self.owner, title='b', phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            accepted_payment_methods=['CASH'], deposit_mode='NONE',
        )

    def test_turning_on_mandatory_deposit_needs_card_or_online(self):
        s = BusinessSerializer(self.business, data={'deposit_mode': 'MANDATORY', 'deposit_amount': 50000}, partial=True)
        self.assertFalse(s.is_valid())
        self.assertIn('deposit_mode', s.errors)

    def test_with_card_it_saves(self):
        s = BusinessSerializer(self.business, data={
            'deposit_mode': 'MANDATORY', 'deposit_amount': 50000,
            'accepted_payment_methods': ['CARD'], 'card_number': '6037991234567890',
        }, partial=True)
        self.assertTrue(s.is_valid(), s.errors)

    def test_existing_bad_config_does_not_block_other_edits(self):
        Business.objects.filter(pk=self.business.pk).update(deposit_mode='MANDATORY', deposit_amount=50000)
        self.business.refresh_from_db()
        s = BusinessSerializer(self.business, data={
            'title': 'new name', 'deposit_mode': 'MANDATORY', 'accepted_payment_methods': ['CASH'],
        }, partial=True)
        self.assertTrue(s.is_valid(), s.errors)


class DepositCapTests(UncollectableDepositTests):
    """A deposit is at most Business.MAX_DEPOSIT_AMOUNT (1,000,000 toman)."""

    def _save(self, **data):
        return BusinessSerializer(self.business, data={
            'deposit_mode': 'OPTIONAL', 'accepted_payment_methods': ['CARD'],
            'card_number': '6037991234567890', **data,
        }, partial=True)

    def test_the_cap_itself_is_allowed(self):
        s = self._save(deposit_amount=1_000_000)
        self.assertTrue(s.is_valid(), s.errors)

    def test_above_the_cap_is_refused(self):
        s = self._save(deposit_amount=1_000_001)
        self.assertFalse(s.is_valid())
        self.assertEqual(s.errors['deposit_amount'][0], 'مبلغ بیعانه حداکثر ۱٬۰۰۰٬۰۰۰ تومان است')

    def test_an_older_deposit_above_the_cap_does_not_block_other_edits(self):
        Business.objects.filter(pk=self.business.pk).update(
            deposit_mode='OPTIONAL', deposit_amount=2_000_000,
            accepted_payment_methods=['CARD'], card_number='6037991234567890')
        self.business.refresh_from_db()
        s = self._save(title='new name', deposit_amount=2_000_000)
        self.assertTrue(s.is_valid(), s.errors)
