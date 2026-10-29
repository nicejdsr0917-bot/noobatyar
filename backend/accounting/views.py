from django.shortcuts import render
from django.utils import timezone
from rest_framework.permissions import IsAuthenticated, AllowAny, IsAdminUser

from api.responses import APIResponse
from api.views import _extract_error
from .models import Plan, Subscription, AddOnPack
from .payment.zibal_payment import ZibalPaymentService
from .payment.zibal_payment_verification import PaymentVerificationService
from .payment.addon_payment import (
    NO_SUBSCRIPTION_MESSAGE, AddOnPaymentService, AddOnVerificationService, can_buy_addon,
)
from .serializers import (
    PlanSerializer, SubscriptionSerializer, BuyPlanSerializer,
    AddOnPackSerializer, BuyAddOnSerializer,
)
from . import entitlements, pricing, usage
from rest_framework.decorators import api_view, permission_classes


def _client_callback(path: str) -> str:
    """Absolute URL on the customer web app for a payment gateway to return to.

    These were hardcoded to https://noobatyar.ir/... — the bare domain, which
    resolves to a *different server* than the one running this app. Zibal sent
    every payer there after paying, so the page that calls the verify endpoint
    never ran: transactions stayed `pending` with an empty zibal_response and
    the plan or add-on was never applied.

    CLIENT_WEB_URL already existed for exactly this (the deposit flow uses it)
    and already defaults to the right host.
    """
    from django.conf import settings

    base = (getattr(settings, 'CLIENT_WEB_URL', '') or '').rstrip('/')
    return f'{base}{path}'


def _from_web_panel(request) -> bool:
    """True when the payment was started by the owner web panel (a browser),
    not by the native apps.

    The gateway sends the payer back to a web page either way, but only the
    native apps own the ``noobatyar://`` link that page uses to return. In a
    browser that link is an invalid address (Safari shows an error), so the
    panel gets its own return page that goes back to the panel instead. The
    browser tells us who is asking in the Origin header on its API calls.
    """
    from django.conf import settings

    panel = (getattr(settings, 'PANEL_WEB_URL', '') or '').rstrip('/')
    origin = (request.headers.get('Origin') or '').rstrip('/')
    if not origin:
        return False
    return origin == panel or origin.startswith(('http://localhost', 'http://127.0.0.1'))


def _payment_callback(request, path: str) -> str:
    """The callback URL for this request: the panel's page for browsers,
    the app-link page for everyone else."""
    return _client_callback(f'{path}-web' if _from_web_panel(request) else path)


@api_view(['GET'])
@permission_classes([AllowAny])
def plan_list(request):
    """لیست پلن‌ها — برای همه قابل مشاهده"""
    plans = Plan.objects.filter(is_active=True)
    serializer = PlanSerializer(plans, many=True)
    return APIResponse.success(
        data=serializer.data,
        message=None
    )


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def my_subscription(request):  # ✅ sync
    """اشتراک فعال یا آخرین وضعیت اشتراک کاربر"""
    
    # ابتدا به دنبال اشتراک فعال می‌گردیم
    subscription = Subscription.objects.filter(
        user=request.user,
        status='active'
    ).select_related('plan').first()

    # اگر اشتراک فعال نداشت، آخرین اشتراک ثبت شده (حتی منقضی) را نشان می‌دهیم
    if not subscription:
        subscription = Subscription.objects.filter(
            user=request.user
        ).select_related('plan').order_by('-started_at').first()

    # NOTE: kept as a flat SubscriptionDto for backward compatibility with the
    # released clients. Entitlements + usage are served by /my-entitlements/.
    if not subscription:
        return APIResponse.success(
            data=None,
            message='اشتراک فعالی ندارید'
        )

    serializer = SubscriptionSerializer(subscription)
    return APIResponse.success(data=serializer.data)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def my_entitlements(request):
    """قابلیت‌ها و سقف‌های پلن فعلی کاربر + میزان مصرف این ماه.

    ``coming_soon`` lists feature keys that a plan may well resolve to ``True``
    but that nothing in this backend implements yet. It is sent as a separate
    list rather than by forcing those keys to ``False``, because the entitlement
    genuinely was sold with the plan — the client's job is to render the row
    disabled with a «به‌زودی» badge, not to pretend the user did not buy it.
    """
    user = request.user
    ent = entitlements.get_entitlements(user)
    return APIResponse.success(data={
        'entitlements': ent,
        'coming_soon': list(entitlements.COMING_SOON_FEATURES),
        'usage': {
            'appointments': usage.appointment_balance(user),
            'sms': usage.sms_balance(user),
        },
    })


@api_view(['GET'])
@permission_classes([AllowAny])
def addon_list(request):
    """لیست بسته‌های افزودنی فعال."""
    packs = AddOnPack.objects.filter(is_active=True)
    return APIResponse.success(data=AddOnPackSerializer(packs, many=True).data)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def buy_addon(request):
    """خرید بسته‌ی افزودنی از طریق درگاه زیبال."""
    serializer = BuyAddOnSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))

    try:
        pack = AddOnPack.objects.get(id=serializer.validated_data['pack_id'], is_active=True)
    except AddOnPack.DoesNotExist:
        return APIResponse.error(message='بسته پیدا نشد', code=404)

    if not can_buy_addon(request.user):
        return APIResponse.error(message=NO_SUBSCRIPTION_MESSAGE, code=403)

    result = AddOnPaymentService().create_payment(
        user=request.user,
        pack=pack,
        callback_url=_payment_callback(request, '/home/payment-result-addon'),
    )
    if not result['success']:
        return APIResponse.error(message=result.get('error', 'خطا در ایجاد درخواست پرداخت'))

    return APIResponse.success(
        data={'payment_url': result['payment_url'], 'track_id': result.get('track_id')},
        message='درخواست پرداخت ایجاد شد'
    )


@api_view(['GET'])
@permission_classes([AllowAny])
def addon_payment_callback(request):
    """Zibal callback for add-on purchases."""
    track_id = request.GET.get('trackId')
    if not track_id:
        return render(request, 'payment_result.html', {
            'success': False, 'message': 'شناسه پرداخت یافت نشد', 'data': {}
        })

    result = AddOnVerificationService(track_id).verify_and_grant()
    return render(request, 'payment_result.html', {
        'success': result['success'],
        'message': result['message'],
        'data': result.get('data', {}),
    })





# ============ PAYMENT ============
# accounting/views.py - pay_for_plan همون کد قبلیت sync بمونه
@api_view(['POST'])
@permission_classes([IsAuthenticated])
def pay_for_plan(request):  # ✅ sync
    serializer = BuyPlanSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))

    plan_id = serializer.validated_data['plan_id']

    try:
        plan = Plan.objects.get(id=plan_id, is_active=True)  # ✅ بدون await
    except Plan.DoesNotExist:
        return APIResponse.error(message='پلن پیدا نشد', code=404)

    # جلوگیری از خرید پلن‌های رایگان/آزمایشی از طریق درگاه
    price = plan.discount_price if plan.discount_price is not None else plan.price
    if price <= 0:
        return APIResponse.error(message='پلن‌های رایگان را نمی‌توان از طریق درگاه خریداری کرد')

    user = request.user

    payment_service = ZibalPaymentService()
    result = payment_service.create_payment(  # ✅ بدون await
        user=user,
        plan=plan,
        callback_url=_payment_callback(request, '/home/payment-result'),
    )

    if not result['success']:
        return APIResponse.error(
            message=result.get('error', 'خطا در ایجاد درخواست پرداخت')
        )

    return APIResponse.success(
        data={
            'payment_url': result['payment_url'],
            'track_id': result.get('track_id')
        },
        message='درخواست پرداخت ایجاد شد'
    )

@api_view(['GET'])
@permission_classes([AllowAny])
def payment_callback(request):
    """
    Zibal callback — render HTML page
    Expected params: trackId, success, status, orderId
    """
    track_id = request.GET.get('trackId')

    if not track_id:
        return render(request, 'payment_result.html', {
            'success': False,
            'message': 'شناسه پرداخت یافت نشد',
            'data': {}
        })

    service = PaymentVerificationService(track_id)
    result = service.verify_and_activate()

    return render(request, 'payment_result.html', {
        'success': result['success'],
        'message': result['message'],
        'data': result.get('data', {})
    })


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def checkout_quote(request):
    """
    مبلغ نهایی یک خرید با جزئیات (قیمت، مالیات، کارمزد درگاه/مایکت) تا اپ
    پیش از رفتن به درگاه یا مایکت آن را به کاربر نشان دهد و تأیید بگیرد.
    Query: kind=plan|addon, id, channel=zibal|myket
    """
    kind = request.query_params.get('kind')
    channel = request.query_params.get('channel') or pricing.CHANNEL_ZIBAL
    if channel not in pricing.CHANNELS:
        return APIResponse.error(message='روش پرداخت نامعتبر است')
    try:
        item_id = int(request.query_params.get('id'))
    except (TypeError, ValueError):
        return APIResponse.error(message='شناسه نامعتبر است')

    if kind == 'plan':
        item = Plan.objects.filter(id=item_id, is_active=True).first()
        base = pricing.plan_base(item) if item else None
    elif kind == 'addon':
        item = AddOnPack.objects.filter(id=item_id, is_active=True).first()
        base = item.price if item else None
    else:
        return APIResponse.error(message='نوع خرید نامعتبر است')
    if item is None:
        return APIResponse.error(message='مورد پیدا نشد', code=404)

    data = pricing.quote(base, channel)
    data['title'] = item.name
    return APIResponse.success(data=data)


# ── Myket in-app billing (store build of the owner app) ──────────────────────
# See accounting/payment/myket.py for the full flow.

@api_view(['POST'])
@permission_classes([IsAuthenticated])
def myket_intent(request):
    """Reserve a purchase and return the Myket product id + developer payload."""
    from .payment import myket
    try:
        data = myket.create_intent(
            request.user,
            kind=request.data.get('kind'),
            item_id=request.data.get('id'),
        )
    except myket.StoreError as exc:
        return APIResponse.error(message=exc.message, code=exc.status)
    return APIResponse.success(data=data, message='سفارش ثبت شد')


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def myket_verify(request):
    """Verify a Myket purchase token server-to-server and grant what it paid for."""
    from .payment import myket
    try:
        data = myket.verify_and_grant(
            request.user,
            payload=request.data.get('payload') or '',
            sku=request.data.get('sku') or '',
            token=request.data.get('token') or '',
            order_id=request.data.get('order_id') or '',
        )
    except myket.StoreError as exc:
        return APIResponse.error(message=exc.message, code=exc.status)
    return APIResponse.success(data=data, message=data['message'])
