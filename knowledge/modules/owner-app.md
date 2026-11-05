---
updated: 2026-11-06
tags: [gf]
---
# اپ صاحب کسب‌وکار (mobile_owner)

**هدف:** مدیریت نوبت، مشتری، تقویم، پیامک/پوش تبلیغاتی، اشتراک و پرداخت. Android + iOS + پنل وب wasm (`panel.noobatyar.ir`).

## فایل‌ها
ریشهٔ کد: `mobile_owner/composeApp/src/{commonMain,androidMain,iosMain,wasmJsMain}/kotlin/xyz/sattar/javid/proqueue/`
- `feature/<name>/` — `XScreen.kt` + `XViewModel` (MVI)؛ features: home، calendar، createAppointment، visitorDetails، promo، addons، settings، share، smsReport…
- `data/remoteDataSource/*ApiService.kt` (پاسخ‌ها `ApiResponse`)، `data/localDataSource` (Room؛ wasm: `InMemory*LocalSource`)، `data/repository/*RepositoryImpl`
- `di/KoinModule.kt`، `core/navigation/` (`MainNavHost`، `AuthNavHost`)
- i18n: `composeResources/values/strings.xml` (fa) + `values-en/` → task `generateI18nStrings` کلاس `Str` را می‌سازد؛ کلید جدید را در **هر دو** اضافه کن
- `composeApp/build.gradle.kts` — flavorها (env × store: `LocalSite` …)، `-PlocalBaseUrl`؛ iOS: `ios.baseUrl` در `local.properties` (تغییر commit‌نشده)

## شهر کسب‌وکار (روی برنچ `mobile-client`)
- `core/ui/components/city/CityPicker.kt`: `BusinessCityField` + `CityPickerSheet` (استان → شهر، جستجو) از `composeResources/files/iran_cities.json`.
- فیلد اجباری در مرحلهٔ «اطلاعات تماس» `CreateBusinessRoute.kt`؛ `Business.cityCode/city/province`؛ multipart `city_code`.
- این تغییرات در develop نیست؛ پنل وب (:8080 از repo اصلی) فیلد شهر ندارد.

## اجرا
`./gradlew :composeApp:installLocalSiteDebug -PlocalBaseUrl=http://127.0.0.1:8000` (بدون آن، flavor local به **production** وصل می‌شود). پکیج: `xyz.sattar.javid.proqueue.local`. در worktree، gradlew اجرایی نیست → `java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain` با `JAVA_HOME` = jbr اندروید استودیو و `ANDROID_HOME`.

## وابستگی‌ها
همهٔ APIهای owner در backend؛ [[modules/backend-accounting]] (FeatureGate، checkout).

## نکته‌ها
- TODO: `iosMain/.../Theme.ios.kt` (status bar)، `wasmJsMain/.../ContactActions.wasmJs.kt`.
- ریلیز APK فقط از CI با تگ — [[modules/ops-deploy]].

## تغییرات ۲۰۲۶-۱۰-۳۰ (برنچ mobile-client)
- تست: `bash ./gradlew :composeApp:testLocalSiteDebugUnitTest` (۵۰ تست؛ هرگز allTests). JAVA_HOME از jbr اندروید استودیو و ANDROID_HOME لازم است.
- رابط‌های `SelectedBusinessStore`، `AppPreferences`، `TokenStore` و API در Koin. `DispatcherProvider`، `runCatchingCancellable`، `AppLinks`.
- `AppointmentRepositoryImpl.serverDayWindow`: پنجرهٔ همگام‌سازی همان روز سرور است. خروج از حساب: اول API، بعد توکن، بعد `LocalCacheCleaner`.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰
- لوگو هنگام ساخت کسب‌وکار اجباری است: `isLogoMissing` در `CreateBusinessState.kt`، و فیلد `logo_required=1` در `BusinessApiService`.
- `feature/banner/` بنر چرخشی ۳:۱ صفحهٔ اصلی است (`/api/owner/banners/`). `feature/reviews/` صفحهٔ نظرات است؛ روی اندروید، پوش `NEW_REVIEW` به آن deep link می‌شود. `feature/showcase/` نمونه‌کار و مدارک را با `RectImageCropperDialog` و `cropImageToRect` مدیریت می‌کند. اونر در کراپر قاب را انتخاب می‌کند: مربع، عمودی ۴:۵ یا افقی ۴:۳ (`ShowcaseShape`). صفحهٔ خانه: هدر (`HomeHero`) با اسکرول کامل جمع می‌شود و تقویم و اشتراک به نوار بالا می‌روند (`HomeTopBarActions`)، با snap. کارت امروز (`HomeTodayCard`): عدد کنار چهار وضعیت + نوار تقسیم روز. در گزارش ارسال‌ها شماره داخل `\u2066…\u2069` است تا در RTL برعکس نشود.
- `source` نوبت با `SourceBadge` نشان داده می‌شود. Room نسخهٔ ۲۰ است (destructive؛ Room فقط کش است).
- ⚠️ روی wasm هیچ‌وقت از `TimeZone.of(...)` استفاده نکنید، چون دیتابیس js-joda نیست و صفحهٔ اصلی کرش می‌کرد. `SERVER_ZONE` آفست ثابت UTC+3:30 است. تست JVM آن در `androidUnitTest/.../ServerZoneMatchesTehranTest.kt` است.

## به‌روزرسانی ۲۰۲۶-۱۰-۳۰ (عصر)
- صفحهٔ اصلی: `SourceSummaryCard` (`feature/home/HomeSourceCard.kt`) زیر کارت ماه، داخل `trendBlock`. داده از `GetSourceSummaryUseCase` ← `AppointmentRepository.getSourceCounts` (پیش‌فرض `null` در interface تا stubها نشکنند) ← `appointment/stats/` با `date_from` = اول ماه (`monthOverview.current.rangeStart`) و `date_to` = امروز. اگر APP و WEB هر دو صفر باشند نمایش داده نمی‌شود.
- `HomeViewModel` یک پارامتر سازندهٔ جدید دارد (`getSourceSummaryUseCase`)؛ Koin ۱۴ تا `get()` می‌دهد.

## جلسهٔ 2026-11-02
- **شبکه‌های اجتماعی:** `Business.socialLinks: Map<String,String>`، `SocialLinks` (`domain/model/business/SocialLinks.kt`: ترتیب، `clean`، `encode`/`decode` JSON). در multipart به شکل `social_links` (رشتهٔ JSON). Room: ستون `BusinessEntity.socialLinks` (JSON)، **نسخهٔ دیتابیس 21**.
- `CreateBusinessIntent.CreateBusiness.socialLinks` عمداً پیش‌فرض ندارد؛ هر جایی که intent می‌سازد (`AdvancedSettingsRoute`، `ShareBusinessScreen`) باید `business.socialLinks` را رد کند، وگرنه ذخیره لینک‌ها را پاک می‌کند.
- `SocialLinksField.kt` (فرم کسب‌وکار، زیر شماره‌ها): هر شبکه یک ردیف؛ کاشی تا خالی است خاکستری است و با تایپ رنگ برند می‌گیرد.
- `CityPicker`: state جدا برای لیست استان و شهر.
- `AdvancedSettingsTabs` → `CapacityTab`: بیعانهٔ اجباری بدون `CARD`/`ONLINE` هشدار قرمز و دکمهٔ «رفتن به تب پرداخت» دارد.

## کیف‌پول (۲۰۲۶-۱۱-۰۲)
- `feature/wallet/` (MVI: `WalletIntent` Load/LoadMore/Convert/ClearMessage): کارت تومانی، دیالوگ تبدیل، تاریخچه. از کارت مصرف خانه و ردیف تنظیمات باز می‌شود.
- `CheckoutConfirmation.confirm(quote, requote)` → `Choice(useWallet)?`؛ کلید کیف‌پول دوباره quote می‌گیرد. `PurchaseUseCase` در بیلد استوری `walletOnly=true` می‌فرستد.
- `core/billing/CreditsChanged`: بعد از خرید/تبدیل/hold بالا می‌رود و `HomeViewModel` کارت مصرف را تازه می‌کند.
- علامت +/− در RTL با isolate `⁦…⁩` داخل عدد (نه دور «تومان»).
- تست‌ها ۹۹ تا؛ `Str` در تست JVM کرش می‌کند، helperهای بدون Str را تست کنید. JAVA_HOME: `~/.jdks/jbr-17.0.14`.

## جلسهٔ 2026-11-04/05
- خانه:
  - `HomeHero` با `NestedScrollConnection` در `HomeScreenContent` جمع می‌شود (`HomeHeroCollapseRange` برابر 64dp)، نه از offset لیست. همین پرش را رفع کرد.
  - `NeonLineCanvas` در RTL آینه می‌شود و ترتیب برچسب‌ها اول قدیمی‌ترین است.
  - `PeriodToggle` به‌جای `Surface(onClick)` از `clickable` استفاده می‌کند، که حاشیهٔ ۴۸dp اضافه نمی‌کند.
- باتم‌شیت پروفایل موجودی کیف‌پول را نشان می‌دهد (`UserViewModel` با `GetMyEntitlementsUseCase`) و به صفحهٔ کیف‌پول می‌برد (از `MainTopAppBar` با `onNavigateToWallet`).
- `LaunchSplash` فقط روی اندروید و iOS است، نه wasm. تم `Theme.Noobatyar`.
- هشدار سقف بیعانه (`MAX_DEPOSIT_AMOUNT` در `AdvancedSettingsTabs`).
- `Business.followersCount` در کارت تنظیمات نمایش داده می‌شود. Room v22 (destructive).

## فالوورها و استوری (2026-11-05)
- **خانه:** `HomeFollowersCard.kt` (`NewFollowersCard`) با ۵ فالوور آخر. `HomeViewModel` پارامتر اختیاری `showcaseRepository` دارد، تا `HomeViewModelTest` نشکند.
- **`ShowcaseApi/Repository`:** `getFollowers`، `listStories`، `postStory` (multipart) و `deleteStory`. DTOها `StoryDto` و `StoryDraft`. `FakeShowcaseApi` هم به‌روز است.
- **`feature/stories/`:**
  - `StoriesScreen` (گرید 9:16) و `StoriesViewModel`.
  - composer تمام‌صفحه.
  - «افزودن» یک `ModalBottomSheet` است با ردیف «تخفیف»، که `DiscountDialog` درصدی یا مبلغی را باز می‌کند.
  - عکس با `cropImageToRect(raw, 0,0,1,1, w, h)` فقط کوچک می‌شود.
- **ورود:** `AppScreens.Stories` از تنظیمات (کاشی و کارت) و `AppSidebar`.
- **کمپین:** `audienceHint("followers")`.

## 2026-11-05: فالوورها، تخفیف، ظرفیت، تب پروفایل
- تب پروفایل (`feature/settings/SettingsScreen.kt`) گروه‌بندی شده است (کار سشن Sonnet، `94f8d9c`). «تنظیمات پیشرفته» حذف شد و هر آیتم ردیف خودش را دارد.
- مرحلهٔ «پرداخت» به فرم ساخت و ویرایش کسب‌وکار اضافه شد.
- `feature/home/FollowersListDialog.kt`: لیست کامل فالوورها (صفحه‌بندی، فقط نام و عکس).
- `HomeFirstVisitCard.kt`: `FirstVisitDiscountCard` و `FirstVisitDiscountSheet`.
- `feature/createBusiness/BookingCapacityControls.kt`: `SlotCapacityControl` (−/+، ۱ تا ۵۰۰) و `HoldMinutesControl` (اسلایدر ۲ تا ۱۵). endpoint خودشان را صدا می‌زنند و جای فیلد «حداکثر در ساعت» را گرفته‌اند.
- این endpointها در `ShowcaseApi` با پیاده‌سازی پیش‌فرض هستند (fakeها لازم نیست override کنند). فراخوانی‌ها در `runCatching` هستند؛ بدون آن قطعی شبکه اپ را کرش می‌دهد.
- صف (`QueueItemCard`): `FirstVisitBadge`. Room v23 = `AppointmentEntity.firstVisitDiscount`.

## آیکن مشکی و تم روشن (2026-11-05 شب)
- آیکن «نوبت‌یار کارفرما» مشکی است: `brand/apply-to-apps.js` نسخهٔ اونر را از همان SVGها با جایگزینی رنگ‌های بنفش (`OWNER_STOPS`) می‌سازد (اندروید، iOS، پنل وب، `main_icon`). commit `664fdc6` آیکن سرمه‌ای قبلی را با بنفش جایگزین کرده بود.
- فایل مارکت: `brand/png/noobatyar-owner-store-512.png`.
- پیش‌فرض تم روشن است، در اندروید، iOS و wasm. wasm قبلاً پیش‌فرض دارک داشت.
