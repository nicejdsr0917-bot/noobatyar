---
updated: 2026-11-05
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

## ورود از فایل و فرم هر صنف (2026-11-01)
- `directory_import.run_import(rows, source, dry_run, require_city, default_category)`: هستهٔ مشترک ادمین و `import_directory`. مراکز بی‌تیک (`is_claimed=False`، رزرو خاموش، کاربر سیستمی). گزارش: `ImportReport` (تکراری، نامعتبر با شمارهٔ ردیف، `warning_list`).
- ادمین: `/admin/business/business/import-file/?guild=KEY` (بارگذاری یا چسباندن از اکسل، پیش‌نمایش، توکن تأیید، فایل در `MEDIA_ROOT/tmp_imports` تا ۲۴ ساعت) و قالب نمونه (`?guild=` برای هر صنف).
- `DirectoryFormMixin` (`admin/directory_form.py`): فرم افزودن بخش‌بندی‌شده، پیش‌فرض‌ها، `city_code` از نام شهر، تأیید خودکار مرکز بی‌تیک.
- هشت proxy (`models/guilds.py`، `GUILDS`) با ادمین جدا (`admin/guilds.py`) و بخش «ثبت مرکز» در منو. `_GUILD_MENU` در `settings.py` دستی است (تست مقایسه می‌کند).

## شبکه‌های اجتماعی (2026-11-02)
- `Business.social_links` (JSON، migration `0036`): `{kind: value}` با کلیدهای `instagram`، `telegram`، `whatsapp`، `eitaa`، `bale`، `rubika`، `website`.
- `business/social_links.py` → `normalize_social_links`: هر شکلی (`@name`، لینک پروفایل، `wa.me/98…`) را به آیدی خالی، موبایل `09…` برای واتساپ، یا URL با `https` برای سایت تبدیل می‌کند. `javascript:` و طرح‌های دیگر رد می‌شوند.
- در `BusinessSerializer` (نوشتنی) و `PublicBusinessSerializer` / `ClientBusinessSerializer` (فقط خواندنی) هست.
- `public_policy._mask_phone` آن را هم `{}` می‌کند: هر جا تلفن پنهان است، شبکه‌ها هم پنهان‌اند.
- تست: `tests_social_links.py`؛ `tests_contract_shapes` کلید `social_links: dict` دارد.

## بیعانهٔ اجباری بدون روش آنلاین (2026-11-02)
- `BusinessSerializer.validate`: `deposit_mode=MANDATORY` بدون `CARD` یا `ONLINE` رد می‌شود، **فقط وقتی** بیعانه یا روش‌ها در همین ذخیره عوض شوند (اپ‌های اونر کل کسب‌وکار را می‌فرستند؛ اونرِ قدیمی با این تنظیم باید بتواند اسمش را عوض کند).
- `seed_demo_businesses`: بیعانهٔ اجباری حالا با کارت (`6037997599999999`) ساخته می‌شود.

## فالو و سقف بیعانه (2026-11-04/05)
- `BusinessFollow` (`models/follow.py`): unique روی business و visitor. `Business.followers_count` را فقط `follows.set_following` با `F()` به‌روز می‌کند و `public_cache.bump_business` را صدا می‌زند. migration `0037`.
- API:
  - `POST/DELETE client/businesses/<id>/follow/` (Visitor)
  - `GET client/businesses/following/`
  - `is_following` فقط روی detail برای Visitor برمی‌گردد، بیرون از cache.
- `followers_count` در همهٔ serializerها هست و در `tests_contract_shapes` pin شده است.
- **حریم خصوصی:** فهرست دنبال‌کننده‌ها به هیچ‌کس، حتی اونر، داده نمی‌شود؛ فقط تعداد.
- `Business.MAX_DEPOSIT_AMOUNT = 1_000_000`: `BusinessSerializer.validate` فقط وقتی مبلغ عوض شود رد می‌کند.

## اعلان فالو، فالوورها در کمپین، استوری (2026-11-05)
- **پوش فالو:** `follows.set_following` در فالو تازه `safe_enqueue('business.new_follower_push')` صدا می‌زند، با dedupe `follow_push:<biz>:<visitor>`، یعنی یک بار برای همیشه. هندلر `send_new_follower_push` اسم کوچک را می‌فرستد.
- **لیست فالوورها:** `GET /api/business/<id>/followers/` (`follow_views.OwnerFollowerListView`) فقط برای اونر است و اسم کوچک، avatar و `created_at` برمی‌گرداند، بدون شماره.
- **کمپین پیامکی** (`promo_sms.FOLLOWERS`):
  - این دسته فالوورهایی را برمی‌دارد که `marketing_opt_out=False` باشند و اونر بایگانی‌شان نکرده باشد.
  - برای فالووری که در `owner_customer_ids` نیست:
    - `{name}` = اسم کوچک.
    - `phone` در جزئیات کمپین خالی است.
    - SmsLog بدون visitor ثبت می‌شود.
  - migration `0038`.
- **استوری:** اپ جدا `backend/stories/`.
  - **مدل `Story`:**
    - `button` (`''`/`discount`)
    - `discount_type` (`percent`/`amount`)
    - `discount_value`
    - `expires_at` = ساخت + ۲۴ ساعت
    - حداکثر ۱۰ فعال
  - **`services`:**
    - `create_story` استوری‌های منقضی را پاک می‌کند (cron ندارد) و `stories.notify_followers` را با dedupe روزانه enqueue می‌کند.
    - `active_business_ids` حتماً با `.order_by()` قبل از `distinct()`.
  - **API:** `owner/stories/`، `client/stories/active/`، `client/businesses/<id>/stories/` (`is_following` با توکن مشتری). مستند در `docs/api/P2_SOCIAL_API.md` §10.
  - **عکس:** helperهای `showcase/images.py`. هر نسبتی قبول می‌شود و تا 1080×1920 کوچک می‌شود.
