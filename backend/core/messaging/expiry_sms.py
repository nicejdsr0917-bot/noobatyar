"""
Renewal / expiry SMS: the audience query, the manual blast, the hand-picked
subscription send, the operator test message, and the outbox chunk worker.

Funded like :mod:`core.messaging.admin_message` — straight through
``api.sms.send_sms``, never ``accounting.usage.consume_sms``: a staff-triggered
renewal notice is a platform cost, not something billed to the owner's own SMS
quota.
"""

import logging

from django.db import transaction

from ._common import enqueue_chunks, recipient_row, send_signed_sms

logger = logging.getLogger('core.messaging')


def _reminder_flag(mode):
    """The Subscription flag a send in ``mode`` spends."""
    return 'expired_reminder_sent' if mode == 'expired' else 'reminder_sent'


def expiry_sms_audience(days_before: int, *, plan_ids=None, skip_reminded=True, mode='expiring'):
    """
    The audience of a renewal-reminder SMS, in one of two modes.

    ``mode='expiring'`` (default): active subscriptions ending within
    ``days_before`` days, soonest first. ``skip_reminded`` leaves out anyone
    already sent a reminder for this subscription (by hand or by the
    automatic job), so a second send doesn't message the same owner twice.

    ``mode='expired'``: owners whose subscription ended within the last
    ``days_before`` days and who have **not renewed** — nobody with a later
    subscription of any kind (cancelled ones don't count as a subscription
    to lose), so an owner who already paid again is never nagged. Most
    recently lapsed first. ``skip_reminded`` here leaves out subscriptions
    that already got this "you didn't renew" message, tracked on its own flag
    (``expired_reminder_sent``) because ``reminder_sent`` is spent by the
    "ending soon" message every one of these owners got before they lapsed.

    One query for the live count, the recipient preview and the send itself,
    so the three can never disagree about who is in it.
    """
    from datetime import timedelta

    from django.db.models import Exists, OuterRef
    from django.utils import timezone

    from accounting.models import Subscription

    now = timezone.now()
    if mode == 'expired':
        later = Subscription.objects.filter(
            user_id=OuterRef('user_id'), ends_at__gt=OuterRef('ends_at'),
        ).exclude(status='cancelled')
        subs = (
            Subscription.objects
            .filter(status__in=('active', 'expired'),
                    ends_at__lte=now, ends_at__gt=now - timedelta(days=days_before))
            .exclude(Exists(later))
            .select_related('user', 'plan').order_by('-ends_at')
        )
    else:
        subs = Subscription.objects.filter(
            status='active', ends_at__gt=now, ends_at__lte=now + timedelta(days=days_before),
        ).select_related('user', 'plan').order_by('ends_at')
    if skip_reminded:
        subs = subs.filter(**{_reminder_flag(mode): False})
    if plan_ids:
        subs = subs.filter(plan_id__in=plan_ids)
    return subs


def days_for_sms(sub, mode='expiring'):
    """The number a message's ``{days}`` shows: days left, or — for owners who
    already lapsed — days since it ended. Never below 1, so "0 days" can't read
    oddly."""
    from django.utils import timezone

    if mode == 'expired':
        return max(1, (timezone.now() - sub.ends_at).days)
    return max(1, sub.days_left())


def _create_manual_log(message, days_before, filters, matched, actor):
    from core.models import ExpirySmsLog

    return ExpirySmsLog.objects.create(
        source=ExpirySmsLog.SOURCE_MANUAL,
        message=message, days_before=days_before, filters=filters,
        matched_count=matched, sent_count=0, failed_count=0,
        recipients=[], sent_by=actor,
    )


def _enqueue_expiry_chunks(log, message, items, skip_flagged):
    """``items`` is ``[(subscription_id, mode), ...]`` in send order."""
    enqueue_chunks('core.expiry_sms_chunk', items, lambda chunk: {
        'log_id': log.id, 'message': message,
        'items': [[sid, mode] for sid, mode in chunk],
        'skip_flagged': skip_flagged,
    })


def send_expiry_sms(message: str, days_before: int, actor=None, *,
                    plan_ids=None, skip_reminded=True, mode='expiring'):
    """
    Queues ``message`` for everyone in :func:`expiry_sms_audience`, filled per
    owner by ``accounting.models.render_reminder_text`` (``{full_name}``,
    ``{days}`` — their actual days left (days since expiry in ``expired``
    mode), not the window edge — and ``{plan}``), and records one
    :class:`core.models.ExpirySmsLog` row.

    The log row is created at once with ``matched_count`` set and
    ``sent_count``/``failed_count``/``recipients`` filling in as the outbox
    worker delivers each chunk (:func:`deliver_expiry_chunk`). Each
    subscription reached is marked as reminded by the worker — ``reminder_sent``
    (the same flag the automatic reminder sets, so the cron skips it) in the
    default mode, ``expired_reminder_sent`` in ``expired`` mode.
    """
    from accounting.models import Plan

    subs = expiry_sms_audience(
        days_before, plan_ids=plan_ids, skip_reminded=skip_reminded, mode=mode,
    )
    sub_ids = list(subs.values_list('id', flat=True))

    plan_names = list(Plan.objects.filter(id__in=plan_ids).values_list('name', flat=True)) if plan_ids else []
    with transaction.atomic():
        log = _create_manual_log(
            message, days_before,
            {'plans': plan_names, 'skip_reminded': skip_reminded, 'mode': mode},
            len(sub_ids), actor,
        )
        _enqueue_expiry_chunks(log, message, [(sid, mode) for sid in sub_ids], skip_reminded)
    return log


def send_sms_to_subscriptions(subs, message: str, actor=None):
    """
    Queues ``message`` for the owners of hand-picked subscriptions — the
    "ارسال پیامک" action on the subscription list — and records one
    :class:`core.models.ExpirySmsLog` row, so these sends show up in the same
    report as the campaign-page ones (counts fill in as the worker sends).

    One SMS per owner: when several of one owner's subscriptions are picked,
    the one ending last speaks for them. Each subscription is filled and
    flagged by its own state — already ended reads ``{days}`` as days since
    the end and sets ``expired_reminder_sent``, still running reads days left
    and sets ``reminder_sent`` — so the campaign page's "skip already
    reminded" sees these sends too.
    """
    from django.utils import timezone

    by_user = {}
    for sub in subs:
        current = by_user.get(sub.user_id)
        if current is None or sub.ends_at > current.ends_at:
            by_user[sub.user_id] = sub
    picked = sorted(by_user.values(), key=lambda sub: sub.ends_at, reverse=True)

    now = timezone.now()
    with transaction.atomic():
        log = _create_manual_log(
            message, 0,
            {'mode': 'selection', 'plans': sorted({sub.plan.name for sub in picked})},
            len(picked), actor,
        )
        _enqueue_expiry_chunks(
            log, message,
            [(sub.id, 'expired' if sub.ends_at <= now else 'expiring') for sub in picked],
            False,
        )
    return log


def deliver_expiry_chunk(payload):
    """Outbox handler body (``core.expiry_sms_chunk``): send one chunk and fold
    its result into the log row."""
    from accounting.models import Subscription
    from core.models import ExpirySmsLog

    modes = {sid: mode for sid, mode in payload['items']}
    by_id = {
        sub.id: sub
        for sub in Subscription.objects.filter(id__in=modes).select_related('user', 'plan')
    }
    ordered = []
    for sid, mode in payload['items']:
        sub = by_id.get(sid)
        if sub is None:
            continue
        # A double-submitted campaign (or a retried chunk) must not text twice.
        if payload.get('skip_flagged') and getattr(sub, _reminder_flag(mode)):
            continue
        ordered.append(sub)

    _, sent, failed, recipients = _send_renewal_sms(
        ordered, payload['message'], mode_of=lambda sub: modes[sub.id],
    )

    with transaction.atomic():
        log = ExpirySmsLog.objects.select_for_update().get(pk=payload['log_id'])
        log.sent_count += sent
        log.failed_count += failed
        log.recipients = list(log.recipients or []) + recipients
        log.save(update_fields=['sent_count', 'failed_count', 'recipients'])


def _send_renewal_sms(subs, message, *, mode_of):
    """The send loop behind both renewal-SMS entry points: fill the template
    per owner, send, flag the subscription on success, and collect the
    per-recipient report. ``mode_of(sub)`` is 'expired' or 'expiring'."""
    from accounting.models import render_reminder_text

    matched = sent = failed = 0
    recipients = []
    for sub in subs:
        matched += 1
        mode = mode_of(sub)
        name = sub.user.name or ''
        phone = getattr(sub.user, 'phone', None)
        if not phone:
            failed += 1
            recipients.append(recipient_row(name, '', False, 'بدون شماره تلفن'))
            continue
        personalized = render_reminder_text(
            message, owner_name=name, days_left=days_for_sms(sub, mode), plan_name=sub.plan.name,
        )
        ok, err = send_signed_sms(phone, personalized)
        recipients.append(recipient_row(name, phone, ok, err))
        if ok:
            sent += 1
            flag = _reminder_flag(mode)
            setattr(sub, flag, True)
            sub.save(update_fields=[flag])
        else:
            failed += 1
            logger.warning('Expiry SMS to %s failed: %s', phone, err)
    return matched, sent, failed, recipients


def send_expiry_sms_test(message: str, days_before: int, actor):
    """
    One copy of ``message`` to the operator's own phone, filled with their own
    name, ``days_before`` as the days left and their current plan (or a
    placeholder) — to read the real SMS, signature and opt-out line included,
    before sending it to every owner. Logged like any other send, since it
    costs a real SMS.
    """
    from accounting.models import render_reminder_text
    from core.models import ExpirySmsLog

    name = getattr(actor, 'name', '') or ''
    phone = getattr(actor, 'phone', '') or ''
    sub = actor.subscriptions.select_related('plan').order_by('-ends_at').first()
    plan_name = sub.plan.name if sub else 'پلن نمونه'

    if not phone:
        ok, err = False, 'حساب شما شماره تلفن ندارد'
    else:
        personalized = render_reminder_text(
            message, owner_name=name, days_left=days_before, plan_name=plan_name,
        )
        ok, err = send_signed_sms(phone, personalized)

    return ExpirySmsLog.objects.create(
        source=ExpirySmsLog.SOURCE_TEST,
        message=message, days_before=days_before,
        matched_count=1, sent_count=1 if ok else 0, failed_count=0 if ok else 1,
        recipients=[recipient_row(name, phone, ok, err, always_error_key=True)],
        sent_by=actor,
    )
