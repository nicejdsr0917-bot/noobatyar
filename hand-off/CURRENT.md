---
updated: 2026-11-02 00:05
tags: [gf, current]
---
# وضعیت فعلی

## هدف
اپ مشتری اندروید (`mobile_client/`، برنچ `mobile-client`) را زیبا و روان کنیم (برند بنفش، حرکت روی کامپوننت‌ها مثل اپ اونر) و قابلیت‌های صفحهٔ کسب‌وکار را در هر سه کلاینت هم‌سطح کنیم. **فقط اندروید**؛ iOS بیلد یا بررسی نشود.

قاعدهٔ همیشگی: فقط تغییر افزایشی، چون کاربر واقعی داریم.

## وضعیت
- **روی `mobile-client` کامیت و push شد** (develop و main دست نخورده‌اند).
- **این جلسه انجام شد و روی امولاتور (`Pixel_6a`) دیده شد:**
  - شبکه‌های اجتماعی کسب‌وکار در بک‌اند، اپ اونر (فرم)، وب و اپ مشتری. اپ مشتری: ردیف کاشی با لوگوهای اپ اونر، عنوان «{نام کسب‌وکار} در شبکه‌های اجتماعی».
  - کد تایید خانه‌به‌خانه و متحرک (`OtpCodeField`)؛ فونت درست فیلد شماره.
  - لیست شهرها بعد از انتخاب استان از بالا.
  - بیعانهٔ اجباری بدون کارت/آنلاین دیگر بن‌بست نیست (سرور، اونر، وب، مشتری).
- **دیده نشده در اجرا:** فرم شبکه‌ها و هشدار بیعانه در اپ اونر؛ صفحهٔ وب.
- **تست‌ها:** بک‌اند appointment/business/accounting ۶۵۱ سبز. اپ مشتری `testLocalDebugUnitTest` ۱۳۹ سبز. اونر کامپایل شد. وب `tsc` (جز خطاهای قبلی Playwright).
- **دیپلوی سرور نیمه‌کاره:** بکاپ‌ها گرفته شد، checkout سرور روی `45a9b96` است، build ایمیج‌ها شروع شد. **migrate و بقیه انجام نشده؛ سرور هنوز کد قدیمی را اجرا می‌کند.** کامیت‌های این جلسه هنوز به سرور نرفته‌اند.
- دادهٔ نمایشی (سالن مو اطلس id 83، سالن زیبایی ستاره id 64) فقط در DB محلی worktree است.

## قدم‌های بعدی
1. تمام کردن دیپلوی: bundle تازه (`45a9b96..mobile-client`)، rebuild `web worker worker_bulk frontend`، `up -d web` (migrate شامل `business/0036`، `visitor/0014`، …)، `migrate_credits_to_db` (dry-run، اجرا، dry-run)، `up -d worker worker_bulk nginx`، `/api/healthz`. چک‌لیست: `docs/DEPLOY_SCALE_HARDENING.md`. **پیش از آن از کاربر بپرس CDN جلوی nginx هست یا نه** (`set_real_ip_from`).
2. `seed_insurances` روی سرور.
3. دیدن فرم شبکه‌ها و هشدار بیعانه در اپ اونر و صفحهٔ وب در اجرا.
4. طراحی دوبارهٔ کاوش، «نوبت‌های من» و «نشان‌شده‌ها» با همین سبک.
5. تصمیم کاربر: فیلد جدا «عکس کاور» برای مرکز؟
6. ریلیز اپ اونر بعد از دیپلوی (Room v21؛ فیلد `social_links` لازم دارد سرور به‌روز باشد، وگرنه نادیده گرفته می‌شود).

## مسائل باز
- ⚠️ **قبل از دیپلوی:** migrationهای `business/0036`، `visitor/0014`، `appointment/0017`، `banners/0002`، `business/0035` و P2. `ZIBAL_MERCHANT_ID` در `.env` سرور هست (چک شد). `seed_demo_businesses` فقط محلی.
- 🔒 رمز root سرور در تاریخچهٔ git است و باید عوض شود.
- در DB محلی ۲۱ کسب‌وکار بیعانهٔ اجباری بدون روش دارند (قبل از رفع seed)؛ رزروشان حالا مستقیم به اونر می‌رود.
- `PushMessagingService` اپ کلاینت ثبت نشده است؛ منتظر فایل Firebase.
- **قاعدهٔ متن اپ:** کوتاه و بدون ئ ء ٔ أ. ثابت `BOOKING_BLOCKED_NOTICE` را عوض نکن.
- **محیط (macOS):**
  - امولاتور AVD `Pixel_6a`؛ `adb reverse tcp:8000 tcp:8000` بعد از هر بوت.
  - بک‌اند: preview `backend_mobile_client_mac` (از worktree با venv repo اصلی).
  - بیلد: `java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :composeApp:assembleLocalDebug` با `JAVA_HOME` = jbr اندروید استودیو و `ANDROID_HOME=~/Library/Android/sdk`؛ نصب APK `arm64-v8a`.
  - کاربر هم‌زمان با امولاتور کار می‌کند؛ موقع اسکرول خودکار روی صفحهٔ کسب‌وکار مراقب دکمهٔ ثابت «رزرو نوبت» باش.
  - SSH به سرور فقط با IP ایران (VPN خاموش).

## فایل‌های کلیدی
- `backend/business/social_links.py` — قالب ذخیرهٔ شبکه‌ها
- `backend/appointment/selectors.py` — `deposit_collectable`، `business_requires_payment`
- `mobile_client/.../core/ui/components/OtpCodeField.kt` — کد تایید متحرک
- `mobile_client/.../feature/businessDetail/DetailSocial.kt` — بخش شبکه‌ها
- `mobile_owner/.../feature/createBusiness/SocialLinksField.kt` — فرم شبکه‌ها
- `front_client/app/components/SocialLinks.tsx` — بخش شبکه‌ها در وب

## سشن‌های اخیر
- [[sessions/2026-11-02-0005]] — شبکه‌های اجتماعی در سه اپ، کد تایید متحرک، رفع بن‌بست بیعانه، دادهٔ نمایشی، دیپلوی نیمه‌کاره
- [[sessions/2026-11-01-1741]] — صفحهٔ مرکز با هدر جمع‌شونده، تب پروفایل، کش نقشه
- [[sessions/2026-11-01-0014]] — ادمین: ورود از فایل و فرم هر صنف؛ اپ: «بیمه من»
- [[sessions/2026-10-31-2234]] — فیلتر بیمه، دادهٔ نمایشی، زوم ناحیه، مسیریابی
- [[sessions/2026-10-31-2047]] — لاگین، تایپوگرافی واحد، متن بی‌همزه، آیکن Lucide

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
