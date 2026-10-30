"""Formatting for the panel templates: Persian digits, Jalali dates, money,
customer file codes and query-string building."""

from datetime import datetime

from django import template
from django.utils.http import urlencode

from common import jalali

register = template.Library()

_FA = str.maketrans('0123456789', '۰۱۲۳۴۵۶۷۸۹')


def fa_digits(value):
    return str(value).translate(_FA)


@register.filter
def fa(value):
    """Persian digits; integers also get thousands separators."""
    if value is None or value == '':
        return '—'
    if isinstance(value, bool):
        return value
    if isinstance(value, int):
        return fa_digits(f'{value:,}').replace(',', '٬')
    if isinstance(value, float):
        return fa_digits(f'{value:,.1f}').replace(',', '٬').replace('.', '٫')
    return fa_digits(value)


@register.filter
def toman(value):
    return f'{fa(int(value or 0))} تومان'


@register.filter
def jdate(value):
    if not value:
        return '—'
    if not isinstance(value, datetime):  # a plain date has no timezone to convert
        jy, jm, jd = jalali.to_jalali(value.year, value.month, value.day)
        return fa_digits(f'{jy}/{jm:02d}/{jd:02d}')
    return fa_digits(jalali.format_date(value))


@register.filter
def jdatetime(value):
    return fa_digits(jalali.format_datetime(value)) if value else '—'


def file_code_of(pk):
    return f'C-{int(pk):06d}'


@register.filter
def file_code(pk):
    """Customer file number, the demo's ``C-000542`` shape (visitor id)."""
    return file_code_of(pk) if pk else '—'


@register.filter
def initial(value):
    value = (value or '').strip()
    return value[0] if value else '؟'


@register.filter
def tone(value):
    """Avatar colour slot 1–5, stable per name (same formula as Nava's JS)."""
    value = value or '?'
    return (ord(value[0]) + len(value)) % 5 + 1


@register.simple_tag(takes_context=True)
def qs(context, **changes):
    """Current query string with ``changes`` applied (``None`` drops a key).
    Changing anything but the page resets pagination."""
    params = context['request'].GET.copy()
    if 'page' not in changes:
        params.pop('page', None)
    for key, value in changes.items():
        if value is None or value == '':
            params.pop(key, None)
        else:
            params[key] = value
    encoded = urlencode(sorted(params.items()))
    return f'?{encoded}' if encoded else '?'


@register.filter
def get(mapping, key):
    """``{{ labels|get:code }}`` — dict lookup with a variable key."""
    try:
        return mapping.get(key, key)
    except AttributeError:
        return key
