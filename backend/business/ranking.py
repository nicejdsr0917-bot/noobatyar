"""Directory ranking score (P2.2.5).

``rank_score`` is a pure function of :class:`RankInputs` so the formula is
unit-testable without a database; ``gather_inputs`` / the ``compute_rank_scores``
command feed it. Score is 0-100 times the promo multiplier.

    rating      40  Bayesian average (prior mean 3.5 worth 5 votes) / 5
    reviews     15  log-scaled review count, saturating at 50
    completeness 20 logo / services / city / map pin / portfolio (portfolio only
                    counts when that app exists, so scores stay comparable
                    before and after it ships)
    activity    25  log-scaled appointments in the last 30 days, saturating at 60
"""
import math
from dataclasses import dataclass
from typing import Optional

PRIOR_MEAN = 3.5
PRIOR_WEIGHT = 5
REVIEWS_SATURATION = 50
ACTIVITY_SATURATION = 60


@dataclass(frozen=True)
class RankInputs:
    rating_avg: float = 0.0
    rating_count: int = 0
    has_logo: bool = False
    has_services: bool = False
    has_city: bool = False
    has_pin: bool = False
    # None = the portfolio feature is not installed (criterion not counted).
    portfolio_count: Optional[int] = None
    appointments_30d: int = 0
    promo_multiplier: float = 1.0


def bayesian_rating(avg, count):
    count = max(int(count or 0), 0)
    return (PRIOR_WEIGHT * PRIOR_MEAN + float(avg or 0) * count) / (PRIOR_WEIGHT + count)


def _log_scale(n, saturation):
    return min(math.log1p(max(n, 0)) / math.log1p(saturation), 1.0)


def completeness(i: RankInputs) -> float:
    checks = [i.has_logo, i.has_services, i.has_city, i.has_pin]
    if i.portfolio_count is not None:
        checks.append(i.portfolio_count > 0)
    return sum(1 for c in checks if c) / len(checks)


def rank_score(i: RankInputs) -> float:
    base = (
        40 * (bayesian_rating(i.rating_avg, i.rating_count) / 5)
        + 15 * _log_scale(i.rating_count, REVIEWS_SATURATION)
        + 20 * completeness(i)
        + 25 * _log_scale(i.appointments_30d, ACTIVITY_SATURATION)
    )
    return round(base * max(i.promo_multiplier, 0), 4)


def _portfolio_counts():
    """{business_id: approved portfolio item count}; None if showcase is absent."""
    from django.apps import apps
    from django.db.models import Count
    if not apps.is_installed('showcase'):
        return None
    try:
        from showcase.models import ModerationStatus, PortfolioItem
        rows = (PortfolioItem.objects.filter(status=ModerationStatus.APPROVED)
                .values('business_id').annotate(n=Count('id')))
        return {r['business_id']: r['n'] for r in rows}
    except Exception:  # never let an optional app break the nightly job
        return None
    return None
