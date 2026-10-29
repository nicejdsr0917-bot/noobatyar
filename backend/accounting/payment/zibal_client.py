# accounting/payment/zibal_client.py
"""Re-export shim: the Zibal client lives in ``integrations.zibal``."""
from integrations.zibal import (  # noqa: F401
    CODE_ALREADY_VERIFIED, CODE_SUCCESS, DEFINITIVE_FAILURE_CODES, VERIFY_URL,
    Outcome, VerifyResult, verify, amount_matches, request_payment, RequestResult, start_url,
)
