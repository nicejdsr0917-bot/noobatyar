"""A business's opening window for one calendar day.

Working hours are wall-clock times in the project's timezone, not UTC: anchoring
them to UTC shifted every slot by the offset (+03:30 for Tehran). Shared by the
available-slots endpoint and by client-booking validation so the two can never
disagree about when a business is open.
"""

from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from django.conf import settings

LOCAL_TZ = ZoneInfo(settings.TIME_ZONE)


def working_window(business, target_date):
    """``(opens_at, closes_at)`` aware datetimes for ``target_date`` (a local date)."""
    midnight = datetime(target_date.year, target_date.month, target_date.day, tzinfo=LOCAL_TZ)
    return (
        midnight + timedelta(hours=business.work_start_hour, minutes=business.work_start_minute),
        midnight + timedelta(hours=business.work_end_hour, minutes=business.work_end_minute),
    )
