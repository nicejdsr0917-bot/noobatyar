"""360 pages: user detail, business detail (with the owner-message form) and
the full customer list of one business.

core.detail_views is the query layer; these views do only "fetch object →
gate on the page-level permission → let detail_views gate every panel
individually → render".
"""

from django.contrib import messages
from django.http import HttpResponseRedirect
from django.shortcuts import get_object_or_404
from django.template.response import TemplateResponse
from django.urls import reverse

from ._common import require_perm


def user_detail_view(site, request, user_id):
    from api.models import User
    from core import detail_views

    # Page-level gate: no api.view_user, no page at all — matches how
    # accounting.TransactionAdmin._check_access() gates the financial
    # report on the one permission that makes the page meaningful.
    # Everything *inside* the page (subscriptions, wallet, businesses,
    # activity) degrades panel-by-panel from there — see
    # detail_views.permissions_for_user().
    require_perm(request, 'api.view_user')
    user = get_object_or_404(User, pk=user_id)
    context = {
        **site.each_context(request),
        'title': f'{user.name} ({user.phone})',
        'opts': User._meta,
        'detail': detail_views.build_user_detail(user, request.user),
    }
    return TemplateResponse(request, 'admin/core/user_detail.html', context)


def business_detail_view(site, request, business_id):
    from business.models import Business
    from core import detail_views
    from core.models import AdminMessageLog

    require_perm(request, 'business.view_business')
    business = get_object_or_404(Business, pk=business_id)

    can_message = request.user.has_perm('core.send_business_message')

    if request.method == 'POST' and can_message:
        handle_business_message(request, business)
        # PRG: a refresh after sending must not resubmit the form.
        return HttpResponseRedirect(
            reverse('admin:core_business_detail', args=[business.id], current_app=site.name)
        )

    context = {
        **site.each_context(request),
        'title': business.title,
        'opts': Business._meta,
        'detail': detail_views.build_business_detail(business, request.user),
        'customers_url': reverse(
            'admin:core_business_customers', args=[business.id], current_app=site.name,
        ),
        'can_message': can_message,
        'message_logs': (
            AdminMessageLog.objects.filter(business=business).select_related('sent_by')[:20]
            if can_message else []
        ),
    }
    return TemplateResponse(request, 'admin/core/business_detail.html', context)


def handle_business_message(request, business):
    """POST handler for the "ارسال پیام" form on the business 360 page.

    Split out of business_detail_view so that view stays a readable
    GET-shaped function — this is the one branch that does something
    rather than just rendering.
    """
    from core.messaging import send_admin_message

    title = (request.POST.get('message_title') or '').strip()
    body = (request.POST.get('message_body') or '').strip()
    channels = request.POST.getlist('channels')

    if not body:
        messages.error(request, 'متن پیام نمی‌تواند خالی باشد.')
        return
    if not channels:
        messages.error(request, 'حداقل یک کانال ارسال را انتخاب کنید.')
        return

    logs = send_admin_message(business, title, body, channels, actor=request.user)
    # `logs` holds only the channels that failed before they could be
    # queued; the rest are delivered by the outbox worker, which writes
    # their own log rows (visible in the message log panel).
    failed = [log for log in logs if log.status == 'FAILED']
    queued = len([c for c in ('PUSH', 'SMS') if c in channels]) - len(logs)
    if not failed:
        messages.success(request, 'پیام در صف ارسال قرار گرفت.')
    elif queued <= 0:
        messages.error(request, f'ارسال پیام ناموفق بود: {failed[0].error_detail}')
    else:
        messages.warning(
            request,
            f'پیام برای یکی از کانال‌ها در صف ارسال قرار گرفت؛ کانال دیگر ناموفق بود: {failed[0].error_detail}',
        )


def business_customers_view(site, request, business_id):
    """The full, paginated customer list for one business — the standalone
    counterpart to the short preview embedded in business_detail_view.

    Gated on `visitor.view_visitor` in addition to `business.view_business`:
    unlike the 360 page (where this is one panel among several that can
    each be hidden), this whole page *is* the customer list, so the
    equivalent of "hide the panel" here is "no page".
    """
    from django.core.paginator import Paginator

    from business.models import Business
    from core import detail_views

    require_perm(request, 'business.view_business')
    require_perm(request, 'visitor.view_visitor')
    business = get_object_or_404(Business, pk=business_id)

    page_number = request.GET.get('page') or 1
    paginator = Paginator(detail_views.business_customers_queryset(business), 50)
    page = paginator.get_page(page_number)

    context = {
        **site.each_context(request),
        'title': f'مراجعان {business.title}',
        'opts': Business._meta,
        'business': business,
        'page': page,
        'total': paginator.count,
    }
    return TemplateResponse(request, 'admin/core/business_customers.html', context)
