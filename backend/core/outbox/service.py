"""enqueue() and the worker core (claim / process / recover)."""

import logging
import random
import threading
from datetime import timedelta

from django.db import IntegrityError, connection, transaction
from django.db.models import Q
from django.utils import timezone

from .models import OutboxMessage

logger = logging.getLogger(__name__)

MAX_ATTEMPTS = 6
BACKOFF_BASE_SECONDS = 30
BACKOFF_CAP_SECONDS = 3600
STUCK_AFTER = timedelta(minutes=10)
# Alive handlers refresh locked_at this often (must be << STUCK_AFTER).
HEARTBEAT_INTERVAL = 60

_HANDLERS = {}


def register(kind):
    """Decorator: ``@register('appointment.booking_sms')`` on ``fn(payload)``."""
    def deco(fn):
        _HANDLERS[kind] = fn
        return fn
    return deco


def get_handler(kind):
    from . import handlers  # noqa: F401 — populates the registry on first use
    return _HANDLERS.get(kind)


def enqueue(kind, payload=None, dedupe_key=None, delay_seconds=0):
    """Write one outbox row. Returns the row, or None if ``dedupe_key`` exists.

    Runs in the caller's transaction when there is one, so a rollback takes the
    message with it. The insert sits in its own savepoint so a duplicate key
    does not poison the surrounding transaction.
    """
    try:
        with transaction.atomic():
            return OutboxMessage.objects.create(
                kind=kind,
                payload=payload or {},
                dedupe_key=dedupe_key,
                next_attempt_at=timezone.now() + timedelta(seconds=delay_seconds),
            )
    except IntegrityError:
        if dedupe_key is None:
            raise
        logger.info('Outbox %s deduplicated (%s)', kind, dedupe_key)
        return None


def safe_enqueue(kind, payload=None, dedupe_key=None, delay_seconds=0):
    """enqueue() that never raises: for call sites where a notification must
    not be able to fail the request that triggered it."""
    try:
        return enqueue(kind, payload, dedupe_key, delay_seconds)
    except Exception:
        logger.exception('Failed to enqueue outbox message %s (%s)', kind, dedupe_key)
        return None


def backoff_seconds(attempts):
    base = min(BACKOFF_BASE_SECONDS * (2 ** max(attempts - 1, 0)), BACKOFF_CAP_SECONDS)
    return base * random.uniform(0.8, 1.2)


# Lanes: campaigns send thousands of messages inline, so they run on their own
# worker and can never delay a booking / cancellation / decision SMS.
LANE_DEFAULT, LANE_BULK, LANE_ALL = 'default', 'bulk', 'all'
BULK_KINDS = frozenset({
    'promo.campaign', 'core.push_campaign_chunk', 'core.expiry_sms_chunk',
})


def lane_of(kind):
    if kind in BULK_KINDS or 'campaign' in kind or kind.endswith('_chunk'):
        return LANE_BULK
    return LANE_DEFAULT


def _bulk_q():
    return (Q(kind__in=BULK_KINDS) | Q(kind__contains='campaign') | Q(kind__endswith='_chunk'))


def _filter_lane(qs, lane):
    if lane == LANE_BULK:
        return qs.filter(_bulk_q())
    if lane == LANE_DEFAULT:
        return qs.exclude(_bulk_q())
    return qs


# --- per-message context (set while a handler runs) -------------------------
_current = threading.local()


def current_message_id():
    return getattr(_current, 'message_id', None)


def step_done(step):
    """True if the running message already performed side effect ``step``
    (e.g. the SMS was accepted by the provider before a crash)."""
    mid = current_message_id()
    if mid is None:
        return False
    steps = OutboxMessage.objects.filter(id=mid).values_list('done_steps', flat=True).first()
    return step in (steps or [])


def mark_step(step):
    """Durably record, right after the side effect, that ``step`` happened.
    Autocommit unless the caller is inside a transaction (handlers are not)."""
    mid = current_message_id()
    if mid is None:
        return
    with transaction.atomic():
        qs = OutboxMessage.objects.filter(id=mid)
        if connection.vendor != 'sqlite':
            qs = qs.select_for_update()
        steps = list(qs.values_list('done_steps', flat=True).first() or [])
        if step not in steps:
            steps.append(step)
            OutboxMessage.objects.filter(id=mid).update(done_steps=steps)


def heartbeat(message_id):
    """Refresh locked_at of a message that is still being worked on."""
    return OutboxMessage.objects.filter(
        id=message_id, status=OutboxMessage.PROCESSING,
    ).update(locked_at=timezone.now())


def claim_one(lane=LANE_ALL):
    """Claim a single due message; ``locked_at`` is stamped right now, so the
    'processing' age always reflects the message actually being worked on."""
    now = timezone.now()
    with transaction.atomic():
        qs = _filter_lane(OutboxMessage.objects.filter(
            status=OutboxMessage.PENDING, next_attempt_at__lte=now,
        ), lane).order_by('next_attempt_at', 'id')
        if connection.vendor != 'sqlite':
            qs = qs.select_for_update(skip_locked=True)
        msg = qs.first()
        if msg is None:
            return None
        OutboxMessage.objects.filter(id=msg.id).update(
            status=OutboxMessage.PROCESSING, locked_at=now,
        )
    msg.status, msg.locked_at = OutboxMessage.PROCESSING, now
    return msg


def claim_batch(batch_size=20, lane=LANE_ALL):
    """Claim up to ``batch_size`` messages (each stamped individually)."""
    out = []
    for _ in range(batch_size):
        msg = claim_one(lane)
        if msg is None:
            break
        out.append(msg)
    return out


def _beat_loop(message_id, stop, interval):
    opened = False
    try:
        while not stop.wait(interval):
            try:
                opened = True
                heartbeat(message_id)
            except Exception:  # noqa: BLE001 — never kill the handler over a missed beat
                logger.exception('Outbox heartbeat failed for #%s', message_id)
    finally:
        if opened:
            connection.close()


def process_message(msg, max_attempts=MAX_ATTEMPTS):
    """Run one claimed message OUTSIDE any transaction; record the outcome."""
    handler = get_handler(msg.kind)
    stop = threading.Event()
    beat = threading.Thread(
        target=_beat_loop, args=(msg.id, stop, HEARTBEAT_INTERVAL), daemon=True,
    )
    beat.start()
    _current.message_id = msg.id
    try:
        if handler is None:
            raise LookupError(f'no handler registered for {msg.kind!r}')
        handler(msg.payload)
    except Exception as exc:  # noqa: BLE001 — every failure goes through retry
        stop.set()
        beat.join()
        _current.message_id = None
        attempts = msg.attempts + 1
        error = f'{type(exc).__name__}: {exc}'[:2000]
        if attempts >= max_attempts:
            OutboxMessage.objects.filter(id=msg.id).update(
                status=OutboxMessage.FAILED, attempts=attempts, last_error=error,
                processed_at=timezone.now(), locked_at=None,
            )
            logger.error('Outbox %s#%s failed permanently: %s', msg.kind, msg.id, error)
        else:
            OutboxMessage.objects.filter(id=msg.id).update(
                status=OutboxMessage.PENDING, attempts=attempts, last_error=error,
                next_attempt_at=timezone.now() + timedelta(seconds=backoff_seconds(attempts)),
                locked_at=None,
            )
            logger.warning('Outbox %s#%s attempt %s failed: %s', msg.kind, msg.id, attempts, error)
        return False
    stop.set()
    beat.join()
    _current.message_id = None
    OutboxMessage.objects.filter(id=msg.id).update(
        status=OutboxMessage.DONE, processed_at=timezone.now(), locked_at=None,
    )
    return True


def recover_stuck(older_than=STUCK_AFTER):
    """Rows left 'processing' by a dead worker go back to pending."""
    cutoff = timezone.now() - older_than
    return OutboxMessage.objects.filter(
        status=OutboxMessage.PROCESSING, locked_at__lt=cutoff,
    ).update(status=OutboxMessage.PENDING, locked_at=None)


def run_batch(batch_size=20, max_attempts=MAX_ATTEMPTS, lane=LANE_ALL):
    """Claim (one at a time) and process up to ``batch_size`` messages.
    Returns the number handled."""
    n = 0
    for _ in range(batch_size):
        msg = claim_one(lane)
        if msg is None:
            break
        process_message(msg, max_attempts=max_attempts)
        n += 1
    return n
