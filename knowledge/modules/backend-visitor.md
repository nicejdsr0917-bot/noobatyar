---
updated: 2026-11-05
tags: [gf]
---
# backend: visitor (مشتری)

**هدف:** مشتری‌های هر کسب‌وکار، auth مشتری با OTP، فعالیت، لاگ پیامک و پوش.

## فایل‌ها
- `backend/visitor/models.py` — `Visitor`، `VisitorContact`، `VisitorArchive`، `VisitorActivity`، `VisitorDeviceToken`، `SmsLog`، `PushLog`
- `backend/visitor/urls.py` — owner: `api/visitor/`، `<id>/`، `<id>/messages/`، `<id>/restore/`
- `backend/visitor/client_auth_urls.py` — `api/client/auth/{otp/send,otp/verify,register,me,me/avatar,devices/...}`
- `backend/visitor/client_activity_urls.py` — `api/client/activity/`
- تست‌ها: `tests_reports.py`، `tests_cross_business.py`، `tests_profile.py`

## وابستگی‌ها
[[modules/backend-business]]، [[modules/backend-appointment]]؛ مصرف‌کننده: [[modules/front-client]]، [[modules/mobile-client]].

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- `Visitor.readable_by` با `Exists` (بدون JOIN/DISTINCT)؛ لیست مشتری‌ها در DB صفحه‌بندی (۲ کوئری).
- `VisitorMessageHistoryView`: فقط کسب‌وکارهای خود اونر؛ `business_id` نامعتبر/`0` → صفحه خالی (نه 404، به‌خاطر اپ‌های قدیمی).

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `visitor/services.py`: attach، update_by_owner، archive. attach هرگز مالکیت ردیف موجود را نمی‌دهد. شماره قفل است اگر `has_independent_identity` (ورود کلاینت، توکن پوش، اونر دیگر، نوبت جای دیگر).
- `Visitor.phone_changed_at` و `last_client_login_at`: `VisitorTokenAuthentication` توکنِ قبل از تغییر شماره را رد می‌کند. `VisitorContact.display_name`: نامی که اونر وارد کرده.

## پروفایل مشتری (2026-11-01، برنچ mobile-client)
- `Visitor.avatar` (`visitor_avatars/`، migration `0014_visitor_avatar`).
- `ClientMeView`:
  - GET با `ClientMeSerializer`: `id`، `full_name`، `phone_number`، `avatar` (URL کامل)، `created_at`.
  - PATCH فقط `full_name` را عوض می‌کند (`ClientMeUpdateSerializer`، ۲ تا ۶۰ حرف، فاصله‌های اضافه جمع می‌شوند). شماره عوض نمی‌شود.
- `ClientAvatarView`:
  - POST چندبخشی با فیلد `avatar`: با `showcase/images.py` باز و بررسی می‌شود، `ImageOps.fit` مربع ۵۱۲ پیکسل، JPEG. فایل قبلی پاک می‌شود.
  - DELETE عکس را برمی‌دارد.
- `avatar` در `VisitorSerializer` (که اونرها می‌خوانند) **نیست**، پس صف بررسی ادمین لازم ندارد.

## بیو و عکس در نظرها (2026-11-05)
- `Visitor.bio` (حداکثر ۱۵۰) فقط برای خود مشتری است. `ClientMeUpdateSerializer`: `full_name` و `bio` هر دو اختیاری‌اند، حداقل یکی لازم است. bio تمیز می‌شود (فاصله‌ها و حداکثر یک خط خالی). migration `0015`.
- `reviews.PublicReviewSerializer`: `visitor_avatar` اضافه شد و `visitor_phone` همیشه `""` است (قبلاً شماره پوشیده نشت می‌کرد). `OwnerReviewSerializer` شماره پوشیده را نگه می‌دارد. viewها `context={'request'}` می‌دهند تا URL عکس کامل باشد.
