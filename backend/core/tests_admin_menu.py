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

    def test_new_sections_present(self):
        titles = ' '.join(g.get('title', '') for g in settings.UNFOLD['SIDEBAR']['navigation'])
        for word in ('بررسی کسب‌وکارها', 'نمونه‌کار و مدارک', 'نظرات و گزارش‌ها', 'بنرها', 'بازاریابی', 'اشتراک و مالی', 'پیامک'):
            self.assertIn(word, titles)
