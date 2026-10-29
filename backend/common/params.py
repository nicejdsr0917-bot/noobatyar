"""Query-string parameter parsing shared by the API views."""

from rest_framework import status

from common.responses import APIResponse


def positive_int(value, default: int) -> int:
    """``int(value)`` if it is a positive integer, else ``default``."""
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return parsed if parsed > 0 else default


def parse_business_id(raw) -> int:
    """``int(raw)``; ValueError/TypeError propagate to the caller."""
    return int(raw)


def business_id_param(request, invalid_message: str = 'business_id باید عدد باشد',
                      missing_message: str = 'پارامتر business_id الزامی است'):
    """Read ``?business_id=`` as an int.

    Returns ``(business_id, None)`` or ``(None, error_response)`` -- 400 with
    the standard envelope when it is missing or not an integer.
    """
    raw = request.query_params.get('business_id')
    if not raw:
        return None, APIResponse.error(missing_message, code=status.HTTP_400_BAD_REQUEST)
    try:
        return int(raw), None
    except ValueError:
        return None, APIResponse.error(invalid_message, code=status.HTTP_400_BAD_REQUEST)
