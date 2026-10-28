---
updated: 2026-10-28
tags: [gf]
---
# backend: accounting (اشتراک و پرداخت)

**هدف:** پلن‌ها، اشتراک، add-on (بستهٔ پیامک…)، کیف پول/دفتر اعتبار، پرداخت زیبال و مایکت، VAT، محدودیت‌های هر پلن.

## فایل‌ها
- `backend/accounting/models.py` — `Plan`، `Subscription`، `AddOnPack`/`AddOnPurchase`، `Transaction`، `CreditLedger`، `StorePurchase`، `ContactVisibilityPolicy`، `SubscriptionReminderSettings`
- `backend/accounting/entitlements.py` — `get_entitlements`، `resolve_entitlements`، `get_active_subscription`
- `backend/accounting/payment/zibal_payment.py`
- urls: `plans/`، `my-subscription/`، `my-entitlements/`، `plans/payment/`، `payment-result`، `addons/`، `addons/buy/`، `checkout/quote/`، `store/myket/{intent,verify}/`
- commands: `run_subscription_lifecycle`، `myket_prices`، `rebuild_wallets_from_ledger`، `seed_plans`
- مستند: `backend/PLANS.md`

## وابستگی‌ها
[[modules/backend-business]] (gating، `is_expired`)، [[modules/owner-app]] (`FeatureGate`، `CheckoutConfirmation.kt`).

## نکته‌ها
- `plans/buy/` (فعال‌سازی بدون پرداخت) حذف شد — برنگردان.
- روی مایکت فاکتور فقط «قیمت + VAT» نشان داده می‌شود (سهم مایکت داخل قیمت).
