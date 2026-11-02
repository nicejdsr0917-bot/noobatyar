---
updated: 2026-11-02
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
- `feature/banner/` بنر چرخشی ۳:۱ صفحهٔ اصلی است (`/api/owner/banners/`). `feature/reviews/` صفحهٔ نظرات است؛ روی اندروید، پوش `NEW_REVIEW` به آن deep link می‌شود. `feature/showcase/` نمونه‌کار و مدارک را با `RectImageCropperDialog` و `cropImageToRect` (۱:۱ و ۴:۵) مدیریت می‌کند.
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
