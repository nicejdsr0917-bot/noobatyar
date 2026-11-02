from django.shortcuts import render
from django.utils import timezone
from rest_framework.permissions import IsAuthenticated, AllowAny, IsAdminUser

from common.pagination import StandardPagination
from common.responses import APIResponse
from common.errors import extract_error as _extract_error
from .models import Plan, AddOnPack, CreditLedger
from .payment.zibal_payment import ZibalPaymentService
from .payment.zibal_payment_verification import PaymentVerificationService
from .payment.addon_payment import (
    NO_SUBSCRIPTION_MESSAGE, AddOnPaymentService, AddOnVerificationService, can_buy_addon,
)
from .serializers import (
    PlanSerializer, SubscriptionSerializer, BuyPlanSerializer,
    AddOnPackSerializer, BuyAddOnSerializer, WalletLedgerEntrySerializer, WalletConvertSerializer,
)
from . import entitlements, services, toman_wallet, usage
from rest_framework.decorators import api_view, permission_classes


# Kept under their old names: tests import and patch these from here.
_client_callback = services.client_callback
_from_web_panel = services.from_web_panel
_payment_callback = services.payment_callback_url


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
    
    subscription = services.current_subscription(request.user)

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
            'toman_wallet': toman_wallet.get_balance(user),
        },
    })


WALLET_METRICS = (
    CreditLedger.METRIC_TOMAN_WALLET, CreditLedger.METRIC_APPOINTMENT_WALLET, CreditLedger.METRIC_SMS_WALLET,
)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def wallet_history(request):
    """موجودی و تاریخچهٔ کیف‌پول تومانی و اعتبار اضافه (بسته‌های افزودنی) — صفحه‌بندی‌شده.

    Only the persistent wallets are listed (toman, booking and SMS credit);
    monthly counters are not something the owner owns. Wallets belong to the
    user, so the history spans all of their businesses.
    ``?metric=toman_wallet|appointment_wallet|sms_wallet`` narrows it.
    """
    user = request.user
    metric = request.query_params.get('metric')
    if metric and metric not in WALLET_METRICS:
        return APIResponse.error(
            f'metric باید یکی از این مقادیر باشد: {", ".join(WALLET_METRICS)}', code=400,
        )
    queryset = CreditLedger.objects.filter(
        user=user, metric__in=[metric] if metric else WALLET_METRICS,
    ).order_by('-created_at', '-id')

    paginator = StandardPagination()
    page = paginator.paginate_queryset(queryset, request)
    data = paginator.get_paginated_response(WalletLedgerEntrySerializer(page, many=True).data).data
    prices = toman_wallet.unit_prices()
    data['balances'] = {
        'toman_wallet': toman_wallet.get_balance(user),
        'sms_unit_price': prices[toman_wallet.KIND_SMS],
        'appointment_unit_price': prices[toman_wallet.KIND_APPOINTMENT],
        'appointment_wallet': usage.get_appt_wallet(user),
        'sms_wallet': usage.get_wallet(user),
        'appointment_quota': entitlements.get_quota(user, entitlements.QUOTA_MONTHLY_APPOINTMENTS),
        'sms_quota': entitlements.get_quota(user, entitlements.QUOTA_MONTHLY_SMS),
    }
    return APIResponse.success(data=data)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def wallet_convert(request):
    """تبدیل موجودی کیف‌پول تومانی به پیامک یا نوبت، با قیمت‌های TomanWalletSettings."""
    serializer = WalletConvertSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))
    try:
        data = toman_wallet.convert(request.user, **serializer.validated_data)
    except toman_wallet.WalletError as exc:
        return APIResponse.error(message=exc.message)
    unit = 'پیامک' if data['kind'] == toman_wallet.KIND_SMS else 'نوبت'
    return APIResponse.success(data=data, message=f"{data['count']} {unit} به اعتبارتان اضافه شد")


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
        pack = services.get_active_pack(serializer.validated_data['pack_id'])
    except services.ServiceError as exc:
        return APIResponse.error(message=exc.message, code=exc.code)

    if not can_buy_addon(request.user):
        return APIResponse.error(message=NO_SUBSCRIPTION_MESSAGE, code=403)

    result = AddOnPaymentService().create_payment(
        user=request.user,
        pack=pack,
        callback_url=_payment_callback(request, '/home/payment-result-addon'),
        use_wallet=serializer.validated_data['use_wallet'],
    )
    if not result['success']:
        return APIResponse.error(message=result.get('error', 'خطا در ایجاد درخواست پرداخت'))
    if result.get('paid_with_wallet'):
        return APIResponse.success(data={'paid_with_wallet': True, **result.get('data', {})}, message=result['message'])

    return APIResponse.success(
        data={'payment_url': result['payment_url'], 'track_id': result.get('track_id')},
        message='درخواست پرداخت ایجاد شد'
    )


@api_view(['GET'])
@permission_classes([AllowAny])
def addon_payment_callback(request):
    """Zibal callback for add-on purchases."""
    return render(request, 'payment_result.html', services.callback_context(
        request.GET.get('trackId'), lambda t: AddOnVerificationService(t).verify_and_grant(),
    ))


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
        plan = services.get_payable_plan(plan_id)
    except services.ServiceError as exc:
        return APIResponse.error(message=exc.message, code=exc.code)

    result = ZibalPaymentService().create_payment(
        user=request.user,
        plan=plan,
        callback_url=_payment_callback(request, '/home/payment-result'),
        use_wallet=serializer.validated_data['use_wallet'],
    )

    if not result['success']:
        return APIResponse.error(
            message=result.get('error', 'خطا در ایجاد درخواست پرداخت')
        )
    if result.get('paid_with_wallet'):
        return APIResponse.success(data={'paid_with_wallet': True, **result.get('data', {})}, message=result['message'])

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
    return render(request, 'payment_result.html', services.callback_context(
        request.GET.get('trackId'), lambda t: PaymentVerificationService(t).verify_and_activate(),
    ))


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def checkout_quote(request):
    """
    مبلغ نهایی یک خرید با جزئیات (قیمت، مالیات، کارمزد درگاه/مایکت) تا اپ
    پیش از رفتن به درگاه یا مایکت آن را به کاربر نشان دهد و تأیید بگیرد.
    Query: kind=plan|addon, id, channel=zibal|myket
    """
    try:
        data = services.checkout_quote(
            request.query_params.get('kind'),
            request.query_params.get('id'),
            request.query_params.get('channel'),
            user=request.user,
            use_wallet=request.query_params.get('use_wallet') in ('1', 'true', 'True'),
        )
    except services.ServiceError as exc:
        return APIResponse.error(message=exc.message, code=exc.code)
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
