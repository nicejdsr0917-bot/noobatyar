# accounting/payment/zibal_client.py
"""One place that talks to Zibal's ``/v1/verify``.

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

VERIFY_URL = "https://gateway.zibal.ir/v1/verify"

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
