"""
api/services/otp.py

Redis-backed OTP service.  All state lives in Django's cache framework
(configured as django-redis in production), making it stateless across
multiple workers and restarts.

Cache keys
----------
otp:code:{phone}        OTP value          TTL = 3 min   (OTP_TTL)
otp:rate:{phone}        Rate-limit flag    TTL = 3 min   (OTP_RATE_TTL)
otp:daily_fail:{phone}  Daily fail counter TTL = 24 h    (OTP_DAILY_FAIL_TTL)

Red Line #3 rules
-----------------
1. Max 1 OTP request per phone per 3 minutes.
2. After 5 wrong verifications in 24 h  → phone banned for the rest of that 24 h.
3. Melipayamak's /api/send/otp/ endpoint generates the OTP itself and returns it
   in the response; we store THAT code (not a locally generated one) so that the
   code the user receives always matches what we verify against.
"""

import logging

from django.core.cache import cache
from django.conf import settings

logger = logging.getLogger(__name__)

# ── Configurable constants (override via settings if needed) ──────────────────
OTP_TTL           = getattr(settings, "OTP_TTL_SECONDS",      180)   # 3 min
OTP_RATE_TTL      = getattr(settings, "OTP_RATE_TTL_SECONDS",  180)  # 3 min cooldown
OTP_DAILY_FAIL_TTL = getattr(settings, "OTP_DAILY_FAIL_TTL",  86400) # 24 h ban window
OTP_MAX_DAILY_FAIL = getattr(settings, "OTP_MAX_DAILY_FAIL",     5)  # failures before ban
OTP_MAX_ATTEMPTS  = getattr(settings, "OTP_MAX_ATTEMPTS",         5)  # per-code verify tries


# ── Key helpers ────────────────────────────────────────────────────────────────

def _key_code(phone: str) -> str:
    return f"otp:code:{phone}"

def _key_rate(phone: str) -> str:
    return f"otp:rate:{phone}"

def _key_daily_fail(phone: str) -> str:
    return f"otp:daily_fail:{phone}"

def _key_attempts(phone: str) -> str:
    return f"otp:attempts:{phone}"


# ── Public API ────────────────────────────────────────────────────────────────

def send_otp(phone: str) -> dict:
    """
    Generate an OTP and dispatch it (synchronously).

    Returns
    -------
    {"success": True}
        OTP was generated and SMS dispatch started.
    {"success": False, "error": str}
        Request rejected (rate limit or daily ban).
    """
    # 0. Dev bypass (DEBUG only — settings forces OTP_DEV_CODE empty otherwise).
    #    Uses a fixed code, sends no SMS, and skips the cooldown so a local test
    #    run isn't blocked for 3 minutes between attempts.
    dev_code = getattr(settings, "OTP_DEV_CODE", "")

    # 1. Daily ban check (never in dev — a few mistyped codes during testing
    #    would otherwise lock the number out for a day)
    fail_count = cache.get(_key_daily_fail(phone), 0)
    if not dev_code and fail_count >= OTP_MAX_DAILY_FAIL:
        logger.warning("OTP daily ban active for %s**** (fails=%d)", phone[-4:], fail_count)
        return {
            "success": False,
            "error": "این شماره به دلیل تلاش‌های ناموفق مکرر تا ۲۴ ساعت مسدود شده است",
        }

    # 2. Cooldown (3 min). Claimed atomically with cache.add BEFORE the slow
    #    (up to 10 s) HTTP call: a check-then-set would let parallel requests
    #    all pass the check and each trigger an SMS. add() succeeds for exactly one.
    claimed = False
    if not dev_code:
        if not cache.add(_key_rate(phone), 1, timeout=OTP_RATE_TTL):
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
            cache.delete(_key_rate(phone))
        return {
            "success": False,
            "error": "خطا در ارسال پیامک. لطفاً کمی بعد دوباره تلاش کنید",
        }

    # 4. Store the code Melipayamak actually sent; reset the attempts counter.
    cache.set(_key_code(phone),     str(code), timeout=OTP_TTL)
    cache.set(_key_attempts(phone), 0,         timeout=OTP_TTL)

    logger.info("OTP sent and stored for %s****", phone[-4:])
    return {"success": True}


def verify_otp(phone: str, code: str) -> dict:
    """
    Verify a submitted OTP code.

    Side effects
    ------------
    - On success  : clears code, rate, and attempt keys.
    - On failure  : increments daily fail counter; increments attempt counter.
      If attempt count reaches OTP_MAX_ATTEMPTS the code is invalidated early.
    """
    # Dev bypass (DEBUG only — settings forces OTP_DEV_CODE empty otherwise): the
    # fixed code works for any number, whether or not "send" was pressed or the
    # code has expired, and a wrong guess never counts towards a ban.
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
        # Too many tries — kill the code
        _invalidate_otp(phone)
        return {"success": False, "error": "تعداد تلاش بیش از حد مجاز — لطفاً کد جدید دریافت کنید"}

    if stored_code != str(code):
        logger.warning("OTP verify failed: mismatch for %s**** (attempt %d)", phone[-4:], attempts)
        _increment_daily_fail(phone)
        remaining = OTP_MAX_ATTEMPTS - attempts
        return {
            "success": False,
            "error": f"کد اشتباه است — {remaining} تلاش باقی مانده",
        }

    # ✓ Correct — clean up all related keys
    _invalidate_otp(phone)
    return {"success": True}


def _bump(key: str, timeout: int) -> int:
    """Atomic counter increment; returns the new value."""
    cache.add(key, 0, timeout=timeout)
    try:
        return cache.incr(key)
    except ValueError:  # expired between add and incr
        cache.set(key, 1, timeout=timeout)
        return 1


# ── Internal helpers ───────────────────────────────────────────────────────────

def _invalidate_otp(phone: str) -> None:
    """Remove all per-OTP keys (called on success or max-attempt breach)."""
    cache.delete(_key_code(phone))
    cache.delete(_key_rate(phone))
    cache.delete(_key_attempts(phone))


def _increment_daily_fail(phone: str) -> None:
    """
    Atomically increment the daily failure counter.
    We use add+incr because Django's cache.incr raises ValueError if the
    key doesn't exist; cache.add is a no-op if it already exists.
    """
    if not cache.add(_key_daily_fail(phone), 1, timeout=OTP_DAILY_FAIL_TTL):
        try:
            cache.incr(_key_daily_fail(phone))
        except ValueError:
            cache.set(_key_daily_fail(phone), 1, timeout=OTP_DAILY_FAIL_TTL)


def _send_via_melipayamak(phone: str) -> str | None:
    """
    Call Melipayamak's OTP endpoint and return the code it generated & sent.

    The `/api/send/otp/{token}` service creates the OTP on Melipayamak's side and
    delivers it by SMS; the generated code is returned in the JSON response as
    `code`. We return that code so the caller can store it for verification.

    Returns the code as a string on success, or None on any failure.
    """
    import requests

    otp_token = getattr(settings, "MELIPAYAMAK_OTP_TOKEN", None)
    if not otp_token:
        logger.error("MELIPAYAMAK_OTP_TOKEN is not configured — OTP SMS not sent")
        return None

    if phone.startswith("98"):
        phone = "0" + phone[2:]

    url = f"https://console.melipayamak.com/api/send/otp/{otp_token}"
    try:
        response = requests.post(url, json={"to": phone}, timeout=10)
        result = response.json() if response.text.strip() else {}
        code = result.get("code")
        if code:
            logger.info("OTP SMS delivered to %s**** (code captured)", phone[-4:])
            return str(code)
        logger.warning("Melipayamak returned no code for %s****: %s", phone[-4:], result)
        return None
    except requests.Timeout:
        logger.error("OTP SMS timeout for %s****", phone[-4:])
        return None
    except Exception as exc:
        logger.exception("OTP SMS unexpected error for %s****: %s", phone[-4:], exc)
        return None
