# core/messaging/__init__.py
"""
Platform-initiated messaging — everything staff send from the admin, as
opposed to anything an owner or customer triggers themselves.

* :mod:`.admin_message` — one message to one business owner (push / SMS)
* :mod:`.push_campaigns` — promotional push to customers and/or owners
* :mod:`.expiry_sms` — renewal / expiry SMS blasts and their outbox worker

Every name that used to live in the single ``core/messaging.py`` module is
re-exported here so ``from core.messaging import ...`` keeps working.
``deliver_push_chunk`` resolves ``_deliver`` through this package so tests can
patch ``core.messaging._deliver``.
"""

from ._common import CHUNK_SIZE, _chunks
from .admin_message import (
    _personalize,
    _send_push,
    _send_sms,
    deliver_admin_message,
    send_admin_message,
)
from .expiry_sms import (
    _enqueue_expiry_chunks,
    _send_renewal_sms,
    days_for_sms,
    deliver_expiry_chunk,
    expiry_sms_audience,
    send_expiry_sms,
    send_expiry_sms_test,
    send_sms_to_subscriptions,
)
from .push_campaigns import (
    _AUDIENCE_DISPATCH,
    _deliver,
    _owner_recipients,
    _visitor_recipients,
    deliver_push_chunk,
    send_push_campaign,
)

__all__ = [
    'CHUNK_SIZE', 'send_admin_message', 'deliver_admin_message',
    'send_push_campaign', 'deliver_push_chunk',
    'expiry_sms_audience', 'days_for_sms', 'send_expiry_sms',
    'send_sms_to_subscriptions', 'deliver_expiry_chunk', 'send_expiry_sms_test',
]
