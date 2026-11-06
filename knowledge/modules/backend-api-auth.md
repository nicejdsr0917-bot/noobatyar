---
updated: 2026-11-05
tags: [gf]
---
# backend: api (کاربر و auth)

**هدف:** حساب صاحب کسب‌وکار، ورود با رمز/OTP، JWT، ثبت دستگاه برای پوش.

## فایل‌ها
- `backend/api/models.py` — `User`، `DeviceToken`
- `backend/api/otp.py` — ارسال/تایید OTP (در DEBUG با `OTP_DEV_CODE` و `SMS_DEV_MODE` پیامک واقعی نمی‌رود)
- `backend/api/services/push.py` — `send_to_user` (FCM)
- `backend/api/urls.py` — `api/auth/{register,login,logout,otp/send,otp/verify,token/refresh,forgot-password/...}`، `users/<pk>/`، `devices/{register,unregister}`
- تست‌ها: `tests_security.py`، `tests_push.py`

## وابستگی‌ها
مالک [[modules/backend-business]]؛ پوش از [[modules/backend-appointment]] و [[modules/backend-core]] صدا زده می‌شود.

## نکته‌ها
- auth مشتری جداست (در [[modules/backend-visitor]]).
- کاربر seed دمو: `backend/seed_demo_data.py`.

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- OTP (`api/services/otp.py`): cooldown ۳ دقیقه با `cache.add`؛ تلاش‌ها با `incr` (۵ حدس برای هر کد)؛ ۵ ارسال/ساعت بر (شماره، IP) و ۱۵ ارسال موفق/روز بر شماره؛ verify ۱۰/دقیقه بر (شماره، IP) و ۳۰/دقیقه بر IP؛ **بن ۲۴ ساعته حذف شد**؛ قطعی Redis → پیام «سرویس موقتاً در دسترس نیست». کد OTP دیگر لاگ نمی‌شود.
- `api/throttles.client_ip()` — IP مورد اعتماد بر اساس `NUM_PROXIES` (هرگز چپ‌ترین XFF).
- انتقال HTTP پیامک/FCM در `backend/integrations/melipayamak.py`، `fcm.py`.

## IP اونرها و نسخه کلاینت (2026-11-04/05)
- `api/authentication.IpRecordingJWTAuthentication` (در `DEFAULT_AUTHENTICATION_CLASSES`) هر IP اونر را حداکثر ساعتی یک بار در `UserIpSighting` ثبت می‌کند. محدودیت با `cache.add` است؛ در تست `cache.clear()` لازم است. migration `0006`.
- `Appointment.booking_ip` را `client_book(ip=...)` از `client_ip(request)` پر می‌کند.
- `versions/views._requested_app`: درخواست بدون `app` با کد در `CLIENT_ONLY_CODES` (1789813665..1790244241) CLIENT حساب می‌شود. `versions/0004` نسخه‌های ۱.۱.۰ و ۱.۲.۰ کلاینت را seed می‌کند. بدون ردیف CLIENT جواب 404 است و اپ کلاینت آن را «خطای شبکه» نشان می‌دهد.
