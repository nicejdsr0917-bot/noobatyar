"""Project-timezone and epoch-millisecond helpers (no Django app imports).

Two *different* "epoch ms -> day" readings exist on purpose; clients mirror the
first one (audit A3, intentionally deferred -- do not "fix" it here):

* :func:`utc_date_tehran_midnight` -- the **UTC** calendar date of the instant,
  turned into midnight of that same date in Asia/Tehran.
* :func:`local_date_midnight` -- the **Tehran** calendar date of the instant,
  at Tehran midnight.

Helpers raise the raw ``ValueError``/``OSError``/``OverflowError`` from
``int()``/``fromtimestamp``; each caller keeps its own ``except`` tuple so its
error handling is unchanged.
"""

from datetime import date, datetime, time, timezone as dt_timezone
from zoneinfo import ZoneInfo

from django.conf import settings
from django.utils import timezone


def project_tz():
    return ZoneInfo(settings.TIME_ZONE)


def local_midnight(day: date, tz=None) -> datetime:
    """Wall-clock midnight of ``day`` in ``tz`` (default: the project zone).

    ``combine(..., tzinfo=tz)`` rather than ``.replace(hour=0)``, so the offset
    is the right one for the target wall time, not that of another instant.
    """
    return datetime.combine(day, time.min, tzinfo=tz or project_tz())


def epoch_ms_to_utc(value) -> datetime:
    """Epoch milliseconds (int or numeric string) -> aware UTC datetime."""
    return datetime.fromtimestamp(int(value) / 1000, tz=dt_timezone.utc)


def utc_date_tehran_midnight(value) -> datetime:
    """UTC calendar date of the epoch-ms ``value`` -> that date's Tehran midnight."""
    return local_midnight(epoch_ms_to_utc(value).date())


def local_date_midnight(value) -> datetime:
    """Tehran calendar date of the epoch-ms ``value`` -> that date's Tehran midnight."""
    return local_midnight(timezone.localtime(epoch_ms_to_utc(value)).date())
