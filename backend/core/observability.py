"""Structured logging helpers: JSON formatter, request-id/access-log middleware
and an opt-in slow-query logger. Stdlib only, no external service."""
import contextvars
import json
import logging
import re
import time
import uuid

from asgiref.sync import iscoroutinefunction, markcoroutinefunction
from django.conf import settings
from django.db import connection

access_logger = logging.getLogger('nobatyar.request')
slow_logger = logging.getLogger('nobatyar.slowquery')

_SAFE_ID = re.compile(r'^[A-Za-z0-9._\-]{1,100}$')

_STD_ATTRS = set(logging.makeLogRecord({}).__dict__) | {'message', 'asctime'}


_JSON_SAFE = (str, int, float, bool, type(None))
_DROP_EXTRAS = {'request', 'server_time'}

request_id_var = contextvars.ContextVar('request_id', default=None)


class RequestIdFilter(logging.Filter):
    """Stamps every record with the current request id (if any)."""

    def filter(self, record):
        if not getattr(record, 'request_id', None):
            rid = request_id_var.get()
            if rid:
                record.request_id = rid
        return True


class JsonFormatter(logging.Formatter):
    def format(self, record):
        data = {
            'ts': self.formatTime(record, '%Y-%m-%dT%H:%M:%S%z'),
            'level': record.levelname,
            'logger': record.name,
            'msg': record.getMessage(),
        }
        for k, v in record.__dict__.items():
            if (k in _STD_ATTRS or k.startswith('_') or k in _DROP_EXTRAS
                    or not isinstance(v, _JSON_SAFE)):
                continue
            data[k] = v
        if record.exc_info:
            data['exc'] = self.formatException(record.exc_info)
        return json.dumps(data, ensure_ascii=False, default=str)


def safe_path(request):
    """Route pattern when resolved (no secrets), else path with long
    segments redacted (e.g. the Bale webhook secret)."""
    match = getattr(request, 'resolver_match', None)
    if match is not None and match.route:
        return '/' + match.route.lstrip('/')
    return '/'.join('<redacted>' if len(seg) > 20 else seg
                    for seg in request.path.split('/'))


class RequestLogMiddleware:
    """Sets X-Request-ID (reusing a sane inbound one) and logs one line per
    request with route, status, duration_ms and user id when known.
    Works for sync and async stacks."""

    sync_capable = True
    async_capable = True
    skip_paths = ('/healthz', '/readyz', '/api/healthz', '/api/readyz')

    def __init__(self, get_response):
        self.get_response = get_response
        self.is_async = iscoroutinefunction(get_response)
        if self.is_async:
            markcoroutinefunction(self)

    @staticmethod
    def _start(request):
        inbound = request.META.get('HTTP_X_REQUEST_ID', '')
        rid = inbound if _SAFE_ID.match(inbound) else uuid.uuid4().hex
        request.request_id = rid
        return rid, request_id_var.set(rid), time.monotonic()

    @staticmethod
    def _finish(request, rid, token, start, status):
        try:
            if request.path not in RequestLogMiddleware.skip_paths:
                user = getattr(request, 'user', None)
                uid = user.pk if user is not None and getattr(user, 'is_authenticated', False) else None
                path = safe_path(request)
                access_logger.info(
                    '%s %s %s', request.method, path, status,
                    extra={
                        'request_id': rid, 'method': request.method,
                        'path': path, 'status': status,
                        'duration_ms': round((time.monotonic() - start) * 1000, 1),
                        'user_id': uid,
                    })
        finally:
            request_id_var.reset(token)

    def __call__(self, request):
        if self.is_async:
            return self.__acall__(request)
        rid, token, start = self._start(request)
        status = 500
        slow_cm = _slow_query_wrapper(rid) if getattr(settings, 'SLOW_QUERY_LOG', False) else None
        try:
            if slow_cm:
                with slow_cm:
                    response = self.get_response(request)
            else:
                response = self.get_response(request)
            status = response.status_code
            response['X-Request-ID'] = rid
            return response
        finally:
            self._finish(request, rid, token, start, status)

    async def __acall__(self, request):
        # Slow-query logging is sync-only: DB work in async views runs on
        # other threads where this connection wrapper does not apply.
        rid, token, start = self._start(request)
        status = 500
        try:
            response = await self.get_response(request)
            status = response.status_code
            response['X-Request-ID'] = rid
            return response
        finally:
            self._finish(request, rid, token, start, status)


def _slow_query_wrapper(request_id):
    threshold = getattr(settings, 'SLOW_QUERY_MS', 200)

    def wrapper(execute, sql, params, many, context):
        t0 = time.monotonic()
        try:
            return execute(sql, params, many, context)
        finally:
            ms = (time.monotonic() - t0) * 1000
            if ms >= threshold:
                slow_logger.warning(
                    'slow query %.0f ms', ms,
                    extra={'request_id': request_id, 'duration_ms': round(ms, 1),
                           'sql': sql[:500]})

    return connection.execute_wrapper(wrapper)
