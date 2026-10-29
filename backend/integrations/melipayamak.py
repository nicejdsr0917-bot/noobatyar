# integrations/melipayamak.py
"""Melipayamak HTTP transport (OTP and simple-send endpoints).

No Django models and no policy here: callers decide about tokens, dev mode,
send windows and how to read the answer. Network/JSON errors propagate as
``requests`` exceptions / ``ValueError`` so each caller keeps its own handling.
"""
from dataclasses import dataclass
from typing import Any, Dict

import requests
from requests import RequestException, Timeout  # noqa: F401  (re-exported for callers)

BASE_URL = "https://console.melipayamak.com/api/send"
TIMEOUT = 10

# One pooled session for the process.
_session = requests.Session()
_session.mount("https://", requests.adapters.HTTPAdapter(pool_connections=4, pool_maxsize=16))


@dataclass
class ApiResponse:
    data: Dict[str, Any]

    @property
    def code(self):
        return self.data.get("code")

    @property
    def status(self) -> str:
        return str(self.data.get("status", ""))

    @property
    def rec_id(self):
        return self.data.get("recId") or self.data.get("recID") or 0


def _post(kind: str, token: str, payload: dict, allow_empty: bool) -> ApiResponse:
    response = _session.post(f"{BASE_URL}/{kind}/{token}", json=payload, timeout=TIMEOUT)
    if allow_empty and not response.text.strip():
        return ApiResponse({})
    return ApiResponse(response.json())


def request_otp(token: str, phone: str, allow_empty: bool = False) -> ApiResponse:
    """Ask Melipayamak to generate and SMS an OTP; the code is in ``.code``."""
    return _post("otp", token, {"to": phone}, allow_empty)


def send_simple(token: str, sender: str, phone: str, text: str) -> ApiResponse:
    return _post("simple", token, {"from": sender, "to": phone, "text": text}, False)
