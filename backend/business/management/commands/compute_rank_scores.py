"""Nightly: python manage.py compute_rank_scores"""
from datetime import timedelta

from django.core.management.base import BaseCommand
from django.db.models import Count
from django.utils import timezone

from appointment.models import Appointment
from business import cache as public_cache
from business.models import Business
from business.ranking import RankInputs, _portfolio_counts, rank_score

CHUNK = 500


class Command(BaseCommand):
    help = 'Recompute Business.rank_score for every business.'

    def handle(self, *args, **opts):
        now = timezone.now()
        recent = dict(
            Appointment.objects.filter(appointment_date__gte=now - timedelta(days=30),
                                       appointment_date__lte=now)
            .exclude(status__in=['CANCELLED', 'LOCKED'])
            .values('business_id').annotate(n=Count('id')).values_list('business_id', 'n')
        )
        portfolio = _portfolio_counts()
        batch, total = [], 0
        for b in Business.objects.only(
            'id', 'logo', 'services', 'city', 'latitude', 'longitude',
            'rating_avg', 'rating_count', 'rank_score', 'rank_updated_at',
        ).iterator(chunk_size=CHUNK):
            b.rank_score = rank_score(RankInputs(
                rating_avg=b.rating_avg, rating_count=b.rating_count,
                has_logo=bool(b.logo), has_services=bool(b.services),
                has_city=bool((b.city or '').strip()),
                has_pin=b.latitude is not None and b.longitude is not None,
                portfolio_count=None if portfolio is None else portfolio.get(b.id, 0),
                appointments_30d=recent.get(b.id, 0),
                promo_multiplier=1.0,  # placeholder: paid promotion
            ))
            b.rank_updated_at = now
            batch.append(b)
            if len(batch) >= CHUNK:
                Business.objects.bulk_update(batch, ['rank_score', 'rank_updated_at'])
                total += len(batch)
                batch = []
        if batch:
            Business.objects.bulk_update(batch, ['rank_score', 'rank_updated_at'])
            total += len(batch)
        public_cache.bump_all()  # list order changed for every page
        self.stdout.write(f'ranked {total} businesses')
