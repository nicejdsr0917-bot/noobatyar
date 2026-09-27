"""
business/tests_closed_weekdays.py

The owner-facing side of Business.closed_weekdays: reading and writing it
through BusinessSerializer, and that it reaches the public-facing serializer
read-only. The appointment-side effect (the slots endpoint) is covered in
appointment/tests_closed_weekdays.py.
"""

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient

from .models import Business
from .serializers import PublicBusinessSerializer
from .tests_moderation_api import make_business

User = get_user_model()


class ClosedWeekdaysOwnerWriteTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000031', name='مالک')
        self.business = make_business(self.owner)
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _patch(self, **data):
        # BusinessView only implements PUT (internally partial=True), no PATCH.
        return self.client.put(
            reverse('business-detail', args=[self.business.id]), data, format='json',
        )

    def test_owner_can_set_closed_weekdays(self):
        response = self._patch(closed_weekdays=[4])
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.closed_weekdays, [4])

    def test_default_is_open_every_day(self):
        self.assertEqual(self.business.closed_weekdays, [])

    def test_duplicates_are_deduplicated_not_rejected(self):
        response = self._patch(closed_weekdays=[4, 4, 5])
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.closed_weekdays, [4, 5])

    def test_out_of_range_weekday_is_rejected(self):
        response = self._patch(closed_weekdays=[7])
        self.assertEqual(response.status_code, 400)

    def test_non_list_value_is_rejected(self):
        response = self._patch(closed_weekdays="4")
        self.assertEqual(response.status_code, 400)

    def test_clearing_back_to_open_every_day(self):
        self.business.closed_weekdays = [4]
        self.business.save(update_fields=['closed_weekdays'])

        response = self._patch(closed_weekdays=[])
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.closed_weekdays, [])


class ClosedWeekdaysPublicReadTests(TestCase):
    """The public booking page must be able to read this without the owner endpoint."""

    def test_exposed_read_only_on_public_serializer(self):
        owner = User.objects.create_user(phone='09120000032', name='مالک')
        business = make_business(owner, closed_weekdays=[4])

        data = PublicBusinessSerializer(business).data
        self.assertEqual(data['closed_weekdays'], [4])
