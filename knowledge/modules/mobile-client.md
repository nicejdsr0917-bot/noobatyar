---
updated: 2026-11-05
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
- پکیج `xyz.sattar.javid.proqueue.client.local`؛ `:composeApp:assembleLocalSiteDebug`؛ نصب با `adb install -r .../apk/localSite/debug/composeApp-local-site-arm64-v8a-debug.apk` (`installLocalDebug` به‌خاطر ABI split خطا می‌دهد)
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
- تست: `bash ./gradlew :composeApp:testLocalSiteDebugUnitTest` (۲۴ تست). iOS: `:composeApp:compileKotlinIosSimulatorArm64`. `PushMessagingService` در manifest نیست.

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
- تسک تست Gradle: `testProdSiteDebugUnitTest` یا `testLocalSiteDebugUnitTest` (`testDebugUnitTest` مبهم است).

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
- `PreferencesManager.myInsuranceSlug` / `setMyInsuranceSlug`؛ `MyInsuranceViewModel` و ردیف «بیمه تکمیلی من» در `ProfileScreen` (با `InsuranceSheet`). `MoreScreen` حذف شد.
- `ExploreState.withMyInsurance()`: فقط دسته‌های پزشکی (نه «همه») خودکار با بیمهٔ من فیلتر می‌شوند؛ `insuranceOptedOut` وقتی کاربر عمداً «همه بیمه‌ها» را می‌زند.
- `LocalMyInsuranceSlug` (در `MainNavHost`) و `Business.acceptsInsurance(slug)`: خط «بیمه شما قبول است» روی کارت (`MetaLine`) و یادداشت در کارت بیمهٔ صفحهٔ مرکز.

## صفحه‌ی مرکز، پروفایل، کش نقشه (2026-11-01)
- **فقط اندروید.** iOS بیلد یا بررسی نشود. actual لازم برای iosMain فقط نوشته می‌شود.
- **`feature/businessDetail/DetailHero.kt`:**
  - `HeroCollapse(scroll)`: پیشرفت بسته شدن هدر. فقط در `graphicsLayer` و `drawBehind` خوانده می‌شود، پس اسکرول صفحه را دوباره نمی‌سازد.
  - `window(from, to)` هر بخش را روی برش خودش از این پیشرفت متحرک می‌کند.
  - رفتن اسم به تولبار: جای اسم در هدر و تولبار با `onGloballyPositioned` اندازه گرفته می‌شود. این اندازه‌گیری **قبل** از `graphicsLayer` همان المان است، وگرنه حرکت روی نقطه‌ی شروع خودش اثر می‌گذارد.
  - `PinnedDetailBar`: دکمه‌ها از دایره‌ی شیشه‌ای به آیکن ساده تبدیل می‌شوند. پس‌زمینه‌ی تولبار رنگ تم (`surface`) است، نه بنفش.
  - `DetailHero` اگر `logo` باشد عکس تمام‌صفحه نشان می‌دهد، وگرنه گرادیان برند با `BusinessAvatar`.
  - `StatsRow` و `QuickPickSection` (از `DayStrip` رزرو استفاده می‌کند).
- **رزرو با ساعت از پیش انتخاب‌شده:** مسیر `AppScreens.Booking(businessId, resumeAppointmentId, date, slotAt)`. `BookingViewModel.load(..., preDate, preSlotAt)` روز را انتخاب می‌کند و بعد از رسیدن ساعت‌ها، ساعت را. `bookingDays()` بین صفحه‌ی مرکز و رزرو مشترک است.
- **`ClientBusinessDetailViewModel`:** پارامترهای `bookingApi` (اختیاری، آخر لیست) و `refresh()`. `isRefreshing` برای `PullToRefreshBox(floatingIndicator = true)`.
- **`feature/profile/`:**
  - `ProfileViewModel` در `MainNavHost` ساخته و به `ProfileScreen` داده می‌شود، تا آیکن تب (`ProfileTabIcon`) هم با آن به‌روز شود.
  - API در `data/remoteDataSource/profile/ProfileApi.kt` است: `me`، `rename`، `uploadAvatar`، `deleteAvatar`.
  - `MainTab.More` اسمش «پروفایل» است (اسم object عوض نشد).
- **`Business.categoryIcon()`** (در `DiscoveryUi.kt`): آیکن هر گروه صنف برای مراکز بدون لوگو، هم‌خانواده‌ی پین‌های نقشه.
- **`StateMessage`:** ساده و بی‌حرکت (۲۸dp آیکن، عنوان، متن، `OutlinedButton`). انیمیشن و حلقه‌ها به خواست کاربر حذف شد («چیپ»).
- **کاشی‌ها:**
  - `TileDiskCache` در `cacheDir/map-tiles/osm-hot-v1/z/x/y.t`: ۸ بایت زمان ذخیره، بعد PNG رنگ‌شده. با تغییر `TileRecolor`، مقدار `VERSION` را بالا ببر.
  - `loadTile()` اول کش تازه، بعد شبکه، بعد کش کهنه.
  - `prefetchTilesAround(center)` در `MainNavHost`، ۳ ثانیه بعد از باز شدن اپ و با هر تغییر شهر.
- **`BusinessMapScreen`:** دکمه‌های `TripControls` (مبدا و مقصد) با `flyTo`: اول `fitBoth`، بعد زوم روی همان سر.
- **تست آفلاین روی LDPlayer:** وای‌فای را خاموش نکن، چون adb قطع می‌شود. از `settings put global global_http_proxy_host/port/exclusion_list` استفاده کن.

## جلسهٔ 2026-11-02
- **`OtpCodeField`** (`core/ui/components/`): کد تایید خانه‌به‌خانه با حرکت (نشانگر چشمک‌زن، ورود فنری رقم، لرزش وقتی کد کامل خالی برگردد، موج تکمیل). در `LoginScreen` و `BookingScreen` (مرحلهٔ کد). یک `BasicTextField` نامرئی روی ردیف است تا paste و autofill کار کند. پس‌زمینهٔ خانه باید مات باشد (`compositeOver`)، وگرنه سایهٔ رنگی از زیرش پیداست.
- **`ltrInput`** (`BookingScreen.kt`) حالا getter کامپوزبل روی `typography.bodyLarge` است؛ `TextStyle` خالی فونت اپ را با فونت پیش‌فرض عوض می‌کرد.
- **`DetailSocial.kt` → `SocialLinksSection(links, businessTitle, index)`**: ردیف افقی کاشی ۶۰dp با لوگوهای اپ اونر (`ic_brand_*` در drawable؛ PNG برای بله/ایتا/روبیکا)، ورود فنری پلکانی، نوار نور مداوم مثل «درباره ما»ی اونر. عنوان: «{نام کسب‌وکار} در شبکه‌های اجتماعی». URL در اپ ساخته می‌شود.
- **`CityPickerSheet`**: هر لیست state جدا دارد؛ شهرهای هر استان از بالا شروع می‌شوند و لیست استان‌ها جایش را نگه می‌دارد.
- **`BookingFlow.kt`**: `Business.depositCollectable()` (لیست خالی = کارت، مثل `paymentOptions`)؛ `expectsDeposit` آن را هم می‌خواهد. `paymentOptions` برای بیعانهٔ اجباری بی‌روش، نقد می‌دهد.

## جلسهٔ 2026-11-02 (عصر) — کاوش
- **`feature/explore/ExploreSheet.kt`** (جدید): شیت سه‌حالته `SheetStop { Collapsed, Half, Expanded }` با `AnchoredDraggableState`. `ExploreSheetLayout(state, collapsedHeight, halfHeight, …, handle, sheetContent, body)`. `SheetNestedScroll` دست‌به‌دست شیت و لیست را انجام می‌دهد.
  - ⚠️ `AnchoredDraggableState.settle(velocity)` وقتی state بدون threshold ساخته شده کرش می‌کند؛ برای همین `settle` دستی با `closestAnchor` و `FLICK_VELOCITY = 600f`.
  - `COLLAPSED_LIST_PEEK = 52.dp`: حالت پایین فقط تا خط عنوان و تعداد.
- **`ExploreHeader.kt` → `CategoryBar`**: همهٔ ۷ دسته در یک ردیف با `weight(1f)` (کاشی ۴۴dp + `CategoryGroup.shortLabel`). جای `LazyRow` چیپ‌ها.
- **`ExploreScreen.kt`**: `BottomSheetScaffold` حذف شد و `ExploreSheetLayout` جایش آمد. `resultsTitle` اسم دسته را می‌گذارد («مراکز دندان‌پزشکی در تهران»)؛ برای «همه» فقط «مراکز». انتخاب پین روی نقشه شیت را به Half می‌برد.
- **`ExploreMap.kt`**: پارامتر `bottomInset: () -> Dp` دکمه‌های نقشه را بالای شیت نگه می‌دارد.
- دارک مود بخش‌های جدید دیده نشده.

## جلسهٔ 2026-11-05: پروفایل تازه، فالو، کراپ، اسپلش
- **تب پروفایل** (`feature/profile/ProfileScreen.kt`) بازطراحی شد:
  - سطح خنثی، خط `outlineVariant`، آیکون تک‌رنگ، بدون حلقهٔ رنگین‌کمانی.
  - بالای صفحه نام و بیو؛ زیرش `StatsStrip` (نوبت، دنبال می‌کنید، نشان‌شده).
  - `EditProfileSheet`: کراپ (`ImageCropperDialog` + `core/utils/ImageCropper` با actual اندروید و iOS)، بیو با شمارنده (`BIO_MAX_LENGTH`). `ProfileApi.update(name, bio)`.
- **فالو:**
  - `FollowRow` در `DetailIdentity.kt` زیر `StatsRow`.
  - `ClientBusinessDetailViewModel.toggleFollow`: خوش‌بینانه است و با خطا برمی‌گردد. مهمان به ورود می‌رود.
  - `feature/following/` (`AppScreens.Following`).
  - `BusinessRepository.setFollowing` و `getFollowing`.
- نظرها عکس نویسنده را نشان می‌دهند (`ReviewDto.visitorAvatar`، coil).
- `LaunchSplash` و `NoobatyarMark.kt` (از اپ اونر). تم `Theme.Noobatyar` در `res/values*/themes.xml`.
- `CheckVersionRequestDto` حالا `app = "CLIENT"` می‌فرستد.
- flavorهای `site` و `store` از برنچ `mobile-client` در develop ادغام شدند و برنچ `mobile-client` دیگر جدا لازم نیست.
- تست‌ها: `FollowViewModelTest` (۱۴۳ تست کلاینت).

## استوری (2026-11-05)
- **`feature/stories/Stories.kt`:** `StoryApi` و `StoryCenter` (Koin single).
  - `activeIds`: `refresh` حداکثر هر دقیقه یک بار.
  - `open`/`close`/`markSeen`.
  - `follow` از `BusinessRepository.setFollowing`.
- **`StoryViewer.kt`:**
  - `StoryViewerHost()` در `App.kt` است.
  - هر استوری ۵ ثانیه، با دو نیمهٔ لمسی.
  - برچسب تخفیف.
  - دکمهٔ «دنبال کردن» (`FollowButton` با `onImage=true`) فقط وقتی `is_following=false` است (مهمان آن را نمی‌بیند). بعد از فالو می‌ماند تا انیمیشن دیده شود.
- **حلقهٔ بنفش** در خود `BusinessAvatar` (`feature/discovery/DiscoveryUi.kt`) است، پس همه‌جا یکسان دیده می‌شود. `BusinessAvatar` حالا `koinInject<StoryCenter>()` دارد، پس بدون Koin رندر نمی‌شود (preview یا تست UI).

## حرکت و درخواست نظر (2026-11-05 11:18)
- **نوار پایین** (`core/navigation/navHost/MainNavHost.kt`، `TabBar`):
  - crossfade بین آیکن خطی و توپُر، pop هنگام انتخاب، و فرورفتن کوتاه هنگام خروج از انتخاب. موقع باز شدن اپ هیچ آیکنی حرکت نمی‌کند.
  - `SolidTabIcon.kt`: لایهٔ پُر با شکل Lucide، و جزئیات داخل آیکن به رنگ زمینه. `filled()` به درد نمی‌خورد، چون همه‌چیز را یک‌رنگ پر می‌کند.
  - تب پروفایل: بدون عکس، آیکن توپُر `CircleUserRound`. با عکس، `ProfileTabIcon(url: String)` با حلقه‌ای که دور عکس کشیده می‌شود.
- **`BusinessAvatar(business, size, corner = 12.dp)`:** هر جا قاب گردتری دارد شعاعش را پاس بدهد (hero 22dp و نوار جمع‌شده 10dp)، وگرنه گوشه‌های حلقهٔ استوری بریده می‌شوند.
- **`feature/businessDetail/FollowButton.kt`:** گرادیان، برق گذرا، burst و pop. `FollowRow` شمارنده را رول می‌کند.
- **`feature/reviews/ReviewPrompt.kt`:**
  - روی تب‌های اصلی، اگر کاربر وارد شده باشد، `BusinessSocialApi.pendingReviews` را می‌گیرد. این متد در interface پیاده‌سازی پیش‌فرض دارد، پس fakeها لازم نیست override کنند.
  - اولین موردی را که رد نشده نشان می‌دهد.
  - ستاره → `AppScreens.ClientBusinessDetail(id, rate)` → `ReviewDialog(initialRating)`.
  - رد شده‌ها در `PreferencesManager.dismissedReviewPrompts()` ذخیره می‌شوند (۵۰ مورد آخر).
