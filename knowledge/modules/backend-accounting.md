---
updated: 2026-11-05
tags: [gf]
---
# backend: accounting (اشتراک و پرداخت)

**هدف:** پلن‌ها، اشتراک، add-on (بسته پیامک…)، کیف پول/دفتر اعتبار، پرداخت زیبال و مایکت، VAT، محدودیت‌های هر پلن.

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
- مرجع کیف پول و سهمیه ماهانه: مدل `CreditBalance(user, metric, period, balance)`؛ همه خرج‌ها UPDATE شرطی در `usage.py` (امضای توابع ثابت). ردیف نبود → seed تنبل از Redis/ledger. دستور `migrate_credits_to_db` (پیش‌فرض فقط create؛ `--overwrite` خطرناک).
- دوره سهمیه هنوز ماه **UTC** است (کار بعدی: تهران).
- تایید زیبال/مایکت idempotent: replay = همان پاسخ موفق؛ 201 موفق؛ خطای شبکه pending (503)؛ فقط 202/203 failed و هرگز روی success؛ مبلغ تاییدشده باید `amount*10` ریال باشد. اعتبار add-on داخل همان تراکنش.
- `entitlements.get_entitlements` کش ۶۰ ثانیه (`ent:{version}:{user}`)، پاک‌شدن با `accounting/signals.py`.
- viewها/ادمین به `accounting/services.py` و `admin_services.py` تفویض می‌کنند. کلاینت زیبال: `backend/integrations/zibal.py` (`accounting/payment/zibal_client.py` فقط shim).

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `sms_billing.send_metered_sms`: فرستنده واحد پیامک پولی (مصرف، ارسال، refund، SmsLog). خطای provider = refund. `services.grant_trial` atomic است.
- `integrations/myket.py` و `integrations.zibal` مستقیم؛ shim `zibal_client` حذف شد. بخش‌بندی «کیف پول کم» روی `CreditBalance` است.

## کیف‌پول (۲۰۲۶-۱۱-۰۲، برنچ mobile-client)
- `wallet/history/` (تاریخچه صفحه‌بندی‌شده `CreditLedger` + موجودی‌ها و قیمت واحد)، `wallet/convert/` (`kind` = sms|appointment، `count` ۱..۱۰۰۰۰۰).
- `accounting/toman_wallet.py`: متریک `toman_wallet` (فقط DB، کلید Redis ندارد). `credit()` برای اپ‌های دیگر (برگشت تخفیف)، `spend()` None اگر کم باشد.
- خرید با `use_wallet`/`wallet_only` در `plans/payment/` و `addons/buy/`. `wallet_part`: یا کامل، یا حداقل `MIN_SELLABLE_PRICE` برای زیبال؛ مایکت همه یا هیچ. بخش کیف‌پول با `hold()` روی `wallet_amount` (migration `0016`) نگه داشته می‌شود؛ `give_back` idempotent است. رد درگاه / verify FAILED / رها شدن ۱ ساعت (`release_stale_holds` در `run_subscription_lifecycle`) پول را برمی‌گرداند.
- `TomanWalletSettings` (singleton، ادمین) قیمت تبدیل. `manage.py toman_wallet_credit`.
- order_id پلن و add-on دنباله uuid دارد (برخورد در یک ثانیه). گزارش درآمد فقط `amount` نقدی.
- تست: `tests_toman_wallet.py`، `tests_wallet_history.py`.

## پاداش مشتری تازه (2026-11-04/05)
- `toman_wallet.reward_new_client(appointment)` از `appointment_change_status_by_owner` در تراکنش «انجام‌شده» صدا زده می‌شود. اگر خطا بدهد، نوبت باز هم تکمیل می‌شود.
- شرط‌ها: منبع نوبت سایت یا اپ باشد، شماره مشتری شماره خود اونر نباشد، و این اولین نوبت COMPLETED آن مشتری در این کسب‌وکار باشد. هر مشتری در هر کسب‌وکار یک بار حساب می‌شود (`NewClientReward` unique روی business و visitor).
- `TomanWalletSettings`: `new_client_reward` (پیش‌فرض ۰ یعنی خاموش)، `new_client_reward_business_cap` (۵۰۰٬۰۰۰، کل عمر کسب‌وکار)، `new_client_reward_monthly_cap` (۰ یعنی بدون سقف). migrationها `0017` تا `0019`.
- `risk_flags`:
  - `same_ip` قوی است: IP نوبت در `UserIpSighting` اونر در ۳۰ روز اخیر.
  - `new_account`، `quick_complete` (زیر ۳۰ دقیقه) و `burst` (۵ یا بیشتر در روز) ضعیف‌اند.
  - `is_blocked`: یک نشانه قوی یا دو ضعیف.
- `grant_blocked_reward` پشت اکشن ادمین «پرداخت پاداش» است. ردیف‌ها در منوی مالی ← «پاداش مشتری» هستند.
- ledger: `reason=new_client_reward`، `ref_type=business:<id>`، `ref_id=visitor_id`.

## پاداش مشتری تازه (2026-11-05)
- `toman_wallet.reward_new_client` فقط وقتی پرداخت می‌شود که `business.first_visit_discount > 0` باشد. معنایش: سهم نوبت‌یار از تخفیف اولین مراجعه.
- ضدتقلب (`same_ip`، `quick_complete`، …) پاداش را `blocked` می‌کند. ادمین آن را با `grant_blocked_reward` پرداخت می‌کند.
