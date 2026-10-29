"""DB-backed outbox: work that must happen "soon, but not inside the request".

A row is written (in the caller's transaction, when there is one) and a
separate worker process (``manage.py run_outbox``) performs it. This replaces
daemon threads: a deploy or crash can no longer lose a notification that was
already promised, and bulk sends no longer hold an HTTP request open.
"""

from django.db import models


class OutboxMessage(models.Model):
    PENDING = 'pending'
    PROCESSING = 'processing'
    DONE = 'done'
    FAILED = 'failed'
    STATUS_CHOICES = [
        (PENDING, 'در صف'),
        (PROCESSING, 'در حال پردازش'),
        (DONE, 'انجام شد'),
        (FAILED, 'ناموفق'),
    ]

    kind = models.CharField(max_length=80)
    payload = models.JSONField(default=dict)
    # Idempotency: a second enqueue with the same key is a no-op.
    dedupe_key = models.CharField(max_length=200, unique=True, null=True, blank=True)
    status = models.CharField(max_length=12, choices=STATUS_CHOICES, default=PENDING)
    attempts = models.PositiveIntegerField(default=0)
    next_attempt_at = models.DateTimeField()
    # When the worker claimed the row; used to recover rows whose worker died.
    locked_at = models.DateTimeField(null=True, blank=True)
    last_error = models.TextField(blank=True, default='')
    created_at = models.DateTimeField(auto_now_add=True)
    processed_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        indexes = [models.Index(fields=['status', 'next_attempt_at'], name='outbox_status_next_idx')]

    def __str__(self):
        return f'{self.kind}#{self.pk} [{self.status}]'
