import json
import logging
import subprocess
import sys
from unittest import mock

from django.db import OperationalError
from django.test import TestCase, override_settings


class RequestLogTests(TestCase):
    def test_request_id_generated_and_reused(self):
        r = self.client.get('/healthz')
        self.assertEqual(len(r['X-Request-ID']), 32)
        r = self.client.get('/healthz', HTTP_X_REQUEST_ID='abc-123')
        self.assertEqual(r['X-Request-ID'], 'abc-123')
        r = self.client.get('/healthz', HTTP_X_REQUEST_ID='bad id\n')
        self.assertNotEqual(r['X-Request-ID'], 'bad id\n')

    def test_access_log_fields(self):
        with self.assertLogs('nobatyar.request', 'INFO') as cm:
            self.client.get('/api/version/changelog/')
        rec = cm.records[0]
        self.assertEqual(rec.path, '/api/version/changelog/')
        self.assertEqual(rec.status, 200)
        self.assertIsNone(rec.user_id)
        self.assertGreaterEqual(rec.duration_ms, 0)
        self.assertTrue(rec.request_id)

    def test_probes_not_logged(self):
        with self.assertNoLogs('nobatyar.request', 'INFO'):
            self.client.get('/healthz')

    @override_settings(SLOW_QUERY_LOG=True, SLOW_QUERY_MS=0)
    def test_slow_query_logged_when_enabled(self):
        with self.assertLogs('nobatyar.slowquery', 'WARNING') as cm:
            self.client.get('/api/version/changelog/')
        self.assertTrue(cm.records[0].sql)

    def test_slow_query_off_by_default(self):
        with self.assertNoLogs('nobatyar.slowquery', 'WARNING'):
            self.client.get('/api/version/changelog/')

    def test_json_formatter(self):
        from core.observability import JsonFormatter
        rec = logging.LogRecord('x', logging.INFO, 'f', 1, 'hi %s', ('there',), None)
        rec.request_id = 'r1'
        data = json.loads(JsonFormatter().format(rec))
        self.assertEqual(data['msg'], 'hi there')
        self.assertEqual(data['request_id'], 'r1')


class HealthTests(TestCase):
    def test_healthz_no_db(self):
        with self.assertNumQueries(0):
            r = self.client.get('/healthz')
        self.assertEqual(r.status_code, 200)

    def test_api_alias(self):
        self.assertEqual(self.client.get('/api/healthz').status_code, 200)

    def test_readyz_ok(self):
        r = self.client.get('/readyz')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['checks'], {'db': 'ok', 'redis': 'ok'})

    def test_readyz_db_down(self):
        with mock.patch('core.health.connection') as conn:
            conn.cursor.side_effect = OperationalError('down')
            r = self.client.get('/readyz')
        self.assertEqual(r.status_code, 503)
        self.assertEqual(r.json()['checks']['db'], 'fail')

    def test_readyz_redis_down(self):
        with mock.patch('core.health._redis_ok', return_value=False):
            r = self.client.get('/readyz')
        self.assertEqual(r.status_code, 503)
        self.assertEqual(r.json()['checks']['redis'], 'fail')


class ZibalMerchantTests(TestCase):
    def _merchant(self, **env):
        # Set (not pop) the keys: settings loads .env.local without override, so
        # a popped key would be refilled from a developer's local file.
        env = {'ZIBAL_MERCHANT_ID': '', 'DEBUG': 'False', **env}
        code = ("import os\nos.environ.update(%r)\n"
                "from core import settings\nprint(settings.ZIBAL_MERCHANT_ID)" % env)
        out = subprocess.run([sys.executable, '-c', code], capture_output=True, text=True,
                             cwd=str(__import__('django').conf.settings.BASE_DIR))
        return out.stdout.strip().splitlines()[-1] if out.stdout.strip() else ''

    def test_debug_defaults_to_sandbox(self):
        self.assertEqual(self._merchant(DEBUG='True'), 'zibal')

    def test_no_hardcoded_real_merchant_in_prod(self):
        self.assertEqual(self._merchant(SECRET_KEY='x'), '')

    def test_env_wins(self):
        self.assertEqual(self._merchant(SECRET_KEY='x', ZIBAL_MERCHANT_ID='m1'), 'm1')


class HardeningTests(TestCase):
    def test_bale_secret_never_logged(self):
        secret = 'S3CRET' * 6
        with self.assertLogs('nobatyar.request', 'INFO') as cm:
            self.client.post(f'/api/bale/webhook/{secret}/', {})
            self.client.get(f'/nope/{secret}/')
        for rec in cm.records:
            self.assertNotIn(secret, rec.getMessage())
            self.assertNotIn(secret, rec.path)

    def test_json_formatter_drops_request_and_unsafe_extras(self):
        from core.observability import JsonFormatter
        rec = logging.LogRecord('x', logging.INFO, 'f', 1, 'm', (), None)
        rec.request = object()
        rec.blob = {'a': 1}
        rec.status_code = 404
        out = json.loads(JsonFormatter().format(rec))
        self.assertNotIn('request', out)
        self.assertNotIn('blob', out)
        self.assertEqual(out['status_code'], 404)

    def test_request_id_filter_stamps_any_record(self):
        from core.observability import RequestIdFilter, request_id_var
        tok = request_id_var.set('rid-9')
        try:
            rec = logging.LogRecord('x', logging.INFO, 'f', 1, 'm', (), None)
            RequestIdFilter().filter(rec)
        finally:
            request_id_var.reset(tok)
        self.assertEqual(rec.request_id, 'rid-9')

    def test_async_middleware(self):
        import asyncio
        from django.http import HttpResponse
        from django.test import RequestFactory
        from core.observability import RequestLogMiddleware

        async def view(request):
            return HttpResponse('ok')

        mw = RequestLogMiddleware(view)
        self.assertTrue(asyncio.iscoroutinefunction(mw))
        resp = asyncio.run(mw(RequestFactory().get('/x')))
        self.assertTrue(resp['X-Request-ID'])

    def test_readyz_db_timeout(self):
        import time
        with mock.patch('core.health._db_ok', side_effect=lambda: time.sleep(0.5)), \
                mock.patch('core.health.DB_TIMEOUT', 0.05):
            r = self.client.get('/readyz')
        self.assertEqual(r.status_code, 503)
        self.assertEqual(r.json()['checks']['db'], 'fail')

    def test_zibal_system_check(self):
        from core.checks import zibal_merchant_check
        with override_settings(DEBUG=False, ZIBAL_MERCHANT_ID=''):
            self.assertEqual(zibal_merchant_check(None)[0].id, 'core.W001')
        with override_settings(DEBUG=False, ZIBAL_MERCHANT_ID='m'):
            self.assertEqual(zibal_merchant_check(None), [])
        with override_settings(DEBUG=True, ZIBAL_MERCHANT_ID=''):
            self.assertEqual(zibal_merchant_check(None), [])
