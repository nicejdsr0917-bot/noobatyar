from rest_framework import serializers

from . import pricing, toman_wallet, usage
from .models import Plan, Subscription, AddOnPack, CreditLedger


def _store_price(request, base):
    """Myket build (``X-Store: myket``) is charged the store price, so show that.

    None for every other client, which keeps showing the plain price. Old app
    versions don't send the header and so see no change.
    """
    if request is None or request.headers.get('X-Store', '').lower() != pricing.CHANNEL_MYKET:
        return None
    return pricing.quote(base, pricing.CHANNEL_MYKET)['total']


class PlanSerializer(serializers.ModelSerializer):
    """نمایش پلن‌ها برای کاربر"""
    price_display = serializers.SerializerMethodField()
    duration_display = serializers.SerializerMethodField()

    class Meta:
        model = Plan
        fields = ['id', 'name', 'price', 'discount_price', 'price_display', 'duration_display', 'description', 'is_vip', 'features', 'myket_sku']

    def get_price_display(self, obj):
        """قیمت رو فارسی نمایش میده (با اولویت قیمت تخفیف)"""
        price = obj.discount_price if obj.discount_price is not None else obj.price
        if price == 0:
            return 'رایگان'
        price = _store_price(self.context.get('request'), price) or price
        return f"{price:,} تومان"

    def get_duration_display(self, obj):
        unit = 'روز' if obj.duration_unit == 'day' else 'ماه'
        return f"{obj.duration_value} {unit}"


class SubscriptionSerializer(serializers.ModelSerializer):
    """نمایش اشتراک فعال کاربر"""
    plan = PlanSerializer(read_only=True)
    is_valid = serializers.SerializerMethodField()
    days_left = serializers.SerializerMethodField()

    class Meta:
        model = Subscription
        fields = ['id', 'plan', 'status', 'started_at', 'ends_at', 'is_valid', 'days_left']

    def get_is_valid(self, obj):
        return obj.is_valid()

    def get_days_left(self, obj):
        return obj.days_left()


class AddOnPackSerializer(serializers.ModelSerializer):
    """نمایش بسته‌های افزودنی قابل خرید"""
    price_display = serializers.SerializerMethodField()

    class Meta:
        model = AddOnPack
        fields = ['id', 'name', 'price', 'price_display', 'kind', 'sms_amount', 'appointment_amount', 'feature_key', 'duration_days', 'myket_sku']

    def get_price_display(self, obj):
        price = _store_price(self.context.get('request'), obj.price) or obj.price
        return f"{price:,} تومان"


class BuyPlanSerializer(serializers.Serializer):
    """درخواست خرید پلن"""
    plan_id = serializers.IntegerField()
    use_wallet = serializers.BooleanField(required=False, default=False)
    # Pay entirely from the wallet or not at all (store builds: no gateway).
    wallet_only = serializers.BooleanField(required=False, default=False)


class BuyAddOnSerializer(serializers.Serializer):
    """درخواست خرید بسته‌ی افزودنی"""
    pack_id = serializers.IntegerField()
    use_wallet = serializers.BooleanField(required=False, default=False)
    # Pay entirely from the wallet or not at all (store builds: no gateway).
    wallet_only = serializers.BooleanField(required=False, default=False)


class WalletLedgerEntrySerializer(serializers.ModelSerializer):
    """One row of the owner's add-on credit history (wallet metrics only).

    ``reason`` stays the machine code so clients can localise it; ``reason_label``
    is the Persian fallback for codes a client does not know yet.
    """
    reason_label = serializers.SerializerMethodField()

    class Meta:
        model = CreditLedger
        fields = ['id', 'metric', 'delta', 'balance_after', 'reason', 'reason_label', 'created_at']

    def get_reason_label(self, obj):
        if obj.metric == CreditLedger.METRIC_TOMAN_WALLET or obj.reason in toman_wallet.REASON_LABELS:
            label = toman_wallet.REASON_LABELS.get(obj.reason)
            if label:
                return label
        return usage.wallet_reason_label(obj.reason, obj.delta)


class WalletConvertSerializer(serializers.Serializer):
    """تبدیل موجودی کیف‌پول تومانی به پیامک یا نوبت"""
    kind = serializers.ChoiceField(choices=['sms', 'appointment'])
    count = serializers.IntegerField(min_value=1, max_value=100000)
