"""JSON for the command palette (⌘K): businesses, customers and owners by
name, phone number, page code or customer file number. Each group only for a
viewer who may open its pages; a handful of rows each, never a full scan
rendered to the client."""

from django.db.models import Q
from django.http import JsonResponse
from django.urls import reverse

from api.models import User
from business.models import Business
from visitor.models import Visitor

from ..access import panel_view
from ..templatetags.panel import fa_digits, file_code_of
from ._common import file_code_id, search_term

LIMIT = 6


@panel_view()
def search(request):
    q = search_term(request)
    user = request.user
    results = []
    if len(q) < 2:
        return JsonResponse({'results': results})

    code = file_code_id(q)
    if user.has_perm('visitor.view_visitor'):
        visitors = Visitor.objects.filter(pk=code) if code else Visitor.objects.filter(
            Q(phone_number__contains=q) if q.isdigit() else Q(full_name__icontains=q) | Q(phone_number__contains=q))
        for v in visitors.only('id', 'full_name', 'phone_number').order_by('-id')[:LIMIT]:
            results.append({
                'group': 'مشتریان', 'icon': 'user', 'label': v.full_name or 'بی‌نام',
                'hint': f'{file_code_of(v.pk)} · {fa_digits(v.phone_number)}',
                'href': reverse('staffpanel:customer_detail', args=[v.pk]),
            })
    if user.has_perm('business.view_business') and not code:
        businesses = Business.objects.filter(
            Q(title__icontains=q) | Q(phone__contains=q) | Q(unique_code__iexact=q) | Q(user__phone__contains=q))
        for b in businesses.only('id', 'title', 'city').order_by('-id')[:LIMIT]:
            results.append({
                'group': 'کسب‌وکارها', 'icon': 'building', 'label': b.title, 'hint': b.city or '',
                'href': reverse('staffpanel:business_detail', args=[b.pk]),
            })
    if user.has_perm('api.view_user') and q.isdigit():
        for u in User.objects.filter(phone__contains=q).only('id', 'name', 'phone').order_by('-id')[:LIMIT]:
            results.append({
                'group': 'کاربران', 'icon': 'key', 'label': u.name or u.phone, 'hint': fa_digits(u.phone),
                'href': reverse('admin:core_user_detail', args=[u.pk]),
            })
    return JsonResponse({'results': results})
