"""
business/push_moderation.py

Owner-directed push notifications for moderation events — the FCM twin of
business/sms_moderation.py, covering the same three decisions plus one SMS
deliberately skips.

sms_moderation.notify_moderation_decision explicitly does not text PENDING:
its docstring calls that "noise" for the case where PENDING was really just
the automatic re-review after the owner's own edit. That reasoning holds for
SMS (a billed-feeling, delayed channel) but not for push: it's free,
near-instant, and this whole feature exists so an owner who just tapped
"save" — on a re-review that can take ten minutes or two hours — gets an
immediate "received" instead of silence. So this module adds
notify_review_queued_push() alongside the three decision pushes.

Every function here fails soft, same as sms_moderation.py and
api.services.push underneath it: a push is a convenience channel next to SMS
and the in-app status, never something a moderation decision or a save
should roll back over.
"""

import logging

logger = logging.getLogger(__name__)


def _owner_user_id(business):
    # Same field SMS uses to reach the owner (business.user), just the FK id
    # push wants instead of a phone number.
    return getattr(business, 'user_id', None)


def notify_review_queued_push(business) -> bool:
    """Tell the owner their edit landed in the review queue.

    Fired from the same two places services.py already pings staff about a
    re-queue (resubmit_if_content_changed and save_with_moderation) — this is
    the owner-facing half of that same event, not a new trigger.
    """
    user_id = _owner_user_id(business)
    if not user_id:
        return False

    try:
        from api.services.push import send_to_user

        delivered = send_to_user(
            user_id,
            title='در صف بازبینی',
            body=(
                f'تغییرات کسب‌وکار «{business.title}» برای بازبینی ارسال شد. '
                'این فرآیند معمولاً بین ۱۰ دقیقه تا ۲ ساعت طول می‌کشد.'
            ),
            data={'type': 'moderation_queued', 'business_id': str(business.pk)},
        )
        return delivered > 0
    except Exception:
        logger.exception('Queued-for-review push failed for business %s', business.pk)
        return False


def _approved_push(business):
    return (
        '✅ تایید شد',
        f'کسب‌وکار «{business.title}» تایید شد و اکنون در نوبت‌یار قابل مشاهده است.',
    )


def _rejected_push(business):
    reason = (business.moderation_note or '').strip()
    body = f'کسب‌وکار «{business.title}» تایید نشد.'
    if reason:
        body += f' دلیل: {reason}'
    body += ' می‌توانید اطلاعات را در اپلیکیشن اصلاح کنید تا دوباره بررسی شود.'
    return ('❌ تایید نشد', body)


def _suspended_push(business):
    reason = (business.moderation_note or '').strip()
    body = f'کسب‌وکار «{business.title}» موقتاً از دسترس عموم خارج شد.'
    if reason:
        body += f' دلیل: {reason}'
    return ('⛔ غیرفعال شد', body)


# Same three statuses as sms_moderation.py's _TEXT_BUILDERS, split into
# (title, body) instead of one SMS blob.
_PUSH_BUILDERS = {
    'APPROVED': _approved_push,
    'REJECTED': _rejected_push,
    'SUSPENDED': _suspended_push,
}


def notify_moderation_decision_push(business, to_status) -> bool:
    """Push the owner about a reviewer's decision. Returns True if any of
    their devices accepted it.

    Never raises, same guarantee as the SMS twin: a dead FCM credential or a
    provider outage must not roll back a decision that already committed.
    """
    builder = _PUSH_BUILDERS.get(to_status)
    if builder is None:
        return False

    user_id = _owner_user_id(business)
    if not user_id:
        return False

    try:
        title, body = builder(business)
    except Exception:
        logger.exception('Moderation push text build failed for business %s', business.pk)
        return False

    try:
        from api.services.push import send_to_user

        delivered = send_to_user(
            user_id,
            title=title,
            body=body,
            data={
                'type': 'moderation_decision',
                'business_id': str(business.pk),
                'status': to_status,
            },
        )
        return delivered > 0
    except Exception:
        logger.exception(
            'Moderation push failed for business %s (%s)', business.pk, to_status
        )
        return False
