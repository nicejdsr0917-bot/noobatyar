# accounting/payment/zibal_payment.py
import logging
from django.conf import settings
from django.utils import timezone
from accounting.models import Transaction
from accounting import pricing
from accounting.payment import zibal_client

logger = logging.getLogger(__name__)

class ZibalPaymentService:
    def create_payment(self, user, plan, callback_url):  # ✅ sync
        """Create payment request and return payment URL"""
        order_id = self._generate_order_id(user.id, plan.id)
        
        # قیمت پلن + مالیات + کارمزد زیبال؛ همان مبلغی که دیالوگ تأیید به کاربر نشان داده
        final_price = pricing.quote(pricing.plan_base(plan), pricing.CHANNEL_ZIBAL)["total"]

        result = zibal_client.request_payment(
            settings.ZIBAL_MERCHANT_ID,
            final_price * 10,  # تبدیل تومان به ریال برای زیبال
            callback_url, order_id,
        )
        if result.transport_error:
            return {'success': False, 'error': 'خطا در اتصال به درگاه پرداخت'}
        if not result.success:
            logger.error(f"Zibal error: {result.message}")
            return {'success': False, 'error': result.message}

        track_id = result.track_id
        self._save_transaction(str(track_id), order_id, user, plan, final_price)  # قیمت نهایی ذخیره شود

        return {
            'success': True,
            'payment_url': result.payment_url,
            'track_id': track_id
        }

    def _generate_order_id(self, user_id, plan_id):
        timestamp = int(timezone.now().timestamp())
        return f"ORD-{user_id}-{plan_id}-{timestamp}"

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
