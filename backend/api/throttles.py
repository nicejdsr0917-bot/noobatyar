"""
api/throttles.py

HTTP-layer rate limiting for the OTP / SMS-triggering endpoints.

The OTP service (api/services/otp.py) already enforces a per-phone 3-minute
cooldown and a 24h ban, but that is keyed on the phone *inside* the request.
This throttle adds an outer, per-client-IP limit so a single source cannot
sweep across many phone numbers to trigger a flood of SMS (SMS-bombing) — the
counter is shared across workers via the Redis cache backend.
"""

from rest_framework.settings import api_settings
from rest_framework.throttling import AnonRateThrottle, SimpleRateThrottle

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


class _PerPhoneThrottle(_DefaultRateMixin, SimpleRateThrottle):
    """Keyed on the normalised phone in the request body (works across IPs)."""

    def get_cache_key(self, request, view):
        try:
            raw = request.data.get('phone') or ''
        except Exception:
            raw = ''
        phone = normalize_phone(str(raw))
        if not phone:
            return None
        return self.cache_format % {'scope': self.scope, 'ident': phone}


class OTPVerifyPhoneThrottle(_PerPhoneThrottle):
    scope = 'otp_verify_phone'
    default_rate = '10/min'


class OTPSendPhoneThrottle(_PerPhoneThrottle):
    scope = 'otp_send_phone'
    default_rate = '5/hour'
