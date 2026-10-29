---
updated: 2026-10-29 10:39
tags: [gf, current]
---
# وضعیت فعلی

## هدف
اپ کلاینت (برنچ `mobile-client`) و اپ اونر با یک بک‌اند مشترک کار کنند و تست سرتاسری روی امولاتور ممکن باشد. کارهای بعدی در `docs/PLAN_NEXT.md` به ترتیب اولویت نوشته شده‌اند.

## وضعیت
- برنچ `mobile-client` با develop merge شده و در worktree `../Noobatyar-mobile-client` است؛ کار یکپارچه همین‌جاست.
- **انتخاب شهر با لیست دیوار** پیاده شده: بک‌اند (`city_code` + فیلتر)، اپ اونر (فیلد اجباری شهر)، اپ کلاینت (خوشامد «انتخاب شهر» + انتخابگر). ۵۴۱ تست بک‌اند پاس. سمت کلاینت روی امولاتور تست شده؛ فرم اونر روی امولاتور تست نشده.
- وب کلاینت (develop): کارت خدمات و روزهای کاری در `b/[slug]`.
- دیتای محلی worktree تمیز است: فقط کسب‌وکاری که در اونر ساخته شده («ارز نو») + کاربرها و پلن‌ها.
- اجرای محلی: بک‌اند preview `backend_mobile_client_mac` (:8000)، `front_client` (:3000)، `mobile_owner_web_mac` (:8080، از develop، بدون فیلد شهر)، امولاتور `Pixel_6a` با `adb reverse` برای 8000 و 3000.

## قدم‌های بعدی
1. تست فرم شهر در اپ اونر روی امولاتور (ورود با حساب کاربر).
2. اجرای `docs/PLAN_NEXT.md` در سشن جدید، از P0.

## مسائل باز
- 🐞 تب پیام‌های پروفایل مشتری در اپ اونر پیام‌های همهٔ کسب‌وکارها را نشان می‌دهد (جزئیات در PLAN_NEXT، P0).
- 🔒 شماره کارت در API عمومی؛ 🐞 آمار «امروز» با UTC؛ 🐞 بله مشخصات را بعد از تصمیم پاک می‌کند.
- کیفیت کاشی نقشه (OSM ۲۵۶px) تار است؛ تصمیم دربارهٔ سرویس نقشه گرفته نشده.
- پنل وب اونر از develop اجرا می‌شود و تغییرات برنچ `mobile-client` را ندارد.
- امولاتور ممکن است از snapshot قدیمی بالا بیاید → APKها را دوباره نصب کن.
- فایل‌های commit‌نشدهٔ قدیمی در repo اصلی: `mobile_owner/composeApp/build.gradle.kts` (baseUrl iOS)، `InMemoryUserLocalSource.kt`، `.claude/*`، `.mcp.json`، `SALES_PLAYBOOK.md`، `docs/SERVER_DISK_CLEANUP.md`.

## فایل‌های کلیدی
- `docs/PLAN_NEXT.md` — پلن اولویت‌دار کار بعدی
- `../Noobatyar-mobile-client/backend/business/iran_cities.py` — لیست شهرها و فیلتر
- `../Noobatyar-mobile-client/mobile_client/.../feature/explore/CityPickerSheet.kt` — انتخابگر شهر کلاینت
- `../Noobatyar-mobile-client/mobile_owner/.../core/ui/components/city/CityPicker.kt` — انتخابگر شهر اونر

## سشن‌های اخیر
- [[sessions/2026-10-29-1039]] — انتخاب شهر با لیست دیوار، تمیز کردن دیتای محلی، رفع بازگشت درگاه، پلن کار بعدی
- [[sessions/2026-10-28-2144]] — بررسی پروژه، خدمات/روزهای کاری در وب، merge mobile-client، ساخت knowledge base

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
