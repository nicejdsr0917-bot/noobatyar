# n8n: به‌روزرسانی دوره‌ای لیست پزشکان و بیمه‌ها

## معماری
- n8n در کانتینر جدا (`backend/docker-compose.n8n.yml`)، بدون رمز دیتابیس و بدون volume مشترک.
- تنها کاری که می‌تواند بکند: `POST /api/internal/directory-sync/` با `Authorization: Bearer <DIRECTORY_SYNC_TOKEN>`.
- Django (`business/directory_sync.py`): لیست جدید می‌سازد (بدون صاحب، رزرو خاموش) و به لیست‌های **ادعا نشده** فقط بیمه اضافه می‌کند. چیزی حذف یا بازنویسی نمی‌شود و کسب‌وکار ادعاشده دست‌نخورده می‌ماند.
- nginx مسیر `/api/internal/` را از بیرون 404 می‌کند؛ n8n مستقیم به `web:8000` وصل می‌شود.

## لوکال
```bash
cd backend
docker compose -f docker-compose.n8n.yml --env-file .env.n8n up -d   # http://localhost:5679
```
- توکن در `backend/.env.local` (`DIRECTORY_SYNC_TOKEN`) و کلید رمزنگاری n8n در `backend/.env.n8n` است (هر دو git-ignore).
- آدرس Django از داخل کانتینر: `http://host.docker.internal:8000` (باید در `ALLOWED_HOSTS` باشد).
- توکن را در n8n به‌صورت Credential (Header Auth) بگذارید، نه داخل نود.

## workflow آماده
فایل `backend/n8n/directory-sync.workflow.json` (بدون هیچ راز): هر یکشنبه ۰۳:۰۰ (و دکمه Run manually) → دریافت و نرمال‌سازی هر منبع → ارسال تکه‌های ۴۰۰تایی به Django با Credential → گزارش (اگر منبع یا Django خطا بدهد اجرا قرمز می‌شود).
- منابع و `DRY_RUN` در بالای نود **Fetch and normalize** تنظیم می‌شوند. منبع فعلی: **دانا** (API عمومی صفحه `appsef.dana-insurance.ir/subportal`، انواع «مطب»، «دندان‌پزشکی» و «کلینیک/درمانگاه»، ۲۵ ردیف در هر صفحه). متن‌ها یکدست می‌شوند (ي/ك و بدون همزه). اجرای لوکال: ۲۹۶۲ لیست ساخته شد، بدون خطا.
- هر بیمه دیگر = یک بلوک جدید در `SOURCES` (آدرس API + نگاشت نوع مرکز به دسته‌بندی). `DRY_RUN` الان `false` است؛ برای فقط گزارش `true` کنید.
- اجرای دوباره امن است: ردیف‌های تکراری رد می‌شوند و فقط بیمه‌های جاافتاده به لیست‌های ادعا نشده اضافه می‌شود.
- وارد کردن: `docker cp` فایل‌ها به کانتینر و `n8n import:credentials` / `n8n import:workflow`؛ بعد فایل‌های موقت را با `docker exec -u 0` پاک کنید. اجرای تستی: `docker exec -e N8N_RUNNERS_BROKER_PORT=5680 <container> n8n execute --id=<id>`.
- **مهم:** سایت بیمه‌ها API یا فایل عمومی ندارند (جستجو و بررسی این روش را تایید نکرد). برای هر بیمه یا باید آدرس JSON/CSV واقعی پیدا شود، یا یک نود پارس HTML برایش نوشت (و شرایط استفاده سایت بررسی شود).

## بدنه درخواست
```json
{"source": "n8n-dana", "dry_run": true,
 "rows": [{"title": "...", "city": "...", "phone": "...", "insurances": "بیمه دانا، بیمه آسیا"}]}
```
حداکثر ۵۰۰ ردیف در هر فراخوانی. اول با `dry_run: true` بررسی کنید.

## سرور
- `DIRECTORY_SYNC_TOKEN` را در `.env` سرور بگذارید و `web:8000` (یا نام سرویس) را به `ALLOWED_HOSTS` اضافه کنید.
- پورت n8n فقط روی `127.0.0.1` بماند؛ ویرایشگر را با SSH tunnel باز کنید.
- بعد از تایید نهایی روی لوکال، nginx را reload کنید.
