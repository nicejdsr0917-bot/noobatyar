"""Appointment.insurance: the client's supplementary insurance, picked at booking, shown to the owner."""
from rest_framework.test import APIClient

from business.models import Insurance

from .models import Appointment
from .tests_client_booking_rules import _Fixture


class BookingInsuranceTests(_Fixture):
    def setUp(self):
        super().setUp()
        self.supp = Insurance.objects.create(name='دی', slug='day', insurance_type='SUPPLEMENTARY')
        self.basic = Insurance.objects.create(name='تامین اجتماعی', slug='tamin', insurance_type='BASIC')
        self.other = Insurance.objects.create(name='ایران', slug='iran', insurance_type='SUPPLEMENTARY')
        self.business.insurances.add(self.supp, self.basic)
        self.business.category = 'DENTIST'
        self.business.save(update_fields=['category'])

    def test_picked_supplementary_is_stored(self):
        self.assertEqual(self.book(insurance='day').status_code, 200)
        self.assertEqual(Appointment.objects.get().insurance, self.supp)

    def test_basic_or_untaken_or_unknown_is_dropped(self):
        for slug in ('tamin', 'iran', 'nope', '', None):
            Appointment.objects.all().delete()
            r = self.book(insurance=slug)
            self.assertEqual(r.status_code, 200, (slug, r.content))
            self.assertIsNone(Appointment.objects.get().insurance_id, slug)

    def test_non_medical_business_never_records_one(self):
        self.business.category = 'WOMENS_SALON'
        self.business.save(update_fields=['category'])
        self.assertEqual(self.book(insurance='day').status_code, 200)
        self.assertIsNone(Appointment.objects.get().insurance_id)

    def test_omitted_is_null(self):
        self.assertEqual(self.book().status_code, 200)
        self.assertIsNone(Appointment.objects.get().insurance_id)

    def test_owner_query_returns_name(self):
        self.book(insurance='day')
        api = APIClient()
        api.force_authenticate(user=self.business.user)
        rows = api.get('/api/appointment/query', {'business_id': self.business.id}).json()['data']['results']
        self.assertEqual(rows[0]['insurance_name'], 'دی')
        self.assertIsNone(rows[0]['insurance_logo'])
        Appointment.objects.update(insurance=None)
        rows = api.get('/api/appointment/query', {'business_id': self.business.id}).json()['data']['results']
        self.assertIsNone(rows[0]['insurance_name'])
