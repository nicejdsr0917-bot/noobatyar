---
updated: 2026-11-10 22:13
tags: [gf, current]
---
# وضعیت فعلی

## هدف
۱) جمع‌آوری خودکار لیست مراکز درمانی بیمه‌ها با n8n و جداسازی «مراکز» (بدون نوبت‌دهی) از «کسب‌وکارها»، برای تماس و تبدیل به کسب‌وکار. ۲) انتشار اپ کارفرما در مایکت (v1.13.3 در بازبینی) و ویدیوی معرفی اپ کارفرما (در سشن تبلیغات). بعد: تخفیف مخصوص اپ و دعوت دوست. قاعده همیشگی: کاربر واقعی داریم، فقط تغییر افزایشی. با کاربر فارسی حرف بزن.

## وضعیت
- **مراکز (کامیت `8a6940ae`، push و دیپلوی نشده):** منوی «مراکز» در ادمین، پیگیری تماس (`CenterOutreach`)، `Business.origin`، فیلدهای `is_noobatyar`/`kind`/`booking_available` در API. ۱۲۳۳ تست پاس.
- **n8n لوکال:** `docker compose -f backend/docker-compose.n8n.yml --env-file backend/.env.n8n up -d` → `http://localhost:5679` (حساب owner را کاربر باید بسازد). workflow «Noobatyar directory sync» import شده و `DRY_RUN=false` است. منبع فعلی فقط **دانا** (۲۹۶۲ مرکز در دیتابیس لوکال). endpoint: `POST /api/internal/directory-sync/` (توکن `DIRECTORY_SYNC_TOKEN`).
- لوکیشن مراکز بدون مختصات **خالی می‌ماند** (تصمیم کاربر؛ ۸۳۴ از ۲۹۶۲ مختصات دارند).
- **مایکت:** v1.13.3 دوباره برای بازبینی فرستاده شده؛ نسخه مایکت هدر `X-Store: myket` می‌فرستد. منتظر نتیجه.
- **prod** روی `develop` در `d1c91f9` است. تگ APK اونر `v1.13.3`، آخرین تگ مشتری `client-v1.3.0`. پلن آزمایشی ۱۰ روزه است؛ در محتوای اینستاگرام قیمت نگو.
- **ویدیو:** اپ مشتری تمام (v3)؛ کارفرما شروع شده (`marketing/owner-store-v2/`)، ادامه‌اش در سشن تبلیغات.
- **باگ A3 رفع شده ولی merge نشده:** `refs/heads/a3-tehran-day-boundary` (`ae748ce`).
- **لوکال:** ادمین `09120000100` (رمز در `TEST_ACCOUNTS.local.md`). superuserهای قبلی پاک شدند؛ پشتیبان `backend/db.sqlite3.bak-20261110-predeleteadmin`.

## قدم‌های بعدی
1. کاربر: حساب owner در n8n بسازد و workflow را روشن کند.
2. بیمه‌های بعدی را وصل کن: البرز `alborzinsurance.ir/MedicalCenters`، کوثر `kins.ir/CenterAgreement`، دی `dayins.com`، آسیا `bimehasia.com`، ایران `iraninsurance.ir`. اول API زیر صفحه را با مرورگر پیدا کن (روش دانا: `Network`/اسکریپت صفحه)؛ User-Agent جعل نکن.
3. در ادمین `origin` را برای ۱۰۷ کسب‌وکار قدیمی مشخص کن.
4. دیپلوی مراکز روی prod (با تایید کاربر): migration `0043`، `DIRECTORY_SYNC_TOKEN` و `web` در `ALLOWED_HOSTS`، reload nginx، n8n روی شبکه docker سرور، `seed_insurances`. n8n فقط از داخل ایران به سایت بیمه‌ها می‌رسد.
5. نتیجه بازبینی مایکت؛ ویدیوی کارفرما؛ تگ `client-v1.4.0` (با تایید کاربر)؛ merge برنچ A3؛ چیپ بیمه در `BookingScreen`؛ تخفیف مخصوص اپ و دعوت دوست.

## مسایل باز
- 🔒 رمز root سرور در تاریخچه git است.
- **بعد از rebuild وب روی prod حتما `nginx -s reload` بزن** (502).
- سایت بیمه‌ها از بیرون ایران باز نمی‌شوند (tamin.ir geo-block). از سرور: `DEPLOY_HOST=194.146.69.137 bash scripts/remote.sh '...'` (فایل اجرایی نیست؛ با `bash`). خواندن prod نیاز به اجازه کاربر دارد.
- جعل User-Agent یا دور زدن bot check مجاز نیست (سیستم رد می‌کند).
- پورت 5678 دست `peyk_n8n` است؛ n8n ما 5679. اجرای CLI: `docker exec -e N8N_RUNNERS_BROKER_PORT=5680 ... n8n execute --id=...`.
- قیمت SKUهای مایکت دستی است: با هر تغییر قیمت، خروجی `manage.py myket_prices` را در پنل مایکت بگذار.
- **همزه ممنوع است** (U+0621، U+0623 تا U+0626، U+0654، U+06C0؛ «آ» مجاز). **فلش «رفتن» «<» است** (`ForwardChevron`). `favicon.ico` ادمین api خطای ۵۰۰ می‌دهد.
- **commit نشوند:** `.claude/launch.json`، `mobile_owner/composeApp/build.gradle.kts`، lockهای `mobile_client/.gradle/`، `marketing/` و فایل‌های untracked کاربر. فایل‌ها را تک‌تک add کن (نه `git add -A`).
- **محیط macOS:** adb در `~/Library/Android/sdk/platform-tools/adb`؛ در zsh `timeout` نیست و `echo ======` خطا می‌دهد؛ شبیه‌ساز با `-gpu host`.

## فایل‌های کلیدی
- `backend/business/directory_sync.py`، `internal_views.py` — endpoint و منطق همگام‌سازی
- `backend/business/admin/centers.py`، `models/centers.py` — مراکز و پیگیری تماس
- `backend/business/public_policy.py` (`apply_center_rules`) — قاعده پاسخ مرکز
- `backend/n8n/directory-sync.workflow.json`، `backend/docker-compose.n8n.yml`، `docs/N8N_DIRECTORY_SYNC.md`
- `backend/accounting/serializers.py` — `_store_price` (مایکت)

## سشن‌های اخیر
- [[sessions/2026-11-10-2213]]: مراکز، پیگیری تماس، `origin`، n8n و منبع دانا
- [[sessions/2026-11-10-1817]]: رفع ایرادهای مایکت، v1.13.3، دیپلوی بک‌اند
- [[sessions/2026-11-07-0006]]: ویدیوی معرفی اپ مشتری (v3)
- [[sessions/2026-11-06-1317]]: زیپ اسکرین‌ها، تگ v1.13.1، داده تست اپ مشتری
- [[sessions/2026-11-06-1211]]: بیمه تکمیلی و کد پیگیری، فلش RTL، حذف همزه

ببین: [نقشه پروژه](../knowledge/INDEX.md)
