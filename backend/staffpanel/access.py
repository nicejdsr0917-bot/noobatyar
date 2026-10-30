"""Gatekeeping for every panel view — the same rules as the admin.

* anonymous → redirect to the admin login, coming back here afterwards;
* signed in but not active staff → 403 (the admin would show its login again,
  which only invites retrying a password that is not the problem);
* missing model permission → 403, never a half-rendered page.

Everything is ``never_cache``: these pages show personal data and queue state.
"""

from functools import wraps

from django.contrib.auth.views import redirect_to_login
from django.core.exceptions import PermissionDenied
from django.http import HttpResponseNotAllowed
from django.urls import reverse
from django.views.decorators.cache import never_cache


def has_perms(user, perms):
    if not perms:
        return True
    if isinstance(perms, str):
        perms = (perms,)
    return user.is_superuser or all(user.has_perm(p) for p in perms)


def is_panel_user(user):
    return user.is_authenticated and user.is_active and user.is_staff


def panel_view(perms=None, methods=('GET',)):
    """Decorator: staff + ``perms`` (a codename or a tuple of them, all
    required) + allowed HTTP methods. POST views still go through Django's CSRF
    middleware — nothing here is csrf_exempt."""

    def deco(view):
        @wraps(view)
        def wrapper(request, *args, **kwargs):
            user = request.user
            if not user.is_authenticated:
                return redirect_to_login(request.get_full_path(), reverse('admin:login'))
            if not is_panel_user(user) or not has_perms(user, perms):
                raise PermissionDenied
            if request.method not in methods:
                return HttpResponseNotAllowed(methods)
            return view(request, *args, **kwargs)

        return never_cache(wrapper)

    return deco


def require(user, perms):
    """In-view check for an action that needs more than the page did."""
    if not has_perms(user, perms):
        raise PermissionDenied
