"""Helpers shared by the messaging modules: outbox chunking and the
"never let one bad SMS sink the blast" send wrapper."""


#: Recipients per outbox message. Small enough that one retry never re-sends a
#: huge batch, large enough that a campaign is a handful of rows.
CHUNK_SIZE = 100


def _chunks(items, size=CHUNK_SIZE):
    for i in range(0, len(items), size):
        yield items[i:i + size]


def enqueue_chunks(kind, items, payload_for_chunk):
    """Queue one outbox message of ``kind`` per ``CHUNK_SIZE`` slice of
    ``items``; ``payload_for_chunk(chunk)`` builds each payload."""
    from core.outbox.service import enqueue

    for chunk in _chunks(items):
        enqueue(kind, payload_for_chunk(chunk))


def send_signed_sms(phone, text):
    """``(ok, err)`` for one signed SMS; an exception counts as a failure."""
    from api.sms import send_sms, signed

    try:
        return send_sms(phone, signed(text))
    except Exception as exc:  # noqa: BLE001 — one bad send must not sink the whole blast
        return False, str(exc)


def recipient_row(name, phone, ok, err, *, always_error_key=False):
    """One entry of ``ExpirySmsLog.recipients``."""
    row = {'name': name, 'phone': phone, 'status': 'SENT' if ok else 'FAILED'}
    if not ok or always_error_key:
        row['error'] = '' if ok else (err or '')
    return row
