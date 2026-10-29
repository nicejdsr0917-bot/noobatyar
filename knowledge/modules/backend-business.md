---
updated: 2026-10-29
tags: [gf]
---
# backend: business

**هدف:** پروفایل کسب‌وکار (ساعت کاری، روزهای تعطیل، خدمات، بیعانه، روش پرداخت، موقعیت)، moderation محتوا، پیامک تبلیغاتی، API عمومی برای وب/اپ مشتری.

## فایل‌ها
- `backend/business/models.py` — `Business`، `ServiceCatalogItem`، `PromoCampaign`(+`Recipient`)، `BannedKeyword`، `BusinessModerationLog`، `ContentReport`
- `backend/business/serializers.py` — `BusinessSerializer` (owner)، serializer عمومی؛ `apply_contact_rules` (پنهان کردن تماس/آدرس با `allow_anonymous_view` و `ContactVisibilityPolicy`)، `apply_notice_rules`
- `backend/business/client_urls.py` — `api/client/business/` (لیست/جستجو با `?search=<unique_code>`)، `<id>/`، `<id>/report/`
- `backend/business/urls.py` — owner: `api/business/`، `sms-logs`، `push-logs`، `promo-sms/[preview|<cid>]`، `service-catalog/`، `categories/`
- `backend/business/promo_sms.py`، `sms_moderation.py`، `theme_color.py`

## فیلدهای مهم Business
`services` (لیست رشته، حداکثر ۶۰)، `closed_weekdays` (Mon=0..Sun=6)، `work_start/end_hour/minute`، `deposit_mode`/`deposit_amount`، `accepted_payment_methods`، `latitude/longitude`، `notice_enabled/notice_message`، `booking_enabled`، `theme_color`.

## شهر (روی برنچ `mobile-client`)
- `Business.city_code` (شناسهٔ شهر در دیوار، migration `0030`)؛ `business/iran_cities.py` لیست را از `business/data/iran_cities.json` می‌خواند (`get_city`، `city_filter`).
- serializer اونر `city_code` می‌گیرد و `city`/`province` را از لیست پر می‌کند (مقدار ارسالی اپ نادیده گرفته می‌شود)؛ کد ناشناخته ۴۰۰.
- `api/client/businesses/?city_code=` → کسب‌وکارهای آن شهر + کسب‌وکارهای بدون `city_code` که پینشان داخل شعاع مرکز شهر است. تست‌ها: `business/tests_city_code.py`.

## وابستگی‌ها
[[modules/backend-accounting]] (اشتراک برای `is_expired`)، [[modules/backend-visitor]] (لاگ پیامک)، [[modules/backend-appointment]].

## نکته‌ها
- ⚠ API عمومی لیست کسب‌وکار بدون ورود `card_number` و `card_owner_name` را برمی‌گرداند (۱۳ از ۳۵ کسب‌وکار prod، ۲۰۲۶-۱۰-۲۸) — باید فقط بعد از رزرو در checkout داده شود.
- روی برنچ `mobile-client`: فیلدهای کشف (`province/city/neighborhood`، `is_claimed`، `insurances`)، claim و `OnlineBookingRequest` — [[modules/mobile-client]].
