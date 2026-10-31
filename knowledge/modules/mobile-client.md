---
updated: 2026-11-01
tags: [gf]
---
# اپ موبایل مشتری (mobile_client — برنچ `mobile-client`)

**هدف:** کشف کسب‌وکار روی نقشه (نزدیک‌ترین پزشک/سالن…)، فیلتر بیمه، نشان کردن، رزرو؛ و فهرست کسب‌وکارهای واردشده (unclaimed) که owner می‌تواند claim کند.

## محل
- روی develop پوشهٔ `mobile_client/` **خالی** است. کد روی برنچ `mobile-client`، checkout‌شده به‌صورت worktree در `../Noobatyar-mobile-client`.
- worktree قدیمی دیگری هم هست: `../wt-mobile-client` (detached، `5515f31`).

## Backend روی این برنچ
- `business`: `Insurance`، فیلدهای `province/city/neighborhood/is_claimed/external_source/online_booking_requests_count`، `OnlineBookingRequest`، `BusinessClaimRequest`؛ `claim_views.py`، `claims.py`؛ command `import_directory`، `seed_directory_demo`
- endpointها: `api/client/businesses/` (geo: `lat/lng/radius_km`، `sort=distance|recommended`، `only_claimed`)، `business/claimable/`، `business/<id>/claim[/verify]/`
- migrationها: `0028_insurance_...` و `0029_claims_...` (بعد از 0027 در develop)

## شهر و صفحهٔ کاوش
- اولین اجرا: `feature/welcome/WelcomeScreen.kt` دکمهٔ «انتخاب شهر» → کاوش، که انتخابگر را خودش باز می‌کند.
- `feature/explore/CityPickerSheet.kt`: انتخابگر به سبک دیوار (تک‌انتخابی؛ جستجو، انتخاب‌های قبلی، «کل ایران»، استان → شهر). داده: `core/location/IranCities.kt` از `composeResources/files/iran_cities.json`.
- `SearchArea.City(code, …)` با `city_code` جستجو می‌شود (نه lat/lng)؛ مرکز شهر فقط برای نقشه.
- انتخاب در `PreferencesManager.recentCityCodes` / `selectCity` (Android و iOS) ذخیره می‌شود؛ `ALL_IRAN = 0`.
- پیدا کردن خودکار GPS هنگام باز شدن حذف شد؛ دکمهٔ موقعیت روی نقشه مانده.
- `MAP_ENABLED` در `ExploreScreen.kt`: `false` = فقط لیست با کشیدن برای بروزرسانی (الان `true`).
- (قدیمی) رزرو قبلاً سایت را باز می‌کرد؛ حالا رزرو نیتیو است (پایین‌تر).

## اجرا
- پکیج `xyz.sattar.javid.proqueue.client.local`؛ `:composeApp:assembleLocalDebug`؛ نصب با `adb install -r .../apk/local/debug/composeApp-local-arm64-v8a-debug.apk` (`installLocalDebug` به‌خاطر ABI split خطا می‌دهد)
- بک‌اند باید **از worktree** اجرا شود: preview `backend_mobile_client_mac` (پایتون venv repo اصلی؛ `.env.local` در worktree کپی شده)

## نکته‌ها
- موقعیت امولاتور Mountain View است → در اپ «تهران» را انتخاب کن (`adb emu geo fix` اثر ندارد).
- کاشی OSM از ایران کند است → `tile.openstreetmap.fr/hot`.
- کسب‌وکارهای واردشده مال کاربر سیستمی‌اند → از قانون اشتراک در `apply_contact_rules` مستثنا شدند.
- migrationهای این برنچ با Django 6.0.7 ساخته شده بودند؛ venv و prod روی 4.2.29 هستند.
- امولاتور ممکن است از snapshot قدیمی (اپ ۲۶ اکتبر) بالا بیاید → APK را دوباره نصب کن.
- کاشی OSM ۲۵۶px است و روی صفحهٔ پرتراکم تار دیده می‌شود؛ سرویس بهتر بی‌کلید و تجاری پیدا نشد.

## وابستگی‌ها
[[modules/backend-business]]، [[modules/backend-visitor]]، [[modules/backend-appointment]].

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- کد اونری حذف شد (۱۴۷ فایل). Room بدون DAO و schema نسخهٔ 17 بدون تغییر. `ClientBusinessApi`، `ClientPreferences`، `CityCatalog`، `core/config/BrandConfig.kt`.
- تست: `bash ./gradlew :composeApp:testLocalDebugUnitTest` (۲۴ تست). iOS: `:composeApp:compileKotlinIosSimulatorArm64`. `PushMessagingService` در manifest نیست.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰
- رزرو نیتیو در `feature/booking/` (`BookingViewModel` و `BookingScreen`): خدمت ← روز و ساعت ← OTP (فقط بار اول؛ توکن در `ClientPreferences.visitorToken`) ← تأیید ← بیعانه (آنلاین، کارت با شمارهٔ پیگیری، یا حضوری). نوبت با `source=APP` ثبت می‌شود.
- `feature/appointments/`: تب «نوبت‌های من» با لغو (دیالوگ تأیید)، پرداخت آنلاین، ثبت واریز و پرداخت حضوری. لیست روی resume تازه می‌شود.
- API در `data/remoteDataSource/booking/BookingApi.kt` (هدر `Visitor <token>`) و `social/` (نمونه‌کار و نظرات).
- صفحهٔ کسب‌وکار: `BusinessSocialViewModel` گالری با واترمارک، مدارک و نظرات (ثبت، ویرایش، حذف، گزارش) را می‌آورد.
- `first_free_slot` روی `BusinessRow` با «امروز / فردا / روز هفته» نمایش داده می‌شود.
- نکته‌ها:
  - `AuthPlugin` به همهٔ مسیرهای `/client/` دست نمی‌زند، تا Bearer روی هدر Visitor ننشیند.
  - Json مقدارهای پیش‌فرض را encode نمی‌کند، پس فیلدهای اجباری نباید default داشته باشند.
  - `SystemBackHandler` به صورت expect/actual نوشته شده (navigationevent فقط روی اندروید است).
  - شماره کارت با `formatCardNumber` (LTR isolate) نمایش داده می‌شود.
- ریلیز: `.github/workflows/android-release-client.yml` با تگ `client-v*.*.*`. پیش‌نیازش بخش `## client-vX.Y.Z` در CHANGELOG است.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰ (عصر)
- `feature/businessDetail/PaymentInfo.kt`: `paymentInfo(business)` خط بیعانه و برچسب روش‌های پرداخت را می‌سازد (`ONLINE/GATEWAY/CARD/CASH`)؛ `formatToman` (۱۵۰٬۰۰۰ تومان). بخش «بیعانه و پرداخت» در `ClientBusinessDetailScreen` فقط برای مراکز claim‌شده. تست: `commonTest/.../PaymentInfoTest.kt`.
- مقدار `deposit_mode` سرور `MANDATORY/OPTIONAL/NONE` است، نه `REQUIRED`؛ از ثابت‌های `DEPOSIT_MANDATORY`/`DEPOSIT_OPTIONAL` استفاده کن.
- تسک تست Gradle: `testProdDebugUnitTest` یا `testLocalDebugUnitTest` (`testDebugUnitTest` مبهم است).

## به‌روزرسانی ۲۰۲۶-۱۰-۳۱
- **رزرو:**
  - `feature/booking/`: `BookingFlow.kt` منطق خالص دارد (مرحله‌ها، قواعد پرداخت مثل وب، خدمت دلخواه). `BookingMotion.kt` و `BookingComponents.kt` بخش انیمیشن و اجزای صفحه‌اند.
  - `ReceiptPicker.kt` عکس رسید را انتخاب و با پیشرفت آپلود می‌کند.
  - `Booking(businessId, resumeAppointmentId)` پرداخت نوبتی را که قبلاً قفل شده ادامه می‌دهد.
- **نوبت‌ها:**
  - `feature/appointments/`: تب‌ها، `AppointmentDetailScreen` (فیش نوبت) و `AppointmentRules.kt`.
  - `ClientAppointmentDto` فیلدهای فیش را دارد (خدمات، توضیح، شمارهٔ پیگیری، رسید، روش پرداخت).
- **صفحهٔ مرکز:**
  - `feature/businessDetail/`: `DetailCards.kt` (`GroupCard`، `LinkRow`).
  - `BusinessMapScreen` نقشهٔ کامل با خط منحنی و `PinnedAt` است. `BusinessMapViewModel` فاصله را حساب می‌کند و موقعیت را ذخیره می‌کند.
- **موقعیت و تبلیغ:**
  - `core/prefs/SavedLocation`: آخرین موقعیت کاربر. `core/location/TravelEstimate.kt`: تخمین زمان رانندگی.
  - `feature/promo/` و `data/remoteDataSource/promo/`: نوار تبلیغ `ClientPromo`.
- **نقشه:**
  - همهٔ نقشه‌ها هنوز MapCompose هستند: کاشی HOT با `TileRecolor` در `core/ui/components/map/TileMap.kt`.
  - نسخهٔ MapLibre و Map.ir ساخته شد و کار کرد (`1d91e0f`، `e5d8ffd`)، ولی کاربر آن را رد کرد و در `0bc39e3` برگشت خورد.
  - فقط با کلید Map.ir و بعد از مقایسهٔ کنار هم روی دستگاه برگردد.
  - در آن نسخه، `MapLibre.getInstance` باید **قبل** از `setOkHttpClient` صدا زده شود.
- **نکته‌ها:**
  - overlay پین‌ها باید اندازهٔ کامل والد را بگیرد (مشکل RTL).
  - lifecycle 2.10.0 است.

## لاگین، تایپوگرافی، آیکن (2026-10-31)
- `feature/auth/LoginScreen.kt` + `LoginViewModel` — شماره، کد ۶ رقمی (تایید خودکار)، نام؛ مسیر `AppScreens.Login(bookBusinessId)`. رزرو بدون توکن Visitor به لاگین می‌رود (`MainNavHost`)؛ صفحهٔ مرکز برای مهمان کارت قفل نشان می‌دهد.
- `KtorClientBusinessApi` توکن Visitor را روی لیست و جزییات می‌فرستد؛ با ۴۰۱ توکن را پاک و دوباره مهمان می‌خواند.
- `ui/theme/Typography.kt`: Yekan Bakh، بدون `letterSpacing`؛ `bodyMedium` پیش‌فرض متن و فیلدها (`Theme.kt`).
- آیکن‌ها: Lucide (`com.composables:icons-lucide-cmp`)، پر با `ImageVector.filled()`. پیکان‌های RTL دستی برعکس‌اند.
- متن اپ: کوتاه و بدون ئ ء ٔ أ. `PersianDigits` برای ارقام فیلدهای عددی.

## کاوش، ناحیه، مسیریابی (2026-10-31 شب)
- `CategoryGroup.supportsInsurance`: فیلتر بیمه فقط برای ALL/MEDICAL/DENTAL/HEALTH؛ `selectCategory` بیمهٔ انتخاب‌شده را برای صنف بی‌بیمه پاک می‌کند.
- `ExploreMap`: برای هر ناحیه (شهر، نزدیک من، Picked، شعاع) `fitArea` با حاشیهٔ کم؛ نشان کمرنگ با Canvas در مرکز حلقه. فقط «همه شهرها» روی پین‌ها فیت می‌شود.
- `core/location/DirectionsGate.kt` (`rememberDirections()`): همهٔ دکمه‌های مسیریابی؛ بدون موقعیت نقشه باز نمی‌شود؛ موقعیت را ذخیره می‌کند. پرچم `awaitingSettings` با `LifecycleResumeEffect(Unit)` (کلید دیگر نگذار).
- موقعیت کاربر ذخیره نمی‌شود؛ فقط `travel_distance_m` و بازهٔ رانندگی روی نوبت (به اونر نشان داده نمی‌شود).

## «بیمه من» (2026-11-01)
- `PreferencesManager.myInsuranceSlug` / `setMyInsuranceSlug`؛ `MyInsuranceViewModel` و ردیف «بیمه تکمیلی من» در `MoreScreen` (با `InsuranceSheet`).
- `ExploreState.withMyInsurance()`: فقط دسته‌های پزشکی (نه «همه») خودکار با بیمهٔ من فیلتر می‌شوند؛ `insuranceOptedOut` وقتی کاربر عمداً «همه بیمه‌ها» را می‌زند.
- `LocalMyInsuranceSlug` (در `MainNavHost`) و `Business.acceptsInsurance(slug)`: خط «بیمه شما قبول است» روی کارت (`MetaLine`) و یادداشت در کارت بیمهٔ صفحهٔ مرکز.
