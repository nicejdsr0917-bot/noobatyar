---
updated: 2026-10-30
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

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- منطق در `appointment/services/`: `booking.py` (`appointment_create_for_client/owner`، `business_lock`)، `transitions.py` (همهٔ تغییر وضعیت‌ها UPDATE شرطی روی ردیف قفل‌شده)، `deposits.py` (`deposit_settle`)، `notifications.py` (handlerهای Outbox، `_send_metered_sms`)؛ خواندن‌ها در `selectors.py`. viewها فقط parse/پاسخ.
- ترتیب قفل همه‌جا: business → appointment → اعتبار. اعتبار (`record_appointment`) داخل همان تراکنش ساخت نوبت؛ بدون اعتبار هیچ نوبتی ساخته نمی‌شود.
- callback بیعانه: تأیید زیبال خارج از قفل؛ نوبت منقضی‌شده‌ای که اسلاتش رفته → `?payment=conflict` و `deposit_verified_at` → فیلتر ادمین «پرداخت‌شده بدون نوبت».
- رزرو هم‌پوشان اونر عمداً مجاز است. ظرفیت ساعتی و آمار «امروز» به ساعت تهران. یادآوری‌ها با claim شرطی (یک بار ارسال).

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `services/client_booking.py`: book، submit_payment، cancel، deposit_returned، با side effectها در `on_commit`. `validate_client_booking`: ساعت کاری (`working_hours.py`)، تعطیلی، زمان گذشته، مدت بین ۱ و ۴۸۰.
- `slots.py`: `build_day_slots` و `day_bounds` (دو endpoint عمداً تفاوت دارند). `selectors_stats.py`: آمار. `services/reminders.py`: یادآوری؛ دستور management فقط حلقه است.
- سرور روز را «تاریخ UTC ← نیمه‌شب تهران» حساب می‌کند (A3، عمداً حفظ شده چون اپ اونر تقلیدش می‌کند).

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰
- `slots.py`: توابع `first_free_window(now)` و `first_free_slot_ms(business, now, busy=None)` اضافه شدند. همان قواعد grid و occupancy صفحهٔ available-slots را دارند و در ۷ روز آینده می‌گردند.
- `cache_utils.invalidate_slots_cache` حالا `first_free_slot_key(business_id)` را هم پاک می‌کند.
- `Appointment.source` مقدارهای WEB، APP، OWNER و UNKNOWN را دارد. پیش‌فرض سرور برای رزرو کلاینت `WEB` است، پس اپ باید `APP` را صریح بفرستد.
