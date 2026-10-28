---
updated: 2026-10-28
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
