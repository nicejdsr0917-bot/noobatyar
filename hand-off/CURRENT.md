---
updated: 2026-11-02 14:55
tags: [gf, current]
---
# وضعیت فعلی

## هدف
اپ مشتری اندروید (`mobile_client/`، برنچ `mobile-client`) را حرفه‌ای و روان کنیم (UI سطح بین‌المللی، بدون گرادیان و انیمیشن ارزان) و قابلیت‌ها را در هر سه کلاینت هم‌سطح نگه داریم. کار تازه: **کیف پول اونر**. **فقط اندروید**؛ iOS بیلد یا بررسی نشود.

قاعدهٔ همیشگی: فقط تغییر افزایشی، چون کاربر واقعی داریم. با کاربر فارسی حرف بزن.

## وضعیت
- **صفحهٔ کاوش بازطراحی شد و روی امولاتور دیده شد:** ۷ دسته در یک ردیف، شیت سه‌حالته (پایین‌ترین فقط تا خط عنوان)، عنوان با دسته («مراکز دندان‌پزشکی در تهران»)، حالت خالی/خطای ساده. دارک مود دیده نشده.
- **کیف پول اونر پیاده نشده.** ایدهٔ کاربر: نوبت‌یار برای اولین نوبت هر مشتری ۲۰٪ کمتر بگیرد و همان به کیف پول اونر برود تا با آن پلن یا بسته بخرد. منتظر جواب این سؤال: ۲۰٪ از بیعانهٔ آنلاین (پول واقعی) یا پاداش بی‌پول؟ (پیشنهاد من: پاداش غیرقابل برداشت.)
- DB محلی تازه seed شد: ۱۰۰ کسب‌وکار نمایشی، «سالن زیبایی ستاره» id 52 با لوگو و ۸ نمونه‌کار. DB قبلی: `backend/db.sqlite3.bak-20261102`.
- **دیپلوی سرور از سشن قبل نیمه‌کاره است** (checkout سرور روی `45a9b96`، migrate انجام نشده).

## قدم‌های بعدی
1. گرفتن جواب مبنای ۲۰٪، بعد ساخت کیف پول:
   - بک‌اند: موجودی پولی و دفتر تراکنش به سبک `CreditBalance`/`CreditLedger`، واریز برای اولین نوبت مارکت‌پلیس، کسر موقع خرید پلن یا بسته با باقی‌مانده از زیبال، قواعد ضدتقلب.
   - اپ اونر: کارت موجودی و تاریخچه.
2. دیدن دارک مود کاوش جدید.
3. تمام کردن دیپلوی: `docs/DEPLOY_SCALE_HARDENING.md`. **پیش از آن بپرس CDN جلوی nginx هست یا نه.** بعدش `seed_insurances` روی سرور.
4. تب‌های صفحهٔ مرکز (پیشنهاد Pinterest): فقط اگر کاربر بخواهد.
5. ریلیز اپ اونر بعد از دیپلوی.

## مسائل باز
- 🔒 رمز root سرور در تاریخچهٔ git است و باید عوض شود.
- ⚠️ migrationهای در انتظار سرور: `business/0035`، `business/0036`، `visitor/0014`، `appointment/0017`، `banners/0002`. `seed_demo_businesses` فقط محلی است.
- `PushMessagingService` اپ کلاینت ثبت نشده است؛ منتظر فایل Firebase.
- **قاعدهٔ متن اپ:** کوتاه و بدون ئ ء ٔ أ. ثابت `BOOKING_BLOCKED_NOTICE` را عوض نکن.
- این فایل‌ها commit نشوند: `API_DOCUMENTATION.md`، `implementation_plan.md`، `backend/err.log`، `backend/out.log`، lockهای `mobile_client/.gradle/`.
- **محیط Windows (این سشن):**
  - امولاتور شبیه LDPlayer با صفحهٔ 900x1600.
  - adb در `C:/Users/j_sattar/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
  - بعد از هر بوت `adb reverse tcp:8000 tcp:8000`.
  - بک‌اند: preview `backend` (پورت 8000)، venv در `backend/.venv` (پایتون 3.14). دستورهای Django با `DEBUG=True PYTHONUTF8=1`.
  - بیلد: `cd mobile_client && ./gradlew :composeApp:assembleLocalDebug -q`.
- **محیط macOS:** امولاتور `Pixel_6a`، preview `backend_mobile_client_mac`. جزئیات در سشن [[sessions/2026-11-02-0005]].
- کاربر هم‌زمان با امولاتور کار می‌کند.

## فایل‌های کلیدی
- `mobile_client/.../feature/explore/ExploreSheet.kt`: شیت سه‌حالته
- `mobile_client/.../feature/explore/ExploreScreen.kt`: کاوش و `resultsTitle`
- `mobile_client/.../feature/explore/ExploreHeader.kt`: `CategoryBar`
- `mobile_client/.../feature/discovery/DiscoveryUi.kt`: `CategoryGroup` و `StateMessage`
- `backend/accounting/models.py`: الگوی کیف پول (`CreditBalance`، `CreditLedger`)

## سشن‌های اخیر
- [[sessions/2026-11-02-1455]]: کاوش با دسته‌ها در یک ردیف، شیت سه‌حالته، حالت خالی ساده؛ ایدهٔ کیف پول اونر
- [[sessions/2026-11-02-0005]]: شبکه‌های اجتماعی در سه اپ، کد تایید متحرک، رفع بن‌بست بیعانه، دیپلوی نیمه‌کاره
- [[sessions/2026-11-01-1741]]: صفحهٔ مرکز با هدر جمع‌شونده، تب پروفایل، کش نقشه
- [[sessions/2026-11-01-0014]]: ادمین با ورود از فایل و فرم هر صنف؛ «بیمه من» در اپ
- [[sessions/2026-10-31-2234]]: فیلتر بیمه، دادهٔ نمایشی، زوم ناحیه، مسیریابی

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
