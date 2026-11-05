---
updated: 2026-11-05 22:42
tags: [gf]
---
# Ops: سرور، دیپلوی، CI، محیط محلی

## Production
- سرور ایران؛ دیپلوی با rebuild Docker: `backend/deploy.py`، `scripts/remote.sh`، `backend/docker-compose.yml`، `backend/nginx/`
- پنل وب owner: `scripts/deploy-panel.sh` (به `/Applications/Android Studio.app/.../java` وابسته — فقط مک)
- بکاپ: `scripts/backup-to-telegram.sh`
- مستندات: `backend/DEPLOYMENT.md`، `DEPLOY_*.md` در ریشه، `docs/ENVIRONMENTS.md`، `docs/SERVER_DISK_CLEANUP.md`

## ریلیز اندروید
- `.github/workflows/android-release.yml` با push تگ `vX.Y.Z`؛ بلوک `<!-- fa:start -->` در `CHANGELOG.md` الزامی
- versionCode = `CURRENT_PROJECT_VERSION` در `mobile_owner/iosApp/Configuration/Config.xcconfig` روی develop + ۱ (CI بعد از ریلیز commit می‌کند)

## محیط محلی (مک)
- پایتون: `backend/.venv/bin/python` (3.11)؛ `python3` سیستم 3.9 است
- pip mirror runflare خطای SSL دارد → `--index-url https://pypi.org/simple`
- AVD: `Pixel_6a`، SDK در `~/Library/Android/sdk`
- zsh: `head` → ابزار Perl؛ از `/usr/bin/head` استفاده کن. `timeout` نصب نیست.

## اجرای محلی همهٔ پروژه‌ها
- بک‌اند یکپارچه: preview `backend_mobile_client_mac` (اجرا از worktree `../Noobatyar-mobile-client/backend` با venv repo اصلی؛ `.env.local` آن‌جا کپی شده، `OTP_DEV_CODE`/`SMS_DEV_MODE` فعال).
- `CLIENT_WEB_URL=http://127.0.0.1:3000` در `.env.local` (نه `10.0.2.2`، که فقط داخل امولاتور کار می‌کند و بازگشت از درگاه زیبال در مرورگر مک را گیر می‌انداخت).
- ادمین: `http://localhost:8000/admin/` (بعد از ورود به `/accounts/profile/` می‌رود که ۴۰۴ است).
- build پنل وب اونر اگر با «Failed to create MD5 hash … ` 2.xml`» شکست خورد: فایل‌های تکراری « 2» سینک iCloud در `mobile_owner/build/` → پوشهٔ build را پاک کن.

## هشدارها
- `.claude/launch.json` پیکربندی‌های ویندوزی (`cmd /c`، `.bat`) دارد؛ روی مک از `_mac`ها استفاده کن.
- در ریشه ~۲۳ فایل `.exp` با **رمز سرور به‌صورت متن ساده** (gitignore‌شده، IP قدیمی) + zip/tar دیپلوی موقت؛ `backend/backend_deploy.zip` داخل git ثبت شده. چند `db.sqlite3.bak-*` در `backend/`.
- اسکریپت‌های `backend/*.sh` و `*.exp` فرض bash/expect دارند.

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- compose: سرویس‌های `worker` (`--lane default`) و `worker_bulk` (`--lane bulk`) با `SKIP_MIGRATE=1` (فقط web migrate می‌زند). بدون آن‌ها پیامک/کمپین ارسال نمی‌شود.
- nginx: XFF با `$remote_addr` بازنویسی؛ `limit_req` auth 120r/m (به‌جز `token/refresh`) و api 30r/s؛ keepalive، gzip، timeout. اگر CDN جلوست `set_real_ip_from` لازم است.
- gunicorn: `--max-requests 2000 --max-requests-jitter 200 --keep-alive 5 --worker-tmp-dir /dev/shm`. `backend/.dockerignore` اضافه شد؛ `check_db.py` و `backend_deploy.zip` حذف شدند.
- چک‌لیست: `docs/DEPLOY_SCALE_HARDENING.md` (در worktree). تست روی Postgres: Docker Desktop + `postgres:16-alpine` روی پورت 55432.
- وب worktree: preview `front_client_mobile_mac` روی :3010 (3000 دست کانتینر Docker کاربر است).

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰
- workflow جدای کلاینت: `android-release-client.yml` با تگ `client-v*.*.*`. دو نسخه می‌سازد: assembleProdSiteRelease (سایت/تلگرام) و assembleProdStoreRelease (یک APK برای مایکت و بازار) و از secretهای اونر استفاده می‌کند. هنوز اجرا نشده.
- محلی:
  - وب اونر با preview `mobile_owner_web_mobile_client_mac` روی :8081 (پورت 8080 دست Docker است).
  - اپ اونر اندروید: `sh ./gradlew :composeApp:assembleLocalSiteDebug -PlocalBaseUrl=http://127.0.0.1:8000`، سپس `adb install` نسخهٔ arm64. اول preview وب را stop کنید.

## دیپلوی‌ها و ریلیزهای 2026-11-04/05
- prod روی `3ce5e67` است. هر دیپلوی با bundle (`git bundle create f <server-sha>..develop`، بازهٔ SHA..SHA کار نمی‌کند) و dump قبلش انجام شد.
- web، worker و worker_bulk را با `--no-deps --build` بساز. frontend هم وقتی سایت تغییر کرده.
- قبل از بیلد آدرس به‌روزرسانی Shecan را curl کن.
- ریلیزها `v1.12.0` و `client-v1.2.0`. دو CI را پشت سر هم بزن، چون هر دو بدون retry xcconfig را روی develop push می‌کنند.
- بیلد release محلی اونر با کش قدیمی خطای MD5 داد؛ `./gradlew :composeApp:clean` حلش کرد.
- لاگ nginx به stdout لینک است و `tail` روی access.log گیر می‌کند.

## دیپلوی 2026-11-05 ساعت 22:30
- bundle از `829ced1` تا `6c30d6a`، با dump `pre-deploy-20261105-2230.sql.gz`.
- rebuild روی `web`، `worker`، `worker_bulk` و `frontend`، بعد `seed_insurance_logos`، بعد `deploy-panel.sh`.
- ⚠️ **بعد از rebuild وب، nginx را reload کن** (`docker compose exec -T nginx nginx -s reload`). nginx به IP قدیمی کانتینر وصل می‌ماند و همهٔ درخواست‌های API خطای 502 می‌گیرند. این بار حدود یک دقیقه قطعی داشتیم.
- قبل از push معمولاً CI یک commit «sync iOS version» روی origin گذاشته است. `git rebase --autostash origin/develop` کافی است.
