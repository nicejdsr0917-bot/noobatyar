"""
business/tests_contact_policy.py

accounting.ContactVisibilityPolicy: an owner without an active subscription
can have their phone / address withheld from customers. Withheld means null
in the payload (never sent), and `is_expired` always says whether the
subscription is the reason — independent of the owner's own
allow_anonymous_view switch.
"""

from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from visitor.auth import sign_visitor_token
from visitor.models import Visitor
from accounting.models import ContactVisibilityPolicy, Plan, Subscription
from .models import Business
from .serializers import ClientBusinessSerializer, PublicBusinessSerializer
from .tests_moderation_api import make_business

User = get_user_model()


class ContactPolicyTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000050', name='مالک')
        self.plan = Plan.objects.create(name='پرو', price=100000, duration_value=1, duration_unit='month')
        self.business = make_business(
            self.owner,
            extra_phones=['09121112233'],
            latitude=35.7,
            longitude=51.4,
            allow_anonymous_view=True,
            moderation_status=Business.MODERATION_APPROVED,
        )

    def _subscribe(self, *, ends_in_days):
        Subscription.objects.create(
            user=self.owner, plan=self.plan,
            ends_at=timezone.now() + timedelta(days=ends_in_days),
        )

    def _policy(self, **fields):
        policy = ContactVisibilityPolicy.load()
        for key, value in fields.items():
            setattr(policy, key, value)
        policy.save()
        return policy

    def _assert_contact_shown(self, data):
        self.assertEqual(data['phone'], '02112345678')
        self.assertEqual(data['extra_phones'], ['09121112233'])
        self.assertEqual(data['address'], 'تهران، خیابان آزادی')
        self.assertEqual((data['latitude'], data['longitude']), (35.7, 51.4))

    def _assert_contact_withheld(self, data):
        self.assertIsNone(data['phone'])
        self.assertEqual(data['extra_phones'], [])
        self.assertIsNone(data['address'])
        self.assertIsNone(data['latitude'])
        self.assertIsNone(data['longitude'])

    def test_policy_is_off_by_default(self):
        data = PublicBusinessSerializer(self.business).data
        self.assertTrue(data['is_expired'])
        self._assert_contact_shown(data)

    def test_active_subscription_shows_everything(self):
        self._policy(hide_when_expired=True)
        self._subscribe(ends_in_days=10)
        data = PublicBusinessSerializer(self.business).data
        self.assertFalse(data['is_expired'])
        self._assert_contact_shown(data)

    def test_lapsed_subscription_withholds_contact(self):
        self._policy(hide_when_expired=True)
        self._subscribe(ends_in_days=-1)
        data = PublicBusinessSerializer(self.business).data
        self.assertTrue(data['is_expired'])
        self._assert_contact_withheld(data)

    def test_never_subscribed_counts_as_expired(self):
        self._policy(hide_when_expired=True)
        data = PublicBusinessSerializer(self.business).data
        self.assertTrue(data['is_expired'])
        self._assert_contact_withheld(data)

    def test_has_location_survives_the_withholding(self):
        """Says a pin exists, never where."""
        self._policy(hide_when_expired=True)
        data = PublicBusinessSerializer(self.business).data
        self.assertTrue(data['has_location'])
        self.assertIsNone(data['latitude'])

        self.business.latitude = self.business.longitude = None
        self.business.save()
        data = PublicBusinessSerializer(self.business).data
        self.assertFalse(data['has_location'])

    def test_phone_only(self):
        self._policy(hide_when_expired=True, hide_address=False)
        data = PublicBusinessSerializer(self.business).data
        self.assertIsNone(data['phone'])
        self.assertEqual(data['extra_phones'], [])
        self.assertEqual(data['address'], 'تهران، خیابان آزادی')
        self.assertEqual(data['latitude'], 35.7)

    def test_address_only(self):
        self._policy(hide_when_expired=True, hide_phone=False)
        data = PublicBusinessSerializer(self.business).data
        self.assertEqual(data['phone'], '02112345678')
        self.assertIsNone(data['address'])
        self.assertIsNone(data['latitude'])

    def test_owner_privacy_switch_is_not_reported_as_expiry(self):
        """allow_anonymous_view off hides contact from guests, but with an
        active subscription is_expired stays false — the client must not draw
        the 'subscription' blur for the owner's own choice."""
        self._policy(hide_when_expired=True)
        self._subscribe(ends_in_days=10)
        self.business.allow_anonymous_view = False
        self.business.save()
        data = PublicBusinessSerializer(self.business).data
        self.assertFalse(data['is_expired'])
        self.assertIsNone(data['phone'])

    def test_signed_in_customer_is_covered_too(self):
        """Unlike the owner's guest switch, the policy is not lifted by
        signing in."""
        self._policy(hide_when_expired=True)
        customer = User.objects.create_user(phone='09120000051', name='مشتری')
        client = APIClient()
        client.force_authenticate(user=customer)
        response = client.get(reverse('client-business-detail', args=[self.business.id]))
        self.assertEqual(response.status_code, 200, response.content)
        data = response.json()['data']
        self.assertTrue(data['is_expired'])
        self._assert_contact_withheld(data)

    def test_guest_switch_is_lifted_for_a_signed_in_visitor(self):
        """allow_anonymous_view off hides contact from guests; a visitor who
        signed in with their OTP token sees it, a guest (or a bad token) does
        not."""
        self._subscribe(ends_in_days=10)
        self.business.allow_anonymous_view = False
        self.business.save()
        url = reverse('client-business-detail', args=[self.business.id])
        visitor = Visitor.objects.create(full_name='مشتری', phone_number='09390000777')
        token = sign_visitor_token(visitor.id)

        guest = APIClient().get(url)
        self.assertEqual(guest.status_code, 200, guest.content)
        self.assertIsNone(guest.json()['data']['phone'])
        self.assertIsNone(guest.json()['data']['address'])

        signed_in = APIClient().get(url, HTTP_AUTHORIZATION=f'Visitor {token}')
        self.assertEqual(signed_in.status_code, 200, signed_in.content)
        self._assert_contact_shown(signed_in.json()['data'])

        listing = APIClient().get(reverse('client-business-list'), HTTP_AUTHORIZATION=f'Visitor {token}')
        self.assertEqual(listing.status_code, 200, listing.content)
        self.assertEqual(listing.json()['data']['results'][0]['phone'], '02112345678')

        stale = APIClient().get(url, HTTP_AUTHORIZATION='Visitor garbage')
        self.assertEqual(stale.status_code, 401)

    def test_listing_withholds_per_business(self):
        self._policy(hide_when_expired=True)
        paying_owner = User.objects.create_user(phone='09120000052', name='مالک دوم')
        Subscription.objects.create(
            user=paying_owner, plan=self.plan, ends_at=timezone.now() + timedelta(days=5),
        )
        make_business(paying_owner, title='کسب‌وکار فعال', moderation_status=Business.MODERATION_APPROVED)

        response = APIClient().get(reverse('client-business-list'))
        self.assertEqual(response.status_code, 200, response.content)
        by_title = {row['title']: row for row in response.json()['data']['results']}
        self.assertTrue(by_title['سالن زیبایی نمونه']['is_expired'])
        self.assertIsNone(by_title['سالن زیبایی نمونه']['phone'])
        self.assertFalse(by_title['کسب‌وکار فعال']['is_expired'])
        self.assertEqual(by_title['کسب‌وکار فعال']['phone'], '02112345678')

    def test_client_serializer_follows_the_same_rule(self):
        """The business block inside a customer's own appointment."""
        self._policy(hide_when_expired=True)
        data = ClientBusinessSerializer(self.business).data
        self.assertTrue(data['is_expired'])
        self._assert_contact_withheld(data)

    def test_owner_still_sees_their_own_details(self):
        self._policy(hide_when_expired=True)
        client = APIClient()
        client.force_authenticate(user=self.owner)
        response = client.get(reverse('business-detail', args=[self.business.id]))
        self.assertEqual(response.status_code, 200, response.content)
        self.assertEqual(response.json()['data']['phone'], '02112345678')

    def test_singleton(self):
        ContactVisibilityPolicy.load()
        ContactVisibilityPolicy(hide_when_expired=True).save()
        self.assertEqual(ContactVisibilityPolicy.objects.count(), 1)
        self.assertTrue(ContactVisibilityPolicy.load().hide_when_expired)


class ContactPolicyAdminTests(TestCase):
    def setUp(self):
        self.admin = User.objects.create_superuser(phone='09120000060', password='x-test-only', name='ادمین')
        self.client.force_login(self.admin)

    def test_changelist_goes_straight_to_the_settings(self):
        response = self.client.get(reverse('admin:accounting_contactvisibilitypolicy_changelist'))
        self.assertRedirects(
            response,
            reverse('admin:accounting_contactvisibilitypolicy_change', args=[1]),
            fetch_redirect_response=False,
        )

    def test_settings_page_renders_and_saves(self):
        url = reverse('admin:accounting_contactvisibilitypolicy_change', args=[1])
        ContactVisibilityPolicy.load()
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, 'کسب‌وکارهای مشمول')

        response = self.client.post(url, {'hide_when_expired': 'on', 'hide_phone': 'on'})
        self.assertRedirects(response, url, fetch_redirect_response=False)
        policy = ContactVisibilityPolicy.load()
        self.assertTrue(policy.hide_when_expired)
        self.assertTrue(policy.hide_phone)
        self.assertFalse(policy.hide_address)
