# accounting/admin_services.py
"""Logic behind accounting/admin.py: the admin classes stay thin, the rules
(manual grants, SMS recipient picking, CSV export) live here."""
import csv
import io
from uuid import uuid4

from django.db.models import OuterRef
from django.utils import timezone

from api import jalali

from .models import Subscription
from .payment.addon_payment import grant_addon_benefit


def add_display_percentages(report):
    """Mutates ``report`` in place, adding ``*_pct`` display strings next to
    the raw 0..1 fractions ``accounting/reports.py`` returns.

    The fractions stay raw in ``reports.py`` because the tests assert against
    them directly (``assertAlmostEqual(result['rate'], 0.5)``) — multiplying
    by 100 there would make every test's hand-computed expectation a percent
    instead of a ratio for no benefit. The template has no arithmetic filter
    that both multiplies and rounds to one decimal, so this is view-layer
    formatting, the same split ``core/dashboard/panels.py`` uses to turn
    ``metrics.py``'s raw numbers into template-ready strings.
    """
    def pct(rate):
        return f'{rate * 100:.1f}' if rate is not None else None

    report['conversion']['transaction']['rate_pct'] = pct(report['conversion']['transaction']['rate'])
    report['conversion']['addon_purchase']['rate_pct'] = pct(report['conversion']['addon_purchase']['rate'])
    report['churn']['rate_pct'] = pct(report['churn']['rate'])
    return report


def later_subscriptions():
    """Subscriptions of the same owner that end after the outer row — the
    same "renewed" test the expired-mode renewal SMS uses
    (core.messaging.expiry_sms_audience). Cancelled ones don't count."""
    return Subscription.objects.filter(
        user_id=OuterRef('user_id'), ends_at__gt=OuterRef('ends_at'),
    ).exclude(status='cancelled')



def sms_recipients(queryset):
    """One row per owner for the subscription-SMS confirmation page; the
    subscription ending last speaks for them — the same pick
    send_sms_to_subscriptions makes."""
    by_user = {}
    for sub in queryset.select_related('user', 'plan'):
        if sub.user_id not in by_user or sub.ends_at > by_user[sub.user_id].ends_at:
            by_user[sub.user_id] = sub
    now = timezone.now()
    return [
        {
            'name': sub.user.name or '—',
            'phone': sub.user.phone or '',
            'plan': sub.plan.name,
            'ends_at': jalali.format_date(sub.ends_at),
            'ended': sub.ends_at <= now,
        }
        for sub in sorted(by_user.values(), key=lambda sub: sub.ends_at, reverse=True)
    ]


def activate_manual_subscription(obj):
    """After a staff-created/edited Subscription is saved: one active
    subscription per user (as in the purchase flow), and business locks
    re-synced against the new plan."""
    if obj.status == 'active':
        Subscription.objects.filter(user=obj.user, status='active').exclude(pk=obj.pk).update(status='expired')

    # Re-sync business locks (unlocks businesses if the new plan allows more).
    from business.services import sync_locks
    sync_locks(obj.user)


def fill_manual_purchase_defaults(obj):
    """Blank fields of a hand-granted AddOnPurchase (new rows only)."""
    if obj.pk is None:
        if not obj.amount:
            obj.amount = obj.pack.price
        if not obj.order_id:
            obj.order_id = f"MANUAL-{obj.user_id}-{obj.pack_id}-{int(timezone.now().timestamp())}"
        if not obj.track_id:
            obj.track_id = f"manual-{uuid4().hex[:12]}"


def should_grant_purchase(obj) -> bool:
    return obj.status == 'success' and obj.activated_at is None


def grant_purchase(obj):
    grant_addon_benefit(obj)


def affected_business_count() -> int:
    """Public businesses whose owner has no active subscription."""
    from business.models import Business
    from .entitlements import owners_with_active_subscription

    return (
        Business.objects.filter(Business.public_filter())
        .exclude(user_id__in=owners_with_active_subscription())
        .count()
    )


def financial_report_csv(report) -> bytes:
    """The financial report as a UTF-8 (BOM) CSV, every on-screen section included."""
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(['گزارش مالی نوبت‌یار'])
    writer.writerow(['بازه', f"{report['range']['from_jalali']} تا {report['range']['to_jalali']}"])
    writer.writerow([])

    writer.writerow(['درآمد بر اساس منبع (تومان)'])
    writer.writerow(['منبع', 'مبلغ'])
    writer.writerow(['اشتراک‌ها', report['revenue']['subscription']])
    writer.writerow(['بسته پیامک', report['revenue']['sms_pack']])
    writer.writerow(['بسته نوبت', report['revenue']['appointment_pack']])
    if report['revenue']['feature_pack']:
        writer.writerow(['قابلیت موقت (بایگانی)', report['revenue']['feature_pack']])
    writer.writerow(['جمع کل', report['revenue']['total']])
    writer.writerow([])

    writer.writerow(['پرداخت‌کننده‌ها'])
    writer.writerow(['زمان', 'نام', 'موبایل', 'نوع', 'خرید', 'مبلغ (تومان)', 'درگاه', 'کد پیگیری', 'حساب داخلی'])
    for p in report['payments']:
        writer.writerow([
            p['when'], p['name'], p['phone'], p['kind'], p['item'], p['amount'],
            p['channel_label'], p['track_id'], 'بله' if p['is_staff'] else '',
        ])
    if not report['payments']:
        writer.writerow(['—'])
    writer.writerow([])

    writer.writerow(['فروش بر اساس پلن'])
    writer.writerow(['پلن', 'تعداد فروش', 'درآمد (تومان)'])
    for row in report['plans']:
        writer.writerow([row['plan'], row['count'], row['revenue']])
    if not report['plans']:
        writer.writerow(['—', 0, 0])
    writer.writerow([])

    # These four sections were missing from an earlier version of this
    # export: it wrote only revenue-by-source and per-plan sales, so a
    # Finance user who saw the full report on screen and exported "the
    # report" got a file silently missing conversion/MRR/ARPU/churn with
    # no indication anything was left out. Every section on the HTML page
    # must have a CSV counterpart, or the export is not actually a report.
    writer.writerow(['نرخ موفقیت پرداخت'])
    writer.writerow(['نوع', 'موفق', 'در انتظار', 'ناموفق/لغوشده', 'جمع', 'نرخ موفقیت'])
    for label, key in (('اشتراک‌ها', 'transaction'), ('بسته‌های افزودنی', 'addon_purchase')):
        block = report['conversion'][key]
        counts = block['counts']
        failed = sum(v for k, v in counts.items() if k not in ('success', 'pending'))
        rate = f"{block['rate'] * 100:.1f}%" if block['rate'] is not None else '—'
        writer.writerow([label, counts.get('success', 0), counts.get('pending', 0),
                          failed, block['total'], rate])
    writer.writerow([])

    writer.writerow(['شاخص‌های تکرارشونده'])
    writer.writerow(['شاخص', 'مقدار'])
    writer.writerow(['MRR (تومان)', report['mrr']])
    writer.writerow(['ARPU (تومان)',
                      round(report['arpu']['arpu']) if report['arpu']['arpu'] is not None else '—'])
    writer.writerow(['کاربران پرداخت‌کننده', report['arpu']['paying_users']])
    # churn() returns a breakdown dict ({'lapsed', 'renewed', 'churned',
    # 'active_at_start', 'rate'}), not a bare rate — same shape the HTML
    # template reads via report['churn']['rate'] (admin.py's own
    # _add_display_percentages, a few lines above). Treating the dict
    # itself as a number here raised a TypeError on every export.
    churn_rate = report['churn']['rate']
    writer.writerow(['نرخ ریزش', f"{churn_rate * 100:.1f}%" if churn_rate is not None else '—'])
    writer.writerow(['اشتراک‌های منقضی در بازه', report['churn']['lapsed']])
    writer.writerow(['تمدید شده', report['churn']['renewed']])
    writer.writerow(['ریزش‌شده', report['churn']['churned']])
    writer.writerow(['فعال در ابتدای بازه', report['churn']['active_at_start']])
    writer.writerow([])

    writer.writerow(['گردش مالی بیعانه'])
    writer.writerow([report['deposit_note']])

    # utf-8-sig, not a bare utf-8 encode with a manually prepended BOM
    # character: encoding to utf-8-sig is what actually inserts the
    # 0xEF 0xBB 0xBF byte sequence Excel checks for before it will trust
    # a CSV is UTF-8 rather than the system codepage — without it, every
    # Persian column renders as mojibake the moment the file is opened.
    return buffer.getvalue().encode('utf-8-sig')


def financial_report_filename(report) -> str:
    return f"financial-report-{report['range']['from'].isoformat()}-{report['range']['to'].isoformat()}.csv"
