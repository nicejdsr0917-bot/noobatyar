# integrations/zibal.py
"""The one place that talks to Zibal: ``/v1/request`` and ``/v1/verify``.

Verification must be safe to repeat: the payer's browser can hit the callback
twice, and Zibal answers a second verify of a paid order with 201 ("already
verified"). So the outcome is typed — callers decide on the outcome, never on
a raw HTTP error — and a network problem is never mistaken for a failed payment.
"""
import enum
import logging
from dataclasses import dataclass, field
from typing import Any, Dict

import httpx
from django.conf import settings

logger = logging.getLogger(__name__)

BASE_URL = "https://gateway.zibal.ir"
REQUEST_URL = f"{BASE_URL}/v1/request"
VERIFY_URL = f"{BASE_URL}/v1/verify"

REQUEST_TIMEOUT = 10.0

CODE_SUCCESS = 100
CODE_ALREADY_VERIFIED = 201
# Zibal says, for certain, that this order was not paid / is not a known track id.
DEFINITIVE_FAILURE_CODES = frozenset({202, 203})

# One pooled client for the whole process (connection reuse, bounded waits).
_client = httpx.Client(timeout=httpx.Timeout(10.0, connect=5.0))


class Outcome(str, enum.Enum):
    SUCCESS = "success"
    ALREADY_VERIFIED = "already_verified"
    FAILED = "failed"                    # definitive: the money did not arrive
    TRANSPORT_ERROR = "transport_error"  # unknown: timeout, 5xx, garbage, unlisted code


@dataclass
class VerifyResult:
    outcome: Outcome
    code: Any = None
    message: str = ""
    data: Dict[str, Any] = field(default_factory=dict)

    @property
    def paid(self) -> bool:
        return self.outcome in (Outcome.SUCCESS, Outcome.ALREADY_VERIFIED)


def amount_matches(verify_data, expected_toman) -> bool:
    """Zibal reports the verified amount in Rial; requests are sent as
    ``toman * 10``. A missing or different amount is a mismatch."""
    try:
        return int(verify_data["amount"]) == int(expected_toman) * 10
    except (KeyError, TypeError, ValueError):
        return False


def verify(track_id, merchant=None) -> VerifyResult:
    """``merchant`` defaults to the platform's own; the client-deposit flow passes
    the business owner's merchant id, since that is whose account was paid."""
    try:
        response = _client.post(
            VERIFY_URL,
            headers={"Content-Type": "application/json"},
            json={"merchant": merchant or settings.ZIBAL_MERCHANT_ID, "trackId": int(track_id)},
        )
        response.raise_for_status()
        data = response.json()
        if not isinstance(data, dict):
            raise ValueError("unexpected Zibal payload")
    except (httpx.HTTPError, ValueError) as e:
        logger.error("Zibal verify transport error for %s: %s", track_id, e)
        return VerifyResult(Outcome.TRANSPORT_ERROR, message="خطا در ارتباط با درگاه پرداخت",
                            data={"error": str(e)})

    code = data.get("result")
    message = data.get("message") or "پرداخت تایید نشد"
    if code == CODE_SUCCESS:
        return VerifyResult(Outcome.SUCCESS, code, message, data)
    if code == CODE_ALREADY_VERIFIED:
        return VerifyResult(Outcome.ALREADY_VERIFIED, code, message, data)
    if code in DEFINITIVE_FAILURE_CODES:
        return VerifyResult(Outcome.FAILED, code, message, data)
    logger.error("Zibal verify unknown result for %s: %s", track_id, data)
    return VerifyResult(Outcome.TRANSPORT_ERROR, code, "پاسخ نامشخص از درگاه پرداخت؛ دوباره تلاش کنید", data)


@dataclass
class RequestResult:
    """Outcome of opening a payment. ``message`` is Zibal's raw text (may be None)."""
    success: bool
    track_id: Any = None
    payment_url: str = ""
    message: Any = None
    transport_error: bool = False  # True when Zibal could not be reached at all


def start_url(track_id) -> str:
    return f"{BASE_URL}/start/{track_id}"


def request_payment(merchant: str, amount_rial: int, callback_url: str, order_id: str,
                    description: str = None) -> RequestResult:
    """Open a payment (``/v1/request``). ``track_id`` is returned as Zibal sent it.

    Only ``httpx.HTTPError`` is absorbed (as ``transport_error``); a malformed
    body still raises, exactly as the per-flow copies of this call used to.
    """
    payload = {
        "merchant": merchant,
        "amount": amount_rial,
        "callbackUrl": callback_url,
        "orderId": order_id,
    }
    if description is not None:
        payload["description"] = description
    try:
        response = _client.post(
            REQUEST_URL,
            headers={"Content-Type": "application/json"},
            json=payload,
            timeout=REQUEST_TIMEOUT,
        )
        response.raise_for_status()
        data = response.json()
    except httpx.HTTPError as e:
        logger.error("Zibal request failed: %s", e)
        return RequestResult(False, message=str(e), transport_error=True)

    if data.get("result") != CODE_SUCCESS:
        return RequestResult(False, message=data.get("message"))
    track_id = data["trackId"]
    return RequestResult(True, track_id=track_id, payment_url=start_url(track_id), message=data.get("message"))
