"""Iran's provinces and cities, as Divar lists them.

``data/iran_cities.json`` is Divar's city list (ids, names, province grouping
and display order unchanged), with each city's centroid and radius. The owner
and client apps ship the same file, so a ``Business.city_code`` picked in the
owner app is the same id the client app filters by.
"""
import json
import math
from functools import lru_cache
from pathlib import Path

from django.db.models import Q

_DATA = Path(__file__).resolve().parent / 'data' / 'iran_cities.json'


@lru_cache(maxsize=1)
def cities_by_id():
    """``{city_id: {'name', 'province', 'lat', 'lng', 'radiusKm'}}``."""
    data = json.loads(_DATA.read_text(encoding='utf-8'))
    return {
        city['id']: {**city, 'province': province['name']}
        for province in data['provinces']
        for city in province['cities']
    }


def get_city(city_id):
    return cities_by_id().get(city_id)


def city_filter(city_id):
    """Businesses in this city: those the owner put there (``city_code``),
    plus pinned businesses with no city yet whose pin is within the city's
    radius — so a listing from before city codes existed still shows up."""
    city = get_city(city_id)
    if city is None:
        return Q(pk__in=[])
    radius_km = max(city['radiusKm'], 5.0)
    lat_delta = radius_km / 110.5
    lng_delta = radius_km / (111.32 * max(math.cos(math.radians(city['lat'])), 0.01))
    in_radius = Q(
        city_code__isnull=True,
        latitude__gte=city['lat'] - lat_delta,
        latitude__lte=city['lat'] + lat_delta,
        longitude__gte=city['lng'] - lng_delta,
        longitude__lte=city['lng'] + lng_delta,
    )
    return Q(city_code=city_id) | in_radius
