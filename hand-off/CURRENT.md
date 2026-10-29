---
updated: 2026-10-29 20:22
tags: [gf, current]
---
# وضعیت فعلی

## هدف
سرور زیر هزاران درخواست هم‌زمان درست و سریع کار کند (اولویت کاربر)، سپس بقیهٔ `docs/PLAN_NEXT.md` (P0/P1، بعد قابلیت‌های P2).

## وضعیت
- **همهٔ کار روی برنچ `mobile-client`** در worktree `../Noobatyar-mobile-client` است: ۲۳ commit از `3aba241`، **push/دیپلوی نشده**. develop/main دست نخورده. برای دیپلوی باید `mobile-client` در develop merge شود (اپ کلاینت هم همراهش می‌آید).
- ۷۳۳ تست بک‌اند روی SQLite و Postgres 16 سبز (اجرای Postgres: حافظهٔ `noobatyar-postgres-tests`).
- انجام‌شده: قفل/UPDATE شرطی در رزرو و پرداخت، کیف پول در `CreditBalance`، صف Outbox با `worker`/`worker_bulk`، OTP ضد سوءاستفاده، لیست عمومی با کوئری ثابت + trigram، شماره کارت خصوصی، nginx/gunicorn/Redis، ساختار services/selectors/integrations. جزئیات: [[sessions/2026-10-29-2022]].
- چک‌لیست دیپلوی: `../Noobatyar-mobile-client/docs/DEPLOY_SCALE_HARDENING.md`.
- لوکال بالاست: preview `backend_mobile_client_mac` (:8000)، `front_client_mobile_mac` (:3010، وب worktree)، `run_outbox` در پس‌زمینه.
- از PLAN_NEXT: P0 بندهای ۱، ۳، ۴، ۶ تمام؛ ۲ نیمه؛ ۵ (بله) مانده. P1 بخشی؛ P2/P3 شروع نشده.

## قدم‌های بعدی
1. load test روی سرور خود کاربر (بپرس کدام سرور و دسترسی SSH؛ نشتی حافظهٔ workerها، اتصال‌های DB، صف Outbox را بسنج).
2. دورهٔ سهمیهٔ ماهانه → ماه تهران (`accounting/usage.py` `_period` + `business/sms_views.py` + مهاجرت ماه جاری).
3. P0-۵: پیام بله بعد از تصمیم مشخصات را پاک نکند (`bale/views.py` `_finalise_message`).
4. تست جداسازی داده برای همهٔ endpointهای اونر؛ CI روی هر push؛ health check + Sentry؛ `AppVersion.app` برای جدایی دو اپ.

## مسائل باز
- 🔒 رمز root سرور `93.127.223.93` در تاریخچهٔ git است (`check_db.py`) — کاربر باید عوض کند.
- ❓ جلوی nginx CDN/proxy (ابرآروان) هست؟ اگر هست `set_real_ip_from` لازم است وگرنه 429 دسته‌جمعی.
- load test محلی بی‌نتیجه بود (Postgres amd64 emulate‌شده گلوگاه).
- دیپلوی: بعد از migrate، `migrate_credits_to_db`؛ بدون `worker` پیامکی نمی‌رود، بدون `worker_bulk` کمپینی.
- کاربرهای نسخهٔ قدیمی اپ اونر هنوز ترکیب پیام‌های چند کسب‌وکار را می‌بینند (رفع در `acd490b`، نیاز به ریلیز).
- کاشی نقشه OSM تار؛ پنل وب اونر از develop اجرا می‌شود.
- فایل‌های commit‌نشدهٔ قدیمی در این repo: `mobile_owner/composeApp/build.gradle.kts`، `InMemoryUserLocalSource.kt`، `.claude/*`، `.mcp.json`، `SALES_PLAYBOOK.md`، `docs/SERVER_DISK_CLEANUP.md`.

## فایل‌های کلیدی
- `docs/PLAN_NEXT.md` — پلن اولویت‌دار
- `../Noobatyar-mobile-client/docs/DEPLOY_SCALE_HARDENING.md` — چک‌لیست دیپلوی
- `../Noobatyar-mobile-client/backend/appointment/services/` — رزرو، تغییر وضعیت، بیعانه، اعلان
- `../Noobatyar-mobile-client/backend/accounting/usage.py` — اعتبار و سهمیه روی `CreditBalance`
- `../Noobatyar-mobile-client/backend/core/outbox/` — صف و worker

## سشن‌های اخیر
- [[sessions/2026-10-29-2022]] — مقاوم‌سازی سرور برای بار بالا، پول در DB، Outbox، OTP، کارایی، بازسازی SOLID
- [[sessions/2026-10-29-1039]] — انتخاب شهر با لیست دیوار، تمیز کردن دیتای محلی، رفع بازگشت درگاه، پلن کار بعدی
- [[sessions/2026-10-28-2144]] — بررسی پروژه، خدمات/روزهای کاری در وب، merge mobile-client، ساخت knowledge base

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
