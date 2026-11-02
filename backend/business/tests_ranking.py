from io import StringIO

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.core.management import call_command
from django.test import SimpleTestCase, TestCase
from django.utils import timezone

from appointment.models import Appointment
from business.models import Business
from business.ranking import RankInputs, bayesian_rating, completeness, rank_score
from visitor.models import Visitor

User = get_user_model()


class RankScoreFunctionTests(SimpleTestCase):
    def test_empty_profile_is_low_but_prior_rated(self):
        s = rank_score(RankInputs())
        self.assertAlmostEqual(s, 40 * 3.5 / 5, places=3)

    def test_bayesian_pulls_small_samples_to_prior(self):
        self.assertLess(bayesian_rating(5, 1), bayesian_rating(5, 50))
        self.assertAlmostEqual(bayesian_rating(0, 0), 3.5)

    def test_more_reviews_beats_one_perfect_review(self):
        one = rank_score(RankInputs(rating_avg=5, rating_count=1))
        many = rank_score(RankInputs(rating_avg=4.8, rating_count=40))
        self.assertGreater(many, one)

    def test_completeness_portfolio_only_when_installed(self):
        full = RankInputs(has_logo=True, has_services=True, has_city=True, has_pin=True)
        self.assertEqual(completeness(full), 1.0)
        self.assertEqual(completeness(RankInputs(**{**full.__dict__, 'portfolio_count': 0})), 0.8)

    def test_activity_monotonic_and_capped(self):
        a = rank_score(RankInputs(appointments_30d=5))
        b = rank_score(RankInputs(appointments_30d=500))
        c = rank_score(RankInputs(appointments_30d=5000))
        self.assertTrue(a < b)
        self.assertEqual(b, c)

    def test_promo_multiplier_and_upper_bound(self):
        top = RankInputs(5, 1000, True, True, True, True, 3, 1000)
        self.assertLessEqual(rank_score(top), 100)
        self.assertAlmostEqual(rank_score(RankInputs(**{**top.__dict__, 'promo_multiplier': 1.5})),
                               rank_score(top) * 1.5, places=2)


class RankCommandAndOrderingTests(TestCase):
    def setUp(self):
        cache.clear()
        self.owner = User.objects.create_user(phone='09120000600', password='x', name='o')

    def biz(self, title, **kw):
        return Business.objects.create(
            user=self.owner, title=title, phone='02100000000', address='t',
            default_service_duration=30, work_start_hour=9, work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED, **kw)

    def test_command_sets_scores_and_list_orders_by_rank(self):
        newer_weak = self.biz('weak')
        older_strong = self.biz('strong', city='تهران', services=['x'], rating_avg=5, rating_count=30)
        Business.objects.filter(pk=older_strong.pk).update(created_at=timezone.now() - timezone.timedelta(days=30))
        v = Visitor.objects.create(full_name='v', phone_number='09350000600')
        Appointment.objects.create(business=older_strong, visitor=v, appointment_date=timezone.now() - timezone.timedelta(days=2), status='COMPLETED')
        call_command('compute_rank_scores', stdout=StringIO())
        newer_weak.refresh_from_db(); older_strong.refresh_from_db()
        self.assertGreater(older_strong.rank_score, newer_weak.rank_score)
        self.assertIsNotNone(older_strong.rank_updated_at)
        r = self.client.get('/api/client/businesses/')
        titles = [x['title'] for x in r.json()['data']['results']]
        self.assertEqual(titles, ['strong', 'weak'])
