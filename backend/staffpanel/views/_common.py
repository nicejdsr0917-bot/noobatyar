"""Shared plumbing for panel views: page rendering, pagination, search input
and safe redirects back to the page an action came from."""

import re

from django.contrib import messages
from django.core.paginator import Paginator
from django.shortcuts import redirect
from django.template.response import TemplateResponse
from django.urls import reverse
from django.utils.http import url_has_allowed_host_and_scheme

from .. import nav

PAGE_SIZE = 25

_TO_LATIN = str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩', '01234567890123456789')
_FILE_CODE = re.compile(r'^[cC]-?0*(\d+)$')


def render_panel(request, template, page, context=None, title=''):
    sections = nav.build(request.user, page)
    ctx = {
        'nav_sections': sections,
        # The command palette lists the same pages the menu shows.
        'palette_pages': [
            {'label': i['label'], 'icon': i['icon'], 'href': i['href']}
            for s in sections for i in s['items']
        ],
        'page_id': page,
        'title': title,
    }
    ctx.update(context or {})
    return TemplateResponse(request, template, ctx)


def paginate(request, queryset, per_page=PAGE_SIZE):
    return Paginator(queryset, per_page).get_page(request.GET.get('page'))


def search_term(request, key='q'):
    """The search box value, trimmed, with Persian/Arabic digits as Latin so a
    phone number typed on a Persian keyboard still matches."""
    return (request.GET.get(key) or '').strip().translate(_TO_LATIN)[:100]


def file_code_id(term):
    """``C-000542`` / ``c542`` → 542, else None."""
    m = _FILE_CODE.match(term)
    return int(m.group(1)) if m else None


def back(request, fallback_name, *args):
    """Redirect to the ``next`` field (same host only) or the named page."""
    target = request.POST.get('next') or request.GET.get('next')
    if target and url_has_allowed_host_and_scheme(target, {request.get_host()}, request.is_secure()):
        return redirect(target)
    return redirect(reverse(fallback_name, args=args))


def ok(request, text):
    messages.success(request, text)


def fail(request, text):
    messages.error(request, text)


def warn(request, text):
    messages.warning(request, text)


def post_ids(request, key='ids'):
    out = []
    for raw in request.POST.getlist(key):
        for part in str(raw).split(','):
            if part.strip().isdigit():
                out.append(int(part))
    return out
