---
description: ساخت یک فیچر جدید (Generate Feature) به‌صورت یکپارچه در بک‌اند، وب مشتری و اپ‌های موبایل نوبت‌یار
argument-hint: <نام-فیچر> [توضیح کوتاه]
---

فیچر جدید با این مشخصات را بساز: **$ARGUMENTS**

## مرحله ۰ — شناخت پروژه (قبل از نوشتن هر کدی)

نوبت‌یار یک مونوریپو با این بخش‌هاست:

| پوشه | نقش | استک |
|---|---|---|
| `backend/` | API مشترک | Django 4.2 + DRF، ویوهای async (`adrf`)، JWT |
| `front_client/` | وب مشتری (رزرو نوبت) | Next.js — قبل از کد زدن `front_client/AGENTS.md` را بخوان |
| `mobile_owner/` | اپ صاحب کسب‌وکار | Kotlin Multiplatform / Compose |
| `mobile_client/` | اپ مشتری (marketplace، جستجوی جغرافیایی) | Kotlin Multiplatform / Compose |

اپ‌های جنگو: `core`، `api`، `business`، `appointment`، `visitor`، `accounting`، `versions`.
مستندات مرجع: `README.md`، `backend/README.md`، `docs/ENVIRONMENTS.md`، `technical_roadmap.md`.

1. بررسی کن فیچر دقیقاً به کدام بخش(ها) مربوط است و کدام اپ جنگو صاحب آن است؛ اگر اپ مناسبی نیست، به جای ساخت اپ جدید اول بپرس.
2. کد مشابه موجود را پیدا کن و از همان سبک (نام‌گذاری، ساختار فایل، فرمت پاسخ در `api/responses.py`، `api/pagination.py`، `api/permissions.py`) پیروی کن.
3. اگر درخواست مبهم است یا تصمیم محصولی دارد، قبل از شروع بپرس.

## مرحله ۱ — بک‌اند (`backend/`)

- مدل در `models.py` اپ مربوطه + migration (`python manage.py makemigrations <app>`)؛ migration را حتماً چک کن.
- serializer، ویو (async، مطابق ویوهای هم‌خانواده)، و ثبت مسیر در `urls.py` همان اپ.
- دسترسی‌ها: از permissionهای `api/permissions.py` استفاده کن؛ صاحب کسب‌وکار فقط به داده‌ی خودش دسترسی دارد.
- متن‌های کاربرمحور فارسی، تاریخ‌ها با ابزارهای `api/jalali.py`، شماره موبایل با `api/phone.py`.
- ثبت در `admin.py` در صورت نیاز.
- تست در `tests*.py` همان اپ؛ اجرا: `python manage.py test <app>`.
- اگر endpoint عمومی است، rate-limit/throttle (`api/throttles.py`) را در نظر بگیر.

## مرحله ۲ — کلاینت‌ها (فقط در صورت نیاز فیچر)

- **وب مشتری:** صفحه/کامپوننت در `front_client/app/`؛ UI فارسی و RTL؛ آدرس API را هاردکد نکن (طبق `docs/ENVIRONMENTS.md`).
- **اپ‌های موبایل:** کد مشترک در `composeApp/src/commonMain`؛ رنگ‌ها/آیکن‌ها از `brand/` و `docs/BRAND_GUIDE_FOR_AI.md`؛ آدرس‌های محلی را با جدول هاست‌نیم `docs/ENVIRONMENTS.md` هماهنگ نگه دار.

## مرحله ۳ — اعتبارسنجی

- تست‌های بک‌اند مربوطه را اجرا کن و نتیجه را گزارش بده.
- برای وب: `npm --prefix front_client run lint` و در صورت امکان اجرا با تسک‌های `.claude/launch.json` (`backend`، `front_client`).
- موارد اجرا‌نشده/تاییدنشده را صریح بگو.

## مرحله ۴ — مستندسازی و تحویل

- اگر endpoint جدید اضافه شد، `API_DOCUMENTATION.md` / `backend/api_curl.md` را به‌روز کن.
- تغییر قابل‌توجه برای کاربر نهایی را در `CHANGELOG.md` ثبت کن.
- بدون درخواست صریح کاربر commit یا push نکن؛ روی شاخه‌ی فعلی کار کن و فایل‌های بی‌ربط (مثل `backend/*.log`) را دست نزن.
- در پایان خلاصه بده: فایل‌های تغییرکرده، تصمیم‌های مهم، و آنچه هنوز باقی است.
