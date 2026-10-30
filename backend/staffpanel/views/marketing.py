"""بازاریابی: calling unclaimed centres. «امروز» is the admin's TodayCallsFilter
(follow-up due today or overdue, lead still open)."""

from django.db.models import Count, Prefetch, Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.utils.dateparse import parse_date

from marketing.models import LeadNote, MarketingLead

from .. import nav
from ..access import panel_view, require
from ._common import back, fail, ok, paginate, render_panel, search_term


def _today_q(today):
    return Q(next_follow_up__lte=today) & ~Q(status__in=MarketingLead.CLOSED_STATUSES)


@panel_view('marketing.view_marketinglead')
def marketing(request):
    today = timezone.localdate()
    tabs = [('today', 'تماس امروز'), ('all', 'همه')] + list(MarketingLead.STATUS_CHOICES)
    keys = {k for k, _ in tabs}
    tab = request.GET.get('tab') if request.GET.get('tab') in keys else 'today'
    agg = {'today': Count('id', filter=_today_q(today)), 'all': Count('id')}
    agg.update({code: Count('id', filter=Q(status=code)) for code, _ in MarketingLead.STATUS_CHOICES})
    counts = MarketingLead.objects.aggregate(**agg)

    qs = MarketingLead.objects.select_related('business', 'assigned_to').prefetch_related(
        Prefetch('notes', queryset=LeadNote.objects.select_related('created_by').order_by('-created_at')))
    if tab == 'today':
        qs = qs.filter(_today_q(today)).order_by('next_follow_up', 'id')
    else:
        if tab != 'all':
            qs = qs.filter(status=tab)
        qs = qs.order_by('-updated_at', '-id')
    if request.GET.get('mine') == '1':
        qs = qs.filter(assigned_to=request.user)
    q = search_term(request)
    if q:
        qs = qs.filter(Q(business__title__icontains=q) | Q(business__phone__contains=q) | Q(business__city__icontains=q))
    page = paginate(request, qs)
    return render_panel(request, 'staffpanel/marketing.html', 'marketing', {
        'tab': tab, 'tabs': [(k, label, counts[k]) for k, label in tabs],
        'page_obj': page, 'q': q, 'today': today, 'mine': request.GET.get('mine') == '1',
        'statuses': MarketingLead.STATUS_CHOICES,
        'can_change': request.user.has_perm('marketing.change_marketinglead'),
    }, title='بازاریابی')


@panel_view(('marketing.view_marketinglead', 'marketing.change_marketinglead'), methods=('POST',))
def lead_update(request, pk):
    """One form per call: new status, next follow-up and what was said."""
    lead = get_object_or_404(MarketingLead.objects.select_related('business'), pk=pk)
    status = request.POST.get('status')
    raw_date = (request.POST.get('next_follow_up') or '').strip()
    note = (request.POST.get('note') or '').strip()
    follow_up = parse_date(raw_date) if raw_date else None
    if status not in dict(MarketingLead.STATUS_CHOICES):
        fail(request, 'وضعیت نامعتبر است.')
        return back(request, 'staffpanel:marketing')
    if raw_date and follow_up is None:
        fail(request, 'تاریخ پیگیری نامعتبر است.')
        return back(request, 'staffpanel:marketing')
    if note:
        require(request.user, 'marketing.add_leadnote')  # before any write
    lead.status = status
    lead.next_follow_up = None if status in MarketingLead.CLOSED_STATUSES else follow_up
    if lead.assigned_to_id is None:
        lead.assigned_to = request.user
    lead.save(update_fields=['status', 'next_follow_up', 'assigned_to', 'updated_at'])
    if note:
        LeadNote.objects.create(lead=lead, text=note, created_by=request.user)
    nav.forget_badges()
    ok(request, f'«{lead.business.title}» به‌روز شد.')
    return back(request, 'staffpanel:marketing')
