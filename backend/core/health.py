"""Liveness/readiness probes. Plain Django views: no DRF, so no auth or
throttling applies."""
import logging
import uuid

from django.core.cache import cache
from django.db import connection
from django.http import JsonResponse

logger = logging.getLogger(__name__)


def healthz(request):
    """Liveness: process is up. Touches nothing."""
    return JsonResponse({'status': 'ok'})


def _redis_ok():
    # The cache is configured with IGNORE_EXCEPTIONS, so a failed set/get does
    # not raise: verify a round-trip instead.
    key = f'readyz:{uuid.uuid4().hex}'
    cache.set(key, '1', 10)
    ok = cache.get(key) == '1'
    cache.delete(key)
    return ok


def readyz(request):
    """Readiness: DB and Redis reachable, else 503."""
    checks = {}
    try:
        with connection.cursor() as cur:
            cur.execute('SELECT 1')
            cur.fetchone()
        checks['db'] = 'ok'
    except Exception:
        logger.exception('readyz: db check failed')
        checks['db'] = 'fail'
    try:
        checks['redis'] = 'ok' if _redis_ok() else 'fail'
    except Exception:
        logger.exception('readyz: redis check failed')
        checks['redis'] = 'fail'
    healthy = all(v == 'ok' for v in checks.values())
    return JsonResponse({'status': 'ok' if healthy else 'fail', 'checks': checks},
                        status=200 if healthy else 503)
