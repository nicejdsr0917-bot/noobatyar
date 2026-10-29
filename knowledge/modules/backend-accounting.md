---
updated: 2026-10-29
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

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- مرجع کیف پول و سهمیهٔ ماهانه: مدل `CreditBalance(user, metric, period, balance)`؛ همهٔ خرج‌ها UPDATE شرطی در `usage.py` (امضای توابع ثابت). ردیف نبود → seed تنبل از Redis/ledger. دستور `migrate_credits_to_db` (پیش‌فرض فقط create؛ `--overwrite` خطرناک).
- دورهٔ سهمیه هنوز ماه **UTC** است (کار بعدی: تهران).
- تأیید زیبال/مایکت idempotent: replay = همان پاسخ موفق؛ 201 موفق؛ خطای شبکه pending (503)؛ فقط 202/203 failed و هرگز روی success؛ مبلغ تأییدشده باید `amount*10` ریال باشد. اعتبار add-on داخل همان تراکنش.
- `entitlements.get_entitlements` کش ۶۰ ثانیه (`ent:{version}:{user}`)، پاک‌شدن با `accounting/signals.py`.
- viewها/ادمین به `accounting/services.py` و `admin_services.py` تفویض می‌کنند. کلاینت زیبال: `backend/integrations/zibal.py` (`accounting/payment/zibal_client.py` فقط shim).
