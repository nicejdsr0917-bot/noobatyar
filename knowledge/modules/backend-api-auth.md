---
updated: 2026-10-28
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
