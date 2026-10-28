---
updated: 2026-10-28 21:44
tags: [gf, current]
---
# وضعیت فعلی

## هدف
یکپارچه‌سازی: صفحهٔ کسب‌وکار در وب مشتری داده‌های ساختاریافته (خدمات، روزهای کاری) را نشان دهد، و اپ مشتری موبایل (برنچ `mobile-client`) با بک‌اند به‌روز develop کنار اپ owner تست سرتاسری شود.

## وضعیت
- Knowledge base ساخته شد (`knowledge/`).
- وب مشتری `b/[slug]`: کارت خدمات، کارت روزهای کاری و آیکون جدای بیعانه پیاده و روی localhost (fa/en، روشن/تیره) تست شد — **روی develop commit نشده، deploy نشده**.
- برنچ `mobile-client` با develop merge شد (`5852659`، محلی، **push نشده**)؛ ۵۳۴ تست بک‌اند پاس. DB worktree بازسازی و داده برگردانده شد.
- هر سه بالا هستند: بک‌اند worktree روی :8000 (preview `backend_mobile_client_mac`، با OTP آزمایشی)، اپ owner و اپ مشتری روی AVD `Pixel_6a` نصب و به بک‌اند وصل.

## قدم‌های بعدی
1. تست سرتاسری روی AVD: رزرو در اپ مشتری (شهر «تهران») → دیده‌شدن در اپ owner (ورود با seed در `backend/seed_demo_data.py`)
2. commit تغییرات وب مشتری روی develop و deploy (`front_client`)
3. تصمیم دربارهٔ push برنچ `mobile-client`
4. اصلاح باگ timezone در `appointment_stats_view.py` + fixture `tests_daily_counts.py`
5. حذف `card_number`/`card_owner_name` از API عمومی لیست کسب‌وکار

## مسائل باز
- 🐞 آمار «امروز» با UTC حساب می‌شود (`backend/appointment/views/appointment_stats_view.py:93`) → ۳ تست بین ۰۰:۰۰–۰۳:۳۰ تهران fail.
- 🔒 شماره کارت و نام صاحب کارت در `api/client/business/` بدون ورود.
- فایل‌های `.exp` با رمز سرور در ریشه (gitignore‌شده)؛ `backend/backend_deploy.zip` در git؛ zip/tar و `db.sqlite3.bak-*` اضافه.
- یک commit روی develop (`a5bd697`) push نشده؛ تغییر iOS baseUrl در `mobile_owner/composeApp/build.gradle.kts` commit نشده.
- فایل untracked جدید: `mobile_owner/composeApp/src/wasmJsMain/.../InMemoryUserLocalSource.kt` (منشأ نامعلوم در این سشن).
- lint: سه خطای قدیمی `set-state-in-effect` در `front_client/app/b/[slug]/book/page.tsx`.
- محیط: launch.json پیکربندی ویندوزی دارد؛ `head` در zsh alias است.

## فایل‌های کلیدی
- `front_client/app/b/[slug]/BusinessProfileClient.tsx` — صفحهٔ کسب‌وکار (تغییرات جدید)
- `front_client/lib/api.ts` — `closedWeekdaysJs()`
- `../Noobatyar-mobile-client/backend/business/serializers.py` — `apply_contact_rules` با استثنای unclaimed
- `.claude/launch.json` — پیکربندی `backend_mobile_client_mac`

## سشن‌های اخیر
- [[sessions/2026-10-28-2144]] — بررسی پروژه، خدمات/روزهای کاری در وب، merge mobile-client، ساخت knowledge base

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
