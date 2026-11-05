---
updated: 2026-11-05 22:42
tags: [gf]
---
# وب مشتری (front_client)

**هدف:** صفحهٔ عمومی کسب‌وکار، رزرو، پرداخت بیعانه، نوبت‌های من؛ همچنین صفحه‌های بازگشت از درگاه برای پنل وب owner.

## فایل‌ها
- `front_client/app/b/[slug]/page.tsx` (SSR، متادیتا) + `BusinessProfileClient.tsx` — hero، اطلاعیه، کارت‌های آمار (ساعت/بیعانه/پرداخت)، **خدمات** (chip، ۸ تا + «N خدمت دیگر»)، **روزهای کاری** (هفت روز از شنبه، تعطیل خط‌خورده)، درباره، نقشه + نشان/بلد، CTA ثابت
- `front_client/app/b/[slug]/book/page.tsx` → `checkout/[id]/page.tsx` → `booking-success`
- `front_client/app/home/payment-result*` — بازگشت از زیبال
- `front_client/lib/api.ts` — تنها کلاینت API، تایپ `Business`، `getBusinessByCode`، `closedWeekdaysJs()`
- `front_client/lib/i18n/` — `messages.ts` (`fa` + `en`، تایپ `MessageKey`)، `t()`، `getLocale()`، `intlLocale()`؛ server: `lib/i18n/server.ts`
- `front_client/app/components/` — `Icon.tsx` (Tabler؛ `deposit` = سکه)، `BusinessMap`، `LockedContact`، `AppBanner`، `CategoryArt`
- `front_client/app/globals.css` — توکن‌ها، `.themed` (رنگ کسب‌وکار)، `.biz-*`

## اجرا
`npm --prefix front_client run dev` (:3000)، `npx tsc --noEmit -p front_client`. `.env.local`: `NEXT_PUBLIC_API_URL=http://127.0.0.1:8000`.

## نکته‌ها
- Next.js 16 با تغییرات شکننده — قبل از کد جدید `node_modules/next/dist/docs/` را ببین (`front_client/AGENTS.md`).
- CSS فقط logical (`start`/`end`)؛ در server component `getLocale()` همیشه `fa` است → `getServerLocale()`.
- `Intl.ListFormat('fa-IR')` ویرگول قبل از «و» می‌گذارد → اتصال فارسی دستی.
- lint: سه خطای قدیمی `react-hooks/set-state-in-effect` در `book/page.tsx`.

## وابستگی‌ها
[[modules/backend-business]]، [[modules/backend-appointment]]، [[modules/backend-visitor]].

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `lib/auth.ts` (توکن مشتری)، `apiFetch` با FormData، `getPaymentResult`، `app/components/PaymentResult.tsx`. lint باید بدون خطا باشد.
- e2e: `npm run test:e2e` (Playwright، mock API در `e2e/` روی پورت 4187، Next روی 3187).

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰
- `app/components/ShowcaseSection.tsx` و `ReviewsSection.tsx` روی `b/[slug]` رندر می‌شوند. نمونه‌کار و صفحهٔ اول نظرات سمت سرور گرفته می‌شوند، و `aggregateRating` در JSON-LD آمده.
- رزرو با `source: 'WEB'` ثبت می‌شود. mock-api در e2e حالا showcase و reviews را هم دارد؛ ۱۳ تست Playwright.

## جلسهٔ 2026-11-02
- `app/components/SocialLinks.tsx`: زیر اطلاعیهٔ مالک در `BusinessProfileClient`؛ عنوان `t('socialPages', business.title)`. آیکن‌ها در `public/icons/social/` (SVG از vectorهای اپ اونر، PNG برای بله/ایتا/روبیکا). استایل `.social-*` در `globals.css` با `popIn` و `prefers-reduced-motion`.
- `Business.social_links` و `SocialKind` در `lib/api.ts`.
- چک‌اوت (`b/[slug]/checkout/[id]/page.tsx`): `depositUncollectable` → نقد برای بیعانهٔ اجباری بی‌روش.
- `npx tsc` فقط خطاهای Playwright نصب‌نشده (`e2e/`، `playwright.config.ts`) را دارد که قبلی‌اند.

## جلسهٔ 2026-11-05
- `app/components/FollowButton.tsx`: تعداد و دکمهٔ فالو در hero صفحهٔ `/b/[slug]`. مهمان به `/auth/login?redirect=` می‌رود.
- `lib/api.ts`: `getFollowState` و `setFollowing`. `Business.followers_count` و `is_following`، `Review.visitor_avatar`.
- `ReviewsSection`: عکس یا حرف اول نام. با `?review=1` (لینک پوش «نظر بدهید») تا بخش نظرها اسکرول می‌کند. کلیدهای i18n: `follow`، `followingNow`، `followersCount`، `followNeedsLogin`.
- type check محلی: خطاهای `e2e/` و `playwright.config` طبیعی‌اند، چون `@playwright/test` محلی نصب نیست.

## تم روشن پیش‌فرض (2026-11-05 شب)
- `themeScript` در `app/layout.tsx` همیشه `data-theme` می‌گذارد (`light` مگر اینکه کاربر dark ذخیره کرده باشد). دیگر به تم سیستم‌عامل نگاه نمی‌کند. `themeColor` فقط بنفش است.
