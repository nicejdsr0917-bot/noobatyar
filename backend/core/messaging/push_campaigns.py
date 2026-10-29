"""
Promotional push campaigns.

One code path for both audiences. The *only* thing that differs between
pushing to customers and pushing to business owners is which table the
recipients and their device tokens come from, so that difference is isolated
to _visitor_recipients/_owner_recipients below and everything after it — the
payload, the personalization, the loop, the counters, the audit row — is
literally the same code.
"""

import logging

from django.db import transaction

from core.models import MarketingPushLog

from ._common import enqueue_chunks

logger = logging.getLogger('core.messaging')


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


#: audience key → recipient lookup. Adding a third audience later means adding
#: a row here (and a branch in _deliver), not a third branch in the sender.
_AUDIENCE_DISPATCH = {
    MarketingPushLog.AUDIENCE_VISITOR: _visitor_recipients,
    MarketingPushLog.AUDIENCE_OWNER: _owner_recipients,
}


def audience_recipients(audience, filters, exclude_opted_out=True):
    """``(matched, reachable)`` for ``audience`` via the dispatch table."""
    return _AUDIENCE_DISPATCH[audience](filters, exclude_opted_out)


def _deliver(audience, recipient_id, campaign):
    from api.services import push

    send = (
        push.deliver_to_visitor if audience == MarketingPushLog.AUDIENCE_VISITOR
        else push.deliver_to_user
    )
    return send(
        recipient_id, title=campaign.title, body=campaign.body,
        data=campaign.data_payload(), image=campaign.image_url or None,
        link=campaign.link or None,
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

    matched, reachable = audience_recipients(audience, filters, exclude_opted_out)

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
            enqueue_chunks('core.push_campaign_chunk', reachable, lambda chunk: {
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
    from core import messaging
    from core.campaigns import PushCampaign

    audience = payload['audience']
    campaign = PushCampaign(**payload['campaign'])
    totals = SendResult()
    for recipient_id, display_name in payload['recipients']:
        try:
            # Via the package so patching core.messaging._deliver takes effect.
            totals += messaging._deliver(audience, recipient_id, campaign.personalized(display_name))
        except Exception:
            logger.exception('Campaign push to %s %s failed', audience, recipient_id)
            totals.failed += 1

    MarketingPushLog.objects.filter(pk=payload['log_id']).update(
        delivered_count=F('delivered_count') + totals.delivered,
        failed_count=F('failed_count') + totals.failed,
        dead_token_count=F('dead_token_count') + totals.dead,
    )
