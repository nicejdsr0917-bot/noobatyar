"""
api/services/otp.py

Redis-backed OTP service.  All state lives in Django's cache framework
(configured as django-redis in production), making it stateless across
multiple workers and restarts.

Cache keys
----------
otp:code:{phone}          OTP value                  TTL = 3 min   (OTP_TTL)
otp:rate:{phone}          Cooldown flag              TTL = 3 min   (OTP_RATE_TTL)
otp:attempts:{phone}      Wrong-guess counter        TTL = 3 min   (OTP_TTL)
otp:sent_day:{phone}      Successful sends / day     TTL = 24 h    (OTP_DAILY_SEND_TTL)
otp:sent_hr:{phone}:{ip}  Successful sends / hour    TTL = 1 h     (per phone AND ip)

Abuse policy (no number can be locked out by a third party for long)
-------------------------------------------------------------------
+-----------------------------+-----------------------------+-----------------+
| Limit                       | Scope                       | Counts          |
+-----------------------------+-----------------------------+-----------------+
| 1 send / 3 min (cooldown)   | phone                       | every attempt   |
| 5 sends / hour              | (phone, client IP)          | successful only |
| 15 sends / day (SMS ceiling)| phone                       | successful only |
| 5 wrong guesses per code    | phone (code is then killed) | wrong guesses   |
| 10 verifies / min           | (phone, client IP)          | every request   |
| 30 verifies / min           | client IP                   | every request   |
+-----------------------------+-----------------------------+-----------------+

* Guessing is capped by the per-code attempts counter. When it is exhausted the
  code is invalidated and the cooldown stays active, so the next code can only
  be requested after the normal 3 minutes. There is NO multi-hour ban on a
  phone: such a ban could be triggered by anyone against any number.
* The per-phone SMS ceiling (daily cap) and the hourly cap only count sends the
  provider accepted, so a provider outage never locks a user out.
* The per-(phone, IP) limits mean an attacker cannot exhaust the victim's own
  budget from another address. The per-IP throttles (api/throttles.py) stop one
  source sweeping many numbers.
* Cache outage (django_redis IGNORE_EXCEPTIONS): cache.add/incr return None. send
  logs an ERROR and returns a distinct "service unavailable" error; verify treats
  a failed counter as exhausted attempts. Neither raises.
* Melipayamak's /api/send/otp/ endpoint generates the OTP itself and returns it
  in the response; we store THAT code so what the user receives is what we verify.
"""

import logging

from django.core.cache import cache
from django.conf import settings

logger = logging.getLogger(__name__)

# ── Configurable constants (override via settings if needed) ──────────────────
OTP_TTL           = getattr(settings, "OTP_TTL_SECONDS",      180)   # 3 min
OTP_RATE_TTL      = getattr(settings, "OTP_RATE_TTL_SECONDS",  180)  # 3 min cooldown
OTP_DAILY_SEND_TTL = getattr(settings, "OTP_DAILY_SEND_TTL",  86400) # daily send window
OTP_MAX_DAILY_SENDS = getattr(settings, "OTP_MAX_DAILY_SENDS",    15) # successful sends / phone / day
OTP_HOURLY_SEND_TTL = 3600
OTP_MAX_HOURLY_SENDS_PER_IP = getattr(settings, "OTP_MAX_HOURLY_SENDS_PER_IP", 5)  # per (phone, ip)
OTP_MAX_ATTEMPTS  = getattr(settings, "OTP_MAX_ATTEMPTS",         5)  # per-code verify tries


# ── Key helpers ────────────────────────────────────────────────────────────────

def _key_code(phone: str) -> str:
    return f"otp:code:{phone}"

def _key_rate(phone: str) -> str:
    return f"otp:rate:{phone}"

def _key_sent_day(phone: str) -> str:
    return f"otp:sent_day:{phone}"

def _key_sent_hour(phone: str, ip: str) -> str:
    return f"otp:sent_hr:{phone}:{ip or '-'}"

def _key_attempts(phone: str) -> str:
    return f"otp:attempts:{phone}"


# ── Public API ────────────────────────────────────────────────────────────────

def send_ip_exhausted(phone: str, ip: str) -> bool:
    """True if this (phone, ip) already had its hourly quota of delivered sends."""
    return (cache.get(_key_sent_hour(phone, ip), 0) or 0) >= OTP_MAX_HOURLY_SENDS_PER_IP


def _count_success(key: str, timeout: int) -> None:
    """Increment a successful-send counter; never raises (best effort on outage)."""
    if cache.add(key, 1, timeout=timeout) is False:
        try:
            cache.incr(key)
        except ValueError:
            cache.set(key, 1, timeout=timeout)


def send_otp(phone: str, ip: str = "") -> dict:
    """
    Generate an OTP and dispatch it (synchronously).

    ``ip`` is the caller's client IP; it scopes the hourly send quota so a third
    party cannot use up the real owner's budget from another address.

    Returns
    -------
    {"success": True}
        OTP was generated and SMS dispatch started.
    {"success": False, "error": str}
        Request rejected (cooldown, send cap, cache outage or provider failure).
    """
    # 0. Dev bypass (DEBUG only — settings forces OTP_DEV_CODE empty otherwise).
    #    Uses a fixed code, sends no SMS, and skips the cooldown and caps so a
    #    local test run isn't blocked between attempts.
    dev_code = getattr(settings, "OTP_DEV_CODE", "")

    # 1. SMS-bombing ceiling: successful sends per phone per day.
    if not dev_code:
        sent_today = cache.get(_key_sent_day(phone), 0) or 0
        if sent_today >= OTP_MAX_DAILY_SENDS:
            logger.warning("OTP daily send cap hit for %s****", phone[-4:])
            return {
                "success": False,
                "error": "سقف ارسال کد برای این شماره امروز پر شده است؛ فردا دوباره تلاش کنید",
            }

    # 2. Cooldown (3 min). Claimed atomically with cache.add BEFORE the slow
    #    (up to 10 s) HTTP call: a check-then-set would let parallel requests
    #    all pass the check and each trigger an SMS. add() succeeds for exactly one.
    #    add() returns False if the key exists, None if the cache is down.
    claimed = False
    if not dev_code:
        added = cache.add(_key_rate(phone), 1, timeout=OTP_RATE_TTL)
        if added is None:
            logger.error("OTP cache unavailable (cache.add returned None) — cannot send for %s****", phone[-4:])
            return {
                "success": False,
                "error": "سرویس موقتاً در دسترس نیست؛ کمی بعد دوباره تلاش کنید",
            }
        if not added:
            return {
                "success": False,
                "error": "لطفاً ۳ دقیقه صبر کنید و سپس دوباره درخواست کنید",
            }
        claimed = True

    # 3. Ask Melipayamak to send the OTP. IMPORTANT: the /api/send/otp/ endpoint
    #    GENERATES the code itself and returns it in the response — it does NOT
    #    send any text we supply. We must therefore store the code Melipayamak
    #    returns as the source of truth, otherwise every verification fails with
    #    "wrong code" (user receives Melipayamak's code, we stored our own).
    try:
        if dev_code:
            logger.warning("OTP dev bypass active — using fixed code for %s****", phone[-4:])
            code = dev_code
        else:
            code = _send_via_melipayamak(phone)
    except Exception:
        if claimed:
            cache.delete(_key_rate(phone))
        raise
    if not code:
        if claimed:
            # Send failed: release the claim so the user can retry right away.
            # Failed sends are not counted toward any cap.
            cache.delete(_key_rate(phone))
        return {
            "success": False,
            "error": "خطا در ارسال پیامک. لطفاً کمی بعد دوباره تلاش کنید",
        }

    # 4. Store the code Melipayamak actually sent; reset the attempts counter.
    cache.set(_key_code(phone),     str(code), timeout=OTP_TTL)
    cache.set(_key_attempts(phone), 0,         timeout=OTP_TTL)

    # 5. The provider accepted: only now count it toward the send caps.
    if not dev_code:
        _count_success(_key_sent_day(phone), OTP_DAILY_SEND_TTL)
        _count_success(_key_sent_hour(phone, ip), OTP_HOURLY_SEND_TTL)

    logger.info("OTP sent and stored for %s****", phone[-4:])
    return {"success": True}


def verify_otp(phone: str, code: str) -> dict:
    """
    Verify a submitted OTP code.

    Side effects
    ------------
    - On success  : clears code, rate, and attempt keys.
    - On failure  : increments the attempt counter. Once it exceeds
      OTP_MAX_ATTEMPTS the code is invalidated (the cooldown stays active).
    """
    # Dev bypass (DEBUG only — settings forces OTP_DEV_CODE empty otherwise): the
    # fixed code works for any number, whether or not "send" was pressed or the
    # code has expired, and a wrong guess is never counted.
    dev_code = getattr(settings, "OTP_DEV_CODE", "")
    if dev_code:
        if str(code) == dev_code:
            _invalidate_otp(phone)
            return {"success": True}
        return {"success": False, "error": "کد اشتباه است"}

    stored_code = cache.get(_key_code(phone))
    if not stored_code:
        logger.warning("OTP verify failed: no active code for %s****", phone[-4:])
        return {"success": False, "error": "کد منقضی شده یا ارسال نشده است"}

    # Count the attempt atomically BEFORE comparing, so parallel guesses can't
    # exceed OTP_MAX_ATTEMPTS (a read-modify-write would let them all through).
    attempts = _bump(_key_attempts(phone), OTP_TTL)

    if attempts > OTP_MAX_ATTEMPTS:
        # Too many tries — kill the code but keep the cooldown, so the next
        # code can only be requested after the normal wait (no long ban).
        _invalidate_otp(phone, keep_cooldown=True)
        return {"success": False, "error": "تعداد تلاش بیش از حد مجاز — لطفاً کد جدید دریافت کنید"}

    if stored_code != str(code):
        logger.warning("OTP verify failed: mismatch for %s**** (attempt %d)", phone[-4:], attempts)
        remaining = OTP_MAX_ATTEMPTS - attempts
        return {
            "success": False,
            "error": f"کد اشتباه است — {remaining} تلاش باقی مانده",
        }

    # ✓ Correct — clean up all related keys
    _invalidate_otp(phone)
    return {"success": True}


def _bump(key: str, timeout: int) -> int:
    """Atomic counter increment; returns the new value.

    On a cache outage incr returns None: treated as exhausted (never raises).
    """
    cache.add(key, 0, timeout=timeout)
    try:
        value = cache.incr(key)
    except ValueError:  # expired between add and incr
        cache.set(key, 1, timeout=timeout)
        return 1
    if value is None:
        logger.error("OTP cache unavailable (cache.incr returned None) for key %s", key.split(":")[1])
        return OTP_MAX_ATTEMPTS + 1
    return value


# ── Internal helpers ───────────────────────────────────────────────────────────

def _invalidate_otp(phone: str, keep_cooldown: bool = False) -> None:
    """Remove per-OTP keys (on success, or on max-attempt breach with keep_cooldown)."""
    cache.delete(_key_code(phone))
    if not keep_cooldown:
        cache.delete(_key_rate(phone))
    cache.delete(_key_attempts(phone))


def _send_via_melipayamak(phone: str) -> str | None:
    """
    Call Melipayamak's OTP endpoint and return the code it generated & sent.

    The `/api/send/otp/{token}` service creates the OTP on Melipayamak's side and
    delivers it by SMS; the generated code is returned in the JSON response as
    `code`. We return that code so the caller can store it for verification.

    Returns the code as a string on success, or None on any failure.
    """
    from integrations import melipayamak

    otp_token = getattr(settings, "MELIPAYAMAK_OTP_TOKEN", None)
    if not otp_token:
        logger.error("MELIPAYAMAK_OTP_TOKEN is not configured — OTP SMS not sent")
        return None

    if phone.startswith("98"):
        phone = "0" + phone[2:]

    try:
        result = melipayamak.request_otp(otp_token, phone, allow_empty=True).data
        code = result.get("code")
        if code:
            logger.info("OTP SMS delivered to %s**** (code captured)", phone[-4:])
            return str(code)
        logger.warning("Melipayamak returned no code for %s****: %s", phone[-4:], result)
        return None
    except melipayamak.Timeout:
        logger.error("OTP SMS timeout for %s****", phone[-4:])
        return None
    except Exception as exc:
        logger.exception("OTP SMS unexpected error for %s****: %s", phone[-4:], exc)
        return None
