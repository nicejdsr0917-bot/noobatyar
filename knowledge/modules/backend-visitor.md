---
updated: 2026-10-30
tags: [gf]
---
# backend: visitor (مشتری)

**هدف:** مشتری‌های هر کسب‌وکار، auth مشتری با OTP، فعالیت، لاگ پیامک و پوش.

## فایل‌ها
- `backend/visitor/models.py` — `Visitor`، `VisitorContact`، `VisitorArchive`، `VisitorActivity`، `VisitorDeviceToken`، `SmsLog`، `PushLog`
- `backend/visitor/urls.py` — owner: `api/visitor/`، `<id>/`، `<id>/messages/`، `<id>/restore/`
- `backend/visitor/client_auth_urls.py` — `api/client/auth/{otp/send,otp/verify,register,me,devices/...}`
- `backend/visitor/client_activity_urls.py` — `api/client/activity/`
- تست‌ها: `tests_reports.py`، `tests_cross_business.py`

## وابستگی‌ها
[[modules/backend-business]]، [[modules/backend-appointment]]؛ مصرف‌کننده: [[modules/front-client]]، [[modules/mobile-client]].

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- `Visitor.readable_by` با `Exists` (بدون JOIN/DISTINCT)؛ لیست مشتری‌ها در DB صفحه‌بندی (۲ کوئری).
- `VisitorMessageHistoryView`: فقط کسب‌وکارهای خود اونر؛ `business_id` نامعتبر/`0` → صفحهٔ خالی (نه 404، به‌خاطر اپ‌های قدیمی).

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `visitor/services.py`: attach، update_by_owner، archive. attach هرگز مالکیت ردیف موجود را نمی‌دهد. شماره قفل است اگر `has_independent_identity` (ورود کلاینت، توکن پوش، اونر دیگر، نوبت جای دیگر).
- `Visitor.phone_changed_at` و `last_client_login_at`: `VisitorTokenAuthentication` توکنِ قبل از تغییر شماره را رد می‌کند. `VisitorContact.display_name`: نامی که اونر وارد کرده.
