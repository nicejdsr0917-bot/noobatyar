---
updated: 2026-11-06
tags: [gf]
---
# backend: appointment

**هدف:** نوبت‌ها، اسلات‌های خالی، آمار owner، رزرو و پرداخت بیعانه مشتری، یادآوری.

## فایل‌ها
- `backend/appointment/models.py` — `Appointment` (وضعیت‌ها: LOCKED، PENDING_VERIFICATION، WAITING، CONFIRMED، COMPLETED، CANCELLED، NO_SHOW…)
- `backend/appointment/views/` — `appointment_stats_view.py`، `daily_counts_view.py`، `service_breakdown_view.py`، `range_visitors_view.py`، `appointment_query_view.py`، `public_slots_view.py`
- `backend/appointment/available_slots_view.py`، `occupancy.py`، `cache_utils.py`
- `backend/appointment/client_views.py` + `client_urls.py` — `api/client/appointments/`، `<id>/{pay,pay/online,cancel}/`، `deposit-callback/`، `<bid>/available-slots/`
- `backend/appointment/payment/zibal_deposit.py` — بیعانه آنلاین (تومان)
- command: `send_appointment_reminders` (هر ۵ دقیقه با crontab)

## وابستگی‌ها
[[modules/backend-business]]، [[modules/backend-visitor]]، پوش از [[modules/backend-api-auth]].

## نکته‌ها / باگ‌های باز
- 🐞 `appointment_stats_view.py:93-95`: پنجره «امروز» با `timezone.now()` (UTC) ساخته می‌شود → ۳:۳۰ ساعت جابه‌جا. اصلاح: `timezone.localtime(now)`. تست `tests_stats.test_no_params_still_returns_todays_stats` بین ۰۰:۰۰ تا ۰۳:۳۰ تهران fail می‌شود.
- `tests_daily_counts.py`: fixture `_appointment` با `timezone.now()` تاریخ می‌سازد → همان بازه fail (ایراد تست، view درست است).

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- منطق در `appointment/services/`: `booking.py` (`appointment_create_for_client/owner`، `business_lock`)، `transitions.py` (همه تغییر وضعیت‌ها UPDATE شرطی روی ردیف قفل‌شده)، `deposits.py` (`deposit_settle`)، `notifications.py` (handlerهای Outbox، `_send_metered_sms`)؛ خواندن‌ها در `selectors.py`. viewها فقط parse/پاسخ.
- ترتیب قفل همه‌جا: business → appointment → اعتبار. اعتبار (`record_appointment`) داخل همان تراکنش ساخت نوبت؛ بدون اعتبار هیچ نوبتی ساخته نمی‌شود.
- callback بیعانه: تأیید زیبال خارج از قفل؛ نوبت منقضی‌شده‌ای که اسلاتش رفته → `?payment=conflict` و `deposit_verified_at` → فیلتر ادمین «پرداخت‌شده بدون نوبت».
- رزرو هم‌پوشان اونر عمداً مجاز است. ظرفیت ساعتی و آمار «امروز» به ساعت تهران. یادآوری‌ها با claim شرطی (یک بار ارسال).

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `services/client_booking.py`: book، submit_payment، cancel، deposit_returned، با side effectها در `on_commit`. `validate_client_booking`: ساعت کاری (`working_hours.py`)، تعطیلی، زمان گذشته، مدت بین ۱ و ۴۸۰.
- `slots.py`: `build_day_slots` و `day_bounds` (دو endpoint عمداً تفاوت دارند). `selectors_stats.py`: آمار. `services/reminders.py`: یادآوری؛ دستور management فقط حلقه است.
- سرور روز را «تاریخ UTC ← نیمه‌شب تهران» حساب می‌کند (A3). این باگ واقعی است: تقویم اونر برای روز D نوبت‌های D-1 را می‌گیرد و خالی نشان می‌دهد. رفعش روی برنچ `a3-tehran-day-boundary` (`ae748ce`) است و هنوز merge نشده.
- **لغو نوبت اعتبار برنمی‌گرداند** (تصمیم 2026-11-05): `occupancy.refund_quota` فقط `quota_source` را پاک می‌کند. پیامکِ ارسال‌نشده از سمت اپراتور هنوز refund می‌شود.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰
- `slots.py`: توابع `first_free_window(now)` و `first_free_slot_ms(business, now, busy=None)` اضافه شدند. همان قواعد grid و occupancy صفحه available-slots را دارند و در ۷ روز آینده می‌گردند.
- `cache_utils.invalidate_slots_cache` حالا `first_free_slot_key(business_id)` را هم پاک می‌کند.
- `Appointment.source` مقدارهای WEB، APP، OWNER و UNKNOWN را دارد. پیش‌فرض سرور برای رزرو کلاینت `WEB` است، پس اپ باید `APP` را صریح بفرستد.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۱
- **رسید پرداخت:**
  - `_store_receipt` در `services/client_booking.py` رسید را با Pillow باز و بررسی می‌کند، سقف ۸ مگابایت دارد، تا ۱۶۰۰ پیکسل کوچک و به JPEG تبدیل می‌کند.
  - از کمک‌تابع‌های `showcase/images.py` استفاده می‌کند (`open_upload`، `flatten_rgb`، `encode_jpeg`).
  - فایل غیرعکس خطای ۴۰۰ می‌گیرد.
- **فیلدهای فیش:** `ClientAppointmentSerializer` حالا `description`، `selected_services` (به شکل لیست)، `payment_reference`، `payment_receipt` و `deposit_payment_method` را هم برمی‌گرداند؛ فقط برای نوبت خود مراجع.
- **فیلدهای سفر:**
  - `Appointment.travel_distance_m`، `drive_minutes_min`، `drive_minutes_max` (nullable، migration `0017`).
  - `travel_estimate(request.data)` مقدارهای خراب را دور می‌ریزد و ثبت نوبت هرگز به‌خاطر آن رد نمی‌شود.

## فاصله و زمان رانندگی روی نوبت
- `travel_distance_m`، `drive_minutes_min/max` را اپ کلاینت موقع رزرو می‌فرستد (از آخرین موقعیت ذخیره‌شده)؛ مختصات کاربر ذخیره نمی‌شود. هنوز در سریالایزر نوبت، ادمین و اپ اونر نمایش داده نمی‌شود.

## بیعانه غیرقابل دریافت (2026-11-02)
- `selectors.deposit_collectable(business)`: کارت با شماره، یا آنلاین با مرچنت یا لینک. نقد حساب نیست.
- `business_requires_payment`: بیعانه `OPTIONAL` همیشه مرحله پرداخت دارد؛ `MANDATORY` فقط اگر قابل دریافت باشد. اگر نه، رزرو مستقیم `PENDING_APPROVAL` می‌شود و قفل نمی‌شود.
- `client_submit_payment`: نقد برای `MANDATORY` فقط وقتی رد می‌شود که بیعانه قابل دریافت باشد (نوبت‌های قفل‌شده قدیمی گیر نمی‌کنند).
- تست: `tests_uncollectable_deposit.py`.

## ظرفیت هم‌زمان و زمان نگه‌داشتن نوبت (2026-11-05)
- `Business.slot_capacity` (۱ تا ۵۰۰، پیش‌فرض ۱): چند مشتری هم‌زمان یک ساعت.
  - پر بودن = `occupancy.peak_concurrency(busy, start, end) >= capacity`. نوبت‌های پشت‌سرهم هم‌زمان نیستند.
  - روی این‌ها اعمال می‌شود: `find_conflict(..., capacity)` در رزرو مشتری و پرداخت دیرهنگام، `slots.build_day_slots` (grid و اولین نوبت خالی)، و `public_slots_view` (با ظرفیت بیش از ۱ فقط بازه‌های پر را از `full_intervals` و با `status = FULL` می‌فرستد).
  - نوبت ثبت‌شده توسط اونر عمداً چک هم‌پوشانی ندارد و فقط `max_appointments_per_hour` قدیمی را چک می‌کند.
- `Business.hold_minutes` (۲ تا ۱۵، پیش‌فرض ۵؛ قبلاً ثابت ۱۵) در `lock_expiry(now, minutes)` استفاده می‌شود.
  - `appointment_store_gateway_track_id` زمان را به دست‌کم `GATEWAY_HOLD_MINUTES = 10` می‌رساند و هیچ‌وقت کوتاهش نمی‌کند.
- تست‌ها در `appointment/tests_capacity_hold.py`.
- `AppointmentQuerySerializer.first_visit_discount`: درصد تخفیف کسب‌وکار، اگر این اولین مراجعه مشتری باشد.
