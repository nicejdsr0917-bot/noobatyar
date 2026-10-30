---
updated: 2026-10-30
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
