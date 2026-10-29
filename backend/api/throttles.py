"""
api/throttles.py

HTTP-layer rate limiting for the OTP / SMS-triggering endpoints.

The OTP service (api/services/otp.py) enforces the per-phone cooldown, the
daily send cap and per-code guess limits (see its docstring for the full
policy). Phone-based throttles here are keyed on (phone, IP) so a third party
can never throttle a victim's own device. The per-IP throttle adds an outer, per-client-IP limit so a single source cannot
sweep across many phone numbers to trigger a flood of SMS (SMS-bombing) — the
counter is shared across workers via the Redis cache backend.
"""

from rest_framework.settings import api_settings
from rest_framework.throttling import AnonRateThrottle, BaseThrottle, SimpleRateThrottle

from .phone import normalize_phone


class OTPRateThrottle(AnonRateThrottle):
    """Per-IP throttle for OTP/SMS endpoints. Rate comes from THROTTLE_RATES['otp']."""
    scope = 'otp'


class LoginRateThrottle(AnonRateThrottle):
    """Per-IP throttle for password login — the outer layer. The inner one is
    the per-phone failure lockout in api/views.py, which stops a guesser who
    rotates IPs against a single account."""
    scope = 'login'


def client_ip(request) -> str:
    """Client IP, resolved the way DRF throttles do (spoof-resistant).

    Honours REST_FRAMEWORK['NUM_PROXIES']: unset/0 -> REMOTE_ADDR; N>0 -> the
    N-th entry from the right of X-Forwarded-For (the one our own trusted proxy
    appended). The left-most XFF entry is client-controlled and never trusted.
    """
    num_proxies = api_settings.NUM_PROXIES
    meta = request.META
    remote = (meta.get('REMOTE_ADDR') or '').strip()
    if not num_proxies:
        return remote
    xff = meta.get('HTTP_X_FORWARDED_FOR')
    if not xff:
        return remote
    addrs = [a.strip() for a in xff.split(',') if a.strip()]
    if len(addrs) < num_proxies:
        return remote
    return addrs[-num_proxies]


class _DefaultRateMixin:
    """Use THROTTLE_RATES[scope] if configured, else the class-level default."""
    default_rate = None

    def get_rate(self):
        return api_settings.DEFAULT_THROTTLE_RATES.get(self.scope) or self.default_rate


class OTPVerifyThrottle(_DefaultRateMixin, AnonRateThrottle):
    """Per-IP limit on OTP verification attempts (scope 'otp_verify')."""
    scope = 'otp_verify'
    # Generous per IP: Iranian mobile carriers put many users behind one CGNAT
    # address. The per-phone throttle is what actually stops guessing.
    default_rate = '30/min'


def _phone_from(request) -> str:
    try:
        raw = request.data.get('phone') or ''
    except Exception:
        raw = ''
    return normalize_phone(str(raw))


class OTPVerifyPhoneThrottle(_DefaultRateMixin, SimpleRateThrottle):
    """Verify attempts per (phone, client IP), 10/min.

    Keyed on the IP too, so an attacker hammering a victim's number from their
    own address only exhausts their own budget, not the victim's. Guessing
    itself is capped by the per-code attempts counter in the OTP service.
    """
    scope = 'otp_verify_phone'
    default_rate = '10/min'

    def get_cache_key(self, request, view):
        phone = _phone_from(request)
        if not phone:
            return None
        ident = f"{phone}:{client_ip(request)}"
        return self.cache_format % {'scope': self.scope, 'ident': ident}


class OTPSendPhoneThrottle(BaseThrottle):
    """Per-(phone, client IP) hourly send quota (5/hour), check-only.

    It does NOT record the request: the OTP service increments the counter only
    after the SMS provider accepted the send (see api/services/otp.py), so
    failed sends never lock a user out. The global per-phone daily cap lives in
    the service too.
    """

    def allow_request(self, request, view):
        phone = _phone_from(request)
        if not phone:
            return True
        from .services.otp import send_ip_exhausted
        return not send_ip_exhausted(phone, client_ip(request))

    def wait(self):
        return 3600
