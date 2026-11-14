# مستند کامل نوبت‌یار

> نوبت‌یار سامانه نوبت‌دهی آنلاین برای کسب‌وکارهای ایرانی است: صاحب کسب‌وکار با اپ کارفرما نوبت، مشتری، بیعانه و بازاریابی‌اش را مدیریت می‌کند و مشتری از وب یا اپ مشتری کسب‌وکار را پیدا می‌کند و نوبت می‌گیرد. درآمد از اشتراک و بسته‌های افزودنی کارفرماست.
>
> این سندها از روی خود کد روی `develop` (`df608ec1`، ۲۰۲۶-۱۱-۱۴) نوشته و با کد تطبیق داده شده‌اند. هر جا سند قدیمی‌تر (`backend/PLANS.md`، `docs/CI.md`، `knowledge/`) با کد فرق داشت، کد ملاک بوده و اختلاف در همین سندها ذکر شده.

## نقشه کلی
```
اپ کارفرما (اندروید: سایت/مایکت/بازار) + پنل وب wasm ──JWT──┐
وب مشتری Next.js (app.noobatyar.ir) ──────── Visitor token ──┼──> nginx ──> Django/DRF (ASGI) ──> Postgres، Redis
اپ مشتری اندروید (سایت/فروشگاه) ─────────── Visitor token ──┘          ├─ worker outbox (پیامک، پوش، کمپین)
                                                                       ├─ زیبال، مایکت، ملی‌پیامک، FCM، بله
n8n ─────────────── توکن داخلی ── فهرست مراکز بیمه‌ها ──────────────────┘
```

## سندهای پلتفرم

| پلتفرم | کد | سند فنی | سند بیزینسی | پوشش |
|---|---|---|---|---|
| بک‌اند Django | `backend/` | [backend/TECHNICAL.md](backend/TECHNICAL.md) | [backend/BUSINESS.md](backend/BUSINESS.md) | ۱۴ app، ۶۴ مدل (با ۹ proxy)، همه migrationها، همه ۱۲۷ route (با alias)، جریان OTP/JWT، رزرو، بیعانه، پرداخت، پوش، outbox، چرخه اشتراک، moderation، ۱۹ management command، ادمین، همه env، تست، مسایل باز |
| اپ کارفرما KMP | `mobile_owner/` | [owner-app/TECHNICAL.md](owner-app/TECHNICAL.md) | [owner-app/BUSINESS.md](owner-app/BUSINESS.md) | sourceSetها، ۲۹ پوشه feature و همه مقصدهای ناوبری، deep linkها، endpointهای مصرفی، FeatureGate، خرید زیبال و مایکت، پوش، wasm، flavorهای env × store، بیلد، تست، ریلیز، قاعده‌های مایکت |
| وب مشتری Next.js | `front_client/` | [client-web/TECHNICAL.md](client-web/TECHNICAL.md) | [client-web/BUSINESS.md](client-web/BUSINESS.md) | همه routeها (۲۱ فایل page/route/sitemap/robots/manifest/not-found)، ۲۱ کامپوننت، توابع `lib/api.ts` و endpointشان، i18n، PWA و پوش، env، e2e، Docker |
| اپ مشتری اندروید | `mobile_client/` | [client-app/TECHNICAL.md](client-app/TECHNICAL.md) | [client-app/BUSINESS.md](client-app/BUSINESS.md) | ۱۶ پوشه feature، endpointها، flavorهای site/store و `isStoreBuild`، بیلد، تست، ریلیز، قاعده تلگرام مایکت |
| ops، دیپلوی، CI | `.github/workflows/`، `scripts/`، `backend/docker-compose*.yml`، `backend/nginx/` | [ops/TECHNICAL.md](ops/TECHNICAL.md) | [ops/BUSINESS.md](ops/BUSINESS.md) | ۸ سرویس docker، nginx، دیپلوی و reload، پنل، cron، بکاپ، سه workflow و نام secretها، n8n، محیط لوکال، فایل‌های ممنوع برای کامیت، ریموت‌ها |

## سندهای کسب‌وکار

| بخش | سند | پوشش |
|---|---|---|
| مدل درآمد | [business/revenue.md](business/revenue.md) | ۵ پلن و ۱۰ قابلیت و ۳ سقف، ۱۰ بسته افزودنی، فرمول مالیات و کارمزد زیبال و مایکت با جدول قیمت، کیف پول تومانی، پاداش مشتری تازه، چرخه اشتراک، اقتصاد واحد |
| صنف‌ها و بیمه‌ها | [business/guilds-and-insurance.md](business/guilds-and-insurance.md) | ۸ صنف و ۶۸ دسته، ۲۲ بیمه (۱۹ تکمیلی، ۳ پایه)، قاعده `takes_insurance`، لوگوها، قاعده همزه |
| مراکز و n8n | [business/centers-and-n8n.md](business/centers-and-n8n.md) | تعریف مرکز و `origin`، قاعده پاسخ API، ادمین مراکز و پیگیری تماس، ورود فایل، claim، endpoint و workflow n8n و منبع دانا، کارهای باز |
| کانال‌های انتشار | [business/channels.md](business/channels.md) | سایت، مایکت، بازار، وب، پنل؛ APK هر کانال؛ ریلیز؛ قاعده‌های مایکت (تلگرام، قیمت، billing، عنوان، SKU)؛ چک‌لیست ارسال |
| بازاریابی | [business/marketing.md](business/marketing.md) | لید فروش، بخش‌بندی، کمپین پوش و پیامک، بنر، پیامک تبلیغاتی و استوری کارفرما، تخفیف، نظر، سایت وردپرس، برند و ویدیو |

## قاعده‌های همیشگی پروژه
- کاربر واقعی داریم؛ فقط تغییر افزایشی.
- متن فارسی بدون همزه (U+0621، U+0623 تا U+0626، U+0654، U+06C0 ممنوع؛ «آ» مجاز).
- اپ مشتری فقط اندروید.
- رازها (رمز سرور، keystore، توکن‌ها) هرگز در git یا سند.
- push فقط با دستور کاربر.

## سندهای مرتبط قدیمی‌تر
`knowledge/INDEX.md` و `knowledge/modules/*.md` (نقشه ماژول‌ها)، `hand-off/CURRENT.md` (وضعیت جاری)، `backend/PLANS.md`، `backend/DEPLOYMENT.md`، `backend/docs/ADMIN_PANEL.md`، `docs/*.md` (ENVIRONMENTS، NOTIFICATIONS، FCM_SETUP، N8N_DIRECTORY_SYNC، CI، KMP_WEB_GUIDE، OWNER_WEB_PLAN، BOOKING_PARITY، LOAD_TEST، audit، api)، `CHANGELOG.md`.

## به‌روز نگه داشتن
هر تغییر در route، مدل، flavor، workflow یا قیمت باید در سند همان پلتفرم هم بیاید. فهرست route و مدل را می‌شود دوباره از Django گرفت (`get_resolver()`، `apps.get_models()`) و با جدول‌ها مقایسه کرد.
