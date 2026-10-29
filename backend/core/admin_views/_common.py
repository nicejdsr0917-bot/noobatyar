"""Small helpers shared by the custom admin views."""

from django.core.exceptions import PermissionDenied


def require_perm(request, perm):
    if not request.user.has_perm(perm):
        raise PermissionDenied


def require_post_perm(request, perm):
    """The gate every mutating endpoint opens with: the permission, then POST."""
    require_perm(request, perm)
    if request.method != 'POST':
        raise PermissionDenied


def exclude_opted_out(data):
    """The ``exclude_opted_out`` toggle of a GET/POST (default: exclude)."""
    return data.get('exclude_opted_out', '1') != '0'
