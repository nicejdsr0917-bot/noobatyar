# ops، دیپلوی و CI: سند فنی

> منبع: `.github/workflows/`، `scripts/`، `backend/docker-compose*.yml`، `backend/nginx/nginx.conf`، `backend/Dockerfile`، `backend/deploy.py`. سند بیزینسی: [BUSINESS.md](BUSINESS.md)
> هیچ رمز، کلید keystore یا توکنی در این سند نیست و نباید بیاید. رمز root سرور در تاریخچه git مانده؛ آن را هرگز کپی نکن.

## ۱. زیرساخت production
- یک سرور در ایران؛ همه سرویس‌ها با `backend/docker-compose.yml`. دامنه‌ها: `api.noobatyar.ir` (Django)، `app.noobatyar.ir` (وب مشتری + مسیرهای Django)، `panel.noobatyar.ir` (پنل wasm کارفرما)، `noobatyar.ir` (سایت وردپرس جدا، محل دانلود APK).

| سرویس | image / فرمان | نکته |
|---|---|---|
| `db` | `postgres:15` | volume `postgres_data` |
| `pgbouncer` | `edoburu/pgbouncer` | Django به `pgbouncer:6432` وصل است، `DB_CONN_MAX_AGE=0` |
| `redis` | `redis:7-alpine` | `--save 60 1 --maxmemory 256mb --maxmemory-policy volatile-lru` |
| `web` | build `backend/Dockerfile`؛ `gunicorn core.asgi:application -k uvicorn.workers.UvicornWorker --workers ${GUNICORN_WORKERS:-4} --timeout 60 --graceful-timeout 30 --max-requests 2000 --max-requests-jitter 200 --keep-alive 5 --worker-tmp-dir /dev/shm` | migrate را فقط web می‌زند (`entrypoint.sh`)؛ کلید FCM از `/etc/nobatyar/fcm.json` read-only |
| `worker` | `python manage.py run_outbox --lane default` | `SKIP_MIGRATE=1`؛ بدون آن پیامک و پوش ارسال نمی‌شوند |
| `worker_bulk` | `run_outbox --lane bulk` | کمپین‌ها |
| `frontend` | build `../front_client` با `NEXT_PUBLIC_API_URL=https://api.noobatyar.ir` | تغییر آدرس = rebuild |
| `nginx` | `nginx:1.25` روی ۸۰ و ۴۴۳ | گواهی از certbot میزبان (`/etc/letsencrypt`)، `./panel` برای پنل، static و media |

### ۱.۱ nginx (`backend/nginx/nginx.conf`)
- `limit_req_zone`: `auth` ۱۲۰ در دقیقه (مسیرهای `api/auth/` به‌جز `token/refresh/` و `api/client/auth/`)، `api` ۳۰ در ثانیه.
- XFF با `$remote_addr` بازنویسی می‌شود (`NUM_PROXIES=1`). اگر CDN جلو بیاید `set_real_ip_from` لازم است.
- بلوک پیش‌فرض و `app.noobatyar.ir`: `^/(api|admin|plans|my-subscription|payment-result)` به Django، بقیه به frontend؛ `^~ /api/internal/` → 404.
- بلوک `api.noobatyar.ir`: همه چیز به Django. **این بلوک `/api/internal/` را نمی‌بندد**؛ فقط توکن از endpoint محافظت می‌کند. باید مثل دو بلوک دیگر 404 شود.
- بلوک `panel.noobatyar.ir`: فایل‌های ثابت wasm و `manifest.webmanifest`.
- تغییر `ADMIN_URL` یعنی افزودن پیشوند تازه به regex هر دو بلوک.

## ۲. دیپلوی بک‌اند
1. dump دیتابیس قبل از هر دیپلوی (`pre-deploy-YYYYMMDD-HHMM.sql.gz`).
2. انتقال کد با git bundle: `git bundle create f <server-sha>..develop` (بازه SHA..SHA کار نمی‌کند).
3. `docker compose up -d --no-deps --build web worker worker_bulk` (و `frontend` اگر سایت عوض شده).
4. **حتما** `docker compose exec -T nginx nginx -s reload`؛ وگرنه nginx به IP قدیمی کانتینر وصل می‌ماند و همه API خطای 502 می‌دهد.
5. فرمان‌های بعد از دیپلوی در صورت نیاز (`seed_insurances`، `seed_insurance_logos`، migration).
- اجرای فرمان روی سرور: `DEPLOY_HOST=<host> bash scripts/remote.sh '<cmd>'` (کلید SSH ترجیح دارد؛ وگرنه `DEPLOY_PASSWORD` از env با `read -rs`). خواندن prod نیاز به اجازه کاربر دارد.
- قبل از بیلد آدرس به‌روزرسانی Shecan را curl کن (مخزن‌ها از ایران).
- لاگ nginx به stdout لینک است؛ `tail` روی access.log گیر می‌کند.
- مستندهای قدیمی‌تر: `backend/DEPLOYMENT.md`، `DEPLOY_UPDATE_BACKEND.md`، `DEPLOY_TLS_DOMAIN.md`، `docs/ENVIRONMENTS.md`، `docs/DEPLOY_SCALE_HARDENING.md`.
- اسکریپت‌های قدیمی در `backend/`: `bootstrap_server.sh`، `fresh_server_setup.sh`، `server_hardening.sh`، `deploy_tls.sh`، `update_server.sh`، `post_reinstall_restore.sh`، `pre_reinstall_inventory.sh`، `deploy.py`.

### ۲.۱ دیپلوی معلق: مراکز
روی prod هنوز نیست: migration `business/0043`، `DIRECTORY_SYNC_TOKEN` و `web` در `ALLOWED_HOSTS`، reload nginx، n8n روی شبکه docker سرور، `seed_insurances`. prod روی `develop` در `d1c91f9` است (طبق hand-off).

## ۳. پنل وب کارفرما
`scripts/deploy-panel.sh`: `:composeApp:wasmJsBrowserDistribution` → خروجی `mobile_owner/composeApp/build/dist/wasmJs/productionExecutable` بدون `*.map` → `backend/panel` روی سرور. اسکریپت مسیر java اندروید استودیو مک را فرض می‌کند.

## ۴. cron سرور
| زمان | فرمان |
|---|---|
| هر ۵ دقیقه | `docker compose exec -T web python manage.py send_appointment_reminders` |
| هر ساعت (`0 * * * *`) | `run_subscription_lifecycle` (یادآوری تمدید، انقضا با مهلت، قفل کسب‌وکار مازاد، آزادسازی hold) |
| هر شب ۰۳:۳۰ | `compute_rank_scores` (بدون آن ترتیب لیست عمومی «جدیدترین» است) |
| دستی/دوره‌ای | `send_promo_campaigns` (کمپین‌ها از worker_bulk هم می‌روند) |
| هر شب ۰۰:۰۰ | `/root/backup-to-telegram.sh`: dump Postgres + media → آرشیو رمزدار → تلگرام؛ رازها فقط در `/root/.backup_env` |

## ۵. CI (`.github/workflows/`)
> سهمیه GitHub Actions این ماه تمام شده؛ تا ماه بعد workflowها اجرا نمی‌شوند.

| workflow | trigger | کار |
|---|---|---|
| `ci.yml` | هر push و PR | **backend**: Python 3.11، سرویس `postgres:16`، `makemigrations --check --dry-run`، تست ترتیبی روی Postgres (موازی تست‌های race را می‌شکند). **web**: Node 20، `npm ci`، `tsc --noEmit`، lint؛ job جدای e2e با Playwright Chromium. **kmp**: JDK 17 temurin، یک variant برای هر اپ: `:composeApp:testLocalSiteDebugUnitTest` |
| `android-release.yml` | تگ `v*.*.*` | اپ کارفرما: نسخه از تگ، decode keystore، `assembleProdSiteRelease`، `assembleProdBazaarRelease`، `assembleProdMyketRelease`، حذف keystore، نام‌گذاری splitها، استخراج یادداشت فارسی از `CHANGELOG.md`، بررسی APK، آپلود روی noobatyar.ir (FTP)، GitHub Release، ارسال به تلگرام، sync نسخه در `mobile_owner/iosApp/Configuration/Config.xcconfig` روی develop |
| `android-release-client.yml` | تگ `client-v*.*.*` | اپ مشتری: `assembleProdSiteRelease`، `assembleProdStoreRelease`؛ همان مراحل؛ sync نسخه در xcconfig مشتری |

secretها (فقط نام): `ANDROID_KEYSTORE_BASE`، `ANDROID_KEYSTORE_PASSWORD`، `ANDROID_KEY_ALIAS`، `ANDROID_KEY_PASSWORD`، `MYKET_IAB_PUBLIC_KEY` (فقط اونر)، `TELEGRAM_BOT_TOKEN`، `TELEGRAM_CHAT_ID`، `WP_FTP_HOST`، `WP_FTP_PORT`، `WP_FTP_USERNAME`، `WP_FTP_PASSWORD`.

قاعده‌ها: دو CI ریلیز را پشت سر هم بزن (هر دو بدون retry روی develop push می‌کنند). قبل از push معمولا CI یک commit «sync iOS version» گذاشته؛ `git rebase --autostash origin/develop`. versionCode = `CURRENT_PROJECT_VERSION` + ۱.

## ۶. n8n (لوکال)
- `backend/docker-compose.n8n.yml` (پروژه `noobatyar-n8n`)، env در `backend/.env.n8n` (git-ignore، `N8N_ENCRYPTION_KEY`)، پورت `127.0.0.1:${N8N_HOST_PORT:-5679}`، `mem_limit 700m`، `NODES_EXCLUDE` برای executeCommand و ssh.
- اجرا: `docker compose -f backend/docker-compose.n8n.yml --env-file backend/.env.n8n up -d` → `http://localhost:5679`. پورت 5678 مال پروژه دیگری است.
- workflow: `backend/n8n/directory-sync.workflow.json`. جزییات: [centers](../business/centers-and-n8n.md) و `docs/N8N_DIRECTORY_SYNC.md`.

## ۷. محیط لوکال
- ویندوز ۱۱ با Git Bash و PowerShell؛ پایتون venv: `backend/.venv/Scripts/python.exe` (3.12). روی مک `backend/.venv/bin/python`.
- پیکربندی‌های preview در `.claude/launch.json` (کامیت نشود).
- اپ‌ها: `adb reverse tcp:8000 tcp:8000` و `tcp:3000`. اپ کارفرما حتما با `-PlocalBaseUrl=http://127.0.0.1:8000`.
- ادمین لوکال: `http://localhost:8000/admin/`؛ حساب تست در `TEST_ACCOUNTS.local.md` (بیرون از git).
- pip: اگر mirror خطای SSL داد `--index-url https://pypi.org/simple`.

## ۸. فایل‌هایی که هرگز کامیت نمی‌شوند
`.claude/launch.json`، `marketing/`، `mobile_client/.gradle/` lockها، `front_client/package-lock.json`، `mobile_owner/composeApp/build.gradle.kts` (تغییرهای محلی)، فایل‌های `.env*` به‌جز `.env.example`، `*.exp` ریشه، فایل‌های untracked کاربر. فایل‌ها تک‌تک add شوند، نه `git add -A`.

## ۹. ریموت‌ها
- `origin`: `github.com/javiddeveloper/Noobatyar-automation`
- `newrepo`: `github.com/nicejdsr0917-bot/noobatyar` (ریموت تازه؛ `develop` و `main` آن الان هم‌سطح لوکال است).
