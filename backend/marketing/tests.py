from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from business.models import Business

from .models import LeadNote, MarketingLead
from . import services

User = get_user_model()


class MarketingTests(TestCase):
    def setUp(self):
        self.staff = User.objects.create_superuser(phone='09120000601', password='x', name='S')
        self.client.force_login(self.staff)

    def test_service_creates_unclaimed_business(self):
        lead = services.create_lead(title='کلینیک', category='DOCTOR', phone='02112345678', address='تهران')
        b = lead.business
        self.assertFalse(b.is_claimed)
        self.assertEqual(b.user.phone, services.SYSTEM_PHONE)
        self.assertEqual(b.moderation_status, Business.MODERATION_APPROVED)
        self.assertEqual(lead.status, MarketingLead.NOT_CALLED)

    def test_admin_add_with_pin_validation(self):
        url = reverse('admin:marketing_marketinglead_add')
        base = {'title': 'سالن', 'category': 'BEAUTY_SALON', 'phone': '02111111111', 'address': 'تهران',
                'province': '', 'city': 'تهران'}
        bad = self.client.post(url, {**base, 'latitude': '35.7'})
        self.assertEqual(bad.status_code, 200)
        self.assertEqual(MarketingLead.objects.count(), 0)
        ok = self.client.post(url, {**base, 'latitude': '35.7', 'longitude': '51.4', 'assigned_to': self.staff.id})
        self.assertEqual(ok.status_code, 302)
        lead = MarketingLead.objects.get()
        self.assertEqual((lead.business.latitude, lead.business.city), (35.7, 'تهران'))
        self.assertEqual(lead.assigned_to, self.staff)

    def test_notes_inline_stamps_author(self):
        lead = services.create_lead(title='x', category='DOCTOR', phone='1', address='a')
        url = reverse('admin:marketing_marketinglead_change', args=[lead.pk])
        self.assertEqual(self.client.get(url).status_code, 200)
        r = self.client.post(url, {
            'status': 'CALLED', 'next_follow_up': '', 'assigned_to': '',
            'notes-TOTAL_FORMS': '1', 'notes-INITIAL_FORMS': '0', 'notes-MIN_NUM_FORMS': '0',
            'notes-MAX_NUM_FORMS': '1000', 'notes-0-text': 'گفت فردا زنگ بزنید', 'notes-0-lead': lead.pk,
        })
        self.assertEqual(r.status_code, 302, r.context['errors'] if r.context else '')
        note = LeadNote.objects.get()
        self.assertEqual(note.created_by, self.staff)
        lead.refresh_from_db()
        self.assertEqual(lead.status, 'CALLED')

    def test_today_calls_filter_and_status_filter(self):
        today = timezone.localdate()
        for name, status, d in [
            ('due', 'CALLED', today), ('late', 'NOT_CALLED', today - timedelta(days=3)),
            ('future', 'INTERESTED', today + timedelta(days=2)), ('joined', 'JOINED', today),
            ('no', 'NOT_INTERESTED', today - timedelta(days=1)), ('nodate', 'CALLED', None),
        ]:
            lead = services.create_lead(title=name, category='DOCTOR', phone='1', address='a', next_follow_up=d)
            lead.status = status
            lead.save()
        url = reverse('admin:marketing_marketinglead_changelist')
        r = self.client.get(url, {'today': '1'})
        self.assertEqual(r.status_code, 200)
        titles = {x.business.title for x in r.context['cl'].result_list}
        self.assertEqual(titles, {'due', 'late'})
        r = self.client.get(url, {'status__exact': 'JOINED'})
        self.assertEqual({x.business.title for x in r.context['cl'].result_list}, {'joined'})

    def test_non_staff_has_no_access(self):
        self.client.logout()
        user = User.objects.create_user(phone='09120000602', password='x', name='U')
        self.client.force_login(user)
        r = self.client.get(reverse('admin:marketing_marketinglead_changelist'))
        self.assertEqual(r.status_code, 302)
