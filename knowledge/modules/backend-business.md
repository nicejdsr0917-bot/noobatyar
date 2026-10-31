---
updated: 2026-10-31
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

## روی برنچ mobile-client (۲۰۲۶-۱۰-۲۹، هنوز در develop نیست)
- `business/models/` پکیج شد (core، moderation، catalog، booking، promo) با re-export کامل؛ schema بدون تغییر.
- لیست/جزئیات عمومی: `business/selectors.py` (`public_businesses`، `attach_owner_state`) → تعداد کوئری ثابت (صفحهٔ ۱۰۰تایی ۱۰ کوئری). geo در SQL. migration `0031` (فقط Postgres، CONCURRENTLY): ایندکس trigram روی `UPPER(col)` چون Django `icontains` را `UPPER(col) LIKE` می‌سازد.
- `card_number`/`card_owner_name` از `PublicBusinessSerializer` و `ClientBusinessSerializer` حذف؛ فقط روی نوبت LOCKED خود مشتری (`ClientAppointmentSerializer`).
- اکشن‌های گروهی ادمین از طریق `business/services.py`.

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- `business/cache.py` و `signals.py`: کش Redis برای لیست (۶۰ ثانیه) و جزئیات عمومی (۳۰۰ ثانیه)، فقط برای درخواست بدون `Authorization`، با version stamp. کلید شامل origin است. خاموش کردن: `PUBLIC_DIRECTORY_CACHE_ENABLED=False`.
- `public_policy.py`، `geo.py`، `selectors.public_business_search`، `categories.py` (`Business.CATEGORY_GROUPS` به‌صورت alias). `admin/` حالا یک بسته است.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰
- `business/first_free.py` → `attach_first_free_slots(rows)`: فیلد `first_free_slot` (epoch ms یا null) را بعد از کش payload به لیست و جزئیات عمومی (`client_views.py`) اضافه می‌کند. فقط برای کسب‌وکارهای claimed با رزرو فعال. هزینه‌اش دو کوئری در هر صفحه است: یکی کسب‌وکارها، یکی همهٔ نوبت‌های پنجره. کش ۳۰ ثانیه‌ای `first_free:{id}` دارد.
- تست‌های قرارداد (`tests_contract_shapes.py`) و تعداد کوئری (`MAX_LIST_QUERIES = 16`، و `tests_hot_endpoint_queries.py` با سقف ۱۴) برای این فیلد به‌روز شدند.
- بنرها در `banners/` هستند و دقیقاً ۱۲۰۰×۴۰۰ پیکسل‌اند (validator). روی گروه دسته‌بندی (`BannerCategoryGroup`) یا یک دستهٔ مشخص تعریف می‌شوند. لینک مشترک‌شان `SiteSetting.banner_link_url` است.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰ (عصر)
- `business/admin/business.py`: ستون `rank_column` («رتبه»، مرتب‌سازی `-rank_score`، «محاسبه نشده» تا اولین اجرای cron). `rating_avg`، `rating_count`، `rank_score`، `rank_updated_at` فقط‌خواندنی‌اند. تست در `tests_admin_smoke.py`.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۱
- **اپ `banners`، مدل `ClientPromo`** (migration `0002`):
  - فیلدها: متن، متن دکمه، مرکز یا لینک، محل نمایش (`EXPLORE` / `BUSINESS_MAP` / `ALL`)، و زمان‌بندی.
  - API: `GET /api/client/promos/?placement=` (عمومی). در ادمین زیر «نوارهای تبلیغ اپ مشتری» است.

## احراز Visitor روی API عمومی (2026-10-31)
- `ClientBusinessListView` و `ClientBusinessDetailView` (`client_views.py`): `authentication_classes = [VisitorTokenAuthentication, JWTAuthentication]`. مشتری واردشده «نمایش به مهمان خاموش» مالک را دور می‌زند؛ توکن بد ۴۰۱ می‌دهد. درخواست دارای Authorization کش عمومی را رد می‌کند. تست: `tests_contact_policy`.

## فرمان‌های seed (2026-10-31 شب)
- `seed_insurances` (از `business/insurance_catalog.py`): ۱۹ بیمهٔ تکمیلی و ۳ پایه؛ idempotent؛ روی سرور اجرا شود.
- `seed_demo_businesses --count N`: کسب‌وکار `DEMO0001…` در همهٔ گروه‌ها با صاحب و اشتراک؛ فقط محلی/staging.
