"""Liveness/readiness probes. Plain Django views: no DRF, so no auth or
throttling applies."""
import logging
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeout
import uuid

from django.core.cache import cache
from django.db import connection
from django.http import JsonResponse

logger = logging.getLogger(__name__)


def healthz(request):
    """Liveness: process is up. Touches nothing."""
    return JsonResponse({'status': 'ok'})


DB_TIMEOUT = 2.0
REDIS_TIMEOUT = 1.0


def _bounded(fn, timeout):
    """Run fn in a worker thread; a hung backend counts as failure instead of
    hanging the probe (the worker is abandoned, not killed)."""
    ex = ThreadPoolExecutor(max_workers=1)
    try:
        return ex.submit(fn).result(timeout=timeout)
    except FutureTimeout:
        logger.error('readyz: %s timed out after %ss', fn.__name__, timeout)
        return False
    finally:
        ex.shutdown(wait=False)


def _db_ok():
    try:
        with connection.cursor() as cur:
            cur.execute('SELECT 1')
            cur.fetchone()
        return True
    finally:
        connection.close()


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
        checks['db'] = 'ok' if _bounded(_db_ok, DB_TIMEOUT) else 'fail'
    except Exception:
        logger.exception('readyz: db check failed')
        checks['db'] = 'fail'
    try:
        checks['redis'] = 'ok' if _bounded(_redis_ok, REDIS_TIMEOUT) else 'fail'
    except Exception:
        logger.exception('readyz: redis check failed')
        checks['redis'] = 'fail'
    healthy = all(v == 'ok' for v in checks.values())
    return JsonResponse({'status': 'ok' if healthy else 'fail', 'checks': checks},
                        status=200 if healthy else 503)
