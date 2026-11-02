"""
bale/views.py

The inbound half: Bale POSTs an Update here when the operator taps a button or
sends the bot a message.

Authentication, and why it looks like this
------------------------------------------
Bale does not sign webhook deliveries — there is no HMAC header to verify, and
this project has no existing signing pattern to reuse. So the endpoint stacks
two independent checks:

1. **An unguessable path segment.** ``webhook_secret`` is 48 random characters
   minted by BaleSettings.save() and only ever travels inside the HTTPS URL
   handed to setWebhook. A wrong secret is a 404, not a 403, so probing cannot
   confirm the endpoint exists.
2. **Sender identity.** The update must come from the configured ``chat_id``.
   This is the check that still holds if the URL leaks — through a proxy log, a
   backup, or a screenshot — because leaking the URL does not give an attacker
   the operator's Bale account.

Neither check alone is enough: (1) protects against someone who knows the chat
id, (2) against someone who knows the URL.

**Everything on this path must be fast.** (The handlers live in bale/handlers.py.) Bale keeps the tapped button in a
loading state until answerCallbackQuery arrives, and retries the whole delivery
if the webhook is slow to return — so the owner's notification SMS is fired on a
background thread rather than awaited inline. It used to be awaited, and a
Melipayamak round trip with a 10s timeout was long enough to leave the button
spinning and the update redelivered.

Always answers 200, so a handler bug cannot turn into an infinite retry loop.
"""

import hmac
import logging

from django.http import Http404
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from . import handlers
from .models import BaleSettings

logger = logging.getLogger(__name__)


@api_view(['POST'])
@permission_classes([AllowAny])
def webhook(request, secret):
    """Entry point for Bale updates. Never raises, never returns non-200."""
    config = BaleSettings.load()

    if not config.is_configured:
        raise Http404

    # compare_digest rather than == : the comparison is against a secret, and a
    # timing oracle on a 48-char value is worth closing even though the attack
    # is impractical over the network.
    if not hmac.compare_digest(str(secret), str(config.webhook_secret)):
        logger.warning('Bale webhook called with a bad secret')
        raise Http404

    try:
        handlers.handle(config, request.data)
    except Exception:
        logger.exception('Bale webhook handler failed')

    return Response({'ok': True})
