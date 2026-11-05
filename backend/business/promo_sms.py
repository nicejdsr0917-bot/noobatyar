"""
business/promo_sms.py

An owner's promotional SMS to their own customers.

Flow: the app asks for a preview (who it reaches, how many billed parts, what
the owner has left), the owner confirms, and :func:`create_campaign` writes a
``PromoCampaign`` plus one ``PromoCampaignRecipient`` per person holding the
exact final text. Sending then happens off the request in
:func:`process_campaign` — a salon's customer list can be a few hundred people
and the provider takes one HTTP call per message, far past a request timeout.

It runs in the outbox worker: one ``promo.campaign`` row is queued in the same
transaction that creates the campaign. Recipients are claimed one row at a time
(PENDING -> SENDING via a conditional UPDATE), so if a deploy kills the worker
mid-send (the outbox also re-delivers a stuck row), ``python manage.py send_promo_campaigns``
picks up exactly the rows that are still PENDING and nobody is texted twice.

Billing and line rules are the same as every other message an owner chooses to
send: each recipient's parts are charged through ``accounting.usage.consume_sms``
(refunded if the provider rejects it), every attempt lands in
``visitor.SmsLog``, and ``api.sms.signed`` appends the footer and the mandatory
لغو11 — the advertising line only delivers 08:00–22:00, so campaigns are refused
outside that window instead of being charged for messages that never arrive.
"""

import logging
import math
from datetime import timedelta

from django.db import transaction
from django.db.models import Max
from django.utils import timezone

from accounting import entitlements, sms_billing, usage
from api.phone import is_iran_phone, normalize_phone
from api.sms import (
    SEND_WINDOW_END_HOUR, SEND_WINDOW_START_HOUR, signed, within_send_window,
)

logger = logging.getLogger(__name__)

QUOTA_DETAIL = 'اعتبار پیامک تمام شده است'

# What the owner may type. Two Persian parts of body leaves room for a long
# name and the footer within three billed parts; anything longer is almost
# always a paste accident and would silently triple the bill.
MAX_MESSAGE_CHARS = 250

RECENT_DAYS = 30
INACTIVE_DAYS = 60

NAME_TOKEN = '{name}'
BUSINESS_TOKEN = '{business}'

# Starting points offered in the app, same {name}/{business} tokens.
READY_TEXTS = [
    '{name} عزیز، دلمان برایتان تنگ شده\n'
    'این هفته با نوبت‌گیری آنلاین از {business} منتظرتان هستیم',
    '{name} عزیز، تا پایان این هفته روی همه‌ی خدمات {business} تخفیف ویژه داریم\n'
    'برای رزرو نوبت همین حالا اقدام کنید',
    '{name} عزیز، خدمت جدید {business} راه‌اندازی شد\n'
    'برای اطلاعات بیشتر و رزرو با ما تماس بگیرید',
    '{name} عزیز، به مناسبت ایام پیش رو نوبت‌های {business} در حال تکمیل شدن است\n'
    'پیشنهاد می‌کنیم زودتر رزرو کنید',
]

# GSM 03.38 basic set — a message using only these travels 7-bit (160/153 per
# part). Anything else, which means any Persian letter, forces UCS-2 (70/67).
_GSM7 = set(
    "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
    "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
)


def sms_parts(text: str) -> int:
    """How many billed parts ``text`` costs — the operator's arithmetic."""
    if not text:
        return 0
    if all(ch in _GSM7 for ch in text):
        length, single, multi = len(text), 160, 153
    else:
        # UCS-2 counts UTF-16 code units: an emoji is two.
        length, single, multi = len(text.encode('utf-16-le')) // 2, 70, 67
    return 1 if length <= single else math.ceil(length / multi)


def render(message: str, *, name: str, business_title: str) -> str:
    """The exact text one recipient gets: tokens filled, footer and لغو11 on."""
    body = (message or '').replace(NAME_TOKEN, name or '').replace(BUSINESS_TOKEN, business_title or '')
    return signed(body)


# ── Audience ──────────────────────────────────────────────────────────────────

FOLLOWERS = 'followers'


def owner_customer_ids(business):
    """Visitors already in the owner's own customer list, whose numbers the
    owner sees anyway. Any other follower is shown by first name only."""
    from visitor.models import Visitor
    return set(Visitor.readable_by(business.user).values_list('id', flat=True))


def _audience_visitors(business, audience):
    """Visitors ``audience`` selects, from the owner's own customer list.

    The base is exactly the list the owner sees in the app
    (``Visitor.readable_by``) minus anyone they archived — an archived person is
    someone the owner chose to stop dealing with, so a campaign must not reach
    them either. The recency cuts use appointments at *any* of the owner's
    businesses, matching how that list is scoped; cancelled bookings don't count
    as a visit.
    """
    from appointment.models import Appointment
    from visitor.models import Visitor

    owner = business.user
    if audience == FOLLOWERS:
        # Following counts as consent to promotional SMS from that business,
        # unless the customer opted out of marketing altogether; the owner's
        # archive still wins.
        return (Visitor.objects.filter(follows__business=business, marketing_opt_out=False)
                .exclude(archives__owner=owner).order_by('id'))
    base_ids = set(
        Visitor.readable_by(owner)
        .exclude(archives__owner=owner)
        .values_list('id', flat=True)
    )
    if audience == 'all' or not base_ids:
        selected = base_ids
    else:
        now = timezone.now()
        last_visits = (
            Appointment.objects
            .filter(business__user=owner, visitor_id__in=base_ids, appointment_date__lte=now)
            .exclude(status='CANCELLED')
            .values('visitor_id')
            .annotate(last=Max('appointment_date'))
        )
        if audience == 'recent':
            cutoff = now - timedelta(days=RECENT_DAYS)
            selected = {row['visitor_id'] for row in last_visits if row['last'] >= cutoff}
        elif audience == 'inactive':
            cutoff = now - timedelta(days=INACTIVE_DAYS)
            selected = {row['visitor_id'] for row in last_visits if row['last'] < cutoff}
        else:
            selected = set()

    return Visitor.objects.filter(id__in=selected).order_by('id')


def audience_counts(business):
    return {key: _audience_visitors(business, key).count() for key, _ in _audience_choices()}


def _audience_choices():
    from .models import PromoCampaign
    return PromoCampaign.AUDIENCE_CHOICES


def _recipients(business, audience, message):
    """(name, phone, visitor, text, parts) for everyone reachable by SMS."""
    rows = []
    known = owner_customer_ids(business) if audience == FOLLOWERS else None
    for visitor in _audience_visitors(business, audience):
        phone = normalize_phone(visitor.phone_number or '')
        if not is_iran_phone(phone):
            continue
        name = visitor.full_name
        if known is not None and visitor.id not in known:
            name = (name or '').strip().split(' ')[0]
        text = render(message, name=name, business_title=business.title)
        rows.append((name, phone, visitor, text, sms_parts(text)))
    return rows


# ── Preview / create ──────────────────────────────────────────────────────────

class CampaignError(Exception):
    """Something the owner has to change before this campaign can go out."""

    def __init__(self, message, code='invalid'):
        super().__init__(message)
        self.message = message
        self.code = code


def available_credit(owner):
    """SMS parts the owner can still spend, or None for unlimited."""
    balance = usage.sms_balance(owner)
    if balance['quota'] == entitlements.UNLIMITED:
        return None
    return max(balance['monthly_remaining'], 0) + max(balance['wallet'], 0)


def _validate_message(message):
    message = (message or '').strip()
    if not message:
        raise CampaignError('متن پیامک خالی است.', 'empty')
    if len(message) > MAX_MESSAGE_CHARS:
        raise CampaignError(f'متن پیامک نباید بیشتر از {MAX_MESSAGE_CHARS} کاراکتر باشد.', 'too_long')
    return message


def preview(business, audience, message):
    """Everything the confirm dialog shows, computed from the real recipients."""
    from .models import PromoCampaign

    if audience not in dict(PromoCampaign.AUDIENCE_CHOICES):
        raise CampaignError('گروه مخاطب نامعتبر است.', 'audience')
    message = (message or '').strip()
    rows = _recipients(business, audience, message) if message else []
    total_parts = sum(r[4] for r in rows)
    available = available_credit(business.user)
    sample = render(message, name='مریم', business_title=business.title) if message else ''
    return {
        'audience': audience,
        'recipient_count': len(rows),
        'total_parts': total_parts,
        'parts_per_message': sms_parts(sample),
        'sample_text': sample,
        'sample_length': len(sample.encode('utf-16-le')) // 2 if sample else 0,
        'available_credit': available,
        'enough_credit': available is None or available >= total_parts,
        'within_send_window': within_send_window(),
    }


def create_campaign(business, audience, message, actor):
    """Validate, persist and start sending. Returns the PromoCampaign."""
    from .models import PromoCampaign, PromoCampaignRecipient

    if not entitlements.has_feature(business.user, entitlements.FEATURE_PROMOTIONAL_SMS):
        raise CampaignError('پیامک تبلیغاتی در پلن فعلی شما فعال نیست.', 'feature')
    message = _validate_message(message)
    if audience not in dict(PromoCampaign.AUDIENCE_CHOICES):
        raise CampaignError('گروه مخاطب نامعتبر است.', 'audience')
    if not within_send_window():
        raise CampaignError(
            f'ارسال پیامک تبلیغاتی فقط بین ساعت {SEND_WINDOW_START_HOUR} تا '
            f'{SEND_WINDOW_END_HOUR} امکان‌پذیر است.',
            'window',
        )
    if PromoCampaign.objects.filter(business=business, status='SENDING').exists():
        raise CampaignError('یک کمپین دیگر هنوز در حال ارسال است؛ کمی بعد دوباره تلاش کنید.', 'busy')

    rows = _recipients(business, audience, message)
    if not rows:
        raise CampaignError('در این گروه مشتری‌ای با شماره‌ی معتبر وجود ندارد.', 'empty_audience')
    total_parts = sum(r[4] for r in rows)
    available = available_credit(business.user)
    if available is not None and available < total_parts:
        raise CampaignError(
            f'این کمپین {total_parts} پیامک لازم دارد ولی اعتبار شما {available} پیامک است.',
            'credit',
        )

    with transaction.atomic():
        campaign = PromoCampaign.objects.create(
            business=business, created_by=actor, audience=audience, message=message,
            recipient_count=len(rows), total_parts=total_parts,
        )
        PromoCampaignRecipient.objects.bulk_create([
            PromoCampaignRecipient(
                campaign=campaign, visitor=visitor, name=name or '', phone=phone,
                text=text, parts=parts,
            )
            for name, phone, visitor, text, parts in rows
        ])
        start_background_send(campaign.id)
    return campaign


# ── Sending ───────────────────────────────────────────────────────────────────

def start_background_send(campaign_id):
    """Queue the campaign for the outbox worker (``promo.campaign``)."""
    from core.outbox.service import enqueue

    enqueue('promo.campaign', {'campaign_id': campaign_id}, dedupe_key=f'promo_campaign:{campaign_id}')


def process_campaign(campaign_id):
    """Send every still-PENDING recipient of one campaign, then close it out."""
    from .models import PromoCampaign, PromoCampaignRecipient

    campaign = PromoCampaign.objects.select_related('business').get(id=campaign_id)
    business = campaign.business
    owner_id = business.user_id

    known = owner_customer_ids(business) if campaign.audience == FOLLOWERS else None
    pending_ids = list(
        PromoCampaignRecipient.objects
        .filter(campaign_id=campaign_id, status='PENDING')
        .values_list('id', flat=True)
    )
    for rid in pending_ids:
        # Claim the row; losing the race means another sender already has it.
        if not PromoCampaignRecipient.objects.filter(id=rid, status='PENDING').update(status='SENDING'):
            continue
        recipient = PromoCampaignRecipient.objects.get(id=rid)

        outcome = sms_billing.send_metered_sms_detailed(
            # The owner's SMS report shows the linked visitor's number, so a
            # follower who is not their customer is logged without the link.
            owner_id, business.id,
            recipient.visitor_id if known is None or recipient.visitor_id in known else None,
            recipient.phone, recipient.text,
            amount=recipient.parts, quota_detail=QUOTA_DETAIL,
        )
        if outcome.status == sms_billing.SKIPPED_QUOTA:
            recipient.status = 'SKIPPED_QUOTA'
            recipient.error_detail = QUOTA_DETAIL
            recipient.save(update_fields=['status', 'error_detail'])
            continue

        ok = outcome.status == sms_billing.SENT
        recipient.status = 'SENT' if ok else 'FAILED'
        recipient.error_detail = '' if ok else outcome.error
        recipient.sent_at = timezone.now()
        recipient.save(update_fields=['status', 'error_detail', 'sent_at'])
        PromoCampaign.objects.filter(id=campaign_id).update(heartbeat_at=timezone.now())

    _finish_if_complete(campaign_id)


def _finish_if_complete(campaign_id):
    from django.db.models import Count, Q

    from .models import PromoCampaign, PromoCampaignRecipient

    counts = PromoCampaignRecipient.objects.filter(campaign_id=campaign_id).aggregate(
        open=Count('id', filter=Q(status__in=['PENDING', 'SENDING'])),
        sent=Count('id', filter=Q(status='SENT')),
        failed=Count('id', filter=Q(status='FAILED')),
        skipped=Count('id', filter=Q(status='SKIPPED_QUOTA')),
    )
    fields = {
        'sent_count': counts['sent'],
        'failed_count': counts['failed'],
        'skipped_count': counts['skipped'],
    }
    if not counts['open']:
        fields.update(status='DONE', finished_at=timezone.now())
    PromoCampaign.objects.filter(id=campaign_id).update(**fields)


def resume_stalled(stale_after=timedelta(minutes=10)):
    """Finish campaigns whose sender died. Returns how many were resumed.

    A campaign counts as dead when its sender has not touched ``heartbeat_at``
    for ``stale_after`` (a live sender touches it after every message). Its row
    stuck in SENDING goes back to PENDING — which can, in the worst case, resend
    the one message that was mid-flight when the process died; preferred over
    silently dropping it, and at most one per interrupted campaign.
    """
    from django.db.models import Q

    from .models import PromoCampaign, PromoCampaignRecipient

    cutoff = timezone.now() - stale_after
    resumed = 0
    dead = PromoCampaign.objects.filter(status='SENDING').filter(
        Q(heartbeat_at__lt=cutoff) | Q(heartbeat_at__isnull=True, created_at__lt=cutoff)
    )
    for campaign in dead:
        PromoCampaignRecipient.objects.filter(campaign=campaign, status='SENDING').update(status='PENDING')
        process_campaign(campaign.id)
        resumed += 1
    return resumed
