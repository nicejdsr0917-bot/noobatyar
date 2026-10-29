"""enqueue() and the worker core (claim / process / recover)."""

import logging
import random
from datetime import timedelta

from django.db import IntegrityError, connection, transaction
from django.utils import timezone

from .models import OutboxMessage

logger = logging.getLogger(__name__)

MAX_ATTEMPTS = 6
BACKOFF_BASE_SECONDS = 30
BACKOFF_CAP_SECONDS = 3600
STUCK_AFTER = timedelta(minutes=10)

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


def claim_batch(batch_size=20):
    """Short transaction: lock due rows, mark them processing, return them."""
    now = timezone.now()
    with transaction.atomic():
        qs = OutboxMessage.objects.filter(
            status=OutboxMessage.PENDING, next_attempt_at__lte=now,
        ).order_by('next_attempt_at', 'id')
        if connection.vendor != 'sqlite':
            qs = qs.select_for_update(skip_locked=True)
        ids = list(qs.values_list('id', flat=True)[:batch_size])
        if not ids:
            return []
        OutboxMessage.objects.filter(id__in=ids).update(
            status=OutboxMessage.PROCESSING, locked_at=now,
        )
    return list(OutboxMessage.objects.filter(id__in=ids).order_by('id'))


def process_message(msg, max_attempts=MAX_ATTEMPTS):
    """Run one claimed message OUTSIDE any transaction; record the outcome."""
    handler = get_handler(msg.kind)
    try:
        if handler is None:
            raise LookupError(f'no handler registered for {msg.kind!r}')
        handler(msg.payload)
    except Exception as exc:  # noqa: BLE001 — every failure goes through retry
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


def run_batch(batch_size=20, max_attempts=MAX_ATTEMPTS):
    """Claim and process one batch. Returns the number of messages handled."""
    batch = claim_batch(batch_size)
    for msg in batch:
        process_message(msg, max_attempts=max_attempts)
    return len(batch)
