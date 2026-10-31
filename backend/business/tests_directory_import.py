"""«ورود از فایل»: the import service and the admin page that drives it."""
import io
import re
import shutil
import tempfile

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase, override_settings
from django.urls import reverse

from business import directory_import
from business.models import Business, Insurance

User = get_user_model()

CSV = """نام,شهر,صنف,تلفن,عرض جغرافیایی,طول جغرافیایی,بیمه‌ها,خدمات
کلینیک الف,تهران,دندان‌پزشکی,۰۲۱۸۸۰۰۰۰۰۰,35.75,51.41,"بیمه دانا، ناشناخته",جرم‌گیری، ترمیم
بی‌نقشه,اصفهان,,,,,,
,تهران,,,,,,
بی‌شهر,,,,,,,
کلینیک الف,تهران,دندان‌پزشکی,02188000000,,,,
"""


def rows_of(text):
    path = tempfile.mkstemp(suffix='.csv')[1]
    with open(path, 'w', encoding='utf-8-sig') as f:
        f.write(text)
    return directory_import.read_rows(path)


class ImportServiceTests(TestCase):
    def setUp(self):
        Insurance.objects.create(name='بیمه دانا', slug='dana')

    def test_dry_run_saves_nothing_and_reports(self):
        report = directory_import.run_import(rows_of(CSV), 'src', dry_run=True)
        self.assertEqual(Business.objects.count(), 0)
        self.assertEqual((report.total, report.created, report.duplicates, report.invalid), (5, 2, 1, 2))
        self.assertEqual([p[0] for p in report.problems], [4, 5])  # spreadsheet rows
        self.assertIn('بدون موقعیت روی نقشه', report.warnings)
        self.assertIn('بیمه «…» ناشناخته', report.warnings)

    def test_import_creates_unclaimed_listings_with_codes_and_gaps_allowed(self):
        directory_import.run_import(rows_of(CSV), 'نظام پزشکی')
        full = Business.objects.get(title='کلینیک الف')
        self.assertFalse(full.is_claimed)
        self.assertFalse(full.booking_enabled)
        self.assertEqual(full.user.phone, directory_import.SYSTEM_USER_PHONE)
        self.assertEqual(full.external_source, 'نظام پزشکی')
        self.assertEqual(full.category, 'DENTIST')
        self.assertEqual(full.phone, '02188000000')
        self.assertEqual((full.latitude, full.longitude), (35.75, 51.41))
        self.assertEqual(full.city_code, 1)
        self.assertEqual([i.slug for i in full.insurances.all()], ['dana'])
        self.assertEqual(full.services, ['جرم‌گیری', 'ترمیم'])

        bare = Business.objects.get(title='بی‌نقشه')
        self.assertEqual((bare.phone, bare.latitude, bare.longitude), ('', None, None))
        self.assertEqual(bare.category, 'OTHER')
        self.assertEqual(bare.city_code, 4)  # Isfahan, from the city name

    def test_import_twice_adds_nothing(self):
        directory_import.run_import(rows_of(CSV), 'src')
        again = directory_import.run_import(rows_of(CSV), 'src')
        self.assertEqual(again.created, 0)
        self.assertEqual(Business.objects.count(), 2)

    def test_headers_in_either_language_and_semicolons(self):
        report = directory_import.run_import(
            rows_of('title;city;lat;lng\nمطب;تهران;35.7;51.4\n'), 'src', dry_run=True,
        )
        self.assertEqual(report.created, 1)
        self.assertNotIn('بدون موقعیت روی نقشه', report.warnings)


@override_settings(MEDIA_ROOT=tempfile.mkdtemp())
class ImportAdminPageTests(TestCase):
    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()

    def setUp(self):
        self.staff = User.objects.create_superuser(phone='09120000399', password='x', name='ادمین')
        self.client.force_login(self.staff)
        self.url = reverse('admin:business_business_import_file')

    def upload(self, text=CSV, name='list.csv'):
        return self.client.post(self.url, {'file': SimpleUploadedFile(name, text.encode('utf-8'))})

    def test_page_and_template_download(self):
        self.assertEqual(self.client.get(self.url).status_code, 200)
        sample = self.client.get(reverse('admin:business_business_import_template'))
        self.assertEqual(sample.status_code, 200)
        self.assertIn('نام', sample.content.decode('utf-8-sig'))
        # the sample itself imports cleanly
        report = directory_import.run_import(
            directory_import.read_rows(self._save(sample.content.decode('utf-8-sig'))), 's', dry_run=True,
        )
        self.assertEqual((report.created, report.invalid), (2, 0))

    def _save(self, text):
        path = tempfile.mkstemp(suffix='.csv')[1]
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        return path

    def test_preview_saves_nothing_and_confirm_imports_that_file(self):
        preview = self.upload()
        self.assertEqual(preview.status_code, 200)
        self.assertEqual(Business.objects.count(), 0)
        token = re.search(r'name="token" value="([0-9a-f]{32})"', preview.content.decode()).group(1)

        done = self.client.post(self.url, {'token': token, 'source': 'دستی'})
        self.assertEqual(done.status_code, 302)
        self.assertEqual(Business.objects.filter(external_source='دستی', is_claimed=False).count(), 2)
        # the token is single use
        again = self.client.post(self.url, {'token': token, 'source': 'دستی'})
        self.assertContains(again, 'دیگر در دسترس نیست')
        self.assertEqual(Business.objects.count(), 2)

    def test_bad_input_is_explained_not_crashed(self):
        self.assertContains(self.upload('x', 'list.xlsx'), 'CSV')
        self.assertContains(self.client.post(self.url, {}), 'فایلی انتخاب نشده')
        self.assertContains(self.client.post(self.url, {'token': '../../etc/passwd'}), 'دیگر در دسترس نیست')

    def test_needs_staff(self):
        self.client.logout()
        self.assertEqual(self.client.get(self.url).status_code, 302)


class AdminAddFormTests(TestCase):
    def setUp(self):
        self.staff = User.objects.create_superuser(phone='09120000398', password='x', name='ادمین')
        self.client.force_login(self.staff)
        self.add_url = reverse('admin:business_business_add')

    def test_add_page_links_to_the_import_and_defaults_to_the_system_user(self):
        page = self.client.get(self.add_url)
        self.assertContains(page, reverse('admin:business_business_import_file'))
        system = User.objects.get(phone=directory_import.SYSTEM_USER_PHONE)
        self.assertEqual(page.context['adminform'].form.initial['user'], system.pk)
        self.assertFalse(page.context['adminform'].form.initial['is_claimed'])

    def test_a_listing_added_by_hand_is_public_with_its_city_code(self):
        # What the browser would post: the form's own initial values, plus what the person typed.
        page = self.client.get(self.add_url)
        form = page.context['adminform'].form
        data = {name: form[name].value() for name in form.fields
                if form[name].value() is not None and form[name].value() is not False}
        data.update({'title': 'مطب دستی', 'category': 'DENTIST', 'phone': '', 'address': 'خ ۱', 'city': 'اصفهان'})
        data.pop('is_claimed', None)
        for inline in page.context['inline_admin_formsets']:
            management = inline.formset.management_form
            data.update({management.add_prefix(n): management[n].value() for n in management.fields})
        response = self.client.post(self.add_url, data)
        if response.status_code == 200:
            self.fail(response.context['adminform'].form.errors.as_text())
        biz = Business.objects.get(title='مطب دستی')
        self.assertFalse(biz.is_claimed)
        self.assertEqual(biz.phone, '')
        self.assertEqual(biz.moderation_status, Business.MODERATION_APPROVED)
        self.assertEqual(biz.city_code, 4)
        self.assertIsNone(biz.latitude)
