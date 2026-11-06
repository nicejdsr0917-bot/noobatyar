---
updated: 2026-11-05
tags: [gf]
---
# نوبت‌یار (Noobatyar) — نقشه پروژه

سامانه نوبت‌دهی آنلاین برای کسب‌وکارهای ایرانی (سالن زیبایی، پزشک، دندان‌پزشک، مشاور…). **صاحب کسب‌وکار (owner)** با اپ KMP نوبت‌ها، مشتری‌ها، پیامک/پوش تبلیغاتی و اشتراکش را مدیریت می‌کند؛ **مشتری (client/visitor)** از وب Next.js (`app.noobatyar.ir`) نوبت می‌گیرد و در صورت نیاز بیعانه می‌دهد. یک **اپ مشتری موبایل** (کشف کسب‌وکار روی نقشه، رزرو، نظر، فالو صفحه‌ها) هم هست؛ از 2026-11-05 روی develop است (برنچ `mobile-client` ادغام شد). درآمد: اشتراک (`Plan`) و add-on از درگاه زیبال یا مایکت.

مرجع کامل‌تر معماری/routeها: `.claude/PROJECT_GRAPH.md` و گراف کد در `.claude/graphify-out/`.

## Stack
- Backend: Django 4.2 + DRF، JWT، ASGI (gunicorn/uvicorn)، Redis، Docker + nginx؛ SQLite محلی، Postgres در prod
- Owner app: Kotlin Multiplatform + Compose (Android، iOS، wasmJs = پنل وب `panel.noobatyar.ir`)، Koin، Ktor، Room
- Client web: Next.js 16 (app router، **نسخه جدید با تغییرات شکننده** — `front_client/AGENTS.md`)، zustand، axios
- Client mobile: KMP + Compose (`mobile_client/`، روی develop)

## اجرا / build / تست
```bash
# backend (همیشه پایتون venv؛ python3 سیستم 3.9 است و خطا می‌دهد)
cd backend && .venv/bin/python manage.py migrate && .venv/bin/python manage.py runserver 8000
cd backend && .venv/bin/python manage.py test          # ~500 تست، ترتیبی (--parallel با خطای pickle می‌شکند اگر تستی fail شود)
# client web
npm --prefix front_client run dev                      # :3000 ، npx tsc --noEmit -p front_client
# owner app روی AVD (Pixel_6a)
cd mobile_owner && ./gradlew :composeApp:installLocalSiteDebug -PlocalBaseUrl=http://127.0.0.1:8000
adb reverse tcp:8000 tcp:8000 && adb reverse tcp:3000 tcp:3000
```
در پنل Preview: پیکربندی‌های `.claude/launch.json` (`backend_mac`، `front_client`، `backend_mobile_client_mac`، `mobile_owner_web_mac`).

Env (`backend/.env.local`، از `.env.example`): `DEBUG`، `ALLOWED_HOSTS`، `ZIBAL_MERCHANT_ID`، `CLIENT_WEB_URL`، `SITE_URL`، `OTP_DEV_CODE`، `SMS_DEV_MODE`. بدون `DEBUG=True` محلی، settings به‌خاطر نبود `SECRET_KEY` خطا می‌دهد. Front: `front_client/.env.local` → `NEXT_PUBLIC_API_URL`، `NEXT_PUBLIC_SITE_URL`.

**هشدار محیط:** چند پیکربندی `launch.json` مخصوص ویندوز‌اند (`cmd /c`، `.bat`)؛ روی مک از نسخه‌های `_mac` استفاده کن. در zsh این مک `head` به ابزار Perl alias شده → `/usr/bin/head`. جزئیات در [[modules/ops-deploy]].

## ماژول‌ها
- [[modules/backend-api-auth]] — کاربر، OTP، JWT، توکن دستگاه و FCM
- [[modules/backend-business]] — کسب‌وکار، خدمات، moderation، پیامک تبلیغاتی، API عمومی کسب‌وکار، فالو و استوری (`backend/stories`)
- [[modules/backend-appointment]] — نوبت، اسلات خالی، آمار، رزرو و بیعانه مشتری
- [[modules/backend-visitor]] — مشتری‌ها، auth مشتری، لاگ پیامک/پوش
- [[modules/backend-accounting]] — پلن، اشتراک، add-on، پرداخت زیبال/مایکت، entitlements، VAT
- [[modules/backend-core]] — settings/urls، ادمین سفارشی، کمپین‌ها، بله، versions، cron jobها
- [[modules/owner-app]] — اپ KMP صاحب کسب‌وکار (+ پنل وب wasm)
- [[modules/front-client]] — وب Next.js مشتری
- [[modules/mobile-client]] — اپ موبایل مشتری (کاوش، رزرو، پروفایل، فالو)
- [[modules/ops-deploy]] — سرور، دیپلوی، CI ریلیز اندروید، محیط محلی

معماری: [[architecture]] · وضعیت فعلی: [CURRENT](../hand-off/CURRENT.md)
