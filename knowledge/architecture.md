---
updated: 2026-10-29
tags: [gf]
---
# معماری

```
owner app (Android/iOS/wasm) ──JWT──┐
client web (Next.js SSR+CSR) ───────┼──> Django/DRF (ASGI) ──> Postgres (prod) / SQLite (local)
client mobile (KMP, branch) ────────┘        │   ├─ Redis (cache/throttle)
                                             │   ├─ Zibal (درگاه)، Myket (پرداخت درون‌برنامه)
                                             │   ├─ پیامک (خط تبلیغاتی)، FCM (پوش)، ربات بله
                                             ├─ crontab → management commands (نه celery)
                                             └─ (mobile-client) worker/worker_bulk ← core/outbox (صف در Postgres)
```

## لایه‌ها
- **Backend**: اپ‌های Django = حوزه‌ها ([[modules/backend-business]]، [[modules/backend-appointment]]، [[modules/backend-visitor]]، [[modules/backend-accounting]]، [[modules/backend-api-auth]]، [[modules/backend-core]]). همهٔ پاسخ‌ها در قالب `APIResponse` (`status/code/message/data`). viewهای پرترافیک async هستند (`sync_to_async`). APIهای مشتری زیر `api/client/...` و جدا از APIهای owner.
- **Owner app** ([[modules/owner-app]]): MVI (`BaseViewModel`، State/Intent)، `*ApiService` (Ktor) → `*RepositoryImpl` → ViewModel؛ Room برای کش محلی (wasm: InMemory). expect/actual برای پلتفرم‌ها.
- **Client web** ([[modules/front-client]]): صفحهٔ کسب‌وکار `b/[slug]` SSR + کامپوننت client؛ `lib/api.ts` تنها لایهٔ API؛ i18n با cookie `lang`.

## جریان‌های اصلی
- **رزرو مشتری**: `b/[slug]` → `book` → `available-slots` → ساخت نوبت → بیعانهٔ آنلاین (`zibal_deposit.py`، مبالغ تومان) یا کارت به کارت با رسید → `deposit-callback` → پوش/پیامک به owner.
- **اشتراک/add-on**: owner → `checkout/quote` (VAT + کارمزد) → زیبال یا مایکت → `payment-result` → `Subscription`/`AddOnPurchase` + `Transaction`/`CreditLedger`.
- **محدودیت‌ها**: entitlements حساب → `FeatureGate` در اپ owner. بدون اشتراک فعال، `ContactVisibilityPolicy` تماس/آدرس را در API عمومی پنهان می‌کند (`apply_contact_rules`).
- **پیامک تبلیغاتی**: preview → moderation (`BannedKeyword`) → `send_promo_campaigns`.

## تصمیم‌های مهم
- زمان: `TIME_ZONE='Asia/Tehran'`، `USE_TZ=True` — «امروز» را همیشه با `timezone.localdate()/localtime()` حساب کن (باگ آمار روی mobile-client رفع شد).
- روزهای هفته در API به قرارداد پایتون (Mon=0..Sun=6)؛ وب با `closedWeekdaysJs()` تبدیل می‌کند.
- فیلدهای پنهان‌شده null فرستاده می‌شوند (نه blur سمت کلاینت) + `is_expired`/`has_location` برای نمایش درست.
- رنگ صفحهٔ کسب‌وکار از لوگو سمت سرور (`theme_color`) و در وب با کلاس `.themed` اعمال می‌شود.
- (برنچ mobile-client) لایه‌بندی بک‌اند: view → `services/` (نوشتن، صاحب تراکنش) / `selectors` (خواندن) → `integrations/` (زیبال، ملی‌پیامک، FCM، بله). پول و سهمیه در `CreditBalance` (نه Redis). اعلان‌ها از Outbox، نه thread. ترتیب قفل: business → appointment → اعتبار.
- دیتابیس production Postgres می‌ماند (تصمیم ۲۰۲۶-۱۰-۲۹).
