# بک‌اند Django: سند فنی

> منبع: خود کد در `backend/` روی `develop` (`df608ec1`، ۲۰۲۶-۱۱-۱۴). جدول routeها با `get_resolver()` و جدول مدل‌ها با `apps.get_models()` از خود Django بیرون کشیده شده‌اند.
> سند بیزینسی همین پلتفرم: [BUSINESS.md](BUSINESS.md)

## ۱. معماری

```
اپ کارفرما (Android + پنل وب wasm) ──JWT (Bearer)──┐
وب مشتری Next.js ──────────────── Visitor <token> ─┼──> nginx ──> gunicorn + uvicorn (ASGI) ──> Django 4.2 + DRF
اپ مشتری اندروید ─────────────── Visitor <token> ─┘                 │
                                                                   ├─ Postgres 15 از راه pgbouncer (prod) / SQLite (لوکال)
                                                                   ├─ Redis 7 (کش، throttle، OTP)
                                                                   ├─ worker و worker_bulk: run_outbox (صف پیام در Postgres)
                                                                   ├─ integrations/: زیبال، مایکت، ملی‌پیامک، FCM، بله
                                                                   └─ crontab سرور → management commandها
n8n (فقط شبکه داخلی docker) ──Bearer DIRECTORY_SYNC_TOKEN──> /api/internal/directory-sync/
```

- **لایه‌ها:** view فقط parse و پاسخ است؛ نوشتن در `services/` (صاحب تراکنش)، خواندن در `selectors.py`، تماس با بیرون در `integrations/`. پول و سهمیه در جدول `CreditBalance` نگه داشته می‌شود، نه Redis. اعلان‌ها از Outbox می‌روند، نه thread.
- **ترتیب قفل:** همه‌جا business → appointment → اعتبار.
- **قالب پاسخ:** `common/responses.py` (`APIResponse`) با کلیدهای `status`، `code`، `message`، `data`. خطاها از `api.exceptions.custom_exception_handler` و 404 از `custom_404_handler`.
- **صفحه‌بندی:** `common/pagination.py` (`page_dict`، `page_params`؛ پارامتر خراب 400).
- **زمان:** `TIME_ZONE='Asia/Tehran'`، `USE_TZ=True`. «امروز» همیشه با `timezone.localdate()`/`localtime()`. روزهای هفته در API به قرارداد پایتون است (دوشنبه=0 تا یکشنبه=6). باگ مرز روز (A3، سرور «تاریخ UTC ← نیمه‌شب تهران» حساب می‌کند) روی برنچ `a3-tehran-day-boundary` (`ae748ce`) رفع شده و هنوز merge نشده.
- **مشاهده‌پذیری:** `core/observability.py` (لاگ JSON، `X-Request-ID`، لاگ کویری کند با `SLOW_QUERY_LOG`)، `core/health.py` (`healthz`، `readyz`)، `core/checks.py` (هشدار `core.W001` وقتی `ZIBAL_MERCHANT_ID` خالی است).

## ۲. appهای Django (`INSTALLED_APPS`)

| app | نقش | migration آخر |
|---|---|---|
| `core` | settings، urls، ادمین سفارشی (`NobatyarAdminSite` روی unfold)، بخش‌بندی مخاطب، کمپین پوش و پیامک ادمین، health | بدون مدل migration‌دار جدا (مدل‌ها در `core`) |
| `core.outbox` | صف پیام در دیتابیس و worker | `0002_outboxmessage_done_steps` |
| `api` | کاربر (owner/admin)، OTP، JWT، توکن دستگاه، IP اونر | `0006_reward_risk` |
| `accounting` | پلن، اشتراک، add-on، کیف پول، زیبال، مایکت، VAT، پاداش مشتری تازه | `0019_reward_risk` |
| `versions` | نسخه و changelog دو اپ (OWNER/CLIENT) | `0004_seed_client_versions` |
| `business` | کسب‌وکار، خدمات، moderation، پیامک تبلیغاتی، بیمه، مراکز، claim، فالو، import و sync فهرست | `0043_centers_and_origin` |
| `visitor` | مشتری، auth مشتری، لاگ پیامک و پوش، فعالیت | `0015_follows_and_bio` |
| `appointment` | نوبت، اسلات، آمار، رزرو و بیعانه مشتری، یادآوری | `0019_appointment_insurance` |
| `reviews` | نظر و امتیاز، درخواست نظر | `0001_initial` |
| `bale` | ربات بله برای بررسی محتوا | `0003_persian_labels` |
| `showcase` | نمونه‌کار و مدرک | `0001_initial` |
| `stories` | استوری کسب‌وکار و استوری نوبت‌یار | `0002_platform_stories` |
| `banners` | بنر اپ کارفرما، نوار تبلیغ اپ مشتری | `0002_client_promo` |
| `marketing` | لید فروش (تماس با کسب‌وکارها) | `0001_initial` |

پوشه‌های کمکی بدون app: `common/` (پاسخ، صفحه‌بندی، `jalali.py`، `phone.py`، `timeutils.py`)، `integrations/` (`zibal.py`، `myket.py`، `melipayamak.py`، `fcm.py`، `bale.py`)، `n8n/` (workflow)، `nginx/`، `templates/`، `assets/` (CSS ادمین)، `locale/`.

### ۲.۱ فهرست کامل migrationها

| app | migrationها |
|---|---|
| api (۶) | 0001_initial، 0002_alter_user_name_alter_user_phone، 0003_devicetoken، 0004_persian_labels، 0005_user_password_changed_at، 0006_reward_risk |
| accounting (۱۹) | 0001_initial، 0002_initial، 0003_plan_features_addons، 0004_addonpack_appointment_pack، 0005_rename_…_idx، 0006_creditledger، 0007_subscriptionremindersettings، 0008_alter_…_message_template، 0009_addonpack_myket_sku_plan_myket_sku_storepurchase، 0010_persian_labels، 0011_…_last_run_at، 0012_contactvisibilitypolicy، 0013_subscription_expired_reminder_sent، 0014_subscription_sub_user_status_idx، 0015_creditbalance، 0016_toman_wallet، 0017_new_client_reward، 0018_new_client_reward_business_cap، 0019_reward_risk |
| versions (۴) | 0001_initial، 0002_persian_labels، 0003_appversion_app، 0004_seed_client_versions |
| business (۴۶ فایل، تا 0043) | 0001 تا 0011 (پایه، مهمان، پرداخت، اطلاعیه، روش‌های پرداخت، لینک پرداخت، bio، قفل، نقد پیش‌فرض، پیامک به اونر)، دو 0012 (moderation و اطلاعیه/یادآوری)، دو 0013، دو 0014، 0015، 0016_merge، 0017 (فیلدهای pending)، 0018، 0019، 0020، 0021_theme_color، 0022_closed_weekdays، 0023_extra_phones، 0024، 0025_promocampaign، 0026_persian_labels، 0027_latitude_longitude، 0028_insurance_city_external_source، 0029_claims_and_booking_requests، 0030_city_code، 0031_directory_search_indexes (فقط Postgres، CONCURRENTLY)، 0032_rating، 0033_contentreport_review، 0034_rank_score، 0035_guild_proxy_models، 0036_social_links، 0037_follows_and_bio، 0038_alter_promocampaign_audience، 0039_first_visit_discount، 0040_slot_capacity_hold_minutes، 0041_location_privacy، 0042_insurance_logo، 0043_centers_and_origin |
| visitor (۱۶) | 0001 تا 0006، دو 0007 (status و marketing_opt_out)، 0008_merge، 0009_visitordevicetoken_pushlog، 0010_visitorcontact، 0011_persian_labels، 0012_smslog_biz_sent_idx، 0013_phone_changed_login_contact_display، 0014_visitor_avatar، 0015_follows_and_bio |
| appointment (۱۹) | 0001 تا 0004 (receipt)، 0005_business_status_date_index، 0006_reminder_sent_at، 0007_quota_source، 0008، 0009_deposit_payment_method، 0010_selected_services، 0011 و 0012 (پوش یادآوری)، 0013_persian_labels، 0014_payment_ref_idx، 0015_deposit_verified_at، 0016_source، 0017_travel_estimate، 0018_reward_risk، 0019_appointment_insurance |
| reviews، showcase، marketing | 0001_initial |
| bale | 0001_initial، 0002_pendingreason، 0003_persian_labels |
| stories | 0001_initial، 0002_platform_stories |
| banners | 0001_initial، 0002_client_promo |
| outbox | 0001_initial، 0002_outboxmessage_done_steps |

قاعده: فقط migration افزایشی. CI با `makemigrations --check --dry-run` نبودن migration را چک می‌کند. migration `0043` هنوز روی prod اجرا نشده (مراکز دیپلوی نشده‌اند).

## ۳. مدل‌های داده

> نوع فیلدها خلاصه شده؛ `FK` یعنی ForeignKey. مقدارهای choice در آکولاد.

### api
- **User** (`api_user`): `phone` (یکتا، نام کاربری)، `name`، `role` {BUSINESS_OWNER|CLIENT|ADMIN}، `is_employee`، `is_staff`، `is_active`، `joined_at`، `password_changed_at`.
- **DeviceToken**: `user` FK، `token`، `platform` {ANDROID|IOS|WEB}، `device_name`، `app_version`، `is_active`.
- **UserIpSighting**: `user`، `ip`، `last_seen`. هر IP اونر حداکثر ساعتی یک بار ثبت می‌شود (`api.authentication.IpRecordingJWTAuthentication`). برای ضدتقلب پاداش.

### accounting
- **Plan**: `name`، `price`، `discount_price` (اگر پر باشد مبنای قیمت است)، `description` (JSON، لیست خط)، `duration_value` + `duration_unit` {day|month}، `is_vip`، `is_active`، `myket_sku`، `features` (JSON قابلیت و سقف).
- **Subscription**: `user`، `plan`، `status` {active|expired|cancelled}، `started_at`، `ends_at`، `reminder_sent`، `expired_reminder_sent`.
- **Transaction** (پرداخت پلن): `amount`، `track_id`، `order_id` (با دنباله uuid)، `status` {pending|success|failed|cancelled}، `zibal_response`، `wallet_amount` (سهم نگه‌داشته‌شده از کیف پول).
- **AddOnPack**: `kind` {sms_pack|appointment_pack|feature}، `sms_amount`، `appointment_amount`، `feature_key`، `duration_days`، `price`، `myket_sku`.
- **AddOnPurchase**: مثل Transaction برای بسته؛ `activated_at`، `expires_at`.
- **CreditLedger**: دفتر حرکت اعتبار؛ `metric` {sms_monthly|sms_wallet|appointment_monthly|appointment_wallet|toman_wallet}، `delta`، `balance_after`، `reason`، `ref_type`، `ref_id`.
- **CreditBalance**: مرجع موجودی؛ (`user`، `metric`، `period`) → `balance`. همه خرج‌ها UPDATE شرطی در `usage.py`.
- **StorePurchase**: خرید مایکت؛ `store` {myket}، `kind` {plan|addon}، `sku`، `payload`، `purchase_token`، `status`، `store_response`، `consumed_at`.
- **SubscriptionReminderSettings** (singleton): `is_enabled`، `days_before`، `message_template`، `last_run_at`.
- **ContactVisibilityPolicy** (singleton): `hide_when_expired`، `hide_phone`، `hide_address`.
- **TomanWalletSettings** (singleton): `sms_unit_price`، `appointment_unit_price`، `new_client_reward`، `new_client_reward_monthly_cap`، `new_client_reward_business_cap`.
- **NewClientReward**: `owner`، `business`، `visitor`، `appointment`، `amount`، `status` {granted|blocked|capped}، `flags`، `decided_by`. یکتا روی (business، visitor).

### business
- **Business** (`business`): هویت (`user`، `title`، `category`، `unique_code`، `bio`، `logo`، `theme_color`)، تماس (`phone`، `extra_phones`، `social_links`)، مکان (`address`، `latitude`، `longitude`، `location_privacy` {EXACT|AREA}، `province`، `city`، `neighborhood`، `city_code`)، فهرست (`is_claimed`، `external_source`، `origin` {SELF|ADMIN|DIRECTORY|CONVERTED}، `online_booking_requests_count`)، رتبه (`rating_avg`، `rating_count`، `rating_histogram`، `rank_score`، `rank_updated_at`، `followers_count`)، ساعت کاری (`work_start_hour/minute`، `work_end_hour/minute`، `closed_weekdays`، `default_service_duration`)، رزرو (`booking_enabled`، `services` (حداکثر ۶۰)، `allow_client_add_service`، `slot_capacity` ۱ تا ۵۰۰، `hold_minutes` ۲ تا ۱۵، `max_appointments_per_hour` قدیمی)، پرداخت (`payment_method` {NONE|CARD|GATEWAY}، `accepted_payment_methods`، `deposit_mode` {NONE|MANDATORY|OPTIONAL}، `deposit_amount` (سقف `MAX_DEPOSIT_AMOUNT = 1_000_000`)، `first_visit_discount` ۰ تا ۵۰، `merchant_id`، `payment_link`، `card_number`، `card_owner_name`)، اعلان (`notification_enabled`، `notification_types`، `notification_minutes_before`، `enable_reminder_sms`، `enable_promotional_sms`، `notify_owner_by_sms`، `reminder_delivery` {MANUAL|PANEL}، `reminder_template` {classic|friendly|short|confirm})، نمایش (`allow_anonymous_view`، `is_locked`، `notice_enabled`، `notice_message`)، moderation (`moderation_status` {PENDING|APPROVED|REJECTED|SUSPENDED}، `moderation_note`، `moderation_reviewed_by/at`، `moderation_submitted_at`، `first_approved_at`، `pending_title`، `pending_bio`، `pending_address`، `pending_logo`)، `insurances` M2M.
- **Insurance**: `name`، `slug`، `insurance_type` {SUPPLEMENTARY|BASIC}، `is_active`، `display_order`، `logo`.
- **ServiceCatalogItem**: خدمات پیشنهادی هر دسته.
- **BusinessModerationLog**: `from_status`، `to_status`، `note`، `actor`، `snapshot`.
- **BannedKeyword**: `term`، `severity` {LOW|HIGH}، `is_active`.
- **ContentReport**: گزارش تخلف؛ `reason` {INAPPROPRIATE|MISLEADING|IMPERSONATION|ILLEGAL|SPAM|OTHER}، گزارش‌دهنده (user یا visitor یا شماره)، `review` اختیاری، `status` {NEW|REVIEWING|ACTIONED|DISMISSED}، `resulting_moderation_log`.
- **OnlineBookingRequest**: درخواست مشتری برای فعال شدن نوبت آنلاین یک مرکز؛ `fingerprint`.
- **BusinessClaimRequest**: ادعای مالکیت مرکز؛ `method` {OTP|MANUAL}، `status` {PENDING|APPROVED|REJECTED}.
- **BusinessFollow**: یکتا روی (business، visitor).
- **PromoCampaign** و **PromoCampaignRecipient**: کمپین پیامک تبلیغاتی اونر؛ `audience` {all|recent|inactive|followers}، شمارنده‌ها، heartbeat؛ گیرنده با `status` {PENDING|SENDING|SENT|FAILED|SKIPPED_QUOTA}.
- **CenterOutreach**: OneToOne با مرکز؛ `status` {NEW|CALLED|INTERESTED|CONVERTED|REJECTED}، `note`، `contacted_at`.
- **proxyها:** هشت صنف `MedicalListing`، `DentalListing`، `HealthListing`، `BeautyListing`، `ProfessionalListing`، `EducationListing`، `TradesListing`، `OtherListing` (`models/guilds.py`) و `CenterListing` (`models/centers.py`).

### visitor
- **Visitor**: `user` (اونری که اول ساختش)، `full_name`، `phone_number`، `marketing_opt_out`، `phone_changed_at`، `avatar`، `bio` (۱۵۰)، `last_client_login_at`.
- **VisitorContact**: نامی که هر اونر برای مشتری گذاشته (`display_name`).
- **VisitorArchive**: بایگانی مشتری توسط اونر.
- **VisitorActivity**: `action` {APPOINTMENT_BOOKED|APPOINTMENT_STATUS_CHANGED|APPOINTMENT_CANCELLED|PROFILE_UPDATED|ARCHIVED_BY_OWNER|RESTORED_BY_OWNER}، `actor_type` {VISITOR|OWNER|SYSTEM}.
- **VisitorDeviceToken**: `platform` {WEB} (پوش وب).
- **SmsLog** (`status` {SENT|FAILED|SKIPPED_QUOTA}) و **PushLog**.

### appointment
- **Appointment**: `business`، `visitor`، `user`، `appointment_date`، `service_duration`، `status` {LOCKED|PENDING_VERIFICATION|PENDING_APPROVAL|CONFIRMED|WAITING|IN_PROGRESS|COMPLETED|NO_SHOW|CANCELLED}، `description`، `selected_services`، `locked_at`، `expires_at`، `tracking_code`، `payment_reference`، `payment_receipt`، `deposit_verified_at`، `deposit_payment_method` {NONE|CARD|GATEWAY}، `quota_source`، سه فیلد زمان ارسال یادآوری، `source` {WEB|APP|OWNER|UNKNOWN}، `travel_distance_m`، `drive_minutes_min/max`، `insurance` FK، `booking_ip`.

### بقیه
- **reviews.Review**: یکی برای هر (business، visitor)؛ `rating`، `text`، `appointment`، `is_hidden`، `hidden_reason`.
- **showcase.PortfolioItem** / **Certificate**: `status` {PENDING|APPROVED|REJECTED}، `reject_reason`، `image`، `thumbnail`، `order`؛ مدرک `title`، `issuer`، `year` هم دارد.
- **stories.Story**: `business` اختیاری (خالی = استوری نوبت‌یار)، `image`، `caption`، `button` {''|discount|link}، `discount_type` {percent|amount}، `discount_value`، `link_label`، `link_url`، `expires_at` (ساخت + ۲۴ ساعت).
- **banners**: `BannerCategoryGroup` (کلید صنف)، `Banner` (دقیقا ۱۲۰۰×۴۰۰، روی صنف یا دسته، زمان‌بندی)، `SiteSetting.banner_link_url`، `ClientPromo` (`placement` {EXPLORE|BUSINESS_MAP|ALL}، مرکز یا لینک).
- **marketing**: `MarketingLead` (`status` {NOT_CALLED|CALLED|INTERESTED|JOINED|NOT_INTERESTED}، `next_follow_up`، `assigned_to`) و `LeadNote`.
- **bale**: `BaleSettings` (singleton: `bot_token`، `chat_id`، `webhook_secret`، `is_enabled`) و `PendingReason` (دلیل رد که منتظر پیام بعدی ادمین است).
- **core**: `AudienceSegment` و `AudienceSegmentExport` (بخش‌بندی visitor/owner با `definition` JSON)، `AdminMessageLog` (پیام مستقیم ادمین با PUSH/SMS)، `MarketingPushLog` (کمپین پوش با شمارنده‌های تحویل)، `ExpirySmsLog` (پیامک انقضای اشتراک: manual|auto|test).
- **outbox.OutboxMessage**: `kind`، `payload`، `dedupe_key`، `status` {pending|processing|done|failed}، `attempts`، `next_attempt_at`، `locked_at`، `done_steps`، `last_error`.
- **versions**: `AppVersion` (`app` {OWNER|CLIENT}، `version_name`، `version_code` یکتا برای هر app، `force_update`، `is_active`) و `VersionChangelog` (`changes` JSON).

## ۴. قرارداد API (همه endpointها)

احراز هویت پیش‌فرض: `IpRecordingJWTAuthentication` (هدر `Authorization: Bearer <access>`) و `IsAuthenticated`. APIهای مشتری با `VisitorTokenAuthentication` (هدر `Authorization: Visitor <token>`). «عمومی» یعنی بدون ورود.

### ۴.۱ سلامت و داخلی
| مسیر | متد | توضیح |
|---|---|---|
| `healthz`، `readyz`، `api/healthz`، `api/readyz` | GET | probe؛ نسخه `/api/` چون nginx فقط `/api` را به Django می‌دهد |
| `api/internal/directory-sync/` | POST | sync فهرست مراکز از n8n؛ `Bearer DIRECTORY_SYNC_TOKEN`؛ بدون توکن تنظیم‌شده 404؛ حداکثر ۵۰۰ ردیف؛ `dry_run` |
| `<ADMIN_URL>` | — | پنل ادمین (پیش‌فرض `admin/`) |
| `media/<path>` | GET | فقط در DEBUG؛ در prod را nginx می‌دهد |

### ۴.۲ auth اونر (`api/`)
| مسیر | متد | توضیح |
|---|---|---|
| `auth/register/` | POST | ثبت‌نام اونر |
| `auth/login/` | POST | ورود با شماره و رمز (throttle `login`) |
| `auth/logout/` | POST | blacklist کردن refresh |
| `auth/otp/send/`، `auth/otp/verify/` | POST | ورود با OTP (throttle `otp`، `otp_verify`) |
| `auth/token/refresh/` | POST | simplejwt؛ refresh چرخشی و blacklist بعد از چرخش |
| `auth/forgot-password/send/`، `verify/`، `reset/` | POST | بازیابی رمز؛ فاصله تغییر رمز `PASSWORD_CHANGE_COOLDOWN_HOURS` (۲۴) |
| `users/<pk>/` | GET، PATCH، DELETE | پروفایل کاربر |
| `devices/register/`، `devices/unregister/` | POST | توکن FCM اونر |

### ۴.۳ حسابداری (`api/accounting/` و همان مسیرها بدون پیشوند در ریشه)
مسیرهای ریشه (`plans/`، `payment-result` و …) alias همین‌ها هستند؛ nginx فقط `plans`، `my-subscription` و `payment-result` را از ریشه به Django می‌دهد.

| مسیر | متد | توضیح |
|---|---|---|
| `plans/` | GET | پلن‌های فعال؛ با هدر `X-Store: myket` فیلد `price_display` قیمت نهایی مایکت است |
| `my-subscription/` | GET | اشتراک فعلی |
| `my-entitlements/` | GET | قابلیت‌ها، سقف‌ها، مصرف، کیف پول، `coming_soon` |
| `wallet/history/` | GET | تاریخچه صفحه‌بندی‌شده `CreditLedger` + موجودی و قیمت واحد |
| `wallet/convert/` | POST | تبدیل کیف پول تومانی به پیامک یا نوبت؛ `kind` = sms\|appointment، `count` ۱ تا ۱۰۰٬۰۰۰ |
| `checkout/quote/` | GET | فاکتور خط‌به‌خط (پایه، VAT، کارمزد، جمع) برای `zibal` یا `myket` |
| `plans/payment/` | POST | خرید پلن با زیبال؛ `use_wallet`/`wallet_only` |
| `payment-result` | GET | callback زیبال پلن |
| `addons/` | GET | بسته‌ها |
| `addons/buy/` | POST | خرید بسته |
| `addons/payment-result` | GET | callback زیبال بسته |
| `store/myket/intent/` | POST | ساخت `StorePurchase` و payload |
| `store/myket/verify/` | POST | تایید سمت سرور با `MYKET_ACCESS_TOKEN` و consume |

`plans/buy/` (فعال‌سازی بدون پرداخت) عمدا حذف شده؛ برنگردد.

### ۴.۴ نسخه (`api/version/`)
| مسیر | متد | توضیح |
|---|---|---|
| `check/` | POST | `app` = OWNER\|CLIENT (بدون app و با کد در `CLIENT_ONLY_CODES` = CLIENT)؛ `force_update`؛ بدون ردیف 404 |
| `changelog/` | GET | تغییرات نسخه‌ها |

### ۴.۵ کسب‌وکار اونر (`api/business/`)
| مسیر | متد | توضیح |
|---|---|---|
| `''`، `<id>/` | GET، POST، PUT، DELETE | لیست، ساخت (multipart، `logo_required=1`)، ویرایش، حذف کسب‌وکار؛ سقف تعداد با `max_businesses` |
| `<id>/sms-logs/`، `sms-logs/summary/` | GET | گزارش پیامک و تصویر سهمیه ماه |
| `<id>/push-logs/`، `push-logs/summary/` | GET | گزارش پوش (رایگان) |
| `<id>/followers/` | GET | فالوورها: فقط نام کوچک، عکس، `created_at` |
| `<id>/booking-capacity/` | GET، PUT | `slot_capacity` و `hold_minutes`؛ ظرفیت بیش از ۱ نیاز به `capacity_control` |
| `<id>/first-visit-discount/` | GET، PUT | درصد تخفیف اولین مراجعه (۰ تا ۵۰) |
| `<id>/promo-sms/` | GET، POST | کمپین‌ها و ساخت کمپین |
| `<id>/promo-sms/preview/` | POST | هزینه دقیق قبل از تایید |
| `<id>/promo-sms/<cid>/` | GET | جزییات کمپین (شماره کامل؛ تست عمدا چک می‌کند) |
| `service-catalog/` | GET، POST | خدمات پیشنهادی دسته |
| `categories/` | GET | دسته‌ها و گروه‌ها |
| `claimable/` | GET | جستجوی مراکز قابل ادعا |
| `<id>/claim/`، `<id>/claim/verify/` | POST | ادعای مالکیت با OTP به شماره مرکز (throttle `claim` ۱۰ در ساعت) |

### ۴.۶ مشتری‌های اونر (`api/visitor/`)
| مسیر | متد | توضیح |
|---|---|---|
| `''`، `<id>/` | GET، POST، PUT، DELETE | لیست صفحه‌بندی، افزودن، ویرایش، بایگانی (DELETE = بایگانی) |
| `<id>/messages/` | GET | تاریخچه پیام؛ `business_id` نامعتبر = صفحه خالی (نه 404) |
| `<id>/restore/` | POST | برگرداندن از بایگانی |

شماره مشتری قفل است اگر هویت مستقل داشته باشد (`has_independent_identity`: ورود کلاینت، توکن پوش، اونر دیگر، نوبت جای دیگر).

### ۴.۷ نوبت اونر (`api/appointment/`)
| مسیر | متد | توضیح |
|---|---|---|
| `''`، `<id>/` | GET، POST، PATCH، DELETE | لیست، ثبت نوبت توسط اونر (`source=OWNER`، بدون چک هم‌پوشانی)، تغییر وضعیت، حذف |
| `stats/` | GET | آمار بازه (`date_from`، `date_to`)، شامل شمارش منبع |
| `daily-counts/` | GET | تعداد روزانه |
| `service-breakdown/` | GET | تفکیک خدمات |
| `range-visitors/` | GET | مشتری‌های بازه |
| `query` | GET | جستجوی نوبت (`AppointmentQuerySerializer` با `first_visit_discount`، `insurance_name`، `insurance_logo`) |

ماشین وضعیت اونر (`appointment/services/transitions.py`):

| از | به |
|---|---|
| PENDING_APPROVAL | WAITING، CANCELLED |
| PENDING_VERIFICATION | WAITING، CANCELLED |
| LOCKED | CANCELLED |
| WAITING | IN_PROGRESS، CONFIRMED، COMPLETED، CANCELLED، NO_SHOW |
| CONFIRMED | WAITING، IN_PROGRESS، COMPLETED، CANCELLED، NO_SHOW |
| IN_PROGRESS | COMPLETED، CANCELLED، NO_SHOW |
| COMPLETED، CANCELLED، NO_SHOW | پایانی |

مشتری فقط از LOCKED، PENDING_APPROVAL، PENDING_VERIFICATION، WAITING، CONFIRMED لغو می‌کند. COMPLETED شدن: درخواست نظر (`reviews.review_request_push`) و پاداش مشتری تازه (`toman_wallet.reward_new_client`).

### ۴.۸ کسب‌وکار برای مشتری (`api/client/businesses/` و alias `api/client/business/`)
| مسیر | متد | احراز | توضیح |
|---|---|---|---|
| `''` | GET | عمومی/Visitor/JWT | لیست و جستجو: `search` (`unique_code` یا متن)، `category`، `city_code`، `lat`/`lng`/`radius_km`، `sort=distance\|recommended`، `only_claimed`، بیمه؛ کش ۶۰ ثانیه برای درخواست بدون Authorization |
| `insurances/` | GET | عمومی | بیمه‌های فعال با لوگو |
| `following/` | GET | Visitor | کسب‌وکارهای فالوشده |
| `<id>/` | GET | عمومی/Visitor/JWT | جزییات؛ کش ۳۰۰ ثانیه؛ `is_following` بیرون از کش |
| `<id>/follow/` | POST، DELETE | Visitor | فالو؛ پوش `NEW_FOLLOWER` یک بار برای همیشه |
| `<id>/report/` | POST | عمومی | گزارش تخلف (throttle ۵ در ساعت) |
| `<id>/request-booking/` | POST | عمومی | «نوبت آنلاین بخواهید» برای مرکز (۱۰ در ساعت) |
| `<id>/showcase/` | GET | عمومی | نمونه‌کار و مدرک تاییدشده |
| `<id>/stories/` | GET | عمومی/Visitor | استوری‌های فعال |

قاعده‌های پاسخ عمومی (`business/public_policy.py`):
- `apply_contact_rules`: اگر اشتراک اونر تمام شده باشد و `ContactVisibilityPolicy` بگوید، تلفن/آدرس `null` می‌شود (نه blur)؛ با پنهان شدن تلفن، `social_links` هم `{}` می‌شود. مشتری واردشده «مهمان خاموش» (`allow_anonymous_view=False`) را دور می‌زند.
- `apply_center_rules`: همیشه `is_noobatyar`، `kind`، `booking_available` می‌دهد؛ برای مرکز (`is_claimed=False`) رزرو، بیعانه، تخفیف، پرداخت و اعلان خاموش است.
- `card_number` و `card_owner_name` در payload عمومی نیستند؛ فقط روی نوبت LOCKED خود مشتری (`ClientAppointmentSerializer`).
- فیلدهای افزوده: `first_free_slot` (epoch ms، فقط کسب‌وکار claimed با رزرو فعال)، `takes_insurance`، `followers_count`، `theme_color`، `is_expired`، `has_location`.
- قرارداد شکل پاسخ در `business/tests_contract_shapes.py` pin شده و تعداد کویری در `tests_hot_endpoint_queries.py` (سقف ۱۴) و `MAX_LIST_QUERIES = 16`.

### ۴.۹ نوبت مشتری (`api/client/appointments/`)
| مسیر | متد | توضیح |
|---|---|---|
| `''` | GET، POST | نوبت‌های من؛ رزرو (`business`، `appointment_date`، `service_duration`، `selected_services`، `description`، `source` WEB\|APP، `insurance`، فیلدهای سفر) |
| `<pk>/pay/` | POST | ثبت پرداخت: CARD با کد پیگیری و رسید (Pillow، حداکثر ۸ مگابایت، تا ۱۶۰۰ پیکسل JPEG) یا CASH |
| `<pk>/pay/online/` | POST | شروع بیعانه آنلاین زیبال (`zibal_deposit.py`) |
| `<pk>/cancel/` | POST | لغو (اعتبار برنمی‌گردد) |
| `deposit-callback/` | GET | بازگشت زیبال؛ `?payment=conflict` اگر اسلات رفته باشد |
| `<business_id>/available-slots/` | GET | اسلات‌های روز برای رزرو |
| `slots/<business_id>/` | GET | اسلات عمومی (throttle `public_slots`)؛ با ظرفیت بیش از ۱ فقط بازه‌های پر با `status = FULL` |

### ۴.۱۰ auth و پروفایل مشتری (`api/client/auth/`، `api/client/activity/`)
| مسیر | متد | توضیح |
|---|---|---|
| `otp/send/`، `otp/verify/` | POST | ورود مشتری با OTP؛ خروجی `Visitor` token |
| `register/` | POST | تکمیل نام |
| `me/` | GET، PATCH | پروفایل؛ PATCH فقط `full_name` (۲ تا ۶۰) و `bio` (۱۵۰) |
| `me/avatar/` | POST، DELETE | عکس مربع ۵۱۲ پیکسل JPEG |
| `devices/register/`، `unregister/` | POST | توکن پوش وب مشتری |
| `api/client/activity/` | GET | فعالیت‌های مشتری |

### ۴.۱۱ نظر، نمونه‌کار، استوری، بنر، سایت
| مسیر | متد | توضیح |
|---|---|---|
| `api/client/reviews/pending/` | GET | نوبت‌های انجام‌شده ۱۴ روز اخیر بی‌نظر |
| `api/client/reviews/business/<id>/` | GET | نظرهای عمومی (`visitor_phone` همیشه خالی) |
| `api/client/reviews/business/<id>/summary/` | GET | میانگین و هیستوگرام |
| `api/client/reviews/business/<id>/mine/` | GET، POST، PUT، DELETE | نظر خودم |
| `api/client/reviews/<id>/report/` | POST | گزارش نظر |
| `api/reviews/owner/` | GET | نظرهای کسب‌وکارهای اونر (شماره پوشیده) |
| `api/owner/showcase/portfolio/` و `certificates/` | GET، POST | لیست و افزودن (قاب ۱:۱، ۴:۵، ۴:۳ با ±۱۰٪، عرض ≥ ۶۰۰، ارتفاع ≥ ۴۵۰، حداکثر ۱۰۸۰×۱۳۵۰) |
| `…/reorder/` | POST | ترتیب |
| `…/<pk>/` | PUT، PATCH، DELETE | ویرایش و حذف |
| `api/owner/stories/`، `<pk>/` | GET، POST، DELETE | استوری اونر (حداکثر ۱۰ فعال، ۲۴ ساعت) |
| `api/client/stories/feed/` | GET | نوبت‌یار، بعد فالوشده‌ها، بعد همان شهر (`city_code`) |
| `api/client/stories/platform/` | GET | استوری‌های نوبت‌یار |
| `api/client/stories/active/` | GET | شناسه کسب‌وکارهای دارای استوری فعال |
| `api/owner/banners/` | GET | بنر چرخشی ۳:۱ اپ کارفرما |
| `api/client/promos/?placement=` | GET | نوار تبلیغ اپ مشتری |
| `api/public/site/active-customers/`، `newest-businesses/` | GET | داده سایت وردپرس |
| `api/bale/webhook/<secret>/` | POST | webhook ربات بله |

## ۵. جریان‌ها

### ۵.۱ OTP و JWT
- OTP (`api/services/otp.py`، `api/otp.py`): اعتبار کد ۱۸۰ ثانیه، cooldown ارسال ۱۸۰ ثانیه با `cache.add`، ۵ حدس برای هر کد، ۵ ارسال در ساعت برای هر (شماره، IP)، ۱۵ ارسال موفق در روز برای هر شماره، verify ده بار در دقیقه برای هر (شماره، IP). قطعی Redis = پیام «سرویس موقتا در دسترس نیست». کد لاگ نمی‌شود. در DEBUG با `OTP_DEV_CODE` و `SMS_DEV_MODE` پیامک واقعی نمی‌رود (خارج از DEBUG هر دو اجبارا خاموش‌اند).
- ارسال پیامک: `integrations/melipayamak.py` (`MELIPAYAMAK_OTP_TOKEN`، `MELIPAYAMAK_FROM`).
- JWT: access ۲۴ ساعت (`JWT_ACCESS_HOURS`)، refresh ۱۴ روز (`JWT_REFRESH_DAYS`)، چرخشی با blacklist.
- IP مورد اعتماد: `api/throttles.client_ip()` بر اساس `NUM_PROXIES` (هرگز چپ‌ترین XFF).
- مشتری: `visitor/auth.py` → `VisitorTokenAuthentication` با keyword `Visitor`؛ توکنِ صادرشده قبل از `phone_changed_at` رد می‌شود.

### ۵.۲ رزرو مشتری
1. `available-slots` (grid ساعت کاری، تعطیلی، occupancy، ظرفیت با `peak_concurrency`).
2. `client_book` (`services/client_booking.py`): اعتبارسنجی (ساعت کاری `working_hours.py`، تعطیلی، زمان گذشته، مدت ۱ تا ۴۸۰ دقیقه)، `find_conflict(..., capacity)`، کسر اعتبار نوبت (`record_appointment`) داخل همان تراکنش (بدون اعتبار نوبتی ساخته نمی‌شود)، بیمه فقط اگر بیمه تکمیلی فعال خود کسب‌وکار و `takes_insurance` باشد، `booking_ip`.
3. وضعیت شروع: اگر پرداخت لازم باشد (`business_requires_payment`) → LOCKED با `expires_at = lock_expiry(now, hold_minutes)`؛ وگرنه PENDING_APPROVAL.
4. side effectها در `on_commit` و از Outbox: پوش `NEW_BOOKING` و پیامک به اونر (`appointment.booking_sms`).
5. قفل منقضی lazy بسته می‌شود (`appointment_expire_lock_if_due`، اعتبار خرج‌شده می‌ماند).

### ۵.۳ بیعانه
- `deposit_collectable(business)`: کارت با شماره، یا آنلاین با مرچنت یا لینک. نقد حساب نیست.
- `OPTIONAL` همیشه مرحله پرداخت دارد؛ `MANDATORY` فقط اگر قابل دریافت باشد.
- کارت‌به‌کارت: `pay/` با رسید → PENDING_VERIFICATION → اونر تایید (WAITING) یا رد.
- نقد: → PENDING_APPROVAL؛ برای MANDATORY قابل‌دریافت رد می‌شود.
- آنلاین: `pay/online/` → `track_id` در `payment_reference` و زمان نگه‌داشتن دست‌کم `GATEWAY_HOLD_MINUTES = 10` → زیبال → `deposit-callback` → `deposit_settle(track_id)`: verify خارج از قفل، بعد UPDATE شرطی LOCKED → WAITING با `deposit_payment_method=GATEWAY`. اگر اسلات در این فاصله گرفته شده باشد نتیجه `conflict` است، `deposit_verified_at` پر می‌شود و در ادمین فیلتر «پرداخت‌شده بدون نوبت» برای برگشت دستی پول دیده می‌شود.
- همه مبلغ‌ها تومان؛ زیبال ریال می‌گیرد (`amount*10`).

### ۵.۴ پرداخت اشتراک و بسته
- زیبال: `checkout/quote` → `plans/payment/` یا `addons/buy/` (`order_id` با دنباله uuid) → زیبال → `payment-result` → `Subscription` یا `AddOnPurchase` + `Transaction` و `CreditLedger`.
- idempotent: تکرار callback همان پاسخ موفق؛ کد ۲۰۱ زیبال موفق؛ خطای شبکه pending (503)؛ فقط ۲۰۲/۲۰۳ failed و هرگز روی رکورد success؛ مبلغ تاییدشده باید `amount*10` ریال باشد؛ اعتبار بسته داخل همان تراکنش.
- کیف پول: `use_wallet` (بخشی) یا `wallet_only`. سهم کیف پول یا کامل است یا باید حداقل `MIN_SELLABLE_PRICE` (۱۰٬۰۰۰) برای زیبال بماند؛ مایکت همه یا هیچ. پول با `hold()` روی `wallet_amount` نگه داشته می‌شود و با رد درگاه، verify ناموفق یا رها شدن یک ساعته (`release_stale_holds`) با `give_back` idempotent برمی‌گردد.
- مایکت: `store/myket/intent/` → خرید در اپ → `store/myket/verify/` (`integrations/myket.py`، consume) → فعال‌سازی. بدون `MYKET_ACCESS_TOKEN` خرید با خطای روشن رد می‌شود.
- entitlements: `get_entitlements` با کش ۶۰ ثانیه (`ent:{version}:{user}`) و پاک شدن با `accounting/signals.py`.

### ۵.۵ پوش و پیامک
- FCM: `integrations/fcm.py`، `api/services/push.py` (`send_to_user`)؛ کانال اندروید `FCM_ANDROID_CHANNEL_ID` (`appointment_reminders`)، کلیک وب `FCM_WEBPUSH_CLICK_URL`. کلید سرویس روی سرور در `/etc/nobatyar/fcm.json` (read-only mount).
- نوع پوش‌ها (`type` در data): `NEW_BOOKING`، `APPOINTMENT_REMINDER`، `NEW_REVIEW`، `NEW_FOLLOWER`، `NEW_STORY`، `REVIEW_REQUEST`، `MARKETING`.
- پیامک پولی فقط از `sms_billing.send_metered_sms` (مصرف، ارسال، refund با خطای provider، SmsLog).
- پوش اونر پشت قابلیت `push_notifications` است؛ پوش درخواست نظر به مشتری رایگان و بدون محدودیت پلن.

### ۵.۶ Outbox
`core/outbox`: `enqueue` داخل تراکنش با `dedupe_key`؛ `manage.py run_outbox --lane default|bulk|all`؛ claim تک‌پیام با SKIP LOCKED، heartbeat، `done_steps` تا ارسال یا کسر دوباره رخ ندهد.

handlerهای ثبت‌شده: `appointment.booking_sms`، `appointment.client_sms`، `bale.owner_moderation_notify`، `bale.review_card`، `bale.showcase_card`، `business.new_follower_push`، `core.admin_message`، `core.expiry_sms_chunk`، `core.push_campaign_chunk`، `promo.campaign`، `reviews.new_review_push`، `reviews.review_request_push`، `stories.notify_followers` (به‌علاوه `test.*` فقط در تست).

### ۵.۷ چرخه اشتراک
`run_subscription_lifecycle`: یادآوری قبل از انقضا (`SubscriptionReminderSettings`)، انقضا، مهلت `SUBSCRIPTION_GRACE_DAYS` (پیش‌فرض ۳ روز، فقط وضعیت و قفل کسب‌وکار)، پیامک بعد از انقضا، `release_stale_holds`. سهمیه ماهانه با تمام شدن `ends_at` فورا صفر می‌شود؛ کیف پول بسته‌ها منقضی نمی‌شود. دوره سهمیه هنوز ماه UTC است.

### ۵.۸ moderation
ساخت/ویرایش کسب‌وکار → `PENDING` با فیلدهای `pending_*` → بررسی در ادمین یا ربات بله (کارت بررسی با دکمه تایید/رد و دلیل از `PendingReason`) → `BusinessModerationLog`. متن پیامک تبلیغاتی با `BannedKeyword` (`sms_moderation.py`) بررسی می‌شود. نمونه‌کار و مدرک هم صف بررسی دارند (`bale.showcase_card`).

## ۶. management commandها

| command | کار | زمان‌بندی |
|---|---|---|
| `send_appointment_reminders` | یادآوری نوبت (claim شرطی، یک بار) | crontab هر ۵ دقیقه |
| `run_subscription_lifecycle` | یادآوری، انقضا، مهلت، قفل کسب‌وکار مازاد، آزادسازی hold | crontab هر ساعت |
| `send_promo_campaigns` | ادامه کمپین‌های پیامکی | دستی/دوره‌ای |
| `compute_rank_scores` | رتبه کسب‌وکار (ترتیب پیش‌فرض لیست عمومی) | crontab هر شب ۰۳:۳۰ |
| `run_outbox` | worker صف | سرویس docker |
| `myket_prices` | قیمت SKUهای مایکت برای وارد کردن دستی | دستی بعد از هر تغییر قیمت |
| `seed_plans` | ساخت/به‌روزرسانی پلن و بسته | دستی |
| `migrate_credits_to_db` | انتقال اعتبار به `CreditBalance` (`--overwrite` خطرناک) | یک‌باره |
| `rebuild_wallets_from_ledger` | بازسازی موجودی از دفتر | دستی |
| `toman_wallet_credit` | شارژ دستی کیف پول تومانی | دستی |
| `seed_insurances`، `seed_insurance_logos [--force]` | بیمه‌ها و لوگوها | دستی، idempotent |
| `seed_banned_keywords` | کلمه‌های ممنوع | دستی |
| `import_directory` | ورود فهرست از فایل | دستی |
| `seed_directory_demo`، `seed_demo_businesses --count N` | داده دمو | فقط لوکال/staging |
| `bale_setup`، `bale_status` | راه‌اندازی و وضعیت ربات بله | دستی |
| `setup_admin_roles` | گروه‌های دسترسی ادمین | دستی |

## ۷. پنل ادمین
- `core/admin_site.py` (جدول route) + `core/admin_views/` و `dashboard/`؛ ظاهر unfold، فارسی و RTL همیشگی (بدون `LocaleMiddleware`).
- منو (`UNFOLD.SIDEBAR.navigation`): بررسی، کاربران، رشد، پیام، مالی، سیستم؛ عنوان آیتم حداکثر دو کلمه (`core/tests_admin_menu.py`). منوی «مراکز» و «ثبت مرکز» (هشت صنف، `_GUILD_MENU` دستی و با تست مقایسه می‌شود).
- ابزارها: صف moderation، Business 360 و User 360، سازنده بخش (segment)، کمپین پوش و پیامک انقضا، گزارش مالی و پیامک، تراکنش‌ها، گزارش محتوا، پاداش مشتری (با اکشن «پرداخت پاداش»)، ورود فایل مرکز (`/admin/business/business/import-file/?guild=KEY`)، مراکز و پیگیری تماس (CSV)، لید بازاریابی، استوری نوبت‌یار، بنر و نوار تبلیغ.
- CSS با `_versioned_static` نسخه می‌گیرد؛ نسخه موبایل تا ۳۷۵ پیکسل بدون اسکرول افقی.
- مستند جزییات: `backend/docs/ADMIN_PANEL.md`.

## ۸. تنظیمات و env

| متغیر | پیش‌فرض | معنی |
|---|---|---|
| `DEBUG` | False | بدون True محلی، نبود `SECRET_KEY` خطا می‌دهد |
| `SECRET_KEY` | — | کلید Django |
| `ALLOWED_HOSTS` | `127.0.0.1,10.0.2.2,localhost` | برای n8n روی سرور `web` هم لازم است |
| `CORS_ALLOWED_ORIGINS` | — | در DEBUG همه مجازند؛ `CSRF_TRUSTED_ORIGINS` از این و `ALLOWED_HOSTS` ساخته می‌شود |
| `ADMIN_URL` | `admin/` | تغییرش یعنی تغییر regex nginx |
| `POSTGRES_DB/USER/PASSWORD/HOST/PORT`، `DB_CONN_MAX_AGE` | — | Postgres (prod از pgbouncer:6432، `DB_CONN_MAX_AGE=0`) |
| `REDIS_URL` | `redis://redis:6379/1` | کش و throttle (`DJANGO_REDIS_IGNORE_EXCEPTIONS`) |
| `NUM_PROXIES` | 0 در DEBUG، وگرنه 1 | IP واقعی |
| `THROTTLE_ANON`، `THROTTLE_USER`، `THROTTLE_OTP`، `THROTTLE_OTP_VERIFY`، `THROTTLE_LOGIN`، `THROTTLE_PUBLIC_SLOTS`، `THROTTLE_CONTENT_REPORT`، `THROTTLE_REQUEST_BOOKING`، `THROTTLE_CLAIM` | 60/min، 300/min، 5/min، 30/min، 10/min، 120/min، 5/hour، 10/hour، 10/hour | سقف درخواست |
| `JWT_ACCESS_HOURS`، `JWT_REFRESH_DAYS` | 24، 14 | عمر توکن |
| `ZIBAL_MERCHANT_ID` | `zibal` در DEBUG (sandbox) | مرچنت زیبال |
| `MYKET_ACCESS_TOKEN`، `MYKET_PACKAGE_NAME` | ''، `xyz.sattar.javid.proqueue` | تایید خرید مایکت |
| `MELIPAYAMAK_OTP_TOKEN`، `MELIPAYAMAK_FROM` | '' | پیامک |
| `OTP_DEV_CODE`، `SMS_DEV_MODE` | فقط با DEBUG | توسعه |
| `PASSWORD_CHANGE_COOLDOWN_HOURS` | 24 | |
| `FCM_CREDENTIALS_FILE`، `FCM_PROJECT_ID`، `FCM_ANDROID_CHANNEL_ID`، `FCM_WEBPUSH_CLICK_URL` | | پوش |
| `CLIENT_WEB_URL`، `PANEL_WEB_URL`، `SITE_URL` | `https://app.noobatyar.ir`، `https://panel.noobatyar.ir`، `http://localhost:8000` | آدرس بازگشت درگاه و لینک‌ها |
| `DIRECTORY_SYNC_TOKEN` | '' (endpoint خاموش) | sync n8n |
| `SUBSCRIPTION_GRACE_DAYS` | 3 | مهلت بعد از انقضا |
| `LOG_JSON`، `LOG_LEVEL`، `SLOW_QUERY_LOG`، `SLOW_QUERY_MS` | | لاگ |
| `SEED_BUSINESS_ID` | | فقط اسکریپت seed |
| `DEPLOY_HOST/PORT/USER/PASSWORD`، `DJANGO_SECRET_KEY` | | فقط `backend/deploy.py` (و `scripts/remote.sh`)؛ مقدار هرگز در git یا سند نیاید |

ثابت‌های غیر env: `PUBLIC_DIRECTORY_CACHE_ENABLED` (کش لیست عمومی)، `LANGUAGE_CODE='fa-ir'`، `AUTH_USER_MODEL='api.User'`.

فایل‌ها: `backend/.env.local` (لوکال، از `.env.example`)، prod از `.env` ریشه که docker compose می‌خواند. `backend/.env.n8n` برای n8n لوکال (git-ignore).

## ۹. اجرا، تست و ریلیز

```bash
cd backend && .venv/Scripts/python manage.py migrate      # ویندوز؛ روی مک .venv/bin/python
cd backend && .venv/Scripts/python manage.py runserver 8000
cd backend && .venv/Scripts/python manage.py test         # ترتیبی؛ حدود ۱۲۳۳ تست
```
- تست‌ها کنار هر app با نام `tests*.py`؛ مهم‌ها: `core/tests_tenant_isolation.py` (جداسازی داده)، `business/tests_contract_shapes.py`، `tests_hot_endpoint_queries.py`، `accounting/tests_payment_idempotency.py`، `tests_pricing.py`، `tests_myket.py`، `appointment/tests_capacity_hold.py`، `tests_uncollectable_deposit.py`، `business/tests_directory_sync.py`، `tests_centers.py`.
- تست روی Postgres: `postgres:16-alpine` روی پورت 55432. `--parallel` اگر تستی fail شود با خطای pickle می‌شکند.
- CI: `.github/workflows/ci.yml` (جزییات در [ops](../ops/TECHNICAL.md)).
- دیپلوی: rebuild سرویس‌های `web`، `worker`، `worker_bulk` با `--no-deps --build` و بعد حتما `nginx -s reload` (وگرنه 502). جزییات در [ops](../ops/TECHNICAL.md).

## ۱۰. مسایل باز (از کد)
- بلوک `server_name api.noobatyar.ir` در `backend/nginx/nginx.conf` مسیر `/api/internal/` را 404 نمی‌کند (بلوک‌های `app.` و پیش‌فرض می‌کنند). endpoint با توکن محافظت می‌شود، ولی طبق طرح باید از بیرون بسته باشد.
- باگ مرز روز تهران (A3) هنوز merge نشده.
- دوره سهمیه ماهانه به ماه UTC است، نه تهران.
- جزییات کمپین پیامک (`promo-sms/<id>/`) شماره کامل گیرنده را می‌دهد.
- فیلدهای سفر نوبت (`travel_distance_m`، `drive_minutes_*`) ذخیره می‌شوند ولی در اپ اونر و ادمین نمایش داده نمی‌شوند.
- `favicon.ico` روی ادمین api خطای ۵۰۰ می‌دهد.
- قابلیت `multi_channel` (واتساپ/تلگرام) فروخته می‌شود ولی پیاده نشده (`COMING_SOON_FEATURES`).
