"""
bale/client.py

Thin HTTP layer over the Bale Bot API (https://tapi.bale.ai/bot<TOKEN>/), which
is a clone of Telegram's Bot API — same method names, same inline-keyboard and
callback_query shapes.

Follows the house convention for outbound calls (accounting/payment/*.py):
sync ``httpx.Client``, a 10s timeout, ``raise_for_status()``, catch
``httpx.HTTPError``, and return a ``{'success': bool, ...}`` dict.

**Nothing here raises.** Every caller is either a fire-and-forget notification
thread or a webhook that must answer 200 regardless, so an exception escaping
this module would only ever turn a cosmetic failure into a real one.
"""

import json
import logging

from integrations import bale as transport

logger = logging.getLogger(__name__)

# Re-exported for anything that still reads them from here.
BASE_URL = transport.BASE_URL
TIMEOUT = transport.TIMEOUT
UPLOAD_TIMEOUT = transport.UPLOAD_TIMEOUT


def _call(token: str, method: str, payload: dict) -> dict:
    """POST one Bot API method. Returns ``{'success': bool, 'result'|'error'}``."""
    return transport.call(token, method, payload).as_dict()


def send_message(token: str, chat_id: str, text: str, reply_markup: dict = None) -> dict:
    payload = {'chat_id': chat_id, 'text': text}
    if reply_markup is not None:
        payload['reply_markup'] = reply_markup
    return _call(token, 'sendMessage', payload)


def edit_message_text(token: str, chat_id: str, message_id: int, text: str,
                      reply_markup: dict = None) -> dict:
    """Rewrite an already-sent message.

    Used to swap the decision buttons for the outcome, so a message can never be
    acted on twice from the UI. Passing ``reply_markup=None`` leaves the old
    keyboard in place, so callers that want the buttons *gone* must pass an
    empty ``{'inline_keyboard': []}``.
    """
    payload = {'chat_id': chat_id, 'message_id': message_id, 'text': text}
    if reply_markup is not None:
        payload['reply_markup'] = reply_markup
    return _call(token, 'editMessageText', payload)


def answer_callback_query(token: str, callback_query_id: str, text: str = '',
                          show_alert: bool = False) -> dict:
    """Dismiss the button's spinner and optionally show a toast.

    Bale (like Telegram) leaves the button spinning for the user until this is
    called, so it runs even on the failure paths.
    """
    return _call(token, 'answerCallbackQuery', {
        'callback_query_id': callback_query_id,
        'text': text[:200],
        'show_alert': show_alert,
    })


def send_photo(token: str, chat_id: str, photo_path: str, caption: str = '',
               reply_markup: dict = None) -> dict:
    """Upload a local image file.

    Multipart rather than JSON, so it cannot go through :func:`_call` — and
    ``reply_markup`` has to be a JSON *string* in a multipart body, which is the
    detail that silently drops the keyboard if you pass the dict.
    """
    if not token:
        return {'success': False, 'error': 'bot token is not configured'}

    data = {'chat_id': chat_id}
    if caption:
        # Bale caps captions at 1024 characters like Telegram; a longer one is
        # rejected outright rather than truncated, losing the whole message.
        data['caption'] = caption[:1024]
    if reply_markup is not None:
        data['reply_markup'] = json.dumps(reply_markup)

    return transport.upload_photo(token, data, photo_path).as_dict()


def set_webhook(token: str, url: str) -> dict:
    return _call(token, 'setWebhook', {'url': url})


def get_webhook_info(token: str) -> dict:
    return _call(token, 'getWebhookInfo', {})


def delete_webhook(token: str) -> dict:
    return _call(token, 'deleteWebhook', {})


def get_me(token: str) -> dict:
    return _call(token, 'getMe', {})
