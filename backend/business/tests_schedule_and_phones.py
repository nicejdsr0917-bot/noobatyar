"""
business/tests_schedule_and_phones.py

Three small additions to Business, all through BusinessSerializer's owner-
facing write path:

* extra_phones — additional contact numbers alongside `phone`.
* work_start_minute / work_end_minute — minute precision on top of the
  existing whole-hour work_start_hour/work_end_hour, so a business can open
  at e.g. 19:30.
* bio's max_length raised from 50 to 300.
"""

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient

from .serializers import PublicBusinessSerializer
from .tests_moderation_api import make_business

User = get_user_model()


class OwnerWriteTestCaseMixin:
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000040', name='مالک')
        self.business = make_business(self.owner)
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _put(self, **data):
        return self.client.put(
            reverse('business-detail', args=[self.business.id]), data, format='json',
        )


class ExtraPhonesTests(OwnerWriteTestCaseMixin, TestCase):
    def test_owner_can_add_extra_phones(self):
        response = self._put(extra_phones=['09121234567', '02112345678'])
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.extra_phones, ['09121234567', '02112345678'])

    def test_default_is_empty(self):
        self.assertEqual(self.business.extra_phones, [])

    def test_invalid_number_is_rejected(self):
        response = self._put(extra_phones=['۰۹۱۲'])  # too short even after digit conversion
        self.assertEqual(response.status_code, 400)

    def test_persian_digits_are_normalized_same_as_the_primary_phone(self):
        response = self._put(extra_phones=['۰۹۱۲۱۲۳۴۵۶۷'])
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.extra_phones, ['09121234567'])

    def test_blank_entries_are_dropped_not_rejected(self):
        """An owner who adds a row and leaves it empty must still be able to
        save everything else — the row is just discarded, not an error."""
        response = self._put(extra_phones=['09121234567', '', '   '])
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.extra_phones, ['09121234567'])

    def test_masked_for_anonymous_viewer_without_allow_anonymous_view(self):
        self.business.extra_phones = ['09121234567']
        self.business.allow_anonymous_view = False
        self.business.save(update_fields=['extra_phones', 'allow_anonymous_view'])

        data = PublicBusinessSerializer(self.business).data
        self.assertEqual(data['phone'], None)
        self.assertEqual(data['extra_phones'], [])


class WorkMinutesTests(OwnerWriteTestCaseMixin, TestCase):
    def test_owner_can_set_a_half_hour_start(self):
        response = self._put(work_start_hour=19, work_start_minute=30, work_end_hour=21, work_end_minute=0)
        self.assertEqual(response.status_code, 200, response.content)
        self.business.refresh_from_db()
        self.assertEqual(self.business.work_start_minute, 30)

    def test_default_minute_is_zero(self):
        self.assertEqual(self.business.work_start_minute, 0)
        self.assertEqual(self.business.work_end_minute, 0)

    def test_out_of_range_minute_is_rejected(self):
        response = self._put(work_start_minute=60)
        self.assertEqual(response.status_code, 400)

    def test_same_hour_different_minutes_is_a_valid_window(self):
        """19:00-19:30 used to be impossible to express (or worse, silently
        accepted as invalid) once hours alone decided validity."""
        response = self._put(work_start_hour=19, work_start_minute=0, work_end_hour=19, work_end_minute=30)
        self.assertEqual(response.status_code, 200, response.content)

    def test_same_hour_end_minute_before_start_minute_is_rejected(self):
        response = self._put(work_start_hour=19, work_start_minute=30, work_end_hour=19, work_end_minute=0)
        self.assertEqual(response.status_code, 400)

    def test_partial_update_checks_against_the_stored_minute_not_zero(self):
        """A PUT touching only work_end_hour must compare against the
        business's *stored* work_start_minute, not silently treat it as 0."""
        self.business.work_start_hour = 19
        self.business.work_start_minute = 45
        self.business.save(update_fields=['work_start_hour', 'work_start_minute'])

        # 19:00 as an end hour alone would be >= 19:00 start hour, but the
        # real start is 19:45, so ending at 19:00 must still be rejected.
        response = self._put(work_end_hour=19, work_end_minute=0)
        self.assertEqual(response.status_code, 400)


class BioLengthTests(OwnerWriteTestCaseMixin, TestCase):
    def test_bio_accepts_up_to_300_chars(self):
        response = self._put(bio='ب' * 300)
        self.assertEqual(response.status_code, 200, response.content)

    def test_bio_rejects_over_300_chars(self):
        response = self._put(bio='ب' * 301)
        self.assertEqual(response.status_code, 400)
