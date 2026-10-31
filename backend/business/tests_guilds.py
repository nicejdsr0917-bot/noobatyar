"""One list and one add form per guild (صنف)."""
from django.conf import settings
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse

from business import directory_import
from business.categories import CATEGORY_GROUPS
from business.models import Business
from business.models.guilds import GUILDS

User = get_user_model()


class GuildAdminTests(TestCase):
    def setUp(self):
        self.staff = User.objects.create_superuser(phone='09120000397', password='x', name='ادمین')
        self.client.force_login(self.staff)
        self.owner = directory_import.get_system_user()

    def test_every_category_group_has_a_guild_and_the_sidebar_lists_them(self):
        self.assertEqual({key for key, _l, _p in CATEGORY_GROUPS}, set(GUILDS))
        menu = {name for name, _t, _i in __import__('core.settings', fromlist=['_GUILD_MENU'])._GUILD_MENU}
        self.assertEqual(menu, {model.__name__.lower() for model, _t, _i in GUILDS.values()})

    def test_each_guild_lists_only_its_categories_and_adds_only_those(self):
        for key, (model, _title, _icon) in GUILDS.items():
            codes = {c for k, _l, pairs in CATEGORY_GROUPS if k == key for c, _ in pairs}
            name = model.__name__.lower()
            with self.subTest(guild=key):
                self.assertEqual(self.client.get(reverse(f'admin:business_{name}_changelist')).status_code, 200)
                page = self.client.get(reverse(f'admin:business_{name}_add'))
                self.assertEqual(page.status_code, 200)
                self.assertContains(page, reverse('admin:business_business_import_file'))
                form = page.context['adminform'].form
                self.assertEqual({c for c, _l in form.fields['category'].choices}, codes)
                self.assertIn(form.initial['category'], codes)
                services_section = next(o['fields'] for t, o in page.context['adminform'].fieldsets if t == 'خدمات و ساعت کار')
                self.assertEqual('insurances' in services_section, key in {'MEDICAL', 'DENTAL', 'HEALTH'})

    def test_beauty_list_holds_salons_not_clinics(self):
        base = dict(user=self.owner, phone='', address='خ', is_claimed=False,
                    default_service_duration=30, work_start_hour=9, work_end_hour=17)
        Business.objects.create(title='سالن', category='WOMENS_SALON', **base)
        Business.objects.create(title='مطب', category='DENTIST', **base)
        page = self.client.get(reverse('admin:business_beautylisting_changelist'))
        self.assertContains(page, 'سالن')
        self.assertNotContains(page, 'مطب')

    def test_a_salon_added_in_its_own_form_is_a_public_unclaimed_listing(self):
        add = reverse('admin:business_beautylisting_add')
        page = self.client.get(add)
        form = page.context['adminform'].form
        data = {n: form[n].value() for n in form.fields if form[n].value() is not None and form[n].value() is not False}
        data.update({
            'title': 'سالن رز', 'category': 'BARBERSHOP', 'phone': '', 'address': 'خ ۱', 'city': 'شیراز',
            'services': 'کوتاهی مو، رنگ و مش',
        })
        data.pop('is_claimed', None)
        for inline in page.context['inline_admin_formsets']:
            m = inline.formset.management_form
            data.update({m.add_prefix(n): m[n].value() for n in m.fields})
        response = self.client.post(add, data)
        if response.status_code == 200:
            self.fail(response.context['adminform'].form.errors.as_text())
        biz = Business.objects.get(title='سالن رز')
        self.assertEqual(biz.services, ['کوتاهی مو', 'رنگ و مش'])
        self.assertEqual(biz.category, 'BARBERSHOP')
        self.assertFalse(biz.is_claimed)
        self.assertEqual(biz.moderation_status, Business.MODERATION_APPROVED)
        self.assertEqual(biz.city_code, 6)
