"""Structured logging helpers: JSON formatter, request-id/access-log middleware
and an opt-in slow-query logger. Stdlib only, no external service."""
import json
import logging
import re
import time
import uuid

from django.conf import settings
from django.db import connection

access_logger = logging.getLogger('nobatyar.request')
slow_logger = logging.getLogger('nobatyar.slowquery')

_SAFE_ID = re.compile(r'^[A-Za-z0-9._\-]{1,100}$')

_STD_ATTRS = set(logging.makeLogRecord({}).__dict__) | {'message', 'asctime'}


class JsonFormatter(logging.Formatter):
    def format(self, record):
        data = {
            'ts': self.formatTime(record, '%Y-%m-%dT%H:%M:%S%z'),
            'level': record.levelname,
            'logger': record.name,
            'msg': record.getMessage(),
        }
        for k, v in record.__dict__.items():
            if k not in _STD_ATTRS and not k.startswith('_'):
                data[k] = v
        if record.exc_info:
            data['exc'] = self.formatException(record.exc_info)
        return json.dumps(data, ensure_ascii=False, default=str)


class RequestLogMiddleware:
    """Sets X-Request-ID (reusing a sane inbound one) and logs one line per
    request with path, status, duration_ms and user id when known."""

    skip_paths = ('/healthz', '/readyz', '/api/healthz', '/api/readyz')

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        inbound = request.META.get('HTTP_X_REQUEST_ID', '')
        rid = inbound if _SAFE_ID.match(inbound) else uuid.uuid4().hex
        request.request_id = rid
        start = time.monotonic()
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
            if request.path not in self.skip_paths:
                user = getattr(request, 'user', None)
                uid = user.pk if user is not None and getattr(user, 'is_authenticated', False) else None
                access_logger.info(
                    '%s %s %s', request.method, request.path, status,
                    extra={
                        'request_id': rid, 'method': request.method,
                        'path': request.path, 'status': status,
                        'duration_ms': round((time.monotonic() - start) * 1000, 1),
                        'user_id': uid,
                    })


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
