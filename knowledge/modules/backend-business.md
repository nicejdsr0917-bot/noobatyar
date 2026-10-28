---
updated: 2026-10-28
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

## وابستگی‌ها
[[modules/backend-accounting]] (اشتراک برای `is_expired`)، [[modules/backend-visitor]] (لاگ پیامک)، [[modules/backend-appointment]].

## نکته‌ها
- ⚠ API عمومی لیست کسب‌وکار بدون ورود `card_number` و `card_owner_name` را برمی‌گرداند (۱۳ از ۳۵ کسب‌وکار prod، ۲۰۲۶-۱۰-۲۸) — باید فقط بعد از رزرو در checkout داده شود.
- روی برنچ `mobile-client`: فیلدهای کشف (`province/city/neighborhood`، `is_claimed`، `insurances`)، claim و `OnlineBookingRequest` — [[modules/mobile-client]].
