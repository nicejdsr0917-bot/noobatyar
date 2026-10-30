---
updated: 2026-10-30 09:59
tags: [gf, current]
---
# وضعیت فعلی

## هدف
اجرای P1 و P2 از `../PLAN_NEXT.md` روی برنچ `mobile-client` (worktree `../Noobatyar-mobile-client`). P1 عملاً تمام است و الان روی قابلیت‌های P2 کار می‌شود. قاعده: فقط تغییر افزایشی، چون کاربر واقعی و اپ قدیمی داریم.

## وضعیت
- **`mobile-client`:** ۶۶ commit از `c2377c6`. **push و دیپلوی نشده.** develop و main دست نخورده‌اند.
- **تست‌ها:**
  - بک‌اند: ۹۰۶ تست روی SQLite سبز است. روی Postgres ترتیبی اجرا کن، چون `--parallel` آن‌جا ناپایدار است.
  - اپ اونر: ۵۰ تست سبز.
  - اپ کلاینت: ۲۴ تست سبز و iOS compile می‌شود.
  - وب: ۱۰ تست e2e سبز و lint بدون خطا.
- **انجام‌شده:** P0 کامل؛ امنیت (A1 تصاحب حساب، A2، A6)؛ بازسازی R1 تا R13؛ کش Redis؛ N+1؛ CI؛ `AppVersion.app`؛ لاگ JSON؛ health check؛ زیرساخت تست دو اپ و وب. جزئیات: [[sessions/2026-10-30-0959]].
- **P2 بک‌اند (نیمه‌کاره و merge نشده):**
  - `../wt-p2-social` (برنچ `p2/social`): منبع نوبت و نظرات تمام. مانده: گزارش تخلف، `compute_rank_scores`، سرویس‌های سایت، سند API.
  - `../wt-p2-showcase` (برنچ `p2/showcase`): نمونه‌کار و مدارک، بنرها، بازاریابی و لوگوی opt-in تمام. مانده: منوی ادمین (UNFOLD SIDEBAR)، سند API.
- **ممیزی و بسته‌های کاری:** `../Noobatyar-mobile-client/docs/audit/`. تصمیم‌های کاربر در حافظهٔ `noobatyar-p1p2-decisions`.
- **کنار گذاشته‌شده:** C4 (ماژول مشترک)، O6 کامل، O7، F3، R14، R15، A3 (عمداً؛ اپ اونر رفتار روز سرور را تقلید می‌کند).

## قدم‌های بعدی
1. در هر دو `wt-p2-*` این‌ها را ببین: `git status` و `git log mobile-client..`. کارهای مانده را تمام کن، بعد یک ریویوی Opus روی کل diff P2 بک‌اند انجام بده و هر دو برنچ را merge کن. migrationها جدا هستند. هر دو برنچ `core/urls.py` و `INSTALLED_APPS` را عوض کرده‌اند، پس احتمال conflict کوچک هست.
2. اپ کلاینت بر اساس `docs/api/P2_*.md`: صفحهٔ کسب‌وکار با برند نوبت‌یار، رزرو نیتیو با `source=APP`، ثبت و گزارش نظر، «اولین نوبت خالی» روی کارت.
3. اپ اونر: آپلود نمونه‌کار ۱۰۸۰×۱۰۸۰ و مدارک ۱۰۸۰×۱۳۵۰، صفحهٔ نظرات و نوتیف، نشان منبع، بنر چرخشی. وب: بخش‌های نمونه‌کار، مدارک و نظرات در `b/[slug]`.
4. ثبت cron شبانهٔ `compute_rank_scores` روی سرور. P3: ریلیز جدا برای هر اپ و دیپلوی طبق چک‌لیست.
5. load test روی سرور واقعی (منتظر دسترسی SSH).

## مسائل باز
- ⚠️ **قبل از دیپلوی:** `ZIBAL_MERCHANT_ID` باید در `.env` سرور باشد، وگرنه همهٔ پرداخت‌ها شکست می‌خورند. migrationهای جدید: `versions/0003`، `visitor/0013`، و migrationهای P2. چک‌لیست: `../Noobatyar-mobile-client/docs/DEPLOY_SCALE_HARDENING.md`.
- 🔒 رمز root سرور `93.127.223.93` در تاریخچهٔ git است و کاربر باید عوضش کند.
- ❓ اگر جلوی nginx یک CDN مثل ابرآروان هست، `set_real_ip_from` لازم است.
- شبکهٔ کاربر ناپایدار است و ایجنت‌ها با `ECONNREFUSED` قطع می‌شوند. با SendMessage به همان ایجنت ادامه بده. کاربر از مصرف توکن ناراضی است: کم موازی کار کن و ریویوی Opus فقط برای پول، امنیت و migration.
- پیامک‌هایی که بعد از قبول provider با timeout قطع شوند refund می‌شوند. این یک نشت کوچک درآمد است.
- اپ کلاینت: `PushMessagingService` در manifest ثبت نشده است. این مشکل از قبل از این سشن بوده.
- فایل‌های commit‌نشدهٔ قدیمی در این repo (develop): `mobile_owner/composeApp/build.gradle.kts`، `InMemoryUserLocalSource.kt`، `.claude/*`، `.mcp.json`، `SALES_PLAYBOOK.md`، `docs/SERVER_DISK_CLEANUP.md`.

## فایل‌های کلیدی
- `../PLAN_NEXT.md` — پلن P0 تا P3
- `../Noobatyar-mobile-client/docs/audit/` — ممیزی و بسته‌های کاری
- `../Noobatyar-mobile-client/docs/DEPLOY_SCALE_HARDENING.md` — چک‌لیست دیپلوی
- `../Noobatyar-mobile-client/backend/common/` — پاسخ، صفحه‌بندی، زمان، پارامترها
- `../Noobatyar-mobile-client/backend/business/cache.py` — کش عمومی و invalidation

## سشن‌های اخیر
- [[sessions/2026-10-30-0959]] — اجرای P1 با ایجنت‌ها، رفع تصاحب حساب، بازسازی، تست، شروع P2 بک‌اند
- [[sessions/2026-10-29-2022]] — مقاوم‌سازی سرور برای بار بالا، پول در DB، Outbox، OTP، کارایی، بازسازی SOLID
- [[sessions/2026-10-29-1039]] — انتخاب شهر با لیست دیوار، تمیز کردن دیتای محلی، رفع بازگشت درگاه، پلن کار بعدی
- [[sessions/2026-10-28-2144]] — بررسی پروژه، خدمات/روزهای کاری در وب، merge mobile-client، ساخت knowledge base

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
