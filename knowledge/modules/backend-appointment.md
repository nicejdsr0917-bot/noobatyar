---
updated: 2026-10-28
tags: [gf]
---
# backend: appointment

**هدف:** نوبت‌ها، اسلات‌های خالی، آمار owner، رزرو و پرداخت بیعانهٔ مشتری، یادآوری.

## فایل‌ها
- `backend/appointment/models.py` — `Appointment` (وضعیت‌ها: LOCKED، PENDING_VERIFICATION، WAITING، CONFIRMED، COMPLETED، CANCELLED، NO_SHOW…)
- `backend/appointment/views/` — `appointment_stats_view.py`، `daily_counts_view.py`، `service_breakdown_view.py`، `range_visitors_view.py`، `appointment_query_view.py`، `public_slots_view.py`
- `backend/appointment/available_slots_view.py`، `occupancy.py`، `cache_utils.py`
- `backend/appointment/client_views.py` + `client_urls.py` — `api/client/appointments/`، `<id>/{pay,pay/online,cancel}/`، `deposit-callback/`، `<bid>/available-slots/`
- `backend/appointment/payment/zibal_deposit.py` — بیعانهٔ آنلاین (تومان)
- command: `send_appointment_reminders` (هر ۵ دقیقه با crontab)

## وابستگی‌ها
[[modules/backend-business]]، [[modules/backend-visitor]]، پوش از [[modules/backend-api-auth]].

## نکته‌ها / باگ‌های باز
- 🐞 `appointment_stats_view.py:93-95`: پنجرهٔ «امروز» با `timezone.now()` (UTC) ساخته می‌شود → ۳:۳۰ ساعت جابه‌جا. اصلاح: `timezone.localtime(now)`. تست `tests_stats.test_no_params_still_returns_todays_stats` بین ۰۰:۰۰ تا ۰۳:۳۰ تهران fail می‌شود.
- `tests_daily_counts.py`: fixture `_appointment` با `timezone.now()` تاریخ می‌سازد → همان بازه fail (ایراد تست، view درست است).
