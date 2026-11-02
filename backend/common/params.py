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


def page_params(request, default_page_size: int = 10, max_page_size: int = 100):
    """Strict ``?page=&page_size=`` parsing for the list endpoints.

    Returns ``(page, page_size, None)`` or ``(None, None, error_response)``.
    A non-integer ``page``/``page_size``, or ``page_size < 1``, is a 400 in the
    standard envelope (it used to surface as a 500). ``page_size`` is capped at
    ``max_page_size``; an out-of-range ``page`` is left to ``Paginator.get_page``.
    """
    try:
        page = int(request.query_params.get('page', 1))
        page_size = int(request.query_params.get('page_size', default_page_size))
    except (TypeError, ValueError):
        page = page_size = None
    if page is None or page_size < 1:
        return None, None, APIResponse.error(
            'پارامترهای page و page_size باید عدد صحیح معتبر باشند',
            code=status.HTTP_400_BAD_REQUEST,
        )
    return page, min(page_size, max_page_size), None
