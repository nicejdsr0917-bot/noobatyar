"""کسب‌وکارها: list with moderation-status tabs and a detail page built from
core.detail_views (the same queries as the admin's business 360 page)."""

from django.db.models import Count, Q
from django.shortcuts import get_object_or_404

from business.models import Business
from core import detail_views
from showcase.models import Certificate, ModerationStatus, PortfolioItem

from ..access import panel_view
from ._common import paginate, render_panel, search_term

TABS = (
    ('all', 'همه', None),
    ('pending', 'در انتظار', Business.MODERATION_PENDING),
    ('approved', 'تأییدشده', Business.MODERATION_APPROVED),
    ('rejected', 'ردشده', Business.MODERATION_REJECTED),
    ('suspended', 'معلق', Business.MODERATION_SUSPENDED),
)
OWNERSHIP = {'claimed': True, 'directory': False}


@panel_view('business.view_business')
def businesses(request):
    statuses = {k: s for k, _l, s in TABS}
    tab = request.GET.get('tab') if request.GET.get('tab') in statuses else 'all'
    owner = request.GET.get('owner') if request.GET.get('owner') in OWNERSHIP else ''

    counts = Business.objects.aggregate(**{
        k: Count('id', filter=Q(moderation_status=s)) if s else Count('id') for k, s in statuses.items()
    })
    qs = Business.objects.select_related('user')
    if statuses[tab]:
        qs = qs.filter(moderation_status=statuses[tab])
    if owner:
        qs = qs.filter(is_claimed=OWNERSHIP[owner])
    q = search_term(request)
    if q:
        qs = qs.filter(Q(title__icontains=q) | Q(phone__contains=q) | Q(user__phone__contains=q)
                       | Q(unique_code__iexact=q) | Q(city__icontains=q))
    page = paginate(request, qs.order_by('-created_at', '-id'))
    return render_panel(request, 'staffpanel/businesses.html', 'businesses', {
        'tab': tab, 'tabs': [(k, label, counts[k]) for k, label, _s in TABS],
        'owner': owner, 'page_obj': page, 'q': q,
    }, title='کسب‌وکارها')


@panel_view('business.view_business')
def business_detail(request, pk):
    business = get_object_or_404(Business.objects.select_related('user', 'moderation_reviewed_by'), pk=pk)
    data = detail_views.build_business_detail(business, request.user)
    showcase = {
        'portfolio': PortfolioItem.objects.filter(business=business).aggregate(
            total=Count('id'), pending=Count('id', filter=Q(status=ModerationStatus.PENDING))),
        'certificate': Certificate.objects.filter(business=business).aggregate(
            total=Count('id'), pending=Count('id', filter=Q(status=ModerationStatus.PENDING))),
    }
    return render_panel(request, 'staffpanel/business_detail.html', 'businesses', {
        **data,
        'showcase': showcase,
        'can_decide': request.user.has_perm('business.change_business'),
        'can_customers': request.user.has_perm('visitor.view_visitor'),
    }, title=business.title)
