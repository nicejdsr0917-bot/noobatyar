"""Staff panel: access rules, every write action, and query counts that do not
grow with the number of rows on a list page."""

import io
import tempfile
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.core.cache import cache
from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import connection
from django.test import TestCase, override_settings
from django.test.utils import CaptureQueriesContext
from django.urls import reverse
from django.utils import timezone
from PIL import Image

from appointment.models import Appointment
from banners.models import Banner, BannerCategoryGroup, SiteSetting
from business.models import Business, BusinessModerationLog, ContentReport
from marketing import services as marketing_services
from marketing.models import LeadNote, MarketingLead
from reviews.models import Review
from showcase.models import Certificate, ModerationStatus, PortfolioItem
from visitor.models import SmsLog, Visitor

from ..nav import BADGE_CACHE_KEY

User = get_user_model()
MEDIA = tempfile.mkdtemp()


def png(w, h, name='a.png'):
    buf = io.BytesIO()
    Image.new('RGB', (w, h), (120, 80, 200)).save(buf, format='PNG')
    return SimpleUploadedFile(name, buf.getvalue(), content_type='image/png')


def make_business(owner, title='سالن', status=Business.MODERATION_APPROVED):
    return Business.objects.create(
        user=owner, title=title, phone='02100000000', address='تهران',
        default_service_duration=30, work_start_hour=9, work_end_hour=17,
        moderation_status=status,
    )


def grant(user, *perms):
    for perm in perms:
        app, codename = perm.split('.')
        user.user_permissions.add(Permission.objects.get(content_type__app_label=app, codename=codename))
    return User.objects.get(pk=user.pk)  # drop the cached permission set


class PanelTestCase(TestCase):
    def setUp(self):
        cache.delete(BADGE_CACHE_KEY)
        cache.clear()
        self.admin = User.objects.create_superuser(phone='09120000701', password='x', name='مدیر')
        self.owner = User.objects.create_user(phone='09120000702', name='مالک')
        self.business = make_business(self.owner)
        self.client.force_login(self.admin)

    def visitor(self, n, business=None, source='WEB', status='COMPLETED'):
        v = Visitor.objects.create(full_name=f'مشتری {n}', phone_number=f'0913000{n:04d}')
        Appointment.objects.create(
            business=business or self.business, visitor=v, status=status, source=source,
            appointment_date=timezone.now() - timedelta(days=1))
        return v


# ── Access ──────────────────────────────────────────────────────────────────

PAGES = [
    ('staffpanel:dashboard', ()), ('staffpanel:approvals', ()), ('staffpanel:showcase', ()),
    ('staffpanel:reviews', ()), ('staffpanel:reports', ()), ('staffpanel:customers', ()),
    ('staffpanel:businesses', ()), ('staffpanel:banners', ()), ('staffpanel:marketing', ()),
    ('staffpanel:finance', ()), ('staffpanel:sms', ()), ('staffpanel:search', ()),
]


class AccessTests(PanelTestCase):
    def test_anonymous_is_sent_to_admin_login(self):
        self.client.logout()
        for name, args in PAGES:
            with self.subTest(name):
                r = self.client.get(reverse(name, args=args))
                self.assertEqual(r.status_code, 302)
                self.assertIn(reverse('admin:login'), r['Location'])

    def test_non_staff_gets_403(self):
        self.client.force_login(self.owner)
        for name, args in PAGES:
            with self.subTest(name):
                self.assertEqual(self.client.get(reverse(name, args=args)).status_code, 403)

    def test_non_staff_cannot_post_actions(self):
        self.client.force_login(self.owner)
        pending = make_business(self.owner, 'منتظر', Business.MODERATION_PENDING)
        r = self.client.post(reverse('staffpanel:approval_decide', args=[pending.pk]), {'decision': 'approve'})
        self.assertEqual(r.status_code, 403)
        pending.refresh_from_db()
        self.assertEqual(pending.moderation_status, Business.MODERATION_PENDING)

    def test_staff_without_permission_gets_403(self):
        staff = User.objects.create_user(phone='09120000703', name='کارمند', is_staff=True)
        self.client.force_login(staff)
        self.assertEqual(self.client.get(reverse('staffpanel:dashboard')).status_code, 200)
        for name in ('staffpanel:approvals', 'staffpanel:customers', 'staffpanel:finance', 'staffpanel:marketing'):
            with self.subTest(name):
                self.assertEqual(self.client.get(reverse(name)).status_code, 403)

    def test_view_permission_alone_cannot_decide(self):
        staff = grant(User.objects.create_user(phone='09120000704', name='ک', is_staff=True), 'business.view_business')
        self.client.force_login(staff)
        self.assertEqual(self.client.get(reverse('staffpanel:approvals')).status_code, 200)
        pending = make_business(self.owner, 'منتظر', Business.MODERATION_PENDING)
        r = self.client.post(reverse('staffpanel:approval_decide', args=[pending.pk]), {'decision': 'approve'})
        self.assertEqual(r.status_code, 403)

    def test_menu_shows_only_permitted_items(self):
        staff = grant(User.objects.create_user(phone='09120000705', name='ک', is_staff=True), 'visitor.view_visitor')
        self.client.force_login(staff)
        html = self.client.get(reverse('staffpanel:dashboard')).content.decode()
        self.assertIn(reverse('staffpanel:customers'), html)
        self.assertNotIn(reverse('staffpanel:finance'), html)

    def test_actions_are_post_only(self):
        pending = make_business(self.owner, 'منتظر', Business.MODERATION_PENDING)
        r = self.client.get(reverse('staffpanel:approval_decide', args=[pending.pk]))
        self.assertEqual(r.status_code, 405)

    def test_all_pages_render_for_superuser(self):
        for name, args in PAGES:
            with self.subTest(name):
                self.assertEqual(self.client.get(reverse(name, args=args)).status_code, 200)


# ── Moderation actions ──────────────────────────────────────────────────────

class ApprovalTests(PanelTestCase):
    def setUp(self):
        super().setUp()
        self.pending = make_business(self.owner, 'منتظر', Business.MODERATION_PENDING)

    def test_queue_lists_pending(self):
        r = self.client.get(reverse('staffpanel:approvals'))
        self.assertContains(r, 'منتظر')

    def test_approve(self):
        r = self.client.post(reverse('staffpanel:approval_decide', args=[self.pending.pk]), {'decision': 'approve'})
        self.assertRedirects(r, reverse('staffpanel:approvals'), fetch_redirect_response=False)
        self.pending.refresh_from_db()
        self.assertEqual(self.pending.moderation_status, Business.MODERATION_APPROVED)
        log = BusinessModerationLog.objects.get(business=self.pending)
        self.assertEqual(log.actor, self.admin)

    def test_reject_needs_reason(self):
        self.client.post(reverse('staffpanel:approval_decide', args=[self.pending.pk]), {'decision': 'reject'})
        self.pending.refresh_from_db()
        self.assertEqual(self.pending.moderation_status, Business.MODERATION_PENDING)

    def test_reject_with_reason(self):
        self.client.post(reverse('staffpanel:approval_decide', args=[self.pending.pk]),
                         {'decision': 'reject', 'note': 'لوگو نامناسب'})
        self.pending.refresh_from_db()
        self.assertEqual(self.pending.moderation_status, Business.MODERATION_REJECTED)
        self.assertEqual(self.pending.moderation_note, 'لوگو نامناسب')

    def test_bulk_approve(self):
        other = make_business(self.owner, 'دومی', Business.MODERATION_PENDING)
        self.client.post(reverse('staffpanel:approvals_bulk'),
                         {'decision': 'approve', 'ids': f'{self.pending.pk},{other.pk}'})
        self.assertEqual(Business.objects.filter(moderation_status=Business.MODERATION_PENDING).count(), 0)

    def test_bulk_reject_needs_reason(self):
        self.client.post(reverse('staffpanel:approvals_bulk'), {'decision': 'reject', 'ids': str(self.pending.pk)})
        self.pending.refresh_from_db()
        self.assertEqual(self.pending.moderation_status, Business.MODERATION_PENDING)

    def test_next_redirect_stays_on_host(self):
        r = self.client.post(reverse('staffpanel:approval_decide', args=[self.pending.pk]),
                             {'decision': 'approve', 'next': 'https://evil.example/'})
        self.assertEqual(r['Location'], reverse('staffpanel:approvals'))


@override_settings(MEDIA_ROOT=MEDIA)
class ShowcaseTests(PanelTestCase):
    def setUp(self):
        super().setUp()
        self.item = PortfolioItem.objects.create(business=self.business, image='x.jpg', caption='کار')
        self.cert = Certificate.objects.create(business=self.business, title='مدرک', image='c.jpg')

    def test_lists_pending(self):
        self.assertContains(self.client.get(reverse('staffpanel:showcase')), 'کار')
        self.assertContains(self.client.get(reverse('staffpanel:showcase') + '?kind=certificate'), 'مدرک')

    def test_approve(self):
        self.client.post(reverse('staffpanel:showcase_decide', args=['portfolio', self.item.pk]), {'decision': 'approve'})
        self.item.refresh_from_db()
        self.assertEqual(self.item.status, ModerationStatus.APPROVED)
        self.assertEqual(self.item.reviewed_by, self.admin)

    def test_reject_needs_reason(self):
        url = reverse('staffpanel:showcase_decide', args=['certificate', self.cert.pk])
        self.client.post(url, {'decision': 'reject'})
        self.cert.refresh_from_db()
        self.assertEqual(self.cert.status, ModerationStatus.PENDING)
        self.client.post(url, {'decision': 'reject', 'note': 'ناخوانا'})
        self.cert.refresh_from_db()
        self.assertEqual((self.cert.status, self.cert.reject_reason), (ModerationStatus.REJECTED, 'ناخوانا'))

    def test_bulk_approve(self):
        second = PortfolioItem.objects.create(business=self.business, image='y.jpg')
        self.client.post(reverse('staffpanel:showcase_bulk', args=['portfolio']),
                         {'decision': 'approve', 'ids': f'{self.item.pk},{second.pk}'})
        self.assertFalse(PortfolioItem.objects.filter(status=ModerationStatus.PENDING).exists())

    def test_unknown_kind_404(self):
        r = self.client.post(reverse('staffpanel:showcase_decide', args=['nope', 1]), {'decision': 'approve'})
        self.assertEqual(r.status_code, 404)

    def test_needs_change_permission(self):
        staff = grant(User.objects.create_user(phone='09120000706', name='ک', is_staff=True),
                      'showcase.view_portfolioitem')
        self.client.force_login(staff)
        r = self.client.post(reverse('staffpanel:showcase_decide', args=['portfolio', self.item.pk]), {'decision': 'approve'})
        self.assertEqual(r.status_code, 403)


class ReviewAndReportTests(PanelTestCase):
    def setUp(self):
        super().setUp()
        self.v = self.visitor(1)
        self.review = Review.objects.create(business=self.business, visitor=self.v, rating=1, text='بد')
        self.report = ContentReport.objects.create(business=self.business, reason='SPAM', review=self.review)

    def test_hide_needs_reason_and_updates_summary(self):
        url = reverse('staffpanel:review_visibility', args=[self.review.pk])
        self.client.post(url, {'hidden': '1'})
        self.review.refresh_from_db()
        self.assertFalse(self.review.is_hidden)
        self.client.post(url, {'hidden': '1', 'note': 'توهین'})
        self.review.refresh_from_db()
        self.business.refresh_from_db()
        self.assertTrue(self.review.is_hidden)
        self.assertEqual(self.review.hidden_reason, 'توهین')
        self.assertEqual(self.business.rating_count, 0)

    def test_unhide(self):
        Review.objects.filter(pk=self.review.pk).update(is_hidden=True)
        self.client.post(reverse('staffpanel:review_visibility', args=[self.review.pk]), {'hidden': '0'})
        self.review.refresh_from_db()
        self.business.refresh_from_db()
        self.assertFalse(self.review.is_hidden)
        self.assertEqual(self.business.rating_count, 1)

    def test_reported_tab(self):
        self.assertContains(self.client.get(reverse('staffpanel:reviews') + '?tab=reported'), 'بد')

    def test_report_reviewing_then_close(self):
        url = reverse('staffpanel:report_resolve', args=[self.report.pk])
        self.client.post(url, {'status': 'reviewing'})
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, ContentReport.STATUS_REVIEWING)
        self.assertIsNone(self.report.resolved_by)
        self.client.post(url, {'status': 'dismissed'})  # no note → refused
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, ContentReport.STATUS_REVIEWING)
        self.client.post(url, {'status': 'actioned', 'note': 'نظر مخفی شد'})
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, ContentReport.STATUS_ACTIONED)
        self.assertEqual(self.report.resolved_by, self.admin)
        self.assertEqual(self.report.resolution_note, 'نظر مخفی شد')


# ── Customer file, search ───────────────────────────────────────────────────

class CustomerTests(PanelTestCase):
    def test_file_code_search(self):
        v = self.visitor(1)
        self.visitor(2)
        r = self.client.get(reverse('staffpanel:customers'), {'q': f'C-{v.pk:06d}'})
        self.assertEqual(list(r.context['page_obj'].object_list), [v])

    def test_phone_search_with_persian_digits(self):
        v = self.visitor(7)
        r = self.client.get(reverse('staffpanel:customers'), {'q': '۰۰۰۷'})
        self.assertIn(v, r.context['page_obj'].object_list)

    def test_scope_tabs_and_source(self):
        other = make_business(self.owner, 'دیگری')
        mine = self.visitor(1, source='APP')
        self.visitor(2, business=other, source='OWNER')
        upcoming = self.visitor(3, status='CONFIRMED')
        Appointment.objects.filter(visitor=upcoming).update(appointment_date=timezone.now() + timedelta(days=2))

        r = self.client.get(reverse('staffpanel:customers'), {'business': self.business.pk})
        self.assertEqual(r.context['page_obj'].paginator.count, 2)
        tabs = {k: n for k, _l, n in r.context['tabs']}
        self.assertEqual((tabs['all'], tabs['active'], tabs['done']), (2, 1, 1))

        r = self.client.get(reverse('staffpanel:customers'), {'source': 'APP'})
        self.assertEqual(list(r.context['page_obj'].object_list), [mine])
        self.assertContains(self.client.get(reverse('staffpanel:customers')), 'src-APP')

    def test_detail(self):
        v = self.visitor(1)
        Review.objects.create(business=self.business, visitor=v, rating=5, text='عالی')
        SmsLog.objects.create(business=self.business, visitor=v, message_text='یادآوری نوبت', status='SENT')
        r = self.client.get(reverse('staffpanel:customer_detail', args=[v.pk]))
        self.assertContains(r, f'C-{v.pk:06d}')
        self.assertContains(r, 'عالی')
        self.assertContains(r, 'یادآوری نوبت')
        self.assertEqual(r.context['stats']['completed'], 1)

    def test_search_endpoint(self):
        v = self.visitor(1)
        data = self.client.get(reverse('staffpanel:search'), {'q': 'مشتری'}).json()
        self.assertIn(reverse('staffpanel:customer_detail', args=[v.pk]), [x['href'] for x in data['results']])
        data = self.client.get(reverse('staffpanel:search'), {'q': 'سالن'}).json()
        self.assertEqual(data['results'][0]['group'], 'کسب‌وکارها')
        self.assertEqual(self.client.get(reverse('staffpanel:search'), {'q': 'x'}).json(), {'results': []})

    def test_search_hides_groups_without_permission(self):
        self.visitor(1)
        staff = grant(User.objects.create_user(phone='09120000707', name='ک', is_staff=True), 'business.view_business')
        self.client.force_login(staff)
        groups = {x['group'] for x in self.client.get(reverse('staffpanel:search'), {'q': 'مشتری'}).json()['results']}
        self.assertNotIn('مشتریان', groups)


# ── Banners, marketing ──────────────────────────────────────────────────────

@override_settings(MEDIA_ROOT=MEDIA)
class BannerTests(PanelTestCase):
    def setUp(self):
        super().setUp()
        self.group = BannerCategoryGroup.objects.create(key=BannerCategoryGroup._meta.get_field('key').choices[0][0])

    def test_create_requires_exact_size(self):
        self.client.post(reverse('staffpanel:banner_create'), {'image': png(800, 400), 'group': self.group.pk, 'order': 0})
        self.assertFalse(Banner.objects.exists())
        self.client.post(reverse('staffpanel:banner_create'),
                         {'image': png(1200, 400), 'group': self.group.pk, 'order': 0, 'is_active': 'on'})
        self.assertEqual(Banner.objects.count(), 1)

    def test_toggle_and_link(self):
        banner = Banner.objects.create(group=self.group, image=png(1200, 400))
        self.client.post(reverse('staffpanel:banner_toggle', args=[banner.pk]))
        banner.refresh_from_db()
        self.assertFalse(banner.is_active)
        self.client.post(reverse('staffpanel:banner_link'), {'banner_link_url': 'https://noobatyar.ir/plans'})
        self.assertEqual(SiteSetting.load().banner_link_url, 'https://noobatyar.ir/plans')


class MarketingTests(PanelTestCase):
    def test_today_tab_and_update(self):
        today = timezone.localdate()
        due = marketing_services.create_lead(title='کلینیک', category='DOCTOR', phone='1', address='a',
                                             next_follow_up=today)
        marketing_services.create_lead(title='بعدا', category='DOCTOR', phone='2', address='a',
                                       next_follow_up=today + timedelta(days=3))
        r = self.client.get(reverse('staffpanel:marketing'))
        self.assertEqual([lead.pk for lead in r.context['page_obj'].object_list], [due.pk])

        self.client.post(reverse('staffpanel:lead_update', args=[due.pk]), {
            'status': MarketingLead.INTERESTED, 'next_follow_up': str(today + timedelta(days=1)), 'note': 'زنگ بزن'})
        due.refresh_from_db()
        self.assertEqual(due.status, MarketingLead.INTERESTED)
        self.assertEqual(due.assigned_to, self.admin)
        self.assertEqual(LeadNote.objects.get(lead=due).text, 'زنگ بزن')

    def test_closing_clears_follow_up(self):
        lead = marketing_services.create_lead(title='x', category='DOCTOR', phone='1', address='a',
                                              next_follow_up=timezone.localdate())
        self.client.post(reverse('staffpanel:lead_update', args=[lead.pk]),
                         {'status': MarketingLead.JOINED, 'next_follow_up': '2030-01-01'})
        lead.refresh_from_db()
        self.assertIsNone(lead.next_follow_up)


# ── Query counts ────────────────────────────────────────────────────────────

class QueryCountTests(PanelTestCase):
    """A list page costs the same number of queries for 2 rows as for 6."""

    def _count(self, url):
        cache.delete(BADGE_CACHE_KEY)
        self.client.get(url)  # warm session/auth, and the badge cache
        with CaptureQueriesContext(connection) as ctx:
            self.assertEqual(self.client.get(url).status_code, 200)
        return len(ctx.captured_queries)

    def _seed(self, start, n):
        for i in range(start, start + n):
            biz = make_business(self.owner, f'کسب {i}', Business.MODERATION_PENDING)
            v = self.visitor(i, business=biz)
            r = Review.objects.create(business=biz, visitor=v, rating=4, text='خوب')
            ContentReport.objects.create(business=biz, reason='SPAM', review=r)
            PortfolioItem.objects.create(business=biz, image=f'{i}.jpg')
            SmsLog.objects.create(business=biz, visitor=v, message_text='x', status='SENT')
            marketing_services.create_lead(title=f'مرکز {i}', category='DOCTOR', phone='1', address='a',
                                           next_follow_up=timezone.localdate())

    def test_list_pages_do_not_grow_with_rows(self):
        urls = [reverse(n) for n in (
            'staffpanel:approvals', 'staffpanel:showcase', 'staffpanel:reviews', 'staffpanel:reports',
            'staffpanel:customers', 'staffpanel:businesses', 'staffpanel:marketing', 'staffpanel:sms',
            'staffpanel:finance')]
        self._seed(1, 2)
        small = {u: self._count(u) for u in urls}
        self._seed(10, 4)
        for url in urls:
            with self.subTest(url):
                self.assertEqual(self._count(url), small[url])

    def test_customers_page_query_budget(self):
        self._seed(1, 3)
        self.client.get(reverse('staffpanel:customers'))
        # session, user, tab counts, page count, page rows.
        with self.assertNumQueries(5):
            self.client.get(reverse('staffpanel:customers'))

    def test_reviews_page_query_budget(self):
        self._seed(1, 3)
        self.client.get(reverse('staffpanel:reviews'))
        # session, user, tab counts, page count, page rows.
        with self.assertNumQueries(5):
            self.client.get(reverse('staffpanel:reviews'))
