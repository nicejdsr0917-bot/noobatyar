"""Shared constants, badges and the bulk-confirm mixin for the business admins."""

from django.contrib import admin
from django.http import HttpResponseRedirect
from django.template.response import TemplateResponse
from django.utils.html import escape, format_html
from django.utils.http import url_has_allowed_host_and_scheme
from django.utils.safestring import mark_safe
from .. import moderation
from ..models import Business


QUEUE_PAGE_SIZE = 20

# Labels for the moderated fields, so the queue card and the keyword-hit list
# name the same thing the owner sees in the app.
MODERATED_FIELD_LABELS = {
    'title': 'عنوان',
    'bio': 'معرفی کوتاه',
    'address': 'آدرس',
    'notice_message': 'اطلاعیه',
    'logo': 'لوگو',
}

# Modifier suffixes, not colours. These used to be (fg, bg) hex pairs baked
# into an inline style, which meant every badge in the changelist stayed
# light-mode-only and turned into dark-on-dark under the admin's theme toggle.
# The classes are defined once in admin_custom/css/report.css and carry a value
# for both themes; an empty string is the neutral variant.
_STATUS_BADGE = {
    Business.MODERATION_PENDING:   'warn',
    Business.MODERATION_APPROVED:  'ok',
    Business.MODERATION_REJECTED:  'danger',
    Business.MODERATION_SUSPENDED: '',
}


def _badge(label, variant=''):
    """One `.nb-badge` pill; ``variant`` is 'ok' / 'warn' / 'danger' / '' ."""
    css = 'nb-badge nb-badge--%s' % variant if variant else 'nb-badge'
    return format_html('<span class="{}">{}</span>', css, label)


def _highlight(text, terms):
    """Escaped ``text`` with every hit in ``terms`` wrapped in ``<mark>``.

    Spans come from moderation.find_spans(), which indexes the *original*
    string, so the reviewer reads the owner's own spelling — normalisation is
    only used to decide where the hits are, never to rewrite what is shown.
    """
    text = text or ''
    spans = moderation.find_spans(text, terms) if terms else []
    if not spans:
        return escape(text)

    out, cursor = [], 0
    for start, end in spans:
        out.append(escape(text[cursor:start]))
        out.append(format_html(
            '<mark class="nb-mark-hit">{}</mark>',
            text[start:end],
        ))
        cursor = end
    out.append(escape(text[cursor:]))
    return mark_safe(''.join(out))


def _safe_redirect(request, fallback):
    """Honour a ``next`` parameter, but only when it points back at this host.

    Without the host check the decision endpoints would be an open redirect
    that any page could aim a logged-in moderator at.
    """
    nxt = request.POST.get('next') or ''
    if nxt and url_has_allowed_host_and_scheme(
        nxt, allowed_hosts={request.get_host()}, require_https=request.is_secure()
    ):
        return HttpResponseRedirect(nxt)
    return HttpResponseRedirect(fallback)



class _BulkConfirmMixin:
    """Renders the "write a reason" confirmation page for bulk admin actions."""

    def _bulk_confirm_response(self, request, queryset, title, verb, template, note_error=''):
        context = {
            **self.admin_site.each_context(request),
            'title': title,
            'opts': self.model._meta,
            'queryset': queryset,
            'verb': verb,
            'action_name': request.POST.get('action'),
            'selected': request.POST.getlist(admin.helpers.ACTION_CHECKBOX_NAME),
            'note_error': note_error,
        }
        return TemplateResponse(request, template, context)
