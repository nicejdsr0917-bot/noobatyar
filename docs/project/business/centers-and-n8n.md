# مراکز و همگام‌سازی فهرست با n8n

> منبع: `backend/business/models/centers.py`، `admin/centers.py`، `public_policy.py` (`apply_center_rules`)، `directory_import.py`، `directory_sync.py`، `internal_views.py`، `claims.py`، `claim_views.py`، `backend/n8n/directory-sync.workflow.json`، `backend/docker-compose.n8n.yml`. مستند قبلی: `docs/N8N_DIRECTORY_SYNC.md`.
> وضعیت: کامیت `8a6940ae`، روی prod دیپلوی نشده.

## ۱. ایده کسب‌وکاری
مشتری در اپ دنبال «پزشک نزدیک من با بیمه دانا» است. اگر فقط کسب‌وکارهای ثبت‌نام‌کرده دیده شوند، نقشه خالی است. پس نوبت‌یار فهرست مراکز درمانی طرف قرارداد بیمه‌ها را وارد می‌کند:
- مشتری مرکز را پیدا می‌کند، تماس می‌گیرد، و می‌تواند «نوبت آنلاین بخواهد».
- این درخواست‌ها (`online_booking_requests_count`) و فهرست تماس، ابزار فروش تیم برای تبدیل مرکز به کسب‌وکار پولی‌اند.
- صاحب مرکز با «صاحب این مرکز هستید؟» آن را ادعا می‌کند.

## ۲. تعریف‌ها
| مفهوم | تعریف در کد |
|---|---|
| مرکز | `Business` با `is_claimed=False`، صاحب = کاربر سیستمی، رزرو خاموش، تایید خودکار |
| کسب‌وکار | `is_claimed=True` |
| `origin` | SELF (ساخت اونر از اپ)، ADMIN و DIRECTORY (فرم ادمین)، DIRECTORY (ورود فایل یا sync)، CONVERTED (claim شده)؛ claimedهای قدیمی `''` (۱۰۷ مورد باید در ادمین مشخص شوند) |
| `external_source` | منبع ورود، مثلا `n8n-dana` |

## ۳. قاعده‌های پاسخ API برای مرکز
`apply_center_rules` روی همه payloadهای عمومی و کلاینت: `is_noobatyar`، `kind`، `booking_available` همیشه هست؛ برای مرکز رزرو، بیعانه، تخفیف، پرداخت و اعلان خاموش است. مرکز از قانون پنهان کردن تماس به‌خاطر نبود اشتراک مستثناست (صاحبش کاربر سیستمی است). در اپ مشتری: بدون تیک، کارت «نوبت آنلاین ندارد»، دکمه درخواست، «صاحب این مرکز هستید؟».

## ۴. ادمین
- منوی «مراکز» (`CenterListing`، `CenterAdmin`): فقط مراکز. لیست `BusinessAdmin` فقط claimed است (فقط changelist محدود شده؛ صفحه ویرایش مرکز از هر دو باز می‌شود).
- پیگیری تماس `CenterOutreach`: وضعیت NEW، CALLED، INTERESTED، CONVERTED، REJECTED؛ یادداشت؛ `contacted_at`؛ اکشن علامت‌گذاری گروهی؛ خروجی CSV لیست تماس (فقط دارای تلفن). claim موفق وضعیت را CONVERTED می‌کند.
- ورود از فایل: `/admin/business/business/import-file/?guild=KEY` (اکسل یا چسباندن، پیش‌نمایش، توکن تایید، فایل موقت ۲۴ ساعت در `MEDIA_ROOT/tmp_imports`) و قالب نمونه هر صنف. هسته مشترک: `directory_import.run_import(rows, source, dry_run, require_city, default_category)` با گزارش تکراری، نامعتبر با شماره ردیف و هشدار.
- فرم افزودن صنف (`DirectoryFormMixin`): پیش‌فرض‌ها، `city_code` از نام شهر، تایید خودکار.
- جدا از این، اپ `marketing` (`MarketingLead`، `LeadNote`) برای پیگیری فروش کسب‌وکارهای ثبت‌شده است ([marketing](marketing.md)).

## ۵. ادعای مالکیت (claim)
`api/business/claimable/` (جستجو) → `<id>/claim/` (OTP به شماره مرکز، throttle ۱۰ در ساعت؛ روش MANUAL برای بررسی ادمین) → `<id>/claim/verify/` → `claims.apply_claim`: مرکز به کاربر منتقل، `is_claimed=True`، `origin=CONVERTED`، outreach = CONVERTED. صفحه `ClaimBusinessScreen` در اپ کارفرما.

## ۶. همگام‌سازی با n8n
- endpoint: `POST /api/internal/directory-sync/` با `Authorization: Bearer <DIRECTORY_SYNC_TOKEN>`؛ توکن خالی = 404؛ حداکثر ۵۰۰ ردیف؛ بدنه `{"source": "...", "dry_run": bool, "rows": [{"title", "city", "phone", "insurances", ...}]}`.
- `sync_listings` = `run_import` برای ردیف تازه + افزودن بیمه‌های جاافتاده به مراکز ادعا نشده. **هیچ چیز حذف یا بازنویسی نمی‌شود و کسب‌وکار claimed دست نمی‌خورد.** گزارش: `total`، `created`، `duplicates`، `invalid`، `linked`، `claimed_skipped`.
- workflow «Noobatyar directory sync»: هر یکشنبه ۰۳:۰۰ و اجرای دستی → Fetch and normalize (فهرست `SOURCES`، `DRY_RUN`، `cleanTitle` که پسوند «آنلاین» دانا را حذف می‌کند، یکدست کردن ي/ك و همزه) → تکه‌های ۴۰۰تایی → Django → گزارش؛ خطای منبع یا Django اجرا را قرمز می‌کند.
- منبع فعلی **دانا**: `appsef.dana-insurance.ir/SubPortal/HealthCenter/GetAllMedicalCenters?centerKindId=…&pageNo=…` (۲۵ ردیف در صفحه؛ مطب، دندان‌پزشکی، کلینیک/درمانگاه). لوکال: ۲۹۶۲ مرکز، ۸۳۴ با مختصات؛ مراکز بدون مختصات بی‌لوکیشن می‌مانند (تصمیم کاربر).
- امنیت: n8n بدون رمز دیتابیس و volume مشترک؛ فقط این endpoint؛ nodeهای executeCommand و ssh غیرفعال؛ پورت فقط `127.0.0.1`. nginx مسیر را در بلوک‌های `app.` و پیش‌فرض 404 می‌کند، ولی بلوک `api.noobatyar.ir` نه (مساله باز).
- محدودیت‌ها: سایت بیمه‌ها فقط از داخل ایران باز می‌شوند؛ جعل User-Agent یا دور زدن bot check ممنوع؛ اگر محدودیت نرخ دیده شد ۲۰۰ تا ۳۰۰ میلی‌ثانیه فاصله.

## ۷. کارهای باز
1. اجرای دوباره روی دانا و تایید `created=0` (آخرین تلاش خطای TLS داد).
2. ساخت حساب owner در n8n توسط کاربر و روشن کردن workflow.
3. بیمه‌های بعدی: البرز، کوثر، دی، آسیا، ایران (اول API پشت صفحه را با مرورگر پیدا کن).
4. مشخص کردن `origin` برای ۱۰۷ کسب‌وکار قدیمی.
5. دیپلوی prod با تایید کاربر (فهرست در [ops TECHNICAL §۲.۱](../ops/TECHNICAL.md)).
