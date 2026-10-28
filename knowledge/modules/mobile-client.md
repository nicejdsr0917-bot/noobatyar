---
updated: 2026-10-28
tags: [gf]
---
# اپ موبایل مشتری (mobile_client — برنچ `mobile-client`)

**هدف:** کشف کسب‌وکار روی نقشه (نزدیک‌ترین پزشک/سالن…)، فیلتر بیمه، نشان کردن، رزرو؛ و فهرست کسب‌وکارهای واردشده (unclaimed) که owner می‌تواند claim کند.

## محل
- روی develop پوشهٔ `mobile_client/` **خالی** است. کد روی برنچ `mobile-client`، checkout‌شده به‌صورت worktree در `../Noobatyar-mobile-client`.
- worktree قدیمی دیگری هم هست: `../wt-mobile-client` (detached، `5515f31`).

## Backend روی این برنچ
- `business`: `Insurance`، فیلدهای `province/city/neighborhood/is_claimed/external_source/online_booking_requests_count`، `OnlineBookingRequest`، `BusinessClaimRequest`؛ `claim_views.py`، `claims.py`؛ command `import_directory`، `seed_directory_demo`
- endpointها: `api/client/businesses/` (geo: `lat/lng/radius_km`، `sort=distance|recommended`، `only_claimed`)، `business/claimable/`، `business/<id>/claim[/verify]/`
- migrationها: `0028_insurance_...` و `0029_claims_...` (بعد از 0027 در develop)

## اجرا
- پکیج `xyz.sattar.javid.proqueue.client.local`؛ `:composeApp:assembleLocalDebug`؛ نصب با `adb install -r .../apk/local/debug/composeApp-local-arm64-v8a-debug.apk` (`installLocalDebug` به‌خاطر ABI split خطا می‌دهد)
- بک‌اند باید **از worktree** اجرا شود: preview `backend_mobile_client_mac` (پایتون venv repo اصلی؛ `.env.local` در worktree کپی شده)

## نکته‌ها
- موقعیت امولاتور Mountain View است → در اپ «تهران» را انتخاب کن (`adb emu geo fix` اثر ندارد).
- کاشی OSM از ایران کند است → `tile.openstreetmap.fr/hot`.
- کسب‌وکارهای واردشده مال کاربر سیستمی‌اند → از قانون اشتراک در `apply_contact_rules` مستثنا شدند.
- migrationهای این برنچ با Django 6.0.7 ساخته شده بودند؛ venv و prod روی 4.2.29 هستند.

## وابستگی‌ها
[[modules/backend-business]]، [[modules/backend-visitor]]، [[modules/backend-appointment]].
