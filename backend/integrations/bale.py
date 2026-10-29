# integrations/bale.py
"""Bale Bot API transport (https://tapi.bale.ai/bot<TOKEN>/, a Telegram clone).

**Nothing here raises.** Every caller is a fire-and-forget notification thread
or a webhook that must answer 200 regardless; failures come back as
``BaleResult(success=False, error=...)``. The bot token is in the URL, so the
URL is never logged.
"""
import logging
from dataclasses import dataclass
from typing import Any, Optional

import httpx

logger = logging.getLogger(__name__)

BASE_URL = 'https://tapi.bale.ai'
TIMEOUT = 10.0
# Uploads get their own budget: a logo is orders of magnitude larger than a JSON
# body, and sharing the 10s timeout made photo sends fail on a slow link.
UPLOAD_TIMEOUT = 30.0

_client = httpx.Client(timeout=httpx.Timeout(TIMEOUT, connect=5.0))


@dataclass
class BaleResult:
    success: bool
    result: Any = None
    error: Optional[str] = None

    def as_dict(self) -> dict:
        """The ``{'success': bool, 'result'|'error'}`` shape callers consume."""
        if self.success:
            return {'success': True, 'result': self.result}
        return {'success': False, 'error': self.error}


def _envelope(method: str, data) -> BaleResult:
    if not data.get('ok'):
        # Bale mirrors Telegram's envelope: {"ok": false, "description": "..."}
        error = data.get('description') or 'unknown error'
        logger.warning('Bale %s rejected: %s', method, error)
        return BaleResult(False, error=error)
    return BaleResult(True, result=data.get('result'))


def call(token: str, method: str, payload: dict) -> BaleResult:
    """POST one Bot API method as JSON."""
    if not token:
        return BaleResult(False, error='bot token is not configured')
    try:
        response = _client.post(f'{BASE_URL}/bot{token}/{method}', json=payload, timeout=TIMEOUT)
        response.raise_for_status()
        data = response.json()
    except httpx.HTTPError as e:
        logger.error('Bale %s failed: %s', method, e)
        return BaleResult(False, error=str(e))
    except ValueError as e:
        logger.error('Bale %s returned non-JSON: %s', method, e)
        return BaleResult(False, error='invalid response')
    return _envelope(method, data)


def upload_photo(token: str, form: dict, photo_path: str) -> BaleResult:
    """Multipart ``sendPhoto`` of a local file (``form`` = the text fields)."""
    if not token:
        return BaleResult(False, error='bot token is not configured')
    try:
        with open(photo_path, 'rb') as handle:
            response = _client.post(
                f'{BASE_URL}/bot{token}/sendPhoto', data=form,
                files={'photo': handle}, timeout=UPLOAD_TIMEOUT,
            )
            response.raise_for_status()
            payload = response.json()
    except OSError as e:
        logger.warning('Bale sendPhoto could not read %s: %s', photo_path, e)
        return BaleResult(False, error=str(e))
    except httpx.HTTPError as e:
        logger.error('Bale sendPhoto failed: %s', e)
        return BaleResult(False, error=str(e))
    except ValueError as e:
        logger.error('Bale sendPhoto returned non-JSON: %s', e)
        return BaleResult(False, error='invalid response')
    return _envelope('sendPhoto', payload)
