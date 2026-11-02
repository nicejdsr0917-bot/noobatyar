# accounting/payment/zibal_payment.py
import logging
import uuid
from django.conf import settings
from django.db import transaction as db_transaction
from django.utils import timezone
from accounting.models import Transaction
from accounting import pricing, toman_wallet
from integrations import zibal

logger = logging.getLogger(__name__)

class ZibalPaymentService:
    def create_payment(self, user, plan, callback_url, use_wallet=False, wallet_only=False):  # ✅ sync
        """Create payment request and return payment URL.

        With ``use_wallet`` the toman wallet pays its part first and the gateway
        only the rest (see ``AddOnPaymentService.create_payment``); a wallet that
        covers the whole price activates the plan with no gateway.
        """
        order_id = self._generate_order_id(user.id, plan.id)
        base = pricing.plan_base(plan)
        used = toman_wallet.wallet_part(base, toman_wallet.get_balance(user)) if use_wallet else 0
        if wallet_only and used < base:
            # Store builds never open the gateway; the balance changed since the quote.
            return {'success': False, 'error': toman_wallet.WALLET_CHANGED_MESSAGE}
        final_price = pricing.quote(base - used, pricing.CHANNEL_ZIBAL)["total"]

        held = None
        if used:
            try:
                held = toman_wallet.hold(
                    Transaction, "Transaction", used, toman_wallet.REASON_PLAN_PURCHASE,
                    user=user, plan=plan, amount=final_price, order_id=order_id,
                )
            except toman_wallet.WalletError as exc:
                return {'success': False, 'error': exc.message}
            if final_price == 0:
                from .zibal_payment_verification import activate_plan_transaction
                with db_transaction.atomic():
                    locked = Transaction.objects.select_for_update().select_related('user', 'plan').get(pk=held.pk)
                    data = activate_plan_transaction(locked, {'paid_with': 'wallet'})
                return {
                    'success': True, 'paid_with_wallet': True,
                    'message': f'پلن {data["plan_name"]} با موفقیت فعال شد', 'data': data,
                }

        result = zibal.request_payment(
            settings.ZIBAL_MERCHANT_ID,
            final_price * 10,  # تبدیل تومان به ریال برای زیبال
            callback_url, order_id,
        )
        if result.transport_error or not result.success:
            if held:
                toman_wallet.cancel_hold(held, "Transaction")
            if result.transport_error:
                return {'success': False, 'error': 'خطا در اتصال به درگاه پرداخت'}
            logger.error(f"Zibal error: {result.message}")
            return {'success': False, 'error': result.message}

        track_id = result.track_id
        if held:
            Transaction.objects.filter(pk=held.pk).update(track_id=str(track_id))
        else:
            self._save_transaction(str(track_id), order_id, user, plan, final_price)  # قیمت نهایی ذخیره شود

        return {
            'success': True,
            'payment_url': result.payment_url,
            'track_id': track_id
        }

    def _generate_order_id(self, user_id, plan_id):
        timestamp = int(timezone.now().timestamp())
        # Random tail: two payments in the same second must not share an order id.
        return f"ORD-{user_id}-{plan_id}-{timestamp}-{uuid.uuid4().hex[:6]}"

    def _save_transaction(self, track_id: str, order_id: str, user, plan, amount):  # ✅ مقدار مبلغ اضافه شد
        """Save transaction to database"""
        Transaction.objects.create(
            user=user,
            plan=plan,
            amount=amount,
            track_id=track_id,
            order_id=order_id,
            status='pending'
        )
