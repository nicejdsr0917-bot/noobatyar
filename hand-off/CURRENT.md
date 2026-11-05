---
updated: 2026-11-05 10:42
tags: [gf, current]
---
# وضعیت فعلی

## هدف
شبکهٔ اجتماعی سبک بین مشتری و کسب‌وکار.
- **انجام شده:** فالو، اعلان فالو برای اونر، دستهٔ فالوورها در کمپین پیامکی، و استوری ۲۴ ساعته با دکمهٔ تخفیف.
- **باقی‌مانده:** اعلان «نظر بدهید» بعد از نوبت، و ریلیز اپ‌ها.

قاعدهٔ همیشگی: کاربر واقعی داریم، پس فقط تغییر افزایشی. با کاربر فارسی حرف بزن. کاربر نزدیک لیمیت است و می‌خواهد کار تکه‌تکه پیش برود.

## وضعیت
- **prod** (`194.146.69.137`) روی `c3be330` است (دیپلوی 2026-11-05 10:22، با پنل وب).
  - اعلان فالو، دستهٔ فالوورها و استوری روی سرور فعال‌اند.
  - **`a3777dd` هنوز دیپلوی نشده است.** شامل: `distinct` در `stories.services.active_business_ids`، استوری در منوی کناری پنل، و برچسب تخفیف یک‌خطی.
- **تست محلی** 10:30 تا 10:40: پنل وب و اپ کلاینت روی امولاتور، همه کار کرد. جزئیات: [[sessions/2026-11-05-1042]].
- **ریلیز اپ‌ها:**
  - آخرین ریلیزها: اونر `v1.12.0` و کلاینت `client-v1.2.0`.
  - هیچ‌کدام از کارهای 11-04 و 11-05 هنوز در اپ موبایل نیست: فالو، بیو، کراپ، پروفایل تازه، استوری و کارت فالوورها.
  - changelog `client-v1.3.0` آماده است، ولی استوری را ندارد.
- **پاداش مشتری تازه** روی prod خاموش است (`new_client_reward = 0`). برای روشن کردن: ادمین ← «کیف‌پول تومانی».
- **نسخه‌ها:** کلاینت و اونر جدا چک می‌شوند. در هر ریلیز کلاینت، نسخه را در ادمین Versions با اپ CLIENT ثبت کن.

## قدم‌های بعدی
1. دیپلوی `a3777dd` روی prod (اول از کاربر بپرس).
   - روش: `docs`، و حافظهٔ deploy-ops.
   - bundle از `c3be330`.
   - `up -d --no-deps --build web worker worker_bulk`، با Shecan updater در همان ssh.
   - `scripts/deploy-panel.sh`.
2. **ریلیزها:** `v1.13.0` اونر و `client-v1.3.0`.
   - استوری را به changelog کلاینت اضافه کن.
   - دو CI را هم‌زمان نزن.
3. **اعلان «نظر بدهید» برای مشتری** بعد از اینکه نوبت «انجام‌شده» شد.
   - نقطهٔ اتصال: `appointment/services/transitions.appointment_change_status_by_owner`.
   - پوش اپ کلاینت کار نمی‌کند (Firebase کلاینت نیست)، پس شاید SMS. **بپرس.**
4. **سؤال‌های بی‌جواب از کاربر:**
   - تخفیف استوری فعلاً فقط نمایشی است. روی بیعانه یا رزرو هم اعمال شود؟
   - نمایشگر با نگه داشتن انگشت مکث کند؟
   - استوری در وب مشتری هم باشد؟
5. چک کن `UserIpSighting` روی prod بعد از استفادهٔ واقعی اونرها پر می‌شود.

## مسائل باز
- 🔒 رمز root سرور در تاریخچهٔ git است و باید عوض شود.
- `PushMessagingService` اپ کلاینت ثبت نشده است و منتظر فایل Firebase است. پوش استوری و قدم ۳ فعلاً فقط به توکن‌های وب می‌رسند.
- نمایشگر استوری برای هر استوری ۵ ثانیه است و بعد خودش بسته می‌شود. دکمهٔ فالو کم‌فرصت است.
- کسب‌وکار دمو `id=7` در DB محلی مختصات ندارد و در لیست کلاینت نمی‌آید. برای تست کلاینت از `id=14` (سیمرغ) استفاده کن.
- **قاعدهٔ متن اپ:** کوتاه و بدون ئ ء ٔ أ. ثابت `BOOKING_BLOCKED_NOTICE` را عوض نکن.
- **این‌ها commit نشوند:**
  - `API_DOCUMENTATION.md`، `implementation_plan.md`، `backend/*.log`
  - lockهای `mobile_client/.gradle/`
  - `.claude/launch.json`، `mobile_owner/composeApp/build.gradle.kts`
  - فایل‌های untracked کاربر: `InMemoryUserLocalSource.kt`، `.claude/*` تازه، `SALES_PLAYBOOK.md`، `demo-assets/`، `.mcp.json`
  - فایل‌ها را تک‌تک add کن، هیچ‌وقت `git add -A`.
- **دو CI ریلیز را هم‌زمان نزن.** هر دو xcconfig را روی develop push می‌کنند.
- **محیط macOS:**
  - امولاتور `Pixel_6a`.
  - backend روی 8000.
  - پنل وب: preview `mobile_owner_web_mac` (8080). بعد از هر تغییر Kotlin باید stop و start شود.
  - کلاینت: `sh ./gradlew :composeApp:assembleLocalSiteDebug` با `ANDROID_HOME`، سپس `adb install` نسخهٔ `composeApp-local-site-arm64-v8a-debug.apk` و `adb reverse tcp:8000 tcp:8000`.
  - کار با UI امولاتور: uiautomator dump.
  - انتخاب عکس در پنل wasm: override `HTMLInputElement.prototype.click` (سشن 1042).

## فایل‌های کلیدی
- `backend/stories/`: مدل، سرویس‌ها (ساخت، پاک‌سازی منقضی‌ها، نوتیف فالوورها) و API استوری
- `backend/business/follows.py`، `follow_views.py`: فالو، پوش فالو و لیست فالوورهای اونر
- `backend/business/promo_sms.py`: دستهٔ `followers` و پنهان ماندن شماره
- `mobile_owner/.../feature/stories/`، `feature/home/HomeFollowersCard.kt`
- `mobile_client/.../feature/stories/`، `feature/discovery/DiscoveryUi.kt` (`BusinessAvatar` با حلقه)
- `docs/api/P2_SOCIAL_API.md`: §7 فالو، §10 استوری

## سشن‌های اخیر
- [[sessions/2026-11-05-1042]]: جمع‌بندی شبکهٔ اجتماعی، دیپلوی prod، تست محلی و ۳ اصلاح
- [[sessions/2026-11-05-1020]]: استوری در اپ کلاینت
- [[sessions/2026-11-05-1017]]: صفحهٔ استوری در اپ اونر
- [[sessions/2026-11-05-1011]]: بک‌اند استوری
- [[sessions/2026-11-05-1000]]: دستهٔ فالوورها در کمپین پیامکی

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
