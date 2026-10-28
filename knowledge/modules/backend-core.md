---
updated: 2026-10-28
tags: [gf]
---
# backend: core، bale، versions

**هدف:** settings و urls، پنل ادمین سفارشی، بخش‌بندی مخاطب و کمپین پوش، پیام‌های ادمین، ربات بله، نسخه/changelog اپ.

## فایل‌ها
- `backend/core/settings.py` — `.env.local` را با `load_dotenv` می‌خواند؛ `DEBUG=False` بدون `SECRET_KEY` خطا؛ SQLite محلی / Postgres prod؛ `TIME_ZONE='Asia/Tehran'`
- `backend/core/urls.py` — ریشهٔ همهٔ prefixها (جدول کامل در `.claude/PROJECT_GRAPH.md`)
- `backend/core/admin_site.py`، `dashboard/` — `NobatyarAdminSite` (Unfold) — `backend/docs/ADMIN_PANEL.md`
- `backend/core/campaigns.py` (`MarketingPushLog`)، `messaging.py` (`AdminMessageLog`)، `AudienceSegment`، `ExpirySmsLog`
- `backend/bale/` — `BaleSettings`، webhook `api/bale/webhook/<secret>/`، commands `bale_setup`/`bale_status`
- `backend/versions/` — `AppVersion`، `VersionChangelog`؛ `api/version/{check,changelog}/`

## Cron (management commands، crontab سرور)
`send_appointment_reminders`، `run_subscription_lifecycle`، `send_promo_campaigns`، `myket_prices` — `docs/NOTIFICATIONS.md`

## وابستگی‌ها
همهٔ ماژول‌های backend؛ [[modules/ops-deploy]].
