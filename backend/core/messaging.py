# core/messaging.py
"""
Platform-initiated messaging to a business owner — renewal reminders and
other ops notices staff send from the business's own admin page
(NobatyarAdminSite.business_message_view), as opposed to anything the owner
or a customer triggers themselves.

── Funding ──────────────────────────────────────────────────────────────────

SMS here goes through ``api.sms.send_sms`` directly, never
``accounting.usage.consume_sms``: that function specifically debits the
*owner's own* paid monthly quota/wallet (see appointment/management/commands/
send_appointment_reminders.py's client-SMS path for the only other place that
matters). A staff-initiated "your subscription is expiring" notice is a
platform cost, not something to bill the owner for receiving — charging their
own quota for a message they didn't choose to send would be a real billing
bug, not a rounding error.

Push is free either way, so it goes through the same
``api.services.push.send_to_user`` every other owner-push call site uses —
nothing special there.

Every attempt — one row per (business, channel), success or failure — is
logged to ``core.models.AdminMessageLog`` for audit; see that model's
docstring for why it is not folded into ``visitor.SmsLog``.
"""

import logging

from django.db import transaction

from core.models import AdminMessageLog, MarketingPushLog

logger = logging.getLogger(__name__)


def _personalize(text: str, business) -> str:
    """Replaces the one supported template token. Kept to a single token on
    purpose — a compose box is not a template engine, and staff already write
    these by hand per send; more tokens can be added if a real need shows up."""
    return (text or '').replace('{business_name}', business.title)


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
        failed = _precheck_push(business, personalized_title, personalized_body, actor) \
            if channel == 'PUSH' else _precheck_sms(business, personalized_body, actor)
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


def _precheck_push(business, title, body, actor):
    from api.services import push

    if not push.is_configured():
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='PUSH',
            title=title, body=body, status='FAILED',
            error_detail='سرویس اعلان (FCM) پیکربندی نشده است',
        )
    return None


def _precheck_sms(business, body, actor):
    phone = business.user.phone if business.user_id else ''
    if not phone:
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='SMS',
            title='', body=body, status='FAILED',
            error_detail='این کسب‌وکار شماره تلفن مالک ندارد',
        )
    return None


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

    if not push.is_configured():
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='PUSH',
            title=title, body=body, status='FAILED',
            error_detail='سرویس اعلان (FCM) پیکربندی نشده است',
        )

    try:
        delivered = push.send_to_user(business.user_id, title=title, body=body)
    except Exception as exc:
        logger.exception('Admin push to business %s failed', business.id)
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='PUSH',
            title=title, body=body, status='FAILED', error_detail=str(exc),
        )

    return AdminMessageLog.objects.create(
        business=business, sent_by=actor, channel='PUSH',
        title=title, body=body,
        status='SENT' if delivered else 'FAILED',
        error_detail='' if delivered else 'دستگاه فعالی برای این صاحب کسب‌وکار ثبت نشده است',
    )


def _send_sms(business, body, actor) -> AdminMessageLog:
    from api.sms import send_sms, signed

    phone = business.user.phone if business.user_id else ''
    if not phone:
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='SMS',
            title='', body=body, status='FAILED',
            error_detail='این کسب‌وکار شماره تلفن مالک ندارد',
        )

    ok, err = send_sms(phone, signed(body))
    return AdminMessageLog.objects.create(
        business=business, sent_by=actor, channel='SMS',
        title='', body=body,
        status='SENT' if ok else 'FAILED',
        error_detail='' if ok else err,
    )


# ── Promotional campaigns ────────────────────────────────────────────────────
# One code path for both audiences. The *only* thing that differs between
# pushing to customers and pushing to business owners is which table the
# recipients and their device tokens come from, so that difference is isolated
# to _visitor_recipients/_owner_recipients below and everything after it — the
# payload, the personalization, the loop, the counters, the audit row — is
# literally the same code. Two parallel implementations is exactly how the two
# audiences would end up supporting different features.


def _visitor_recipients(filters, exclude_opted_out):
    """``(matched, reachable)`` for the customer audience.

    ``matched`` is everyone the filter selected; ``reachable`` is the subset
    with at least one active device token, as ``(id, display_name)`` pairs.
    Both are returned because the gap between them — people who never granted
    notification permission — is the most useful number on the report, and it
    is unrecoverable once you only count the ones you could reach.
    """
    from core import segments

    qs = segments.visitor_queryset(filters, exclude_opted_out=exclude_opted_out)
    matched = qs.count()
    reachable = list(
        qs.filter(device_tokens__is_active=True)
        .distinct()
        .values_list('id', 'full_name')
    )
    return matched, reachable


def _owner_recipients(filters, exclude_opted_out=None):
    """``(matched, reachable)`` for the business-owner audience.

    ``exclude_opted_out`` is accepted and ignored so the two recipient
    functions stay interchangeable: ``Visitor.marketing_opt_out`` has no
    owner-side equivalent (see core/segments.py — owners have no marketing
    consent flag), and silently accepting the argument is clearer than making
    the caller special-case which audience takes it.
    """
    from api.models import User
    from core import segments

    owner_ids = segments.owner_ids_for_segment(filters)
    reachable = list(
        User.objects.filter(id__in=owner_ids, device_tokens__is_active=True)
        .distinct()
        .values_list('id', 'name')
    )
    return len(owner_ids), reachable


#: audience key → (recipient lookup, push delivery function). Adding a third
#: audience later means adding a row here, not a third branch in the sender.
_AUDIENCE_DISPATCH = {
    MarketingPushLog.AUDIENCE_VISITOR: _visitor_recipients,
    MarketingPushLog.AUDIENCE_OWNER: _owner_recipients,
}


def _deliver(audience, recipient_id, campaign):
    from api.services import push

    if audience == MarketingPushLog.AUDIENCE_VISITOR:
        return push.deliver_to_visitor(
            recipient_id, title=campaign.title, body=campaign.body,
            data=campaign.data_payload(), image=campaign.image_url or None,
            link=campaign.link or None,
        )
    return push.deliver_to_user(
        recipient_id, title=campaign.title, body=campaign.body,
        data=campaign.data_payload(), image=campaign.image_url or None,
        link=campaign.link or None,
    )


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
        if skip_reminded:
            subs = subs.filter(expired_reminder_sent=False)
    else:
        subs = Subscription.objects.filter(
            status='active', ends_at__gt=now, ends_at__lte=now + timedelta(days=days_before),
        ).select_related('user', 'plan').order_by('ends_at')
        if skip_reminded:
            subs = subs.filter(reminder_sent=False)
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


#: Recipients per outbox message. Small enough that one retry never re-sends a
#: huge batch, large enough that a campaign is a handful of rows.
CHUNK_SIZE = 100


def _chunks(items, size=CHUNK_SIZE):
    for i in range(0, len(items), size):
        yield items[i:i + size]


def _enqueue_expiry_chunks(log, message, items, skip_flagged):
    """``items`` is ``[(subscription_id, mode), ...]`` in send order."""
    from core.outbox.service import enqueue

    for chunk in _chunks(items):
        enqueue('core.expiry_sms_chunk', {
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

    Funded the same way as :func:`send_admin_message` — straight through
    ``api.sms.send_sms``, never ``accounting.usage.consume_sms``: a
    staff-triggered renewal notice is a platform cost, not something billed
    to the owner's own SMS quota (see this module's docstring).
    """
    from accounting.models import Plan
    from core.models import ExpirySmsLog

    subs = expiry_sms_audience(
        days_before, plan_ids=plan_ids, skip_reminded=skip_reminded, mode=mode,
    )
    sub_ids = list(subs.values_list('id', flat=True))

    plan_names = list(Plan.objects.filter(id__in=plan_ids).values_list('name', flat=True)) if plan_ids else []
    with transaction.atomic():
        log = ExpirySmsLog.objects.create(
            source=ExpirySmsLog.SOURCE_MANUAL,
            message=message, days_before=days_before,
            filters={'plans': plan_names, 'skip_reminded': skip_reminded, 'mode': mode},
            matched_count=len(sub_ids), sent_count=0, failed_count=0,
            recipients=[], sent_by=actor,
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

    from core.models import ExpirySmsLog

    by_user = {}
    for sub in subs:
        current = by_user.get(sub.user_id)
        if current is None or sub.ends_at > current.ends_at:
            by_user[sub.user_id] = sub
    picked = sorted(by_user.values(), key=lambda sub: sub.ends_at, reverse=True)

    now = timezone.now()
    with transaction.atomic():
        log = ExpirySmsLog.objects.create(
            source=ExpirySmsLog.SOURCE_MANUAL,
            message=message, days_before=0,
            filters={'mode': 'selection', 'plans': sorted({sub.plan.name for sub in picked})},
            matched_count=len(picked), sent_count=0, failed_count=0,
            recipients=[], sent_by=actor,
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
        flag = 'expired_reminder_sent' if mode == 'expired' else 'reminder_sent'
        # A double-submitted campaign (or a retried chunk) must not text twice.
        if payload.get('skip_flagged') and getattr(sub, flag):
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
    from api.sms import send_sms, signed

    matched = sent = failed = 0
    recipients = []
    for sub in subs:
        matched += 1
        mode = mode_of(sub)
        name = sub.user.name or ''
        phone = getattr(sub.user, 'phone', None)
        if not phone:
            failed += 1
            recipients.append({'name': name, 'phone': '', 'status': 'FAILED', 'error': 'بدون شماره تلفن'})
            continue
        personalized = render_reminder_text(
            message, owner_name=name, days_left=days_for_sms(sub, mode), plan_name=sub.plan.name,
        )
        try:
            ok, err = send_sms(phone, signed(personalized))
        except Exception as exc:  # noqa: BLE001 — one bad send must not sink the whole blast
            ok, err = False, str(exc)
        if ok:
            sent += 1
            recipients.append({'name': name, 'phone': phone, 'status': 'SENT'})
            flag = 'expired_reminder_sent' if mode == 'expired' else 'reminder_sent'
            setattr(sub, flag, True)
            sub.save(update_fields=[flag])
        else:
            failed += 1
            logger.warning('Expiry SMS to %s failed: %s', phone, err)
            recipients.append({'name': name, 'phone': phone, 'status': 'FAILED', 'error': err or ''})
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
    from api.sms import send_sms, signed
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
        try:
            ok, err = send_sms(phone, signed(personalized))
        except Exception as exc:  # noqa: BLE001
            ok, err = False, str(exc)

    return ExpirySmsLog.objects.create(
        source=ExpirySmsLog.SOURCE_TEST,
        message=message, days_before=days_before,
        matched_count=1, sent_count=1 if ok else 0, failed_count=0 if ok else 1,
        recipients=[{
            'name': name, 'phone': phone,
            'status': 'SENT' if ok else 'FAILED', 'error': '' if ok else (err or ''),
        }],
        sent_by=actor,
    )


def send_push_campaign(campaign, audience, filters, definition, *,
                       group_id=None, exclude_opted_out=True, actor=None) -> MarketingPushLog:
    """
    Queue ``campaign`` (a :class:`core.campaigns.PushCampaign`) for everyone in
    one audience matching ``filters``, and record it.

    Recipients without an active device token are skipped rather than failed —
    there is nothing to send to, same as any other channel someone hasn't
    opted into — but they are still counted in ``recipient_count`` so the
    report can show how much of the audience is simply unreachable.

    ``{full_name}`` in the title/body is replaced per recipient; see
    :meth:`PushCampaign.personalized`.

    The audience is resolved here (so the counts are right at once) but the
    delivery loop runs in the outbox worker, in chunks of ``CHUNK_SIZE``
    recipients: the returned log has ``recipient_count``/``reachable_count``
    and its ``delivered_count``/``failed_count``/``dead_token_count`` grow as
    chunks complete (:func:`deliver_push_chunk`), so a large campaign no longer
    holds the admin request open.
    """
    import uuid
    from dataclasses import asdict

    from api.services import push
    from core.outbox.service import enqueue

    lookup = _AUDIENCE_DISPATCH[audience]
    matched, reachable = lookup(filters, exclude_opted_out)

    configured = push.is_configured()
    if not configured and reachable:
        logger.warning(
            'Campaign skipped for %d reachable %s — FCM not configured',
            len(reachable), audience,
        )

    with transaction.atomic():
        log = MarketingPushLog.objects.create(
            group_id=group_id or uuid.uuid4(),
            audience=audience,
            definition=definition,
            title=campaign.title,
            body=campaign.body,
            image_url=campaign.image_url,
            link=campaign.link,
            deep_link=campaign.deep_link,
            recipient_count=matched,
            reachable_count=len(reachable),
            delivered_count=0,
            failed_count=0,
            dead_token_count=0,
            sent_by=actor,
        )
        if configured:
            for chunk in _chunks(reachable):
                enqueue('core.push_campaign_chunk', {
                    'log_id': log.id, 'audience': audience,
                    'campaign': asdict(campaign),
                    'recipients': [[rid, name] for rid, name in chunk],
                })
    return log


def deliver_push_chunk(payload):
    """Outbox handler body (``core.push_campaign_chunk``): deliver one chunk and
    add its totals to the campaign log with atomic counter updates."""
    from django.db.models import F

    from api.services.push import SendResult
    from core.campaigns import PushCampaign

    audience = payload['audience']
    campaign = PushCampaign(**payload['campaign'])
    totals = SendResult()
    for recipient_id, display_name in payload['recipients']:
        try:
            totals += _deliver(audience, recipient_id, campaign.personalized(display_name))
        except Exception:
            logger.exception('Campaign push to %s %s failed', audience, recipient_id)
            totals.failed += 1

    MarketingPushLog.objects.filter(pk=payload['log_id']).update(
        delivered_count=F('delivered_count') + totals.delivered,
        failed_count=F('failed_count') + totals.failed,
        dead_token_count=F('dead_token_count') + totals.dead,
    )
