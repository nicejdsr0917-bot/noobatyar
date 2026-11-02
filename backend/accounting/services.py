"""Request-independent logic behind accounting/views.py.

The views parse, call into here (or into accounting/payment/*), and render.
"""
from typing import Any, Dict, Optional

from django.conf import settings

from . import pricing
from .models import AddOnPack, Plan, Subscription


class ServiceError(Exception):
    """A user-facing refusal: the view turns it into ``APIResponse.error``."""

    def __init__(self, message: str, code: int = 400):
        super().__init__(message)
        self.message = message
        self.code = code


def client_callback(path: str) -> str:
    """Absolute URL on the customer web app for a payment gateway to return to.

    These were hardcoded to https://noobatyar.ir/... — the bare domain, which
    resolves to a *different server* than the one running this app. Zibal sent
    every payer there after paying, so the page that calls the verify endpoint
    never ran: transactions stayed `pending` with an empty zibal_response and
    the plan or add-on was never applied.

    CLIENT_WEB_URL already existed for exactly this (the deposit flow uses it)
    and already defaults to the right host.
    """
    base = (getattr(settings, 'CLIENT_WEB_URL', '') or '').rstrip('/')
    return f'{base}{path}'


def from_web_panel(request) -> bool:
    """True when the payment was started by the owner web panel (a browser),
    not by the native apps.

    The gateway sends the payer back to a web page either way, but only the
    native apps own the ``noobatyar://`` link that page uses to return. In a
    browser that link is an invalid address (Safari shows an error), so the
    panel gets its own return page that goes back to the panel instead. The
    browser tells us who is asking in the Origin header on its API calls.
    """
    panel = (getattr(settings, 'PANEL_WEB_URL', '') or '').rstrip('/')
    origin = (request.headers.get('Origin') or '').rstrip('/')
    if not origin:
        return False
    return origin == panel or origin.startswith(('http://localhost', 'http://127.0.0.1'))


def payment_callback_url(request, path: str) -> str:
    """The callback URL for this request: the panel's page for browsers,
    the app-link page for everyone else."""
    return client_callback(f'{path}-web' if from_web_panel(request) else path)


def current_subscription(user) -> Optional[Subscription]:
    """The active subscription, else the most recent one (even expired), else None."""
    subscription = Subscription.objects.filter(
        user=user, status='active'
    ).select_related('plan').first()
    if not subscription:
        subscription = Subscription.objects.filter(
            user=user
        ).select_related('plan').order_by('-started_at').first()
    return subscription


def get_payable_plan(plan_id) -> Plan:
    """An active, non-free plan, or ServiceError."""
    try:
        plan = Plan.objects.get(id=plan_id, is_active=True)
    except Plan.DoesNotExist:
        raise ServiceError('پلن پیدا نشد', 404)
    # جلوگیری از خرید پلن‌های رایگان/آزمایشی از طریق درگاه
    price = plan.discount_price if plan.discount_price is not None else plan.price
    if price <= 0:
        raise ServiceError('پلن‌های رایگان را نمی‌توان از طریق درگاه خریداری کرد')
    return plan


def get_active_pack(pack_id) -> AddOnPack:
    try:
        return AddOnPack.objects.get(id=pack_id, is_active=True)
    except AddOnPack.DoesNotExist:
        raise ServiceError('بسته پیدا نشد', 404)


def checkout_quote(kind, raw_id, channel, user=None, use_wallet=False) -> Dict[str, Any]:
    """The itemised total of a purchase, or ServiceError.

    Always carries ``wallet_balance`` (the owner's toman wallet) so the app can
    offer it; with ``use_wallet`` the wallet part is taken off before VAT and
    fee (``wallet``), exactly as the purchase itself will charge it.
    """
    channel = channel or pricing.CHANNEL_ZIBAL
    if channel not in pricing.CHANNELS:
        raise ServiceError('روش پرداخت نامعتبر است')
    try:
        item_id = int(raw_id)
    except (TypeError, ValueError):
        raise ServiceError('شناسه نامعتبر است')

    if kind == 'plan':
        item = Plan.objects.filter(id=item_id, is_active=True).first()
        base = pricing.plan_base(item) if item else None
    elif kind == 'addon':
        item = AddOnPack.objects.filter(id=item_id, is_active=True).first()
        base = item.price if item else None
    else:
        raise ServiceError('نوع خرید نامعتبر است')
    if item is None:
        raise ServiceError('مورد پیدا نشد', 404)

    from . import toman_wallet

    balance = toman_wallet.get_balance(user) if user is not None else 0
    data = toman_wallet.quote_with_wallet(base, channel, balance if use_wallet else 0)
    data['wallet_balance'] = balance
    # What the wallet would pay if asked, so the app can label its switch.
    data['wallet_available'] = toman_wallet.with_vat(toman_wallet.wallet_part(base, balance, channel))
    data['title'] = item.name
    return data


def callback_context(track_id, verify) -> Dict[str, Any]:
    """Template context for payment_result.html. ``verify`` is called with the
    track id and returns the verification service's result dict."""
    if not track_id:
        return {'success': False, 'message': 'شناسه پرداخت یافت نشد', 'data': {}}
    result = verify(track_id)
    return {
        'success': result['success'],
        'message': result['message'],
        'data': result.get('data', {}),
    }


def grant_trial(user) -> Optional[Subscription]:
    """Give a freshly registered owner the free trial plan, if one is configured.

    Call inside the same transaction that creates the user, so a failure here
    never leaves an owner without their trial.
    """
    trial_plan = Plan.objects.filter(price=0, is_active=True).first()
    if not trial_plan:
        return None
    return Subscription.objects.create(
        user=user,
        plan=trial_plan,
        ends_at=trial_plan.get_end_date(),
    )
