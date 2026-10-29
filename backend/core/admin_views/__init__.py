"""
Custom admin pages of ``core.admin_site.NobatyarAdminSite``, one module per
feature. Each view is a plain function taking the admin site first
(``view(site, request, ...)``) and is bound as a method on the site class, so
``self.admin_view(...)`` wrapping in ``get_urls`` is unchanged.

Modules here must not import models, ``core.dashboard`` or ``core.detail_views``
at module level: ``core.admin_site`` is imported while the app registry is
still populating (AdminConfig resolves ``default_site`` during autodiscover).
"""
