"""Every admin sidebar link resolves (200) for a superuser."""
from django.conf import settings
from django.contrib.auth import get_user_model
from django.test import TestCase

User = get_user_model()


class AdminSidebarTests(TestCase):
    def test_every_sidebar_link_is_200(self):
        staff = User.objects.create_superuser(phone='09120000801', password='x', name='S')
        self.client.force_login(staff)
        from django.test import RequestFactory
        rf = RequestFactory().get('/')
        rf.user = staff
        seen = 0
        for group in settings.UNFOLD['SIDEBAR']['navigation']:
            for item in group['items']:
                perm = item.get('permission')
                if perm and not perm(rf):
                    continue  # optional app not installed
                link = item['link']
                url = str(link(rf) if callable(link) else link)
                with self.subTest(title=item['title'], url=url):
                    self.assertNotEqual(url, '#')
                    self.assertEqual(self.client.get(url, follow=True).status_code, 200)
                    seen += 1
        self.assertGreater(seen, 30)

    def test_sections_are_short_and_in_daily_order(self):
        nav = settings.UNFOLD['SIDEBAR']['navigation']
        titles = [g['title'] for g in nav if g.get('title')]
        self.assertEqual(titles, ['بررسی', 'ثبت مرکز', 'کاربران', 'رشد', 'پیام', 'مالی', 'سیستم'])
        # Menu labels stay short; what a page is for belongs on the page.
        for group in nav:
            for item in group['items']:
                self.assertLessEqual(len(item['title'].split()), 2, item['title'])

    def test_p2_pages_still_reachable(self):
        items = {i['title'] for g in settings.UNFOLD['SIDEBAR']['navigation'] for i in g['items']}
        for title in ('صف بررسی', 'نمونه‌کارها', 'مدارک', 'تخلف‌ها', 'نظرات', 'بنرها', 'تماس‌های امروز', 'گزارش مالی', 'گزارش پیامک'):
            self.assertIn(title, items)
