"""اشتراک و مالی: payments (subscription transactions, add-on purchases) and
subscriptions. Read-only — money changes stay in the admin, where every
action is audited by its own model admin."""

from datetime import timedelta

from django.db.models import Count, Q, Sum
from django.utils import timezone

from accounting.models import AddOnPurchase, Subscription, Transaction

from ..access import panel_view, require
from ._common import paginate, render_panel, search_term

VIEWS = (
    ('transactions', 'پرداخت اشتراک', 'accounting.view_transaction'),
    ('addons', 'خرید بسته', 'accounting.view_addonpurchase'),
    ('subscriptions', 'اشتراک‌ها', 'accounting.view_subscription'),
)
PAYMENT_STATUSES = (('', 'همه'), ('success', 'موفق'), ('pending', 'در انتظار'), ('failed', 'ناموفق'))
SUB_FILTERS = (('', 'همه'), ('active', 'فعال'), ('expiring', 'رو به پایان (۷ روز)'), ('expired', 'تمام‌شده'))


def _user_search(qs, q):
    return qs.filter(Q(user__phone__contains=q) | Q(user__name__icontains=q)) if q else qs


@panel_view('accounting.view_transaction')
def finance(request):
    user = request.user
    allowed = [(k, label) for k, label, perm in VIEWS if user.is_superuser or user.has_perm(perm)]
    view = request.GET.get('view') if request.GET.get('view') in dict(allowed) else 'transactions'
    require(user, dict((k, p) for k, _l, p in VIEWS)[view])
    status = request.GET.get('status') or ''
    q = search_term(request)
    now = timezone.now()
    month_ago = now - timedelta(days=30)

    totals = {
        'transactions': Transaction.objects.filter(status='success', created_at__gte=month_ago)
        .aggregate(n=Count('id'), s=Sum('amount')),
    }
    if user.is_superuser or user.has_perm('accounting.view_addonpurchase'):
        totals['addons'] = AddOnPurchase.objects.filter(status='success', created_at__gte=month_ago) \
            .aggregate(n=Count('id'), s=Sum('amount'))
    if user.is_superuser or user.has_perm('accounting.view_subscription'):
        totals['subs'] = Subscription.objects.aggregate(
            active=Count('id', filter=Q(status='active', ends_at__gt=now)),
            expiring=Count('id', filter=Q(status='active', ends_at__gt=now, ends_at__lte=now + timedelta(days=7))),
        )

    if view == 'subscriptions':
        qs = Subscription.objects.select_related('user', 'plan')
        if status == 'active':
            qs = qs.filter(status='active', ends_at__gt=now)
        elif status == 'expiring':
            qs = qs.filter(status='active', ends_at__gt=now, ends_at__lte=now + timedelta(days=7))
        elif status == 'expired':
            qs = qs.filter(ends_at__lte=now)
        qs = _user_search(qs, q).order_by('-started_at', '-id')
        filters = SUB_FILTERS
    else:
        model = Transaction if view == 'transactions' else AddOnPurchase
        qs = model.objects.select_related('user', 'plan' if view == 'transactions' else 'pack')
        if status in dict(PAYMENT_STATUSES) and status:
            qs = qs.filter(status=status)
        if q and not q.isdigit():
            qs = _user_search(qs, q)
        elif q:
            qs = qs.filter(Q(user__phone__contains=q) | Q(track_id__contains=q) | Q(order_id__contains=q))
        qs = qs.order_by('-created_at', '-id')
        filters = PAYMENT_STATUSES

    return render_panel(request, 'staffpanel/finance.html', 'finance', {
        'view': view, 'views': allowed, 'status': status, 'filters': filters,
        'page_obj': paginate(request, qs), 'q': q, 'totals': totals, 'now': now,
    }, title='اشتراک و مالی')
