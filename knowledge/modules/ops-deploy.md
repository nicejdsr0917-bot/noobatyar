---
updated: 2026-10-28
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

## هشدارها
- `.claude/launch.json` پیکربندی‌های ویندوزی (`cmd /c`، `.bat`) دارد؛ روی مک از `_mac`ها استفاده کن.
- در ریشه ~۲۳ فایل `.exp` با **رمز سرور به‌صورت متن ساده** (gitignore‌شده، IP قدیمی) + zip/tar دیپلوی موقت؛ `backend/backend_deploy.zip` داخل git ثبت شده. چند `db.sqlite3.bak-*` در `backend/`.
- اسکریپت‌های `backend/*.sh` و `*.exp` فرض bash/expect دارند.
