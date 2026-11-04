---
updated: 2026-11-03 17:55
tags: [gf, current]
---
# وضعیت فعلی

## هدف
اپ مشتری اندروید (`mobile_client/`، برنچ `mobile-client`) را حرفه‌ای و روان کنیم (UI سطح بین‌المللی، بدون گرادیان و انیمیشن ارزان) و قابلیت‌ها را در هر سه کلاینت هم‌سطح نگه داریم. کار تازه: **کیف پول اونر** (انجام شد). **فقط اندروید**؛ iOS بیلد یا بررسی نشود.

قاعدهٔ همیشگی: فقط تغییر افزایشی، چون کاربر واقعی داریم. با کاربر فارسی حرف بزن.

## وضعیت
- **صفحهٔ کاوش بازطراحی شد و روی امولاتور دیده شد:** ۷ دسته در یک ردیف، شیت سه‌حالته (پایین‌ترین فقط تا خط عنوان)، عنوان با دسته («مراکز دندان‌پزشکی در تهران»)، حالت خالی/خطای ساده. دارک مود دیده نشده.
- **کیف پول اونر روی `mobile-client` آماده است** (commitهای `6d4d5d3`، `6b62913`، `017e0d0`): اعتبار نوبت/پیامک + کیف‌پول تومانی (برگشت تخفیف‌های نوبت‌یار، نقدنشدنی؛ خرج در خرید پلن/بسته کامل یا بخشی، تبدیل به پیامک/نوبت). ۱۰۸۷ تست بک‌اند و ۹۹ تست اپ OK، روی امولاتور دیده شد. جزئیات: [[sessions/2026-11-02-2118]].
- **شارژ کیف‌پول هنوز به جایی وصل نیست:** سشن تخفیف بیعانه باید `toman_wallet.credit(...)` را صدا بزند.
- DB محلی تازه seed شد: ۱۰۰ کسب‌وکار نمایشی، «سالن زیبایی ستاره» id 52 با لوگو و ۸ نمونه‌کار. DB قبلی: `backend/db.sqlite3.bak-20261102`.
- **دیپلوی سرور از سشن قبل نیمه‌کاره است** (checkout سرور روی `45a9b96`، migrate انجام نشده).

- **اپ کلاینت دو flavor دارد:** `site` و `store` (یک APK برای مایکت و بازار)؛ workflow ریلیز کلاینت هر دو را می‌سازد، هنوز با تگ واقعی اجرا نشده. اونر دست نخورده. جزئیات: [[sessions/2026-11-03-1755]].

## قدم‌های بعدی
1. وصل کردن شارژ کیف‌پول در سشن تخفیف بیعانه: فقط وقتی نوبت تخفیف‌دار واقعاً پرداخت/تأیید شد `toman_wallet.credit(user, amount, reason="discount_reimbursement", ref_type=..., ref_id=...)`.
2. تنظیم قیمت واحد تبدیل در ادمین («تنظیمات کیف‌پول تومانی»؛ پیش‌فرض ۲۰۰/۵۰۰ تومان).
3. دیدن دارک مود کاوش جدید.
4. تمام کردن دیپلوی: `docs/DEPLOY_SCALE_HARDENING.md`. **پیش از آن بپرس CDN جلوی nginx هست یا نه.** بعدش `seed_insurances` روی سرور. migration تازه: `accounting/0016_toman_wallet`.
5. ریلیز اپ اونر بعد از دیپلوی.

## مسائل باز
- 🔒 رمز root سرور در تاریخچهٔ git است و باید عوض شود.
- ⚠️ migrationهای در انتظار سرور: `accounting/0016`، `business/0035`، `business/0036`، `visitor/0014`، `appointment/0017`، `banners/0002`. `seed_demo_businesses` فقط محلی است.
- `PushMessagingService` اپ کلاینت ثبت نشده است؛ منتظر فایل Firebase.
- **قاعدهٔ متن اپ:** کوتاه و بدون ئ ء ٔ أ. ثابت `BOOKING_BLOCKED_NOTICE` را عوض نکن.
- این فایل‌ها commit نشوند: `API_DOCUMENTATION.md`، `implementation_plan.md`، `backend/err.log`، `backend/out.log`، lockهای `mobile_client/.gradle/`.
- **محیط Windows (این سشن):**
  - امولاتور شبیه LDPlayer با صفحهٔ 900x1600.
  - adb در `C:/Users/j_sattar/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
  - بعد از هر بوت `adb reverse tcp:8000 tcp:8000`.
  - بک‌اند: preview `backend` (پورت 8000)، venv در `backend/.venv` (پایتون 3.14). دستورهای Django با `DEBUG=True PYTHONUTF8=1`.
  - بیلد: `cd mobile_client && ./gradlew :composeApp:assembleLocalSiteDebug -q`.
- تست بک‌اند در worktree کیف‌پول با venv پایتون 3.12 (پایتون 3.14 تست‌های ادمین Django 4.2 را می‌شکند). JAVA_HOME سالم: `C:/Users/j_sattar/.jdks/jbr-17.0.14`.
- **محیط macOS:** امولاتور `Pixel_6a`، preview `backend_mobile_client_mac`. جزئیات در سشن [[sessions/2026-11-02-0005]].
- کاربر هم‌زمان با امولاتور کار می‌کند.

## فایل‌های کلیدی
- `mobile_client/.../feature/explore/ExploreSheet.kt`: شیت سه‌حالته
- `mobile_client/.../feature/explore/ExploreScreen.kt`: کاوش و `resultsTitle`
- `mobile_client/.../feature/explore/ExploreHeader.kt`: `CategoryBar`
- `mobile_client/.../feature/discovery/DiscoveryUi.kt`: `CategoryGroup` و `StateMessage`
- `backend/accounting/toman_wallet.py`: کیف‌پول تومانی (`credit`، `spend`، `hold`، `give_back`، `convert`، `release_stale_holds`)
- `mobile_owner/.../feature/wallet/` و `core/billing/CheckoutConfirmation.kt`: صفحهٔ کیف‌پول و پرداخت با کیف‌پول

## سشن‌های اخیر
- [[sessions/2026-11-03-1755]]: اپ کلاینت: دو نسخهٔ سایت و استور (مایکت و بازار)
- [[sessions/2026-11-02-2118]]: کیف‌پول اونر — اعتبار نوبت/پیامک و کیف‌پول تومانی، تست سخت‌گیرانه
- [[sessions/2026-11-02-1455]]: کاوش با دسته‌ها در یک ردیف، شیت سه‌حالته، حالت خالی ساده؛ ایدهٔ کیف پول اونر
- [[sessions/2026-11-02-0005]]: شبکه‌های اجتماعی در سه اپ، کد تایید متحرک، رفع بن‌بست بیعانه، دیپلوی نیمه‌کاره
- [[sessions/2026-11-01-1741]]: صفحهٔ مرکز با هدر جمع‌شونده، تب پروفایل، کش نقشه

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
