"""Geo helpers for the public directory search (audit R9)."""
import math

from django.db.models import ExpressionWrapper, F, FloatField, Q

# Upper bound on a geo search: keeps the SQL bounding box (and the sort over
# it) small even for a dense city.
MAX_RADIUS_KM = 100.0

# Kilometres per degree of latitude (and of longitude at the equator).
_KM_PER_DEG = 111.195


def haversine_km(lat1, lon1, lat2, lon2):
    """Calculate the great circle distance between two points on the earth in kilometers."""
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2.0) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(dlon / 2.0) ** 2)
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c


def geo_queryset(qs, lat, lng, radius_km, include_unlocated, sort):
    """Bounding box + radius + distance ordering, all in SQL.

    The distance is the squared equirectangular distance in degrees² with the
    longitude axis scaled by cos(lat0) (a Python constant): within the 100 km
    cap it agrees with haversine to well under 1 %, so the radius cut-off and
    the ordering can differ from the exact value only for rows that sit within
    that margin of the boundary / of each other. The exact haversine value is
    still what the response reports (computed for the page's rows only).
    """
    cos_lat0 = max(math.cos(math.radians(lat)), 0.01)
    dist2 = ExpressionWrapper(
        (F('latitude') - lat) * (F('latitude') - lat)
        + (F('longitude') - lng) * (F('longitude') - lng) * (cos_lat0 * cos_lat0),
        output_field=FloatField(),
    )
    qs = qs.annotate(_dist2=dist2)

    if radius_km is not None and radius_km > 0:
        lat_delta = (radius_km / 110.5) * 1.05
        lng_delta = (radius_km / (111.32 * cos_lat0)) * 1.05
        bbox_q = Q(
            latitude__gte=lat - lat_delta, latitude__lte=lat + lat_delta,
            longitude__gte=lng - lng_delta, longitude__lte=lng + lng_delta,
        ) & Q(_dist2__lte=(radius_km / _KM_PER_DEG) ** 2)
        if include_unlocated:
            bbox_q |= Q(latitude__isnull=True) | Q(longitude__isnull=True)
        qs = qs.filter(bbox_q)

    nearest_first = F('_dist2').asc(nulls_last=True)
    if sort == 'distance':
        return qs.order_by(nearest_first, '-is_claimed', '-created_at', '-id')
    # Default: 'recommended' — claimed first, then nearest.
    return qs.order_by('-is_claimed', nearest_first, '-id')
