"""Short-lived response cache for the anonymous public directory (P1.2).

What is cached
    * ``list``   - one page of the public list/search, for requests without
      coordinates (a position makes every response unique, and the exact
      per-viewer distance is computed after the fact anyway).
    * ``detail`` - one business payload, *without* the viewer's distance; the
      distance is added per request from the cached pin, so ``?lat&lng``
      requests are served from the same entry.

What is never cached
    Any request carrying an ``Authorization`` header (JWT user or Visitor):
    the payload depends on the caller (``allow_anonymous_view`` masking), so
    only plain anonymous traffic shares entries. Entries hold the same
    already-masked payload an anonymous caller gets, never owner-private
    fields. 404s and errors are not cached.

Invalidation
    Keys carry version stamps instead of being deleted one by one:
    ``all`` (policy / insurance / subscription changes -> everything),
    ``list`` (any Business change -> every list page) and ``biz:<id>`` (that
    business' detail). A bump stores ``time.time_ns()``, so a stamp that was
    evicted from Redis restarts at a fresh value and can never collide with an
    older cached entry. The bumps are wired in business/signals.py. What is not
    covered (queryset ``update()`` without signals, owner quota counters that
    change on every booking) is bounded by the TTL.
"""
import hashlib
import logging
import time

from django.conf import settings
from django.core.cache import cache

logger = logging.getLogger(__name__)

LIST_TTL = 60
DETAIL_TTL = 300

_PREFIX = 'pubdir'
_ALL = f'{_PREFIX}:v:all'
_LIST = f'{_PREFIX}:v:list'


def _biz_stamp_key(business_id):
    return f'{_PREFIX}:v:biz:{business_id}'


def enabled():
    return getattr(settings, 'PUBLIC_DIRECTORY_CACHE_ENABLED', True)


def is_cacheable_request(request):
    """Only anonymous traffic: any credential may change the payload."""
    return enabled() and not request.META.get('HTTP_AUTHORIZATION')


def _stamps(*keys):
    """Current stamp of every key; a missing one is created (``add`` is atomic)."""
    try:
        got = cache.get_many(list(keys))
        missing = [k for k in keys if k not in got]
        for k in missing:
            cache.add(k, time.time_ns(), None)
        if missing:
            got.update(cache.get_many(missing))
        return [got.get(k, 0) for k in keys]
    except Exception:  # cache outage must never break the endpoint
        logger.warning('public cache stamp read failed', exc_info=True)
        return [0 for _ in keys]


def list_key(params):
    all_v, list_v = _stamps(_ALL, _LIST)
    digest = hashlib.sha1(repr(params).encode('utf-8')).hexdigest()
    return f'{_PREFIX}:list:{all_v}:{list_v}:{digest}'


def detail_key(business_id):
    all_v, biz_v = _stamps(_ALL, _biz_stamp_key(business_id))
    return f'{_PREFIX}:detail:{all_v}:{biz_v}:{business_id}'


def get(key):
    try:
        return cache.get(key)
    except Exception:
        return None


def put(key, value, ttl):
    try:
        cache.set(key, value, ttl)
    except Exception:
        logger.warning('public cache write failed', exc_info=True)


def _bump(*keys):
    try:
        now = time.time_ns()
        for i, k in enumerate(keys):
            cache.set(k, now + i, None)
    except Exception:
        logger.warning('public cache invalidation failed', exc_info=True)


def bump_business(business_id):
    """A business changed: its detail and every list page are stale."""
    _bump(_biz_stamp_key(business_id), _LIST)


def bump_all():
    """Something every payload depends on changed (policy, plans, insurances)."""
    _bump(_ALL)
