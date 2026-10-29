# integrations/myket.py
"""Myket's server-to-server purchase API (verify + consume).

Transport only: no models and no Persian user-facing text. Failures are typed
exceptions so ``accounting.payment.myket`` decides what the app is told.

API docs: https://myket.ir/kb/pages/server-to-server-payment-validation-api/
          https://myket.ir/kb/pages/api-consume/
"""
import logging

import httpx
from django.conf import settings

logger = logging.getLogger(__name__)

API_BASE = "https://developer.myket.ir/api/partners/applications"
TIMEOUT = 15.0


class MyketError(Exception):
    """Base of every failure talking to Myket."""


class MyketNotConfigured(MyketError):
    """``MYKET_ACCESS_TOKEN`` is not set."""


class MyketUnreachable(MyketError):
    """Network-level failure: Myket gave no answer."""


class MyketRejected(MyketError):
    """Myket answered, but not with a 200 for verify."""


def _headers():
    token = getattr(settings, "MYKET_ACCESS_TOKEN", "")
    if not token:
        raise MyketNotConfigured("MYKET_ACCESS_TOKEN is not set")
    return {"X-Access-Token": token, "Content-Type": "application/json"}


def _product_url(sku):
    return f"{API_BASE}/{settings.MYKET_PACKAGE_NAME}/purchases/products/{sku}"


def verify_purchase(sku, token):
    """Myket's verdict (the JSON body) on one purchase token.

    Raises :class:`MyketNotConfigured`, :class:`MyketUnreachable` or
    :class:`MyketRejected`.
    """
    headers = _headers()
    try:
        response = httpx.post(
            f"{_product_url(sku)}/verify", headers=headers,
            json={"tokenId": token}, timeout=TIMEOUT,
        )
    except httpx.HTTPError as exc:
        logger.error("Myket verify unreachable for %s: %s", sku, exc)
        raise MyketUnreachable(str(exc)) from exc
    if response.status_code != 200:
        logger.warning("Myket verify %s -> %s %s", sku, response.status_code, response.text[:300])
        raise MyketRejected(f"HTTP {response.status_code}")
    return response.json()


def consume_purchase(sku, token):
    """Consume a verified purchase so the product can be bought again. True on success."""
    try:
        response = httpx.put(f"{_product_url(sku)}/tokens/{token}/consume", headers=_headers(), timeout=TIMEOUT)
    except (httpx.HTTPError, MyketNotConfigured) as exc:
        logger.error("Myket consume failed for %s: %s", sku, exc)
        return False
    if response.status_code == 200:
        return True
    # Already consumed is the outcome we wanted anyway.
    if response.status_code == 400 and "SkuAlreadyConsumed" in response.text:
        return True
    logger.warning("Myket consume %s -> %s %s", sku, response.status_code, response.text[:300])
    return False
