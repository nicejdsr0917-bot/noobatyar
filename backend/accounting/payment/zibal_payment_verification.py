# accounting/payment/zibal_payment_verification.py
import logging
from typing import Dict, Any, Optional
from django.db import transaction as db_transaction
from django.utils import timezone

from accounting.models import Transaction, Subscription
from integrations import zibal

logger = logging.getLogger(__name__)

class PaymentVerificationService:
    """Verifies a Zibal payment and activates the plan — safe to run any number
    of times, concurrently, for the same track id.

    Zibal is asked OUTSIDE any DB lock; the Transaction row is then locked and
    re-checked, so the plan is granted exactly once. A network error leaves the
    row pending (the callback can be retried); only a definitive Zibal "not
    paid" marks it failed, and never over a success.
    """

    def __init__(self, track_id: str):
        self.track_id = track_id
        self.transaction: Optional[Transaction] = None

    def verify_and_activate(self) -> Dict[str, Any]:
        try:
            try:
                self.transaction = Transaction.objects.select_related('user', 'plan').get(
                    track_id=self.track_id
                )
            except Transaction.DoesNotExist:
                return self._error_response("تراکنش یافت نشد", 404)

            if self.transaction.status == 'success':
                return self._success_response(self.transaction)
            if self.transaction.status == 'cancelled':
                return self._error_response("این تراکنش لغو شده است", 400)

            # A 'failed' row is re-verified too: older code could mark a paid
            # transaction failed after a timeout.
            result = zibal.verify(self.track_id)

            if result.paid and not zibal.amount_matches(result.data, self.transaction.amount):
                # Paid, but not the sum we asked for: grant nothing and leave the
                # row pending for a human; never mark it failed.
                logger.error(
                    "Zibal amount mismatch for %s: verified=%r expected=%s toman",
                    self.track_id, (result.data or {}).get('amount'), self.transaction.amount,
                )
                return self._error_response(
                    "تأیید پرداخت با تأخیر مواجه شد؛ چند لحظه بعد دوباره تلاش کنید", 503
                )

            if result.outcome == zibal.Outcome.TRANSPORT_ERROR:
                return self._error_response(
                    "تأیید پرداخت با تأخیر مواجه شد؛ چند لحظه بعد دوباره تلاش کنید", 503, result.data
                )

            if result.outcome == zibal.Outcome.FAILED:
                Transaction.objects.filter(pk=self.transaction.pk, status='pending').update(
                    status='failed', zibal_response=result.data, updated_at=timezone.now()
                )
                return self._error_response(result.message, 400, result.data)

            with db_transaction.atomic():
                locked = Transaction.objects.select_for_update().select_related(
                    'user', 'plan'
                ).get(pk=self.transaction.pk)
                if locked.status == 'success':
                    return self._success_response(locked)
                if locked.status == 'cancelled':
                    return self._error_response("این تراکنش لغو شده است", 400)
                data = activate_plan_transaction(locked, result.data)

            return {
                'success': True,
                'message': f'پلن {data["plan_name"]} با موفقیت فعال شد',
                'data': data,
                'status_code': 201
            }

        except Exception:
            logger.exception(f"Unexpected error in payment verification: {self.track_id}")
            # Never mark failed here: the payment may well have succeeded.
            return self._error_response("خطای سیستمی در پردازش پرداخت", 500)

    @staticmethod
    def _success_response(txn: Transaction) -> Dict[str, Any]:
        """The same answer a first-time success gives, rebuilt from stored state."""
        sub = Subscription.objects.filter(user=txn.user, plan=txn.plan).order_by('-id').first()
        stored = txn.zibal_response if isinstance(txn.zibal_response, dict) else {}
        return {
            'success': True,
            'message': f'پلن {txn.plan.name} با موفقیت فعال شد',
            'data': {
                'subscription_id': sub.id if sub else None,
                'plan_name': txn.plan.name,
                'ends_at': sub.ends_at.isoformat() if sub else None,
                'ref_number': stored.get('refNumber'),
                'card_number': stored.get('cardNumber'),
                'amount': str(txn.amount),
            },
            'status_code': 201
        }

    @staticmethod
    def _error_response(message: str, status_code: int, data: Optional[Dict] = None) -> Dict:
        """Standardized error response"""
        return {
            'success': False,
            'message': message,
            'data': data or {},
            'status_code': status_code
        }


def activate_plan_transaction(transaction, verify_data: Dict) -> Dict[str, Any]:
    """Mark ``transaction`` successful and put its user on its plan.

    Shared by the Zibal callback and store (Myket) in-app purchases, so a plan
    bought either way extends the current subscription identically.
    """
    with db_transaction.atomic():
        # Mark transaction successful
        transaction.status = 'success'
        transaction.zibal_response = verify_data
        transaction.save(update_fields=['status', 'zibal_response', 'updated_at'])

        # بررسی اشتراک فعلی برای تمدید زمان
        active_sub = Subscription.objects.filter(
            user=transaction.user,
            status='active'
        ).first()

        if active_sub and active_sub.is_valid():
            start_date = active_sub.ends_at
            active_sub.status = 'expired'
            active_sub.save(update_fields=['status'])
        else:
            Subscription.objects.filter(
                user=transaction.user,
                status='active'
            ).update(status='expired')
            start_date = timezone.now()

        # Create new subscription
        subscription = Subscription.objects.create(
            user=transaction.user,
            plan=transaction.plan,
            ends_at=transaction.plan.get_end_date(start_date=start_date)
        )

        # Upgrade user if VIP plan
        if transaction.plan.is_vip and transaction.user.role != 'BUSINESS_OWNER':
            transaction.user.role = 'BUSINESS_OWNER'
            transaction.user.save(update_fields=['role'])

        # Unlock businesses locked under a smaller/expired plan.
        from business.services import sync_locks
        sync_locks(transaction.user)

        return {
            'subscription_id': subscription.id,
            'plan_name': transaction.plan.name,
            'ends_at': subscription.ends_at.isoformat(),
            'ref_number': verify_data.get('refNumber'),
            'card_number': verify_data.get('cardNumber'),
            'amount': str(transaction.amount)
        }
