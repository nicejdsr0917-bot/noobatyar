---
updated: 2026-10-30
tags: [gf]
---
# backend: core، bale، versions

**هدف:** settings و urls، پنل ادمین سفارشی، بخش‌بندی مخاطب و کمپین پوش، پیام‌های ادمین، ربات بله، نسخه/changelog اپ.

## فایل‌ها
- `backend/core/settings.py` — `.env.local` را با `load_dotenv` می‌خواند؛ `DEBUG=False` بدون `SECRET_KEY` خطا؛ SQLite محلی / Postgres prod؛ `TIME_ZONE='Asia/Tehran'`
- `backend/core/urls.py` — ریشه همه prefixها (جدول کامل در `.claude/PROJECT_GRAPH.md`)
- `backend/core/admin_site.py`، `dashboard/` — `NobatyarAdminSite` (Unfold) — `backend/docs/ADMIN_PANEL.md`
- `backend/core/campaigns.py` (`MarketingPushLog`)، `messaging.py` (`AdminMessageLog`)، `AudienceSegment`، `ExpirySmsLog`
- `backend/bale/` — `BaleSettings`، webhook `api/bale/webhook/<secret>/`، commands `bale_setup`/`bale_status`
- `backend/versions/` — `AppVersion`، `VersionChangelog`؛ `api/version/{check,changelog}/`

## Cron (management commands، crontab سرور)
`send_appointment_reminders`، `run_subscription_lifecycle`، `send_promo_campaigns`، `myket_prices` — `docs/NOTIFICATIONS.md`

## وابستگی‌ها
همه ماژول‌های backend؛ [[modules/ops-deploy]].

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- `core/outbox/`: `OutboxMessage` + `enqueue` (داخل تراکنش، `dedupe_key`) + `manage.py run_outbox --lane default|bulk|all`؛ claim تک‌پیام با SKIP LOCKED، heartbeat، `done_steps` برای جلوگیری از ارسال/کسر دوباره. جای همه threadها و ارسال‌های گروهی ادمین.
- `admin_site.py` فقط جدول route است؛ viewها در `core/admin_views/`. `core/messaging/` پکیج شد (re-export).
- settings: `NUM_PROXIES`، `DISABLE_SERVER_SIDE_CURSORS` (pgbouncer)، timeout سوکت Redis، LOGGING به stdout، merchant پیش‌فرض DEBUG = `zibal`.

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `common/` (برنچ mobile-client): responses، pagination (`page_dict`، `page_params` → 400)، timeutils (`utc_date_tehran_midnight` در برابر `local_date_midnight`)، params. shimهای قدیمی در `api/` تا R15 می‌مانند.
- `core/observability.py`: لاگ JSON، `X-Request-ID` (ContextVar)، slow query با `SLOW_QUERY_LOG`. `core/health.py`: `/api/healthz` و `/api/readyz`. `core/checks.py`: هشدار `core.W001` اگر `ZIBAL_MERCHANT_ID` خالی باشد.
- `core/tests_tenant_isolation.py`: ۲۷ تست جداسازی داده. CI: `.github/workflows/ci.yml` (بک‌اند روی Postgres ترتیبی، وب lint+tsc+e2e، تست KMP دو اپ).
- `bale/handlers.py`: هندلرهای bot. `versions.AppVersion.app` (OWNER/CLIENT) با پیش‌فرض OWNER؛ `version/check?app=`.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰ — ادمین
- **منو:** `UNFOLD.SIDEBAR.navigation` در `core/settings.py` شش بخش دارد: بررسی، کاربران، رشد، پیام، مالی، سیستم. «بررسی» و «کاربران» `collapsible: False` هستند. عنوان آیتم‌ها حداکثر دو کلمه است و `core/tests_admin_menu.py` این را چک می‌کند. آیتم‌ها با `_item` و `_optional_item` ساخته می‌شوند و permission دارند.
- **کش CSS:** `_versioned_static(path)` به آدرس `admin_custom/css/unfold.css` پسوند `?v=<mtime>` اضافه می‌کند. بدون این، مرورگرها CSS قدیمی را از کش نشان می‌دهند.
- **موبایل:** در `assets/admin_custom/css/unfold.css`، کارت‌های داشبورد (`.nb-card`، `.nb-panel`، `.nb-alert`) `min-width: 0` دارند و گرید موبایل `minmax(0, 1fr)` است، تا جدول‌های no-wrap صفحه را پهن نکنند. آیکون‌های منوی unfold با `mr-3` فاصله می‌گیرند که در RTL سمت اشتباه است؛ قانون `[dir="rtl"] .material-symbols-outlined.mr-3` آن را درست می‌کند.
- پنل «نوا» (`staffpanel`) یک بار ساخته و revert شد (`60442df`). در کد نیست.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰ (عصر) — ادمین روی موبایل
- بخش ۴ در `assets/admin_custom/css/unfold.css`: زیر ۱۰۲۴px کارت هر ردیف changelist (`#result_list tr.data-row`) گرید دوستونه است و برچسب بالای مقدار؛ ستون لینک (th) یک خط کامل و checkbox در گوشه. ویجت raw-id می‌شکند، `.readonly`/`code` با `overflow-wrap:anywhere`. زیر ۷۶۸px سلول‌های inline جدولی ستونی‌اند.
- audit: هر ۶۲ صفحه ادمین در ۳۷۵px بدون اسکرول افقی (با Playwright از `wt-p2f-web/front_client/node_modules/playwright`).
