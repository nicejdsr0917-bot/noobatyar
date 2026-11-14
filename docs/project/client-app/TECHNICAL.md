# اپ مشتری اندروید (mobile_client): سند فنی

> منبع: `mobile_client/` روی `develop` (برنچ `mobile-client` ادغام شده و دیگر جدا لازم نیست). آخرین تگ `client-v1.4.0` (۲۰۲۶-۱۱-۱۴). این اپ **فقط اندروید** است.
> سند بیزینسی: [BUSINESS.md](BUSINESS.md)

## ۱. معماری
- Kotlin Multiplatform + Compose، یک هدف توزیع: اندروید. DI با Koin، شبکه Ktor، تصویر coil. Room بدون DAO (schema نسخه 17، بی‌تغییر).
- ریشه: `composeApp/src/commonMain/kotlin/xyz/sattar/javid/proqueue/`؛ `androidMain` پیاده‌سازی پلتفرم (`AppConfig.android.kt`، prefs، کراپر، موقعیت)، `commonTest` (۱۶ فایل تست، حدود ۱۴۳ تست).
- `core/`: `config/` (`BrandConfig.kt` کانال‌های اجتماعی و `STORE_HIDDEN_CHANNELS`)، `location/` (`IranCities.kt` از `composeResources/files/iran_cities.json`)، `navigation/` (`MainNavHost`، `TabBar`، `SolidTabIcon`)، `network/` (`AuthPlugin`)، `prefs/` (`PreferencesManager`، `ClientPreferences`)، `push/`، `state/`، `ui/` (تم، `LaunchSplash`، `NoobatyarMark`)، `utils/` (`AppConfig` با `isStoreBuild`، `ImageCropper`، `formatCardNumber`).
- `data/remoteDataSource/`: `booking/` (`BookingApi`، هدر `Visitor <token>`)، `business/` (`ClientBusinessApi`)، `device/`، `profile/` (`ProfileApi`)، `promo/`، `social/` (`BusinessSocialApi`: نمونه‌کار، نظر، `pendingReviews`)، `user/`.

## ۲. صفحه‌ها

| feature | فایل‌ها | کار |
|---|---|---|
| `welcome` | WelcomeScreen | اینترو ۵ صفحه‌ای؛ صفحه بیمه («بیمه تکمیلی دارید؟») و صفحه آخر انتخاب شهر |
| `explore` | ExploreScreen، ExploreSheet، ExploreFilterSheet (`InsuranceSheet`)، CityPickerSheet | کاوش روی نقشه و لیست (`MAP_ENABLED = true`)، فیلتر دسته، بیمه، شهر به سبک دیوار (جستجو، اخیر، «کل ایران» = 0)، ردیف استوری |
| `discovery` | DiscoveryUi (`BusinessAvatar` با حلقه استوری، `BusinessRow`)، OpenStatus، ToothIcon | اجزای لیست و وضعیت باز/بسته، اولین نوبت خالی («امروز/فردا/روز هفته») |
| `businessDetail` | ClientBusinessDetailScreen، BusinessMapScreen، Detail* (Hero، Identity، Cards، Sections، Showcase، Social، Map، Bars)، FollowButton، PaymentInfo | صفحه کسب‌وکار: آمار (نوبت، امتیاز، فالوور)، فالو، دکمه اولین نوبت، خدمات، بیعانه و پرداخت (فقط claimed)، گالری با واترمارک، مدارک، نظرها، کارت «نوبت آنلاین ندارد» و «صاحب این مرکز هستید؟» برای مرکز |
| `booking` | BookingScreen | خدمت → روز و ساعت → OTP (فقط بار اول) → تایید (`FirstVisitTag`) → بیعانه (آنلاین، کارت با کد پیگیری، حضوری)؛ `source=APP` |
| `appointments` | MyAppointmentsScreen، AppointmentDetailScreen | نوبت‌های من، لغو با تایید، پرداخت آنلاین، ثبت واریز، پرداخت حضوری؛ تازه‌سازی در resume |
| `auth` | LoginScreen | OTP |
| `profile` | ProfileScreen (`EditProfileSheet`، `StatsStrip`) | نام، بیو با شمارنده، عکس با کراپ |
| `following` | FollowingScreen | کسب‌وکارهای فالوشده |
| `saved` | SavedScreen | نشان‌شده‌ها (محلی) |
| `stories` | Stories (`StoryApi`، `StoryCenter`)، StoryRow، StoryViewer | فید استوری، ۵ ثانیه هر کدام، تخفیف، دکمه فالو، لینک `business:<id>` |
| `reviews` | ReviewPrompt | پیشنهاد نظر برای نوبت‌های انجام‌شده؛ ردشده‌ها (۵۰ تای آخر) در prefs |
| `promo` | PromoStrip | نوار تبلیغ از `client/promos/` |
| `more` | MyInsuranceViewModel | بیمه من |
| `aboutUs` | AboutUsScreen | درباره ما و کانال‌ها (تلگرام در store پنهان) |
| `version` | (منطق) | `version/check/` با `app = "CLIENT"` |

## ۳. endpointها
`client/businesses/` (+ `<id>/`، `<id>/follow/`، `<id>/report/`، `<id>/request-booking/`، `<id>/showcase/`، `<id>/stories/`، `following/`، `insurances/`)، `client/appointments/` (+ `<id>/cancel/`، `<id>/pay/`، `<id>/pay/online/`، `<bid>/available-slots/`)، `client/auth/` (`otp/send/`، `otp/verify/`، `register/`، `me/`، `me/avatar/`)، `client/reviews/` (`pending/`، `business/<id>/`، `…/summary/`، `…/mine/`، `<rid>/report/`)، `client/stories/` (`feed/?city_code=`، `platform/`، `active/`)، `client/promos/`، `version/check/`.

نکته‌های قرارداد:
- جستجوی شهر با `city_code` است (نه lat/lng)؛ مرکز شهر فقط برای نقشه.
- `deposit_mode` = `MANDATORY|OPTIONAL|NONE` (نه `REQUIRED`)؛ ثابت‌های `DEPOSIT_MANDATORY`/`DEPOSIT_OPTIONAL`.
- رزرو فاصله و زمان رانندگی (`travel_distance_m`، `drive_minutes_min/max`) را از آخرین موقعیت ذخیره‌شده می‌فرستد؛ مختصات کاربر ذخیره نمی‌شود.
- `AuthPlugin` به مسیرهای `/client/` دست نمی‌زند تا Bearer روی هدر `Visitor` ننشیند.
- Json مقدارهای پیش‌فرض را encode نمی‌کند؛ فیلد اجباری default نداشته باشد.
- کد `core/push/PushTokenProvider` هست ولی سرویس پیام در manifest ثبت نشده، پس اپ مشتری عملا پوش نمی‌گیرد؛ deep link هم ثبت نکرده.

## ۴. flavorها

| بعد | مقدار | اثر |
|---|---|---|
| `env` | `prod` | `https://api.noobatyar.ir`، `https://app.noobatyar.ir` |
| `env` | `local` | پسوند `.local`؛ `BASE_URL=http://127.0.0.1:8000` و `BOOKING_BASE_URL=http://127.0.0.1:3000` (با `adb reverse`) |
| `store` | `site` | `STORE=site`؛ همه کانال‌ها |
| `store` | `store` | `STORE=store`؛ یک APK برای مایکت و بازار؛ `AppConfig.isStoreBuild = true` و تلگرام از «درباره ما» حذف می‌شود |

`applicationId` = `xyz.sattar.javid.proqueue.client` (لوکال `….client.local`). نسخه از env `ANDROID_VERSION_CODE`/`ANDROID_VERSION_NAME`؛ CI نسخه را در xcconfig مشتری (`mobile_client/${XCCONFIG}`) sync می‌کند.

## ۵. بیلد، تست و ریلیز
```bash
cd mobile_client
./gradlew :composeApp:assembleLocalSiteDebug       # نه assembleLocalDebug
adb install -r composeApp/build/outputs/apk/localSite/debug/*arm64*debug.apk
adb reverse tcp:8000 tcp:8000 && adb reverse tcp:3000 tcp:3000
./gradlew :composeApp:testLocalSiteDebugUnitTest
```
- روی ویندوز اگر `gradlew` اجرایی نبود: `java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain …` با `JAVA_HOME` = jbr اندروید استودیو.
- ریلیز: تگ `client-vX.Y.Z` → `.github/workflows/android-release-client.yml` → `assembleProdSiteRelease` و `assembleProdStoreRelease`، امضا با همان کلید اونر، آپلود روی سایت، GitHub Release، تلگرام. بخش `## client-vX.Y.Z` با بلوک `<!-- fa:start -->` در `CHANGELOG.md` لازم است.
- `mobile_client/.gradle/` lockها کامیت نشوند.

## ۶. نکته‌های پیاده‌سازی
- `BusinessAvatar(business, size, corner)` با `koinInject<StoryCenter>()`؛ بدون Koin (preview/تست UI) رندر نمی‌شود. هر جا قاب گردتر است شعاع را پاس بده.
- `LocalContentColor` در `AppTheme` از `onBackground` می‌آید (رفع متن سیاه در تم تیره). پیش‌فرض تم روشن.
- `SystemBackHandler` expect/actual است.
- موقعیت امولاتور Mountain View است؛ در اپ «تهران» را انتخاب کن. کاشی نقشه از `tile.openstreetmap.fr/hot`.
- release با `isMinifyEnabled = false`؛ ABI split برای `arm64-v8a`، `armeabi-v7a`، `x86_64`؛ نصب `installLocalDebug` به‌خاطر split خطا می‌دهد.
