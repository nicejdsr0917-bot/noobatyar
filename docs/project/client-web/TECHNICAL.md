# وب مشتری (front_client): سند فنی

> منبع: `front_client/` روی `develop`. آدرس prod: `https://app.noobatyar.ir`. سند بیزینسی: [BUSINESS.md](BUSINESS.md)

## ۱. معماری
- Next.js 16.2.10 (app router، `output: 'standalone'`) و React 19.2.4. این نسخه Next تغییرهای شکننده دارد؛ قبل از کد تازه `node_modules/next/dist/docs/` را ببین (`front_client/AGENTS.md`).
- state با zustand؛ تنها لایه API `lib/api.ts` (`apiFetch`، `ApiError`، `UnauthorizedError`، `forceReLogin`).
- توکن مشتری در localStorage (`lib/auth.ts`، `lib/visitorToken.ts`) با هدر `Authorization: Visitor <token>`.
- i18n: `lib/i18n/` (`messages.ts` با `fa` و `en`، تایپ `MessageKey`، `t()`، `getLocale()`، `intlLocale()`؛ سرور: `lib/i18n/server.ts` و `getServerLocale()`). زبان در cookie `lang`؛ در server component `getLocale()` همیشه `fa` است.
- استایل: `app/globals.css` (توکن‌ها، `.themed` برای رنگ کسب‌وکار از `theme_color`، `.biz-*`، `.social-*`). فقط CSS منطقی (`start`/`end`).
- تم: `themeScript` در `app/layout.tsx` همیشه `data-theme` می‌گذارد؛ روشن مگر کاربر تیره ذخیره کرده باشد.
- PWA و پوش: `public/sw.js` (service worker دست‌نویس با Firebase messaging)، `ServiceWorkerRegistration.tsx` (`updateViaCache: 'none'`)، `PushPermissionPrompt.tsx`، `lib/push.ts`، `public/offline.html`، `app/manifest.ts` و manifest جدا برای هر کسب‌وکار.

## ۲. routeها (صفحه‌ها)

| مسیر | فایل | کار |
|---|---|---|
| `/` | `app/page.tsx` + `HomeClient.tsx` | خانه مشتری: نوبت‌های پیش‌رو زیر بنر اپ. عمدا فهرست کسب‌وکارها نیست |
| `/b/[slug]` | `b/[slug]/page.tsx` (SSR، متادیتا، JSON-LD با `aggregateRating`) + `BusinessProfileClient.tsx` | صفحه عمومی کسب‌وکار: hero، فالو، اطلاعیه، شبکه‌ها، آمار (ساعت/بیعانه/پرداخت)، خدمات (۸ تا + «N خدمت دیگر»)، روزهای کاری از شنبه، درباره، نقشه (نشان/بلد)، نمونه‌کار، نظرها، CTA ثابت |
| `/b/[slug]/book` | `book/page.tsx` | انتخاب خدمت، روز، ساعت؛ چیپ بیمه تکمیلی با لوگو و «ندارم» اگر `takes_insurance`؛ OTP اگر وارد نیست |
| `/b/[slug]/checkout/[id]` | `checkout/[id]/page.tsx` | بیعانه: آنلاین، کارت با رسید (`ReceiptUpload`)، نقد؛ `depositUncollectable` |
| `/b/[slug]/manifest.webmanifest` | `route.ts` | manifest با نام و رنگ کسب‌وکار |
| `/b/[slug]` not-found | `not-found.tsx` | |
| `/booking-success` | | پیام موفقیت |
| `/appointments`، `/appointments/[id]` | | نوبت‌های من و جزییات |
| `/auth/login`، `/auth/register` | | OTP و نام؛ `?redirect=` |
| `/profile` | | پروفایل مشتری |
| `/privacy` | | حریم خصوصی |
| `/home/payment-result`، `/home/payment-result-addon` | | بازگشت زیبال برای خرید از اپ اندروید؛ ادامه با `noobatyar://payment/result` (`lib/appReturn.ts`) |
| `/home/payment-result-web`، `/home/payment-result-addon-web` | | بازگشت زیبال برای پنل وب کارفرما؛ برگشت به `NEXT_PUBLIC_PANEL_URL` |
| `/sitemap.xml` | `sitemap.ts` | همه کسب‌وکارهای عمومی؛ revalidate یک ساعت |
| `/robots.txt` | `robots.ts` | |
| `/manifest.webmanifest` | `manifest.ts` | |

کامپوننت‌ها (`app/components/`): `AppBanner`، `AppointmentCard`، `BusinessMap`، `BusinessNotice`، `BusinessUnavailable`، `CategoryArt`، `FollowButton`، `Icon` (Tabler؛ `deposit` = سکه)، `LanguageToggle`، `LockedContact`، `PaymentResult`، `PushPermissionPrompt`، `ReceiptUpload`، `ReviewsSection` (`?review=1` اسکرول به نظرها)، `ServiceWorkerRegistration`، `ShowcaseSection`، `SocialLinks` (آیکن‌ها در `public/icons/social/`)، `SupportLinks`، `TextField`، `ThemeToggle`، `Toolbar`.

کتابخانه‌ها (`lib/`): `api.ts`، `appReturn.ts`، `auth.ts`، `bankLogos.ts` (+ `public/bank-logos/`)، `format.ts`، `maps.ts` (لینک نشان و بلد)، `push.ts`، `site.ts`، `validation.ts`، `visitorToken.ts`.

## ۳. قرارداد API (مصرف‌شده)
| تابع در `lib/api.ts` | endpoint |
|---|---|
| `sendOtp`، `verifyOtp`، `completeRegister` | `api/client/auth/otp/send/`، `otp/verify/`، `register/` |
| `getMe`، `registerDeviceToken` | `api/client/auth/me/`، `devices/register/` |
| `getBusinessByCode`، `getBusinessById`، `listBusinesses`، `listAllPublicBusinesses` | `api/client/business/?search=<code>`، `api/client/business/<id>/` |
| `getAvailableSlots` | `api/client/appointments/<id>/available-slots/` |
| `bookAppointment(..., insuranceSlug)` | `POST api/client/appointments/` با `source: 'WEB'` و `insurance` |
| `getMyAppointments`، `getAppointment` | `api/client/appointments/` |
| `payAppointment`، `payAppointmentWithReceipt` | `…/<id>/pay/` (FormData) |
| `startOnlineDeposit` | `…/<id>/pay/online/` |
| `cancelAppointment` | `…/<id>/cancel/` |
| `getMyActivity` | `api/client/activity/` |
| `getPaymentResult` | `api/accounting/payment-result`، `addons/payment-result` |
| `getShowcase` | `api/client/businesses/<id>/showcase/` |
| `getReviewSummary`، `getReviews`، `getMyReview`، `saveMyReview`، `deleteMyReview`، `reportReview` | `api/client/reviews/...` |
| `getFollowState`، `setFollowing` | `api/client/business/<id>/`، `…/follow/` |

قاعده‌ها: `closedWeekdaysJs()` قرارداد پایتون (دوشنبه=0) را به جاوااسکریپت تبدیل می‌کند؛ `mediaUrl()` مسیر نسبی media را کامل می‌کند؛ `extractCode(slug)` کد یکتا را از slug درمی‌آورد؛ فیلدهای پنهان‌شده سرور `null` هستند و `LockedContact` جایشان را نشان می‌دهد.

## ۴. جریان‌ها
- **رزرو:** `/b/<code>` → «رزرو» → `book` (اسلات‌ها) → ورود OTP اگر لازم (نیت رزرو در `booking_intent` ذخیره می‌شود) → ثبت → اگر پرداخت لازم: `checkout/<id>` → آنلاین (زیبال → `api/client/appointments/deposit-callback/` → بازگشت) یا کارت با رسید یا نقد → `booking-success`.
- **بازگشت درگاه کارفرما:** زیبال به `CLIENT_WEB_URL/home/payment-result*` برمی‌گردد؛ نسخه بدون `-web` اپ اندروید را با scheme باز می‌کند و نسخه `-web` به پنل برمی‌گرداند.
- **پوش مشتری:** service worker و Firebase → `devices/register/` با `VisitorDeviceToken` (پلتفرم WEB) → پوش یادآوری و درخواست نظر با لینک `?review=1`.

## ۵. env و تنظیمات
| متغیر | کجا | معنی |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | build arg در `backend/docker-compose.yml` (`https://api.noobatyar.ir`) و `.env.local` (`http://127.0.0.1:8000`) | آدرس API؛ در bundle پخته می‌شود و تغییرش rebuild می‌خواهد |
| `NEXT_PUBLIC_SITE_URL` | `.env.local` | آدرس سایت برای متادیتا |
| `NEXT_PUBLIC_PANEL_URL` | پیش‌فرض `https://panel.noobatyar.ir/` | بازگشت پرداخت پنل |
| `DEV_API_PROXY_TARGET` | فقط dev | rewrite `/api` و `/media` به بک‌اند |

`next.config`: `output: 'standalone'`، تنظیم `images`، rewrite فقط در توسعه، و هدر no-cache برای `/sw.js`.

## ۶. بیلد، تست و ریلیز
```bash
npm --prefix front_client run dev            # :3000
npx tsc --noEmit -p front_client
npm --prefix front_client run lint
npm --prefix front_client run test:e2e       # Playwright، mock API در e2e/mock-api.mjs روی 4187، Next روی 3187
```
- ۱۳ تست e2e در `e2e/app.spec.ts`. خطاهای tsc مربوط به `e2e/` و `playwright.config.ts` وقتی `@playwright/test` محلی نصب نیست طبیعی‌اند.
- Docker: `node:20-alpine` چندمرحله‌ای، `npm ci` → `npm run build` → `node server.js`. سرویس `frontend` در `backend/docker-compose.yml`.
- ریلیز: با rebuild سرویس `frontend` روی سرور و بعد `nginx -s reload`. `front_client/package-lock.json` تغییرهای محلی دارد و نباید کامیت شود.

## ۷. نکته‌ها
- `Intl.ListFormat('fa-IR')` قبل از «و» ویرگول می‌گذارد؛ اتصال فارسی دستی است.
- خانه هرگز لیست بدون ورود همه کسب‌وکارها را نشان نمی‌دهد؛ ولی `sitemap.xml` همه صفحه‌های عمومی را برای موتور جستجو می‌سازد.
- پنل HTML/PWA کارفرما (`/panel`) روی برنچ `feat/owner-panel-web` است و در `develop` نیست.
