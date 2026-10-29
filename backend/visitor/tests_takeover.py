"""A1: an owner must not be able to take over a self-registered client account."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from appointment.models import Appointment
from business.models import Business
from visitor.models import Visitor, VisitorDeviceToken, VisitorActivity

User = get_user_model()


def _business(owner, title):
    return Business.objects.create(
        user=owner, title=title, phone='02100000001', address='تهران',
        default_service_duration=30, work_start_hour=9, work_end_hour=17,
        moderation_status=Business.MODERATION_APPROVED,
    )


class VisitorTakeoverTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000101', password='x', name='o')
        self.other = User.objects.create_user(phone='09120000102', password='x', name='o2')
        self.biz = _business(self.owner, 'الف')
        self.other_biz = _business(self.other, 'ب')
        self.client_api = APIClient()
        self.client_api.force_authenticate(self.owner)
        self.victim = Visitor.objects.create(full_name='نام واقعی', phone_number='09390000101')

    def _attach(self, phone='09390000101', name='نام دلخواه مالک'):
        return self.client_api.post(
            reverse('visitor:visitor-list-create'),
            {'full_name': name, 'phone_number': phone}, format='json')

    def _put_phone(self, vid, phone='09390000199'):
        return self.client_api.put(
            reverse('visitor:visitor-detail', args=[vid]),
            {'phone_number': phone}, format='json')

    def test_attach_does_not_assign_user_on_existing_visitor(self):
        r = self._attach()
        self.assertEqual(r.status_code, 200)
        self.victim.refresh_from_db()
        self.assertIsNone(self.victim.user_id)

    def test_attach_then_put_phone_is_forbidden(self):
        self._attach()
        r = self._put_phone(self.victim.id)
        self.assertIn(r.status_code, (403, 404))
        self.victim.refresh_from_db()
        self.assertEqual(self.victim.phone_number, '09390000101')

    def test_attach_response_does_not_leak_global_name(self):
        r = self._attach(name='نام دلخواه مالک')
        self.assertEqual(r.data['data']['full_name'], 'نام دلخواه مالک')
        self.assertEqual(set(r.data['data']), {'id', 'full_name', 'phone_number', 'created_at', 'updated_at'})

    def test_attach_response_shows_stored_name_if_already_readable(self):
        Appointment.objects.create(
            business=self.biz, visitor=self.victim, appointment_date=timezone.now())
        r = self._attach(name='x')
        self.assertEqual(r.data['data']['full_name'], 'نام واقعی')

    def test_owner_created_visitor_phone_change_still_works(self):
        r = self._attach(phone='09390000102', name='جدید')
        self.assertEqual(r.status_code, 201)
        vid = r.data['data']['id']
        r = self._put_phone(vid, '09390000103')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(Visitor.objects.get(id=vid).phone_number, '09390000103')

    def _owned(self):
        return Visitor.objects.create(
            full_name='م', phone_number='09390000104', user=self.owner)

    def test_phone_locked_when_appointment_at_foreign_business(self):
        v = self._owned()
        Appointment.objects.create(
            business=self.other_biz, visitor=v, appointment_date=timezone.now())
        self.assertEqual(self._put_phone(v.id).status_code, 403)
        # name edit still fine
        r = self.client_api.put(reverse('visitor:visitor-detail', args=[v.id]),
                                {'full_name': 'اسم جدید'}, format='json')
        self.assertEqual(r.status_code, 200)

    def test_phone_locked_with_device_token(self):
        v = self._owned()
        VisitorDeviceToken.objects.create(visitor=v, token='t' * 20)
        self.assertEqual(self._put_phone(v.id).status_code, 403)

    def test_phone_locked_with_visitor_activity(self):
        v = self._owned()
        VisitorActivity.objects.create(
            visitor=v, action='APPOINTMENT_BOOKED', actor_type=VisitorActivity.ACTOR_VISITOR)
        self.assertEqual(self._put_phone(v.id).status_code, 403)

    def test_same_phone_resubmitted_is_not_a_change(self):
        v = self._owned()
        VisitorDeviceToken.objects.create(visitor=v, token='t' * 20)
        r = self.client_api.put(reverse('visitor:visitor-detail', args=[v.id]),
                                {'full_name': 'ن', 'phone_number': v.phone_number}, format='json')
        self.assertEqual(r.status_code, 200)
