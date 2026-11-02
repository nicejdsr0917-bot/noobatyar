"""
Platform-initiated message to one business owner — the "ارسال پیام" form on
the business 360 page (``core.admin_views.business.handle_business_message``).

SMS here goes through ``api.sms.send_sms`` directly, never
``accounting.usage.consume_sms``: that function specifically debits the
*owner's own* paid monthly quota/wallet. A staff-initiated notice is a
platform cost, not something to bill the owner for receiving.

Push is free either way, so it goes through the same
``api.services.push.send_to_user`` every other owner-push call site uses.

Every attempt — one row per (business, channel), success or failure — is
logged to ``core.models.AdminMessageLog`` for audit; see that model's
docstring for why it is not folded into ``visitor.SmsLog``.
"""

import logging

from core.models import AdminMessageLog

logger = logging.getLogger('core.messaging')

_ERR_FCM = 'سرویس اعلان (FCM) پیکربندی نشده است'
_ERR_NO_PHONE = 'این کسب‌وکار شماره تلفن مالک ندارد'
_ERR_NO_DEVICE = 'دستگاه فعالی برای این صاحب کسب‌وکار ثبت نشده است'


def _personalize(text: str, business) -> str:
    """Replaces the one supported template token. Kept to a single token on
    purpose — a compose box is not a template engine, and staff already write
    these by hand per send; more tokens can be added if a real need shows up."""
    return (text or '').replace('{business_name}', business.title)


def _log(business, actor, channel, title, body, ok=False, error=''):
    return AdminMessageLog.objects.create(
        business=business, sent_by=actor, channel=channel,
        title=title, body=body, status='SENT' if ok else 'FAILED',
        error_detail='' if ok else error,
    )


def _owner_phone(business):
    return business.user.phone if business.user_id else ''


def _fcm_missing_log(business, title, body, actor):
    from api.services import push

    if push.is_configured():
        return None
    return _log(business, actor, 'PUSH', title, body, error=_ERR_FCM)


def _no_phone_log(business, body, actor):
    if _owner_phone(business):
        return None
    return _log(business, actor, 'SMS', '', body, error=_ERR_NO_PHONE)


def send_admin_message(business, title: str, body: str, channels, actor=None) -> list[AdminMessageLog]:
    """
    Queues a personalized message to ``business``'s owner on each channel in
    ``channels`` (an iterable of ``'PUSH'``/``'SMS'``) for the outbox worker.

    Returns only the :class:`AdminMessageLog` rows of channels that could not
    even be queued (FCM not configured, owner without a phone) — those fail
    immediately and are logged here. Every queued channel writes its own log
    row (SENT/FAILED) when the worker delivers it, so the calling view reports
    "queued" for the rest and the result shows up in the message log panel.
    """
    personalized_title = _personalize(title, business)
    personalized_body = _personalize(body, business)
    logs = []

    for channel in ('PUSH', 'SMS'):
        if channel not in channels:
            continue
        failed = (
            _fcm_missing_log(business, personalized_title, personalized_body, actor)
            if channel == 'PUSH' else _no_phone_log(business, personalized_body, actor)
        )
        if failed is not None:
            logs.append(failed)
            continue
        from core.outbox.service import enqueue
        enqueue('core.admin_message', {
            'business_id': business.id, 'channel': channel,
            'title': personalized_title if channel == 'PUSH' else '',
            'body': personalized_body, 'actor_id': getattr(actor, 'pk', None),
        })

    return logs


def deliver_admin_message(payload):
    """Outbox handler body (``core.admin_message``): the actual send + log row."""
    from api.models import User
    from business.models import Business

    business = Business.objects.select_related('user').filter(pk=payload['business_id']).first()
    if business is None:
        return
    actor = User.objects.filter(pk=payload.get('actor_id')).first() if payload.get('actor_id') else None
    if payload['channel'] == 'PUSH':
        _send_push(business, payload['title'], payload['body'], actor)
    else:
        _send_sms(business, payload['body'], actor)


def _send_push(business, title, body, actor) -> AdminMessageLog:
    from api.services import push

    missing = _fcm_missing_log(business, title, body, actor)
    if missing is not None:
        return missing

    try:
        delivered = push.send_to_user(business.user_id, title=title, body=body)
    except Exception as exc:
        logger.exception('Admin push to business %s failed', business.id)
        return _log(business, actor, 'PUSH', title, body, error=str(exc))

    return _log(business, actor, 'PUSH', title, body, ok=bool(delivered), error=_ERR_NO_DEVICE)


def _send_sms(business, body, actor) -> AdminMessageLog:
    from api.sms import send_sms, signed

    missing = _no_phone_log(business, body, actor)
    if missing is not None:
        return missing

    ok, err = send_sms(_owner_phone(business), signed(body))
    return _log(business, actor, 'SMS', '', body, ok=ok, error=err)
