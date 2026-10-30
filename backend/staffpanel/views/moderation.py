"""صف تأیید (businesses) and نمونه‌کار و مدارک (showcase uploads).

Decisions go through the same services the admin and the Bale bot use —
``business.services.apply_moderation_decision`` (records the log and SMSes the
owner) and ``showcase.services.decide`` (locked, replay-safe) — so the three
entry points can never disagree about what "approve" means.
"""

from django.contrib import admin
from django.core.exceptions import PermissionDenied
from django.db.models import Count, Q
from django.http import Http404
from django.shortcuts import get_object_or_404

from business.models import BannedKeyword, Business
from business.services import apply_bulk_moderation_decision, apply_moderation_decision
from showcase import services as showcase_services
from showcase.models import Certificate, ModerationStatus, PortfolioItem

from .. import nav
from ..access import panel_view
from ._common import back, fail, ok, paginate, post_ids, render_panel, warn

QUEUE_SIZE = 20


def _business_admin():
    # The queue's ordering and card building live on BusinessAdmin; reuse the
    # registered instance rather than copying them.
    return admin.site._registry[Business]


@panel_view('business.view_business')
def approvals(request):
    model_admin = _business_admin()
    page = paginate(request, model_admin._pending_queryset(), QUEUE_SIZE)
    keywords = list(BannedKeyword.objects.filter(is_active=True))
    cards = [model_admin._build_card(b, keywords) for b in page.object_list]
    return render_panel(request, 'staffpanel/approvals.html', 'approvals', {
        'cards': cards, 'page_obj': page,
        'can_decide': request.user.has_perm('business.change_business'),
    }, title='صف تأیید')


def _decision(request):
    decision = request.POST.get('decision')
    note = (request.POST.get('note') or '').strip()
    if decision == 'approve':
        return Business.MODERATION_APPROVED, note, 'تأیید'
    if decision == 'reject':
        return Business.MODERATION_REJECTED, note, 'رد'
    return None, note, ''


@panel_view(('business.view_business', 'business.change_business'), methods=('POST',))
def approval_decide(request, pk):
    business = get_object_or_404(Business, pk=pk)
    to_status, note, verb = _decision(request)
    if to_status is None:
        fail(request, 'تصمیم نامعتبر است.')
    elif to_status == Business.MODERATION_REJECTED and not note:
        fail(request, f'برای رد کردن «{business.title}» باید دلیل بنویسید.')
    else:
        notified = apply_moderation_decision(business, to_status, actor=request.user, note=note)
        nav.forget_badges()
        if notified:
            ok(request, f'«{business.title}» {verb} شد.')
        else:
            warn(request, f'«{business.title}» {verb} شد، اما پیامک اطلاع‌رسانی به مالک ارسال نشد.')
    return back(request, 'staffpanel:approvals')


@panel_view(('business.view_business', 'business.change_business'), methods=('POST',))
def approvals_bulk(request):
    ids = post_ids(request)
    to_status, note, verb = _decision(request)
    queryset = Business.objects.filter(pk__in=ids, moderation_status=Business.MODERATION_PENDING)
    if not ids or to_status is None:
        fail(request, 'هیچ موردی انتخاب نشده است.')
    elif to_status == Business.MODERATION_REJECTED and not note:
        fail(request, 'برای رد گروهی نوشتن دلیل الزامی است.')
    else:
        count, unnotified = apply_bulk_moderation_decision(queryset, to_status, actor=request.user, note=note)
        nav.forget_badges()
        ok(request, f'{count} کسب‌وکار {verb} شد.')
        if unnotified:
            warn(request, f'برای {unnotified} مورد پیامک اطلاع‌رسانی ارسال نشد.')
    return back(request, 'staffpanel:approvals')


# ── Showcase ────────────────────────────────────────────────────────────────

KINDS = {
    'portfolio': (PortfolioItem, 'نمونه‌کار', 'showcase.change_portfolioitem', 'showcase.view_portfolioitem'),
    'certificate': (Certificate, 'مدرک', 'showcase.change_certificate', 'showcase.view_certificate'),
}
STATUS_TABS = (
    (ModerationStatus.PENDING, 'در انتظار'),
    (ModerationStatus.APPROVED, 'تأییدشده'),
    (ModerationStatus.REJECTED, 'ردشده'),
)


@panel_view('showcase.view_portfolioitem')
def showcase(request):
    kind = request.GET.get('kind') if request.GET.get('kind') in KINDS else 'portfolio'
    model, label, change_perm, view_perm = KINDS[kind]
    status = request.GET.get('status') if request.GET.get('status') in dict(STATUS_TABS) else ModerationStatus.PENDING

    pending = {
        k: m.objects.filter(status=ModerationStatus.PENDING).count() for k, (m, *_rest) in KINDS.items()
    }
    by_status = model.objects.aggregate(**{
        s.lower(): Count('id', filter=Q(status=s)) for s, _ in STATUS_TABS
    })
    queryset = (model.objects.filter(status=status).select_related('business', 'reviewed_by')
                .order_by('created_at' if status == ModerationStatus.PENDING else '-reviewed_at', 'id'))
    business_id = request.GET.get('business')
    if business_id and business_id.isdigit():
        queryset = queryset.filter(business_id=business_id)
    page = paginate(request, queryset, 24)
    return render_panel(request, 'staffpanel/showcase.html', 'showcase', {
        'kind': kind, 'kind_label': label, 'status': status, 'page_obj': page,
        'kinds': [(k, v[1], pending[k]) for k, v in KINDS.items()],
        'status_tabs': [(s, l, by_status[s.lower()]) for s, l in STATUS_TABS],
        'can_decide': request.user.has_perm(change_perm) or request.user.is_superuser,
    }, title='نمونه‌کار و مدارک')


def _showcase_decide(request, kind, items):
    model, label, change_perm, _view = KINDS[kind]
    if not (request.user.is_superuser or request.user.has_perm(change_perm)):
        raise PermissionDenied
    decision = request.POST.get('decision')
    reason = (request.POST.get('note') or '').strip()
    if decision not in ('approve', 'reject'):
        fail(request, 'تصمیم نامعتبر است.')
        return 0
    to_status = ModerationStatus.APPROVED if decision == 'approve' else ModerationStatus.REJECTED
    if to_status == ModerationStatus.REJECTED and not reason:
        fail(request, 'برای رد کردن باید دلیل بنویسید.')
        return 0
    changed = sum(
        1 for item in items
        if showcase_services.decide(item, kind, to_status, actor=request.user, reason=reason)
    )
    nav.forget_badges()
    verb = 'تأیید' if to_status == ModerationStatus.APPROVED else 'رد'
    ok(request, f'{changed} {label} {verb} شد.' if changed else 'این مورد قبلاً همین وضعیت را داشت.')
    return changed


@panel_view('showcase.view_portfolioitem', methods=('POST',))
def showcase_decide(request, kind, pk):
    if kind not in KINDS:
        raise Http404
    item = get_object_or_404(KINDS[kind][0], pk=pk)
    _showcase_decide(request, kind, [item])
    return back(request, 'staffpanel:showcase')


@panel_view('showcase.view_portfolioitem', methods=('POST',))
def showcase_bulk(request, kind):
    if kind not in KINDS:
        raise Http404
    ids = post_ids(request)
    if not ids:
        fail(request, 'هیچ موردی انتخاب نشده است.')
    else:
        _showcase_decide(request, kind, list(KINDS[kind][0].objects.filter(pk__in=ids)))
    return back(request, 'staffpanel:showcase')
