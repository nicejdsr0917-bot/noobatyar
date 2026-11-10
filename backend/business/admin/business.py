"""BusinessAdmin: the moderation review queue and bulk decisions (see package docstring)."""

import csv as _csv
import json as _json
import re
import time
import uuid
from pathlib import Path

from api.jalali import format_datetime
from django.conf import settings
from django.contrib import admin, messages
from django.core.exceptions import PermissionDenied
from django.core.paginator import Paginator
from django.db import transaction
from django.http import HttpResponse
from django.shortcuts import redirect
from django.db.models import F
from django.shortcuts import get_object_or_404
from django.template.response import TemplateResponse
from django.urls import path, reverse
from django.utils import timezone
from django.utils.decorators import method_decorator
from django.utils.html import format_html
from django.views.decorators.http import require_POST
from unfold.admin import ModelAdmin, TabularInline
from .. import directory_import, moderation
from ..models import BannedKeyword, Business, BusinessModerationLog
from ..services import apply_bulk_moderation_decision, apply_moderation_decision
from .directory_form import DirectoryFormMixin
from ._common import QUEUE_PAGE_SIZE, MODERATED_FIELD_LABELS, _STATUS_BADGE, _badge, _highlight, _safe_redirect, _BulkConfirmMixin


class BusinessModerationLogInline(TabularInline):
    """The decision history, shown read-only on the business change page.

    An inline rather than a separate screen because the first question a
    reviewer asks about a business is "has this been through here before, and
    what did we say" — the answer has to be on the page they are already on.
    """

    model = BusinessModerationLog
    extra = 0
    can_delete = False
    fields = ('created_jalali', 'transition', 'note', 'actor')
    readonly_fields = ('created_jalali', 'transition', 'note', 'actor')
    verbose_name = 'تصمیم بررسی'
    verbose_name_plural = 'تاریخچه بررسی'
    ordering = ('-created_at',)

    # The log is an audit trail: it is written by moderation.apply_decision()
    # and never by hand, so no add and no change from anywhere in the admin.
    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    @admin.display(description='زمان')
    def created_jalali(self, obj):
        return format_datetime(obj.created_at) if obj.created_at else '—'

    @admin.display(description='تغییر وضعیت')
    def transition(self, obj):
        return f"{obj.from_status or '—'} ← {obj.to_status}"


@admin.register(Business)
class BusinessAdmin(_BulkConfirmMixin, DirectoryFormMixin, ModelAdmin):
    list_display = (
        'title', 'user', 'unique_code', 'category',
        'origin', 'moderation_badge', 'lock_badge', 'rank_column', 'created_at',
    )
    list_filter = ('origin', 'moderation_status', 'category', 'is_claimed', 'is_locked')
    search_fields = ('title', 'unique_code', 'user__phone', 'bio')
    filter_horizontal = ('insurances',)
    date_hierarchy = 'created_at'
    inlines = [BusinessModerationLogInline]

    # `list_editable = ('is_locked',)` was removed deliberately. `is_locked` is
    # *derived* state: business/services.py:sync_locks() recomputes it from the
    # owner's plan quota on every subscription change, so a value typed into the
    # changelist is reverted the next time that runs — with no warning and no
    # trace. Unlocking a business for real means giving the owner the quota
    # (accounting → اشتراک). The field is still editable on the change form for
    # the rare deliberate override, where the help text is visible.

    # Set by the workflow, never typed. moderation_status/moderation_note are in
    # here too, one step beyond what a "system-set field" normally means: a
    # status edited on this form would change what the public sees with no
    # BusinessModerationLog row behind it, i.e. an unattributable decision.
    # Use the review queue or the changelist actions instead — both log.
    #
    # `unique_code` used to be listed here as system-set. It no longer is: the
    # code is the public URL of the booking page and operators need to be able
    # to hand out a vanity one. It stays *generated* by default (blank on the
    # form → Business.save() makes an 8-character code), so nothing changes for
    # a business nobody edits. Editing it is a real, visible decision — the
    # field's help text says that old links stop working, and Business.clean()
    # rejects a code that collides with another one case-insensitively.
    readonly_fields = (
        'moderation_status',
        'moderation_note',
        'moderation_reviewed_by',
        'moderation_reviewed_at',
        'moderation_submitted_at',
        'created_at',
        'updated_at',
        # Written by reviews and the nightly compute_rank_scores; a hand-typed
        # value would be overwritten by the next run.
        'rating_avg',
        'rating_count',
        'rank_score',
        'rank_updated_at',
    )

    actions = ('action_approve', 'action_reject', 'action_suspend')

    # No `fieldsets`: Business is under active development by other parts of the
    # team and an explicit fieldset list silently hides any newly added field
    # from this form. Grouping is not worth losing fields over.

    def get_queryset(self, request):
        # The changelist renders `user` on every row (and search hits
        # user__phone), so join it once instead of per row.
        qs = super().get_queryset(request).select_related('user')
        # «کسب‌وکارها» lists owners with a tick; unclaimed directory listings live
        # under «مراکز» (admin/centers.py). Only the *list* is narrowed: a center's
        # change page, delete page and the claim-request links still resolve here.
        match = getattr(request, 'resolver_match', None)
        if match is not None and (match.url_name or '').endswith('_changelist'):
            qs = qs.filter(is_claimed=True)
        return qs

    # ── Changelist columns ────────────────────────────────────────────────
    # Two badges, deliberately different shapes and wordings, because the two
    # flags get confused constantly: moderation_status is *editorial* (has a
    # human cleared this copy?) and is_locked is *billing* (is the owner's plan
    # paying for this business?). Either one alone hides a business from the
    # public; neither implies the other.

    @admin.display(description='بررسی محتوا', ordering='moderation_status')
    def moderation_badge(self, obj):
        return _badge(
            obj.get_moderation_status_display(),
            _STATUS_BADGE.get(obj.moderation_status, ''),
        )

    @admin.display(description='وضعیت پلن', ordering='is_locked')
    def lock_badge(self, obj):
        if obj.is_locked:
            return _badge('🔒 قفل‌شده (سهمیه پلن)', 'danger')
        # Not a badge: "فعال" is the unremarkable state, and pilling it would
        # give the normal case the same visual weight as the one that needs
        # somebody to act.
        return format_html('<span class="nb-quiet">فعال</span>')

    @admin.display(description='رتبه', ordering='-rank_score')
    def rank_column(self, obj):
        # Score out of ~100 (× promo multiplier) with the rating behind it, so
        # an operator can see why a business sits where it does in the app.
        if not obj.rank_updated_at:
            return format_html('<span class="nb-quiet">محاسبه نشده</span>')
        return format_html(
            '{} <span class="nb-quiet">(★ {} از {} نظر)</span>',
            round(obj.rank_score), f'{obj.rating_avg:.1f}', obj.rating_count,
        )

    # ── Custom URLs ───────────────────────────────────────────────────────

    def get_urls(self):
        # Prepended, so they win over ModelAdmin's catch-all `<path:object_id>/`
        # pattern — otherwise "moderation-queue" is read as a primary key.
        custom = [
            path(
                'moderation-queue/',
                self.admin_site.admin_view(self.moderation_queue_view),
                name='business_business_moderation_queue',
            ),
            path(
                'moderation-queue/<int:pk>/decide/',
                self.admin_site.admin_view(self.moderation_decide_view),
                name='business_business_moderation_decide',
            ),
            path(
                'import-file/',
                self.admin_site.admin_view(self.import_file_view),
                name='business_business_import_file',
            ),
            path(
                'import-file/template.csv',
                self.admin_site.admin_view(self.import_template_view),
                name='business_business_import_template',
            ),
        ]
        return custom + super().get_urls()

    def changelist_view(self, request, extra_context=None):
        # Feeds the "صف بررسی" button in the overridden change_list template.
        extra_context = extra_context or {}
        extra_context['moderation_pending_count'] = Business.objects.filter(
            moderation_status=Business.MODERATION_PENDING
        ).count()
        extra_context['import_file_url'] = reverse(
            'admin:business_business_import_file', current_app=self.admin_site.name
        )
        extra_context['moderation_queue_url'] = reverse(
            'admin:business_business_moderation_queue', current_app=self.admin_site.name
        )
        return super().changelist_view(request, extra_context)

    # ── Import from a file ────────────────────────────────────────────────

    IMPORT_MAX_BYTES = 20 * 1024 * 1024
    IMPORT_KEEP_SECONDS = 24 * 3600

    @staticmethod
    def _import_dir():
        path = Path(settings.MEDIA_ROOT) / 'tmp_imports'
        path.mkdir(parents=True, exist_ok=True)
        return path

    def _drop_old_imports(self):
        cutoff = time.time() - self.IMPORT_KEEP_SECONDS
        for f in self._import_dir().iterdir():
            if f.is_file() and f.stat().st_mtime < cutoff:
                f.unlink(missing_ok=True)

    def import_template_view(self, request):
        if not self.has_add_permission(request):
            raise PermissionDenied
        guild = self._guild(request)
        response = HttpResponse(directory_import.template_csv(guild or None), content_type='text/csv; charset=utf-8')
        response['Content-Disposition'] = f'attachment; filename="directory-template{"-" + guild.lower() if guild else ""}.csv"'
        return response

    @staticmethod
    def _guild(request):
        """The guild whose bulk page this is (``?guild=BEAUTY``), or '' for all categories."""
        from ..models.guilds import GUILDS
        key = request.GET.get('guild') or request.POST.get('guild') or ''
        return key if key in GUILDS else ''

    def _import_context(self, request):
        from ..categories import CATEGORY_GROUPS
        from ..models.guilds import GUILDS, SERVICE_EXAMPLES, TAKES_INSURANCE
        guild = self._guild(request)
        codes = {code for key, _l, pairs in CATEGORY_GROUPS if key == guild for code, _ in pairs}
        options = [(c, l) for c, l in Business.CATEGORY_CHOICES if not guild or c in codes]
        chosen = request.POST.get('default_category') or (options[0][0] if guild else '')
        template_url = reverse('admin:business_business_import_template', current_app=self.admin_site.name)
        columns = [
            ('نام', True, 'نام مرکز'),
            ('شهر', True, 'برای نمایش در فهرست شهر و تشخیص کد شهر'),
            ('صنف', False, 'نام فارسی یا کد صنف' + ('؛ خالی یعنی صنف پیش‌فرض بالا' if guild else '؛ ناشناخته یعنی «سایر»')),
            ('استان', False, 'برای شهرهای هم‌نام'),
            ('محله', False, ''),
            ('آدرس', False, 'خالی باشد از شهر و محله ساخته می‌شود'),
            ('تلفن', False, 'بدون تلفن هم ثبت می‌شود (دکمه تماس نمایش داده نمی‌شود)'),
            ('عرض جغرافیایی / طول جغرافیایی', False, 'هر دو یا هیچ‌کدام؛ بدون آن‌ها روی نقشه نمی‌آید'),
            ('خدمات', False, 'با ویرگول جدا شود' + (f'؛ مثلاً {SERVICE_EXAMPLES[guild]}' if SERVICE_EXAMPLES.get(guild) else '')),
        ]
        if not guild or guild in TAKES_INSURANCE:
            columns.append(('بیمه‌ها', False, 'نام بیمه، با ویرگول جدا شود'))
        columns.append(('معرفی', False, ''))
        return {
            **self.admin_site.each_context(request),
            'title': f'ورود گروهی: {GUILDS[guild][0]._meta.verbose_name_plural}' if guild else 'ورود از فایل',
            'opts': self.model._meta,
            'guild': guild,
            'default_category_options': options,
            'default_category': chosen,
            'template_url': template_url + (f'?guild={guild}' if guild else ''),
            'columns': columns,
            'categories': [label for _code, label in options],
        }

    def import_file_view(self, request):
        """Upload a file or paste rows → check (nothing saved) → confirm. The checked
        data is kept server-side under a token, so confirming imports exactly what was shown."""
        if not self.has_add_permission(request):
            raise PermissionDenied
        context = self._import_context(request)
        if request.method == 'POST':
            token = request.POST.get('token', '')
            source = (request.POST.get('source') or '').strip()[:100]
            if token:
                return self._import_confirm(request, token, source, context)
            return self._import_preview(request, source, context)
        return TemplateResponse(request, 'admin/business/import_file.html', context)

    def _import_preview(self, request, source, context):
        render = lambda: TemplateResponse(request, 'admin/business/import_file.html', context)  # noqa: E731
        upload = request.FILES.get('file')
        pasted = (request.POST.get('pasted') or '').strip()
        if upload is None and not pasted:
            context['error'] = 'فایلی انتخاب نشده و متنی چسبانده نشده است.'
            return render()
        if upload is not None:
            ext = Path(upload.name).suffix.lower()
            if ext not in directory_import.READERS:
                context['error'] = 'فقط فایل CSV، JSON یا JSONL پذیرفته می‌شود. در اکسل: Save As ← CSV UTF-8.'
                return render()
            if upload.size > self.IMPORT_MAX_BYTES:
                context['error'] = 'حجم فایل بیشتر از ۲۰ مگابایت است؛ آن را به چند فایل تقسیم کنید.'
                return render()
            label = upload.name
        else:
            ext, label = '.csv', 'متن چسبانده‌شده'
            if len(pasted.encode('utf-8')) > self.IMPORT_MAX_BYTES:
                context['error'] = 'متن بیش از حد بزرگ است؛ از فایل استفاده کنید.'
                return render()

        self._drop_old_imports()
        token = uuid.uuid4().hex
        path = self._import_dir() / f'{token}{ext}'
        with open(path, 'wb') as out:
            if upload is not None:
                for chunk in upload.chunks():
                    out.write(chunk)
            else:
                out.write(pasted.encode('utf-8'))
        default_category = context['default_category'] or None
        try:
            report = directory_import.run_import(
                directory_import.read_rows(path), source or Path(label).stem, dry_run=True,
                default_category=default_category,
            )
        except (ValueError, UnicodeDecodeError, _csv.Error, _json.JSONDecodeError) as exc:
            path.unlink(missing_ok=True)
            context['error'] = f'فایل خوانده نشد: {exc}'
            return render()

        context.update({
            'report': report,
            'token': token,
            'source': source or Path(label).stem[:100],
            'file_name': label,
        })
        return render()

    def _import_confirm(self, request, token, source, context):
        match = re.fullmatch(r'[0-9a-f]{32}', token)
        path = next(self._import_dir().glob(f'{token}.*'), None) if match else None
        if path is None:
            context['error'] = 'این فایل دیگر در دسترس نیست؛ دوباره آن را بارگذاری کنید.'
            return TemplateResponse(request, 'admin/business/import_file.html', context)
        try:
            with transaction.atomic():
                report = directory_import.run_import(
                    directory_import.read_rows(path), source or 'directory_import',
                    default_category=context['default_category'] or None,
                )
        except (ValueError, UnicodeDecodeError, _csv.Error, _json.JSONDecodeError) as exc:
            context['error'] = f'فایل خوانده نشد: {exc}'
            return TemplateResponse(request, 'admin/business/import_file.html', context)
        finally:
            path.unlink(missing_ok=True)

        messages.success(
            request,
            f'{report.created} مرکز ثبت شد (منبع: {source or "directory_import"}). '
            f'{report.duplicates} تکراری و {report.invalid} ردیف نامعتبر رد شد.',
        )
        from ..models.guilds import GUILDS
        guild = context['guild']
        target = (
            f'admin:business_{GUILDS[guild][0].__name__.lower()}_changelist' if guild else 'admin:business_centerlisting_changelist'
        )
        return redirect(reverse(target, current_app=self.admin_site.name) + '?is_claimed__exact=0')

    # ── The review queue ──────────────────────────────────────────────────

    def _pending_queryset(self):
        return (
            Business.objects
            .filter(moderation_status=Business.MODERATION_PENDING)
            .select_related('user')
            # Oldest wait first. nulls_first because a row with no
            # submitted_at predates the moderation system (see migration 0013)
            # and has therefore been waiting the longest of all. Matches the
            # biz_moderation_queue_idx index on (status, submitted_at).
            .order_by(F('moderation_submitted_at').asc(nulls_first=True), 'created_at', 'id')
        )

    def _build_card(self, business, keywords):
        """Everything the queue template needs for one business."""
        matches = moderation.scan_keywords(business, keywords=keywords)

        # Highlight each field only with the terms that actually hit *it*, so a
        # term found in the address is not also marked up inside the title.
        terms_by_field = {}
        for match in matches:
            terms_by_field.setdefault(match['field'], []).append(match['term'])
            # scan_keywords() returns raw field names because it is shared with
            # the API layer; the reviewer sees the Persian label instead.
            match['field_label'] = MODERATED_FIELD_LABELS.get(match['field'], match['field'])

        fields = []
        for name, value in moderation.moderated_texts(business).items():
            if not value:
                continue
            fields.append({
                'name': name,
                'label': MODERATED_FIELD_LABELS.get(name, name),
                'html': _highlight(value, terms_by_field.get(name, [])),
                'flagged': name in terms_by_field,
            })

        submitted = business.moderation_submitted_at or business.created_at
        return {
            'business': business,
            'fields': fields,
            'matches': matches,
            'has_high': any(m['severity'] == BannedKeyword.SEVERITY_HIGH for m in matches),
            'submitted_jalali': format_datetime(submitted) if submitted else '—',
            'never_submitted': business.moderation_submitted_at is None,
            'change_url': reverse(
                'admin:business_business_change', args=[business.pk],
                current_app=self.admin_site.name,
            ),
            'decide_url': reverse(
                'admin:business_business_moderation_decide', args=[business.pk],
                current_app=self.admin_site.name,
            ),
        }

    def moderation_queue_view(self, request):
        # admin_view() already enforces staff+login; this adds the per-model
        # check, so a staff account without business.change_business cannot
        # reach the decision buttons by URL.
        if not self.has_change_permission(request):
            raise PermissionDenied

        paginator = Paginator(self._pending_queryset(), QUEUE_PAGE_SIZE)
        page = paginator.get_page(request.GET.get('page'))

        # One query for the whole page instead of one per card.
        keywords = list(BannedKeyword.objects.filter(is_active=True))
        cards = [self._build_card(biz, keywords) for biz in page.object_list]

        context = {
            **self.admin_site.each_context(request),
            'title': 'صف بررسی محتوا',
            'opts': self.model._meta,
            'cards': cards,
            'page_obj': page,
            'paginator': paginator,
            'total_pending': paginator.count,
            'keyword_count': len(keywords),
        }
        return TemplateResponse(request, 'admin/business/moderation_queue.html', context)

    # method_decorator, not a bare @require_POST: the plain decorator inspects
    # its first positional argument as the request, which on a bound method is
    # `self` — it would silently never reject anything.
    @method_decorator(require_POST)
    def moderation_decide_view(self, request, pk):
        """Approve or reject a single business from the queue.

        POST-only and CSRF-protected (no csrf_exempt anywhere in this module):
        a decision is a state change on public content, and link prefetchers,
        crawlers behind an authenticated session, and the browser's back button
        all issue GETs.
        """
        if not self.has_change_permission(request):
            raise PermissionDenied

        business = get_object_or_404(Business, pk=pk)
        fallback = reverse(
            'admin:business_business_moderation_queue', current_app=self.admin_site.name
        )

        decision = request.POST.get('decision')
        note = (request.POST.get('note') or '').strip()

        if decision == 'approve':
            to_status = Business.MODERATION_APPROVED
        elif decision == 'reject':
            to_status = Business.MODERATION_REJECTED
            # A rejection with no reason is unusable: the owner is told "no"
            # with nothing to fix, and nobody can answer the support ticket
            # three months later. Approval needs no note — "it was fine" is a
            # complete explanation.
            if not note:
                self.message_user(
                    request,
                    f'برای رد کردن «{business.title}» باید دلیل بنویسید.',
                    level=messages.ERROR,
                )
                return _safe_redirect(request, fallback)
        else:
            self.message_user(request, 'تصمیم نامعتبر است.', level=messages.ERROR)
            return _safe_redirect(request, fallback)

        notified = apply_moderation_decision(
            business, to_status, actor=request.user, note=note,
        )

        verb = 'تایید' if to_status == Business.MODERATION_APPROVED else 'رد'
        # The decision is already committed at this point; a failed SMS is
        # reported, never treated as a failure of the decision itself.
        if notified:
            self.message_user(request, f'«{business.title}» {verb} شد.', level=messages.SUCCESS)
        else:
            self.message_user(
                request,
                f'«{business.title}» {verb} شد، اما پیامک اطلاع‌رسانی به مالک ارسال نشد.',
                level=messages.WARNING,
            )
        return _safe_redirect(request, fallback)

    # ── Bulk actions ──────────────────────────────────────────────────────

    def _bulk_decide(self, request, queryset, to_status, verb, note_required):
        """Shared body for the three changelist actions.

        Rejection and suspension route through a confirmation page that asks for
        a reason, for the same reason the per-card reject does — a bulk decision
        is not a licence to skip the explanation the owner receives.
        """
        if not self.has_change_permission(request):
            raise PermissionDenied

        note = (request.POST.get('moderation_note') or '').strip()

        if note_required and not request.POST.get('apply'):
            return self._bulk_confirm_response(
                request, queryset, f'{verb} گروهی کسب‌وکارها', verb,
                'admin/business/moderation_bulk.html',
            )

        if note_required and not note:
            self.message_user(request, 'نوشتن دلیل الزامی است.', level=messages.ERROR)
            return self._bulk_confirm_response(
                request, queryset, f'{verb} گروهی کسب‌وکارها', verb,
                'admin/business/moderation_bulk.html', note_error='نوشتن دلیل الزامی است.',
            )

        count, unnotified = apply_bulk_moderation_decision(
            queryset, to_status, actor=request.user, note=note,
        )

        self.message_user(request, f'{count} کسب‌وکار {verb} شد.', level=messages.SUCCESS)
        if unnotified:
            self.message_user(
                request,
                f'برای {unnotified} مورد پیامک اطلاع‌رسانی ارسال نشد.',
                level=messages.WARNING,
            )
        return None

    @admin.action(description='تایید کسب‌وکارهای انتخاب‌شده')
    def action_approve(self, request, queryset):
        return self._bulk_decide(
            request, queryset, Business.MODERATION_APPROVED, 'تایید', note_required=False,
        )

    @admin.action(description='رد کردن کسب‌وکارهای انتخاب‌شده')
    def action_reject(self, request, queryset):
        return self._bulk_decide(
            request, queryset, Business.MODERATION_REJECTED, 'رد', note_required=True,
        )

    @admin.action(description='معلق کردن کسب‌وکارهای انتخاب‌شده')
    def action_suspend(self, request, queryset):
        return self._bulk_decide(
            request, queryset, Business.MODERATION_SUSPENDED, 'تعلیق', note_required=True,
        )
