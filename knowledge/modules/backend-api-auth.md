---
updated: 2026-10-29
tags: [gf]
---
# backend: api (کاربر و auth)

**هدف:** حساب صاحب کسب‌وکار، ورود با رمز/OTP، JWT، ثبت دستگاه برای پوش.

## فایل‌ها
- `backend/api/models.py` — `User`، `DeviceToken`
- `backend/api/otp.py` — ارسال/تأیید OTP (در DEBUG با `OTP_DEV_CODE` و `SMS_DEV_MODE` پیامک واقعی نمی‌رود)
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
