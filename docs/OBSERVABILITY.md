# Observability (structured logs only, no Sentry)

- **Logs**: JSON lines on stdout when `DEBUG=False` (override: `LOG_JSON=True/False`); readable text in DEBUG. `LOG_LEVEL` sets the level.
- **Request log**: `core.observability.RequestLogMiddleware` (first middleware) logs one line per request on logger `nobatyar.request` with `request_id, method, path, status, duration_ms, user_id`, and sets `X-Request-ID` (an inbound `X-Request-ID` matching `[A-Za-z0-9._-]{1,100}` is reused, so nginx can forward its own). Probe paths are not logged.
- **Slow queries**: `SLOW_QUERY_LOG=True` enables logger `nobatyar.slowquery` (WARNING, with `sql` truncated to 500 chars and `request_id`) for queries >= `SLOW_QUERY_MS` (default 200). Off by default.
- **Health**: `GET /healthz` liveness (no DB). `GET /readyz` checks DB (`SELECT 1`) and a Redis cache round-trip; 503 with per-check status on failure. Plain Django views, so no auth or throttling. Aliases under `/api/healthz` and `/api/readyz` because nginx only proxies `/api`, `/admin`, ... — add a `location = /healthz` if you want the bare path externally.
- **Zibal**: no real merchant id is hard-coded any more. `ZIBAL_MERCHANT_ID` must be set in prod env (DEBUG defaults to sandbox `zibal`).
