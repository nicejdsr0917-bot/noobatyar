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

from core.models import AdminMessageLog, MarketingPushLog

logger = logging.getLogger(__name__)


def _personalize(text: str, business) -> str:
    """Replaces the one supported template token. Kept to a single token on
    purpose — a compose box is not a template engine, and staff already write
    these by hand per send; more tokens can be added if a real need shows up."""
    return (text or '').replace('{business_name}', business.title)


def send_admin_message(business, title: str, body: str, channels, actor=None) -> list[AdminMessageLog]:
    """
    Sends a personalized message to ``business``'s owner on each channel in
    ``channels`` (an iterable of ``'PUSH'``/``'SMS'``), logging every attempt.

    Returns the created :class:`AdminMessageLog` rows, so the calling view can
    report success/failure per channel without a second query.
    """
    personalized_title = _personalize(title, business)
    personalized_body = _personalize(body, business)
    logs = []

    if 'PUSH' in channels:
        logs.append(_send_push(business, personalized_title, personalized_body, actor))
    if 'SMS' in channels:
        logs.append(_send_sms(business, personalized_body, actor))

    return logs


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


def send_expiry_sms(message: str, days_before: int, actor=None, *,
                    plan_ids=None, skip_reminded=True, mode='expiring'):
    """
    Sends ``message`` to everyone in :func:`expiry_sms_audience`, filled per
    owner by ``accounting.models.render_reminder_text`` (``{full_name}``,
    ``{days}`` — their actual days left (days since expiry in ``expired``
    mode), not the window edge — and ``{plan}``), and records one
    :class:`core.models.ExpirySmsLog` row.

    Each subscription reached is marked as reminded — ``reminder_sent`` (the
    same flag the automatic reminder sets, so the cron skips it) in the
    default mode, ``expired_reminder_sent`` in ``expired`` mode.

    Funded the same way as :func:`send_admin_message` — straight through
    ``api.sms.send_sms``, never ``accounting.usage.consume_sms``: a
    staff-triggered renewal notice is a platform cost, not something billed
    to the owner's own SMS quota (see this module's docstring).
    """
    from accounting.models import Plan, render_reminder_text
    from api.sms import send_sms, signed
    from core.models import ExpirySmsLog

    subs = expiry_sms_audience(
        days_before, plan_ids=plan_ids, skip_reminded=skip_reminded, mode=mode,
    )
    flag = 'expired_reminder_sent' if mode == 'expired' else 'reminder_sent'

    matched = sent = failed = 0
    recipients = []
    for sub in subs:
        matched += 1
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
            setattr(sub, flag, True)
            sub.save(update_fields=[flag])
        else:
            failed += 1
            logger.warning('Expiry SMS to %s failed: %s', phone, err)
            recipients.append({'name': name, 'phone': phone, 'status': 'FAILED', 'error': err or ''})

    plan_names = list(Plan.objects.filter(id__in=plan_ids).values_list('name', flat=True)) if plan_ids else []
    return ExpirySmsLog.objects.create(
        source=ExpirySmsLog.SOURCE_MANUAL,
        message=message, days_before=days_before,
        filters={'plans': plan_names, 'skip_reminded': skip_reminded, 'mode': mode},
        matched_count=matched, sent_count=sent, failed_count=failed,
        recipients=recipients, sent_by=actor,
    )


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
    Send ``campaign`` (a :class:`core.campaigns.PushCampaign`) to everyone in
    one audience matching ``filters``, and record the result.

    Recipients without an active device token are skipped rather than failed —
    there is nothing to send to, same as any other channel someone hasn't
    opted into — but they are still counted in ``recipient_count`` so the
    report can show how much of the audience is simply unreachable.

    ``{full_name}`` in the title/body is replaced per recipient; see
    :meth:`PushCampaign.personalized`.

    A synchronous loop, same as the reminder job and every other admin bulk
    action in this codebase — acceptable for the segment sizes this admin
    panel deals with today. A genuinely large campaign (tens of thousands of
    recipients) would need this moved to a background task instead of a
    request/response cycle; that is a real scaling note, not something this
    function tries to solve.
    """
    import uuid

    from api.services import push
    from api.services.push import SendResult

    lookup = _AUDIENCE_DISPATCH[audience]
    matched, reachable = lookup(filters, exclude_opted_out)

    totals = SendResult()
    if not push.is_configured():
        if reachable:
            logger.warning(
                'Campaign skipped for %d reachable %s — FCM not configured',
                len(reachable), audience,
            )
    else:
        for recipient_id, display_name in reachable:
            try:
                totals += _deliver(audience, recipient_id, campaign.personalized(display_name))
            except Exception:
                logger.exception('Campaign push to %s %s failed', audience, recipient_id)
                totals.failed += 1

    return MarketingPushLog.objects.create(
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
        delivered_count=totals.delivered,
        failed_count=totals.failed,
        dead_token_count=totals.dead,
        sent_by=actor,
    )
