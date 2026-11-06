# دیپلوی مقاوم‌سازی سرور (برنچ mobile-client، از 3aba241)

۲۲ commit: درستی زیر بار هم‌زمان، پول در دیتابیس، صف Outbox، کارایی خواندن، بازسازی ساختار.
تست: ۷۳۳ تست روی SQLite و Postgres 16 سبز.

## قبل از دیپلوی
1. **رمز root سرور قدیمی** (`93.127.223.93`، در `check_db.py` که حذف شد ولی در تاریخچه git هست) را عوض کنید.
2. **جلوی nginx CDN/proxy هست؟** (مثلاً ابرآروان). اگر هست، قبل از دیپلوی در nginx
   `set_real_ip_from <رنج‌های CDN>; real_ip_header X-Forwarded-For;` اضافه شود؛ وگرنه همه پشت چند IP دیده می‌شوند و 429 دسته‌جمعی می‌گیرند.
3. **بکاپ Postgres** (`pg_dump`) و یک نسخه از Redis (`redis-cli BGSAVE` و کپی `dump.rdb`) — موجودی کیف پول‌ها فعلاً در Redis است.
4. نقش دیتابیس اجازه `CREATE EXTENSION pg_trgm` داشته باشد (کاربر `POSTGRES_USER` در ایمیج رسمی superuser است). در غیر این صورت یک بار دستی:
   `psql -U postgres -d <db> -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"`
5. اختیاری ولی توصیه‌شده (به‌جای statement_timeout در کد، که pgbouncer رد می‌کند):
   `ALTER ROLE <db_user> SET statement_timeout = '15s'; ALTER ROLE <db_user> SET idle_in_transaction_session_timeout = '30s';`
6. **`ZIBAL_MERCHANT_ID` در `.env` سرور باشد** (موج ۱): merchant واقعی دیگر در کد نیست؛ بدون آن همه پرداخت‌ها شکست می‌خورند. `manage.py check` در این حالت هشدار `core.W001` می‌دهد.
7. migrationهای موج ۱: `versions/0003_appversion_app` (unique روی `(app, version_code)`) و `visitor/0013` (سه فیلد nullable). هر دو افزایشی‌اند.
8. بعد از دیپلوی: توکن کلاینت مشتری‌هایی که شماره‌شان عوض شود باطل می‌شود (عمدی). health check: `/api/healthz` و `/api/readyz`.

## ترتیب دیپلوی
```bash
docker compose build web worker worker_bulk
docker compose up -d web            # entrypoint: migrate + collectstatic (فقط web)
docker compose logs web | grep -i -E "error|migrat" | tail
docker compose exec web python manage.py migrate_credits_to_db --dry-run
docker compose exec web python manage.py migrate_credits_to_db
docker compose exec web python manage.py migrate_credits_to_db --dry-run   # باید «0 to create» بدهد
docker compose up -d worker worker_bulk nginx
docker compose ps                   # web، worker، worker_bulk، nginx همه Up
```
- `migrate_credits_to_db` را وقتی Redis سالم است اجرا کنید. بدون آن هم کسی موجودی صفر نمی‌بیند (seed تنبل)، ولی این دقیق‌تر است. هرگز `--overwrite` را بعد از ترافیک واقعی نزنید.
- **بدون `worker` هیچ پیامک/پوشی ارسال نمی‌شود؛ بدون `worker_bulk` هیچ کمپینی.**

## یک ساعت اول بعد از دیپلوی
- صف Outbox خالی می‌شود:
  `docker compose exec web python manage.py shell -c "from core.outbox.models import OutboxMessage as M; from django.utils import timezone as t; from datetime import timedelta as d; print(M.objects.filter(status='pending', created_at__lt=t.now()-d(minutes=5)).count(), M.objects.filter(status='failed').count())"` → هر دو 0
- 429ها در nginx: اگر چند IP اپراتور غالب‌اند، `zone=auth` را شل‌تر کنید.
  `docker compose logs nginx | grep ' 429 ' | awk '{print $1}' | sort | uniq -c | sort -rn | head`
- لاگ‌ها: `needs manual refund`، `CreditLedger write FAILED`، `amount mismatch`.
- پنل ادمین → نوبت‌ها → فیلتر «پرداخت‌شده بدون نوبت (نیاز به بازگشت وجه)».
- چند حساب پرمصرف: موجودی `CreditBalance` با مقدار قبلی Redis یکی باشد.

## تغییرات قابل مشاهده برای کاربر
- شماره کارت دیگر در API عمومی کسب‌وکار نیست؛ فقط روی نوبت قفل‌شده خود مشتری (وب به‌روز شده).
- OTP: بدون بن ۲۴ ساعته؛ سقف ۱۵ ارسال موفق در روز برای هر شماره؛ پیام جدید هنگام قطعی Redis.
- callback تکراری پرداخت پلن/add-on حالا «موفق» نشان می‌دهد (قبلاً «قبلاً پردازش شده»).
- پرداخت بیعانه‌ای که دیر رسیده و نوبتش گرفته شده: `?payment=conflict` در وب + صف بازگشت وجه در ادمین.
- پیام‌های ادمین برای ارسال گروهی: «در صف ارسال قرار گرفت».

## بازگشت (rollback)
همه migrationها افزایشی‌اند؛ برگشت کد به نسخه قبل بدون rollback دیتابیس کار می‌کند،
**به‌جز** کیف پول‌ها: کد قدیمی موجودی را از Redis می‌خواند و خرج‌های بعد از دیپلوی در Redis نیستند.
در صورت برگشت، `rebuild_wallets_from_ledger` در نسخه قدیمی را اجرا کنید.

## باز (کارهای بعدی)
- دوره سهمیه ماهانه هنوز ماه UTC است (ساعت ۰۰:۰۰ تا ۰۳:۳۰ روز اول ماه تهران در ماه قبل حساب می‌شود).
- load test معتبر: `docs/LOAD_TEST_2026-10-29.md` به‌خاطر Postgres شبیه‌سازی‌شده (amd64) نتیجه نداد؛ روی staging یا Postgres بومی arm64 با `DEBUG=False` تکرار شود.
- ریلیز اپ اونر: نسخه‌های قدیمی هنوز باگ ترکیب پیام‌های چند کسب‌وکار را دارند (در `acd490b` رفع شده).
