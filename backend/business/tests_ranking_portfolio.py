from django.test import TestCase

from business.ranking import _portfolio_counts


class PortfolioCountsTests(TestCase):
    def test_reads_showcase_app(self):
        # Installed and queryable: an empty dict, not None ("app missing").
        self.assertEqual(_portfolio_counts(), {})
