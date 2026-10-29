# core/admin_site.py
"""
The project's admin site.

Installed as the *default* admin site through core.apps.NobatyarAdminConfig
(`default_site`), which means `django.contrib.admin.site` **is** an instance of
this class. That matters: every app already registers its models with the
bare `@admin.register(...)` decorator, which targets the default site. Swapping
the class instead of instantiating a second site keeps all of those working
without touching a single app's admin.py.

This class only wires URLs; each custom page lives in a feature module under
core/admin_views/ as a function ``view(site, request, ...)`` bound here as a
method.
"""

from django.urls import path
from unfold.sites import UnfoldAdminSite

# `within_send_window` stays importable (and patchable) from this module:
# core.admin_views.expiry_sms resolves it here at call time.
from api.sms import within_send_window  # noqa: F401

from core.admin_views import campaigns, dashboard, detail, expiry_sms, segments

# core.dashboard is imported inside the views, never here. This module is
# imported while the app registry is still populating (AdminConfig resolves
# `default_site` during autodiscover), and core.dashboard pulls in models from
# five apps — importing it at module level fails with a misleading
# 'does not define NobatyarAdminSite' ImportError.

#: (url pattern, view attribute on the site, url name)
_ROUTES = (
        ('dashboard/refresh/', 'dashboard_refresh_view', 'dashboard_refresh'),
        ('core/users/<int:user_id>/', 'user_detail_view', 'core_user_detail'),
        ('core/businesses/<int:business_id>/', 'business_detail_view', 'core_business_detail'),
        ('core/businesses/<int:business_id>/customers/', 'business_customers_view', 'core_business_customers'),
        ('core/segments/', 'segment_builder_view', 'core_segment_builder'),
        ('core/segments/save/', 'segment_save_view', 'core_segment_save'),
        ('core/segments/<int:segment_id>/run/', 'segment_run_view', 'core_segment_run'),
        ('core/segments/export.csv', 'segment_export_view', 'core_segment_export'),
        ('core/segments/notify/', 'segment_notify_view', 'core_segment_notify'),
        ('core/campaigns/', 'campaign_view', 'core_campaigns'),
        ('core/campaigns/send/', 'campaign_send_view', 'core_campaign_send'),
        ('core/campaigns/preview-count/', 'campaign_count_view', 'core_campaign_count'),
        ('core/expiry-sms/send/', 'expiry_sms_send_view', 'core_expiry_sms_send'),
        ('core/expiry-sms/preview-count/', 'expiry_sms_count_view', 'core_expiry_sms_count'),
        ('core/expiry-sms/autosend/', 'expiry_sms_autosend_view', 'core_expiry_sms_autosend'),
        ('core/expiry-sms/test/', 'expiry_sms_test_view', 'core_expiry_sms_test'),
)


class NobatyarAdminSite(UnfoldAdminSite):
    site_header = 'نوبت‌یار — پنل مدیریت'
    site_title = 'نوبت‌یار'
    index_title = 'مدیریت سامانه'

    # Shown on the login page and in the "view site" link. The admin panel is
    # staff-only tooling and has no public counterpart worth linking to, so the
    # link is dropped rather than pointing at the API root.
    site_url = None

    # ── Custom pages (implementations in core/admin_views/) ──────────────────
    dashboard_refresh_view = dashboard.dashboard_refresh_view

    user_detail_view = detail.user_detail_view
    business_detail_view = detail.business_detail_view
    business_customers_view = detail.business_customers_view

    segment_builder_view = segments.segment_builder_view
    segment_save_view = segments.segment_save_view
    segment_run_view = segments.segment_run_view
    segment_export_view = segments.segment_export_view
    segment_notify_view = segments.segment_notify_view

    campaign_view = campaigns.campaign_view
    campaign_send_view = campaigns.campaign_send_view
    campaign_count_view = campaigns.campaign_count_view

    expiry_sms_send_view = expiry_sms.expiry_sms_send_view
    expiry_sms_count_view = expiry_sms.expiry_sms_count_view
    expiry_sms_autosend_view = expiry_sms.expiry_sms_autosend_view
    expiry_sms_test_view = expiry_sms.expiry_sms_test_view

    def get_urls(self):
        """
        Custom admin views, prepended so they win over the catch-all
        `^(?P<app_label>...)/$` pattern Django appends at the end of get_urls().

        Every view is wrapped with `self.admin_view(...)` — that is what
        enforces the staff/login check and adds the never-cache headers. A raw
        view here is world-readable.

        Note: the moderation queue is deliberately *not* built here. It hangs off
        `business/admin.py` so it can reuse the BusinessAdmin querysets and
        permissions.
        """
        extra_urls = [
            path(route, self.admin_view(getattr(self, view)), name=name)
            for route, view, name in _ROUTES
        ]
        return extra_urls + super().get_urls()

    def index(self, request, extra_context=None):
        """The admin landing page: KPI dashboard *above* the usual app list
        (see core.admin_views.dashboard.index_context)."""
        context = dashboard.index_context(request, extra_context)
        return super().index(request, extra_context=context)

    def each_context(self, request):
        """
        Common template context for every admin page.

        Keep anything added here cheap — this runs on *every* admin request,
        including the 404s from bots probing /admin/.
        """
        return super().each_context(request)
