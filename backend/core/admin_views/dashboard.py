"""KPI dashboard on the admin index and its cache-refresh endpoint."""

import logging

from django.http import HttpResponseRedirect
from django.urls import reverse

logger = logging.getLogger('core.admin_site')


def index_context(request, extra_context=None):
    """Context for the admin landing page: KPI dashboard *above* the usual app
    list.

    The app list is not replaced. Staff navigate by model far more often
    than they read metrics, and admin/index.html still renders the stock
    `admin/app_list.html` below the dashboard.

    The dashboard is built here rather than in `each_context()` because
    each_context runs on *every* admin request, including bot 404s; a dozen
    aggregate queries there would be paid for on pages that never show them.

    A failure to compute metrics must not cost staff the admin index, which
    is the entry point to everything else. So the whole thing is wrapped:
    on error the page renders as the plain app list, with the traceback in
    the logs rather than on the screen.
    """
    from core import dashboard

    context = dict(extra_context or {})
    try:
        context.update(dashboard.context_for(request))
    except Exception:
        logger.exception('admin dashboard failed to build; falling back to the app list')
        context['dashboard_any'] = False
        context['dashboard_failed'] = True
    return context


def dashboard_refresh_view(site, request):
    """Drop the cached payload and bounce back to the index.

    The numbers are cached for a minute and a half, which is the wrong
    answer exactly when somebody is watching a payment land. This gives them
    a way to force a recompute without waiting out the TTL.
    """
    from core import dashboard

    dashboard.cache.invalidate()
    return HttpResponseRedirect(reverse('admin:index', current_app=site.name))
