# accounting/admin.py
"""
Admin panel for the plan/entitlement system. Beyond browsing, this lets staff
manually grant benefits to a specific user without a real payment:

  * Add a Subscription row (user + plan, leave "تاریخ پایان" blank) to put a
    user on a plan tier — other active subscriptions for that user are
    auto-expired and locked businesses are re-synced against the new plan.
  * Add an AddOnPurchase row (user + pack, status="success", leave
    track_id/order_id/amount blank) to grant SMS credit or a temporary
    feature — the same benefit-granting logic used by real Zibal payments
    runs automatically on save.

``TransactionAdmin`` also lives here (see its docstring for why it did not
exist before this phase) and hosts the financial reporting page + CSV export
under its own ``get_urls()`` — the same pattern ``business/admin.py`` uses to
hang the moderation queue off ``BusinessAdmin``.
"""

import csv
import io
from uuid import uuid4

from django.contrib import admin, messages
from unfold.admin import ModelAdmin
from django.core.exceptions import PermissionDenied
from django.db.models import Exists, OuterRef, Subquery
from django.http import HttpResponse
from django.shortcuts import redirect
from django.template.response import TemplateResponse
from django.urls import path, reverse
from django.utils import timezone
from django.utils.html import format_html

from api import jalali

from . import reports
from .models import (
    Plan, Subscription, AddOnPack, AddOnPurchase, Transaction, CreditLedger,
    SubscriptionReminderSettings, StorePurchase, ContactVisibilityPolicy,
)
from .payment.addon_payment import grant_addon_benefit


def _add_display_percentages(report):
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


@admin.register(Plan)
class PlanAdmin(ModelAdmin):
    list_display = ['name', 'price', 'duration_value', 'duration_unit', 'is_vip', 'is_active', 'myket_sku', 'feature_count']
    list_editable = ['is_active']  # مستقیم از لیست تغییر بده
    search_fields = ['name']

    def feature_count(self, obj):
        return sum(1 for v in obj.features.values() if v is True)
    feature_count.short_description = 'قابلیت‌های فعال'


def _later_subscriptions():
    """Subscriptions of the same owner that end after the outer row — the
    same "renewed" test the expired-mode renewal SMS uses
    (core.messaging.expiry_sms_audience). Cancelled ones don't count."""
    return Subscription.objects.filter(
        user_id=OuterRef('user_id'), ends_at__gt=OuterRef('ends_at'),
    ).exclude(status='cancelled')


class RenewalStateFilter(admin.SimpleListFilter):
    """Every purchase, trial and manual grant is its own row, so one owner
    shows up once per subscription they ever had — a renewed owner's old
    trial still reads «منقضی». This filter tells history from the present."""

    title = 'وضعیت تمدید'
    parameter_name = 'renewal'

    def lookups(self, request, model_admin):
        return [
            ('current', 'فقط اشتراک فعلی هر کاربر'),
            ('lapsed', 'تمام‌شده و تمدیدنشده'),
            ('superseded', 'سابقه (تمدید یا جایگزین‌شده)'),
        ]

    def queryset(self, request, queryset):
        later = Exists(_later_subscriptions())
        if self.value() == 'current':
            return queryset.exclude(later).exclude(status='cancelled')
        if self.value() == 'lapsed':
            return (
                queryset.exclude(later)
                .filter(status__in=('active', 'expired'), ends_at__lte=timezone.now())
            )
        if self.value() == 'superseded':
            return queryset.filter(later)
        return queryset


@admin.register(Subscription)
class SubscriptionAdmin(ModelAdmin):
    list_display = [
        'user', 'user_phone', 'plan', 'status', 'started_at', 'ends_at',
        'is_valid_display', 'owner_state', 'reminder_sent',
    ]
    list_filter = [RenewalStateFilter, 'status', 'plan']
    search_fields = ['user__phone', 'user__name']
    autocomplete_fields = ['user', 'plan']
    readonly_fields = ['started_at']
    actions = ['send_sms_action']

    def get_queryset(self, request):
        latest_later = _later_subscriptions().order_by('-ends_at')
        return super().get_queryset(request).select_related('user', 'plan').annotate(
            next_plan_name=Subquery(latest_later.values('plan__name')[:1]),
            next_ends_at=Subquery(latest_later.values('ends_at')[:1]),
        )

    @admin.display(description='موبایل', ordering='user__phone')
    def user_phone(self, obj):
        return obj.user.phone

    @admin.display(boolean=True, description='معتبر')
    def is_valid_display(self, obj):
        return obj.is_valid()

    @admin.display(description='وضعیت کاربر')
    def owner_state(self, obj):
        """Where this owner stands now, so an old row can't pass for the
        current one."""
        if obj.next_plan_name:
            return format_html(
                '<span style="color:var(--body-quiet-color)">سابقه — الان: «{}» تا {}</span>',
                obj.next_plan_name, jalali.format_date(obj.next_ends_at),
            )
        if obj.status == 'cancelled':
            return 'لغوشده'
        if obj.ends_at > timezone.now():
            return format_html('<span style="color:#15803d">اشتراک فعلی</span>')
        return format_html('<span style="color:#b91c1c">تمام‌شده، تمدید نکرده</span>')

    # ── "ارسال پیامک" action ──────────────────────────────────────────────

    def has_send_expiry_sms_permission(self, request):
        return request.user.has_perm('core.send_expiry_sms')

    @admin.action(
        description='ارسال پیامک به صاحبان اشتراک‌های انتخاب‌شده',
        permissions=['send_expiry_sms'],
    )
    def send_sms_action(self, request, queryset):
        """Pick owners straight off this list (e.g. «تمام‌شده و تمدیدنشده»),
        write the text on a confirmation page, send. Logged with the
        campaign page's renewal SMS, in its report tab."""
        from api.sms import SEND_WINDOW_CLOSED, within_send_window
        from core.messaging import send_sms_to_subscriptions

        from .models import REMINDER_TOKENS

        message = (request.POST.get('sms_message') or '').strip()
        error = ''
        if request.POST.get('apply'):
            if not message:
                error = 'متن پیامک نمی‌تواند خالی باشد.'
            elif not within_send_window():
                error = SEND_WINDOW_CLOSED
            else:
                log = send_sms_to_subscriptions(queryset.select_related('user', 'plan'), message, actor=request.user)
                report_url = reverse('admin:core_campaigns', current_app=self.admin_site.name) + '?tab=reports'
                self.message_user(
                    request,
                    format_html(
                        'ارسال پیامک برای {} صاحب اشتراک در صف قرار گرفت. <a href="{}">گزارش ارسال</a>',
                        log.matched_count, report_url,
                    ),
                    level=messages.SUCCESS,
                )
                return None

        # One row per owner, the subscription ending last speaks for them —
        # the same pick send_sms_to_subscriptions makes.
        by_user = {}
        for sub in queryset.select_related('user', 'plan'):
            if sub.user_id not in by_user or sub.ends_at > by_user[sub.user_id].ends_at:
                by_user[sub.user_id] = sub
        now = timezone.now()
        recipients = [
            {
                'name': sub.user.name or '—',
                'phone': sub.user.phone or '',
                'plan': sub.plan.name,
                'ends_at': jalali.format_date(sub.ends_at),
                'ended': sub.ends_at <= now,
            }
            for sub in sorted(by_user.values(), key=lambda sub: sub.ends_at, reverse=True)
        ]
        context = {
            **self.admin_site.each_context(request),
            'title': 'ارسال پیامک به صاحبان اشتراک',
            'opts': self.model._meta,
            'recipients': recipients,
            'missing_phone': sum(1 for r in recipients if not r['phone']),
            'message': message,
            'error': error,
            'tokens': REMINDER_TOKENS,
            'window_open': within_send_window(),
            'window_closed_text': SEND_WINDOW_CLOSED,
            'action_name': request.POST.get('action'),
            'selected': request.POST.getlist(admin.helpers.ACTION_CHECKBOX_NAME),
        }
        return TemplateResponse(request, 'admin/accounting/subscription/send_sms.html', context)

    def get_form(self, request, obj=None, **kwargs):
        form = super().get_form(request, obj, **kwargs)
        if obj is None:  # add view — this is the manual-grant path
            form.base_fields['ends_at'].required = False
            form.base_fields['ends_at'].help_text = (
                'اگر خالی بماند، بر اساس مدت پلن انتخاب‌شده محاسبه می‌شود.'
            )
        return form

    def save_model(self, request, obj, form, change):
        if obj.ends_at is None:
            obj.ends_at = obj.plan.get_end_date()
        super().save_model(request, obj, form, change)

        if obj.status == 'active':
            # Only one active subscription per user — matches the purchase flow.
            Subscription.objects.filter(user=obj.user, status='active').exclude(pk=obj.pk).update(status='expired')

        # Re-sync business locks (unlocks businesses if the new plan allows more).
        from business.services import sync_locks
        sync_locks(obj.user)

        messages.success(request, f"اشتراک «{obj.plan.name}» برای {obj.user} ثبت شد.")


@admin.register(AddOnPack)
class AddOnPackAdmin(ModelAdmin):
    list_display = ['name', 'price', 'kind', 'sms_amount', 'appointment_amount', 'feature_key', 'duration_days', 'is_active', 'myket_sku']
    list_editable = ['is_active']
    list_filter = ['kind', 'is_active']
    search_fields = ['name']


@admin.register(AddOnPurchase)
class AddOnPurchaseAdmin(ModelAdmin):
    list_display = ['order_id', 'user', 'pack', 'amount', 'status', 'expires_at', 'created_at']
    list_filter = ['status', 'pack']
    search_fields = ['order_id', 'track_id', 'user__phone', 'user__name']
    autocomplete_fields = ['user', 'pack']
    readonly_fields = ['zibal_response', 'activated_at', 'created_at', 'updated_at']

    def get_form(self, request, obj=None, **kwargs):
        form = super().get_form(request, obj, **kwargs)
        if obj is None:  # add view — this is the manual-grant path
            for field_name in ('track_id', 'order_id', 'amount'):
                form.base_fields[field_name].required = False
                form.base_fields[field_name].help_text = 'در صورت خالی گذاشتن، خودکار پر می‌شود.'
            if 'status' in form.base_fields:
                form.base_fields['status'].initial = 'success'
                form.base_fields['status'].help_text = (
                    'برای اعطای دستی بسته به کاربر، این مقدار را روی «success» نگه دارید.'
                )
        return form

    def save_model(self, request, obj, form, change):
        if obj.pk is None:
            if not obj.amount:
                obj.amount = obj.pack.price
            if not obj.order_id:
                obj.order_id = f"MANUAL-{obj.user_id}-{obj.pack_id}-{int(timezone.now().timestamp())}"
            if not obj.track_id:
                obj.track_id = f"manual-{uuid4().hex[:12]}"

        should_grant = obj.status == 'success' and obj.activated_at is None
        super().save_model(request, obj, form, change)

        if should_grant:
            grant_addon_benefit(obj)
            messages.success(request, f"بسته «{obj.pack.name}» برای {obj.user} فعال شد.")


@admin.register(Transaction)
class TransactionAdmin(ModelAdmin):
    """Read-only browsing of subscription payments, plus the financial report.

    Known gap this closes: phase 2's dashboard (``core/dashboard/panels.py``)
    had to build a URL-reversal fallback around ``Transaction`` because it had
    no ``ModelAdmin`` at all — ``admin:accounting_transaction_changelist``
    didn't exist, so a bare ``reverse()`` for the stuck-payments panel would
    have taken down the whole admin index. Registering it here fixes that
    link and gives Finance (which already holds ``view_transaction`` per
    ``setup_admin_roles.py``) a place to actually look at a payment.

    Read-only rather than editable for everyone, including superusers: a
    Transaction row is the audit trail of a real Zibal payment (or, for a
    manually granted subscription, of the Subscription admin's own save —
    see ``SubscriptionAdmin`` above). Nothing in this codebase ever edits one
    after creation; giving the panel edit controls it has no legitimate use
    for would only invite an accidental change to a financial record with no
    audit trail of its own.
    """
    list_display = ['order_id', 'user', 'plan', 'amount', 'status', 'created_at']
    list_filter = ['status', 'plan']
    search_fields = ['order_id', 'track_id', 'user__phone', 'user__name']
    readonly_fields = [f.name for f in Transaction._meta.fields]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def get_queryset(self, request):
        return super().get_queryset(request).select_related('user', 'plan')

    # ── Custom URLs ───────────────────────────────────────────────────────
    # Prepended, same reason as business/admin.py's moderation queue: these
    # must win over ModelAdmin's catch-all `<path:object_id>/` pattern, or
    # "reports" gets parsed as a primary key.

    def get_urls(self):
        custom = [
            path(
                'reports/',
                self.admin_site.admin_view(self.financial_report_view),
                name='accounting_financial_report',
            ),
            path(
                'reports/export.csv',
                self.admin_site.admin_view(self.financial_report_csv_view),
                name='accounting_financial_report_csv',
            ),
        ]
        return custom + super().get_urls()

    def changelist_view(self, request, extra_context=None):
        # A plain link into the report from the changelist a Finance user
        # already lands on — same "button on the list page" pattern as the
        # moderation queue's entry point off BusinessAdmin.
        extra_context = extra_context or {}
        extra_context['financial_report_url'] = reverse(
            'admin:accounting_financial_report', current_app=self.admin_site.name,
        )
        return super().changelist_view(request, extra_context)

    # ── Financial report ─────────────────────────────────────────────────

    def _check_access(self, request):
        # has_view_permission() is exactly "holds accounting.view_transaction
        # (or a superuser)" for this ModelAdmin — Finance has that permission,
        # Moderator and Support do not (see setup_admin_roles.py), so this is
        # the real permission gate the task calls for, not merely a hidden
        # nav link. admin_view() already enforced is_staff/login before this
        # runs; this adds the per-model check on top of it.
        if not self.has_view_permission(request):
            raise PermissionDenied

    def financial_report_view(self, request):
        self._check_access(request)

        date_from = request.GET.get('from', '')
        date_to = request.GET.get('to', '')
        report = None
        error = None
        try:
            report = reports.build_report(date_from, date_to)
            _add_display_percentages(report)
        except reports.ReportRangeError as exc:
            error = str(exc)

        context = {
            **self.admin_site.each_context(request),
            'title': 'گزارش مالی',
            'opts': self.model._meta,
            'report': report,
            'error': error,
            'date_from': date_from or (report['range']['from_jalali'] if report else ''),
            'date_to': date_to or (report['range']['to_jalali'] if report else ''),
        }
        return TemplateResponse(request, 'admin/accounting/financial_report.html', context)

    def financial_report_csv_view(self, request):
        self._check_access(request)

        date_from = request.GET.get('from', '')
        date_to = request.GET.get('to', '')
        try:
            report = reports.build_report(date_from, date_to)
        except reports.ReportRangeError as exc:
            return HttpResponse(str(exc), status=400, content_type='text/plain; charset=utf-8')

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
        content = buffer.getvalue().encode('utf-8-sig')
        response = HttpResponse(content, content_type='text/csv; charset=utf-8')
        filename = f"financial-report-{report['range']['from'].isoformat()}-{report['range']['to'].isoformat()}.csv"
        response['Content-Disposition'] = f'attachment; filename="{filename}"'
        return response


@admin.register(SubscriptionReminderSettings)
class SubscriptionReminderSettingsAdmin(ModelAdmin):
    list_display = ('__str__', 'is_enabled', 'days_before', 'updated_at')
    readonly_fields = ('updated_at',)
    fieldsets = (
        (None, {
            'fields': ('is_enabled', 'days_before', 'message_template', 'updated_at'),
            'description': (
                'اجرا هر ساعت با <code>python manage.py run_subscription_lifecycle</code> '
                '(کرون سرور). جاهای خالی مجاز در متن پیامک: <code>{plan}</code> نام پلن، '
                '<code>{days}</code> تعداد روز باقی‌مانده.'
            ),
        }),
    )

    def has_add_permission(self, request):
        # Singleton: the row is created on first access by
        # SubscriptionReminderSettings.load(), a second row would just shadow it.
        return request.user.is_superuser and not SubscriptionReminderSettings.objects.exists()

    def has_delete_permission(self, request, obj=None):
        return False

    def changelist_view(self, request, extra_context=None):
        if request.user.is_superuser:
            SubscriptionReminderSettings.load()
        return super().changelist_view(request, extra_context)


@admin.register(ContactVisibilityPolicy)
class ContactVisibilityPolicyAdmin(ModelAdmin):
    """One settings page, no list: the changelist goes straight to the row."""

    readonly_fields = ('affected_businesses', 'updated_at')
    fieldsets = (
        (None, {
            'fields': ('hide_when_expired', 'hide_phone', 'hide_address'),
            'description': (
                'برای کسب‌وکاری که صاحبش اشتراک فعال ندارد (دوره‌ی آزمایشی یا اشتراکش تمام شده و '
                'تمدید نکرده)، موارد انتخاب‌شده از پاسخ سرور حذف می‌شوند — یعنی واقعاً به مرورگر مشتری '
                'نمی‌رسند — و صفحه‌ی کسب‌وکار به‌جایشان یک نسخه‌ی محو و ناخوانا نشان می‌دهد. '
                'این سیاست مستقل از تنظیم خود صاحب کسب‌وکار («نمایش اطلاعات تماس به کاربران مهمان») است '
                'و سرور با فیلد <code>is_expired</code> این دو را از هم جدا می‌کند. '
                'با تمدید اشتراک، اطلاعات بلافاصله دوباره نمایش داده می‌شود.'
            ),
        }),
        ('وضعیت فعلی', {
            'fields': ('affected_businesses', 'updated_at'),
        }),
    )

    @admin.display(description='کسب‌وکارهای مشمول')
    def affected_businesses(self, obj):
        from business.models import Business
        from .entitlements import owners_with_active_subscription

        count = (
            Business.objects.filter(Business.public_filter())
            .exclude(user_id__in=owners_with_active_subscription())
            .count()
        )
        state = 'پنهان شده' if obj.hide_when_expired else 'اگر روشن شود پنهان می‌شود'
        return format_html(
            '<b>{}</b> کسب‌وکار عمومی صاحب اشتراک فعال ندارد — اطلاعات تماسشان {}.',
            count, state,
        )

    def has_add_permission(self, request):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def changelist_view(self, request, extra_context=None):
        if not self.has_view_or_change_permission(request):
            raise PermissionDenied
        policy = ContactVisibilityPolicy.load()
        return redirect(reverse('admin:accounting_contactvisibilitypolicy_change', args=[policy.pk]))

    def response_change(self, request, obj):
        # Back to this same page (the default goes to the changelist, which
        # would only redirect here again) with the usual "saved" message.
        messages.success(request, 'سیاست نمایش اطلاعات تماس ذخیره شد.')
        return redirect(request.path)


@admin.register(CreditLedger)
class CreditLedgerAdmin(ModelAdmin):
    """Read-only browsing of the SMS/appointment wallet audit trail.

    This is the one piece phase 5 (CreditLedger) never actually connected to
    anything: the model and its query layer (accounting/ledger_reports.py)
    existed with no admin registration and no page anywhere that reads them,
    so "how did this user's balance get to zero" — the exact question this
    table exists to answer — had no answer short of a Django shell. Filling
    that gap here, not by building a new page: this changelist alone (filter
    by user/metric, search, read the delta/balance_after/reason columns) is
    the whole reporting surface CreditLedger needs; there's no case here for
    a bespoke report template the way accounting/reports.py's financial
    report earns one.

    Read-only for the same reason Transaction/AddOnPurchase's audit-style
    tables are: nothing in this codebase ever edits a ledger row after
    creation (see accounting/usage.py's _write_ledger), and giving the panel
    edit controls would only invite an accidental rewrite of an audit trail
    that has no audit trail of its own to catch it.
    """
    list_display = ['created_at', 'user', 'metric', 'delta', 'balance_after', 'reason', 'ref_type', 'ref_id']
    list_filter = ['metric', 'reason']
    search_fields = ['user__phone', 'user__name', 'ref_type']
    readonly_fields = [f.name for f in CreditLedger._meta.fields]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def get_queryset(self, request):
        return super().get_queryset(request).select_related('user')


@admin.register(StorePurchase)
class StorePurchaseAdmin(ModelAdmin):
    """Read-only audit of in-app store purchases (Myket), same reasoning as
    TransactionAdmin: each row is the record of a real store payment."""
    list_display = ['created_at', 'store', 'user', 'kind', 'sku', 'status', 'consumed_at']
    list_filter = ['store', 'kind', 'status']
    search_fields = ['user__phone', 'user__name', 'sku', 'purchase_token', 'order_id', 'payload']
    readonly_fields = [f.name for f in StorePurchase._meta.fields]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False
