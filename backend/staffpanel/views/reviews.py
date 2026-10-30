"""نظرات (hide / unhide reviews) and تخلف‌ها (close ContentReports).

Writes go through ``reviews.services.set_hidden`` (also used by the admin
actions; it refreshes rating summaries) and
``business.services.resolve_content_reports``.
"""

from django.db.models import Count, Exists, OuterRef, Q
from django.shortcuts import get_object_or_404

from business.models import ContentReport
from business.services import resolve_content_reports
from reviews import services as review_services
from reviews.models import Review

from .. import nav
from ..access import panel_view
from ._common import back, fail, file_code_id, ok, paginate, render_panel, search_term

OPEN_REPORT = (ContentReport.STATUS_NEW, ContentReport.STATUS_REVIEWING)

REVIEW_TABS = (
    ('all', 'همه'),
    ('visible', 'نمایش'),
    ('hidden', 'مخفی'),
    ('reported', 'گزارش‌شده'),
)


@panel_view('reviews.view_review')
def reviews(request):
    tab = request.GET.get('tab') if request.GET.get('tab') in dict(REVIEW_TABS) else 'all'
    reported = Exists(ContentReport.objects.filter(review=OuterRef('pk'), status__in=OPEN_REPORT))
    base = Review.objects.annotate(is_reported=reported)

    counts = base.aggregate(
        all=Count('id'),
        visible=Count('id', filter=Q(is_hidden=False)),
        hidden=Count('id', filter=Q(is_hidden=True)),
        reported=Count('id', filter=Q(is_reported=True)),
    )
    queryset = base.select_related('business', 'visitor')
    if tab == 'visible':
        queryset = queryset.filter(is_hidden=False)
    elif tab == 'hidden':
        queryset = queryset.filter(is_hidden=True)
    elif tab == 'reported':
        queryset = queryset.filter(is_reported=True)

    rating = request.GET.get('rating')
    if rating in {'1', '2', '3', '4', '5'}:
        queryset = queryset.filter(rating=int(rating))
    business_id = request.GET.get('business')
    if business_id and business_id.isdigit():
        queryset = queryset.filter(business_id=business_id)
    q = search_term(request)
    if q:
        code = file_code_id(q)
        queryset = queryset.filter(visitor_id=code) if code else queryset.filter(
            Q(business__title__icontains=q) | Q(visitor__full_name__icontains=q)
            | Q(visitor__phone_number__contains=q) | Q(text__icontains=q))

    page = paginate(request, queryset.order_by('-created_at', '-id'))
    return render_panel(request, 'staffpanel/reviews.html', 'reviews', {
        'tab': tab, 'tabs': [(k, label, counts[k]) for k, label in REVIEW_TABS],
        'page_obj': page, 'q': q, 'rating': rating,
        'can_change': request.user.has_perm('reviews.change_review'),
    }, title='نظرات')


@panel_view(('reviews.view_review', 'reviews.change_review'), methods=('POST',))
def review_visibility(request, pk):
    review = get_object_or_404(Review, pk=pk)
    hide = request.POST.get('hidden') == '1'
    reason = (request.POST.get('note') or '').strip()
    if hide and not reason:
        fail(request, 'برای مخفی کردن نظر دلیل بنویسید.')
    else:
        review_services.set_hidden(Review.objects.filter(pk=review.pk), hide, reason)
        ok(request, 'نظر مخفی شد.' if hide else 'نظر دوباره نمایش داده می‌شود.')
    return back(request, 'staffpanel:reviews')


# ── Content reports ──────────────────────────────────────────────────────────

REPORT_TABS = (
    ('new', 'جدید', (ContentReport.STATUS_NEW,)),
    ('reviewing', 'در حال بررسی', (ContentReport.STATUS_REVIEWING,)),
    ('closed', 'بسته‌شده', (ContentReport.STATUS_ACTIONED, ContentReport.STATUS_DISMISSED)),
)


@panel_view('business.view_contentreport')
def reports(request):
    tabs = {k: statuses for k, _l, statuses in REPORT_TABS}
    tab = request.GET.get('tab') if request.GET.get('tab') in tabs else 'new'
    counts = ContentReport.objects.aggregate(**{
        k: Count('id', filter=Q(status__in=statuses)) for k, statuses in tabs.items()
    })
    queryset = (ContentReport.objects.filter(status__in=tabs[tab])
                .select_related('business', 'reporter_visitor', 'reporter_user', 'review', 'resolved_by'))
    q = search_term(request)
    if q:
        queryset = queryset.filter(
            Q(business__title__icontains=q) | Q(detail__icontains=q) | Q(reporter_phone__contains=q)
            | Q(reporter_visitor__phone_number__contains=q))
    page = paginate(request, queryset.order_by('-created_at', '-id'))
    return render_panel(request, 'staffpanel/reports.html', 'reports', {
        'tab': tab, 'tabs': [(k, label, counts[k]) for k, label, _s in REPORT_TABS],
        'page_obj': page, 'q': q,
        'can_change': request.user.has_perm('business.change_contentreport'),
        'can_hide_review': request.user.has_perm('reviews.change_review'),
    }, title='گزارش‌های تخلف')


RESOLUTIONS = {
    'reviewing': (ContentReport.STATUS_REVIEWING, 'در حال بررسی'),
    'actioned': (ContentReport.STATUS_ACTIONED, 'اقدام شد'),
    'dismissed': (ContentReport.STATUS_DISMISSED, 'رد شد'),
}


@panel_view(('business.view_contentreport', 'business.change_contentreport'), methods=('POST',))
def report_resolve(request, pk):
    report = get_object_or_404(ContentReport, pk=pk)
    action = request.POST.get('status')
    note = (request.POST.get('note') or '').strip()
    if action not in RESOLUTIONS:
        fail(request, 'وضعیت نامعتبر است.')
        return back(request, 'staffpanel:reports')
    status, label = RESOLUTIONS[action]
    queryset = ContentReport.objects.filter(pk=report.pk)
    if status == ContentReport.STATUS_REVIEWING:
        # Not a resolution: resolved_by/at stay empty, same as the admin action.
        queryset.update(status=status)
    elif not note:
        fail(request, 'برای بستن گزارش نوشتن دلیل الزامی است.')
        return back(request, 'staffpanel:reports')
    else:
        resolve_content_reports(queryset, status, request.user, note)
    nav.forget_badges()
    ok(request, f'گزارش «{label}» شد.')
    return back(request, 'staffpanel:reports')
