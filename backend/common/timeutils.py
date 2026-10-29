"""Project-timezone helpers (no Django app imports)."""

from zoneinfo import ZoneInfo

from django.conf import settings


def project_tz():
    return ZoneInfo(settings.TIME_ZONE)
