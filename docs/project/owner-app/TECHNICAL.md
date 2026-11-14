# اپ کارفرما (mobile_owner): سند فنی

> منبع: `mobile_owner/` روی `develop` (`df608ec1`). نسخه فعلی ۱.۱۳.۳ (`MARKETING_VERSION`)، تگ `v1.13.3`.
> سند بیزینسی: [BUSINESS.md](BUSINESS.md) · API: [backend](../backend/TECHNICAL.md)

## ۱. معماری
- Kotlin Multiplatform + Compose Multiplatform. هدف‌های توزیع: **اندروید** و **پنل وب wasm** (`panel.noobatyar.ir`). سورس‌ست‌های `iosMain` و `desktopMain` در کد هستند ولی کانال انتشار نیستند؛ مسیر آیفون و دسکتاپ برای کارفرما پنل HTML/PWA روی برنچ `feat/owner-panel-web` است (`docs/OWNER_PANEL_WEB.md` روی همان برنچ)، نه این اپ.
- الگو: MVI (`BaseViewModel`، State/Intent/Effect)، `*ApiService` (Ktor) → `*RepositoryImpl` → UseCase → ViewModel. DI با Koin (`di/KoinModule.kt`؛ wasm: `KoinModule.wasmJs.kt`).
- ذخیره محلی: Room فقط کش است (نسخه ۲۵، migration مخرب)؛ روی wasm منبع‌های `InMemory*LocalSource` و `WebUserLocalSource`.
- ریشه کد: `composeApp/src/<sourceSet>/kotlin/xyz/sattar/javid/proqueue/`.

| sourceSet | محتوا |
|---|---|
| `commonMain` | همه UI، ViewModel، داده، شبکه |
| `androidMain` | پوش FCM (`PushMessagingService`)، کانال اعلان، زمان‌بند اعلان محلی، دسترسی‌ها، `HttpClientFactory.android.kt` (هدر `X-Store`) |
| `androidSite`، `androidBazaar` | `StoreBillingFactory` که `null` برمی‌گرداند (پرداخت با زیبال) |
| `androidMyket` | `MyketStoreBilling` (پرداخت درون‌برنامه مایکت) |
| `wasmJsMain` | `main.kt`، توکن و تنظیمات در localStorage، Firebase وب، `PaymentWebViewScreen` (ریدایرکت مرورگر)، منبع داده در حافظه |
| `roomMain` | تعریف Room مشترک اندروید (و iOS) |
| `commonTest` (۱۸ تست)، `androidUnitTest` (۱) | تست JVM |

### ۱.۱ ساختار `commonMain`
- `core/`: `AppConfig` (expect: `BASE_URL`، `BOOKING_BASE_URL`)، `AppLinks`، `billing/` (`CheckoutConfirmation`، `CreditsChanged`، `StoreBilling`)، `i18n/`، `location/`، `navigation/` (`Screens.kt`، `Routes.kt`، `MainNavHost`، `AuthNavHost`، مدیرهای اعلان و پرداخت)، `network/` (توکن، refresh)، `notifications/`، `payment/` (`PaymentWebViewScreen`)، `permissions/`، `prefs/`، `push/`، `state/`، `store/` (`SiteVersionPrompt`)، `ui/` (کامپوننت‌ها، تم، `ForwardChevron`)، `utils/`.
- `data/remoteDataSource/`: `appointment`، `business`، `device`، `showcase`، `user`، `visitor`.
- `domain/`: مدل‌ها و UseCaseها.
- `feature/`: یک پوشه برای هر صفحه (پایین).

## ۲. صفحه‌ها و مسیرها

| feature | صفحه | کار |
|---|---|---|
| `onboarding` | OnboardingScreen | معرفی اول |
| `register`، `login`، `forgetPassword` | RegisterScreen، LoginScreen، SendOTP، ResetPassword | ورود با رمز یا OTP، بازیابی رمز |
| `home` | HomeScreen، FollowersListDialog | هدر جمع‌شونده، کارت امروز، روند ماه، منبع نوبت (`SourceSummaryCard`)، فالوورهای تازه، تخفیف اولین مراجعه، صف نوبت |
| `banner` | (کامپوننت) | بنر چرخشی ۳:۱ از `owner/banners/` |
| `calendar` | CalendarScreen | تقویم نوبت‌ها |
| `createAppointment` | CreateAppointmentScreen | ثبت نوبت دستی |
| `visitorSelection`، `createVisitor`، `visitorDetails`، `lastVisitors` | انتخاب، ساخت، جزییات، آخرین مشتری‌ها | مدیریت مشتری |
| `createBusiness` | CreateBusinessRoute، AdvancedSettingsRoute، `BookingCapacityControls` | فرم چندمرحله‌ای کسب‌وکار (اطلاعات تماس با شهر اجباری، شبکه‌ها، پرداخت، ظرفیت) |
| `businessList` | BusinessListScreen | چند کسب‌وکار |
| `claimBusiness` | ClaimBusinessScreen | ادعای مرکز موجود |
| `messages`، `notifications` | MessagesScreen، NotificationsScreen | پیام‌ها و اعلان‌ها |
| `smsReport` | SmsReportScreen | گزارش پیامک و پوش |
| `promo` | PromoCampaignScreen | کمپین پیامک تبلیغاتی (preview هزینه) |
| `addons` | AddonsScreen | پلن‌ها و بسته‌ها |
| `wallet` | WalletScreen | کیف پول تومانی، تبدیل، تاریخچه |
| `profile` | ProfileBottomSheet، PaymentResultScreen | پروفایل و نتیجه پرداخت |
| `settings` | SettingsScreen، EmergencyNoticeScreen، ThemeScreen | تنظیمات گروه‌بندی‌شده، اطلاعیه اضطراری، تم |
| `share` | ShareBusinessScreen | اشتراک لینک صفحه |
| `showcase` | ShowcaseScreen | نمونه‌کار و مدرک با کراپر قاب |
| `stories` | StoriesScreen | استوری با دکمه تخفیف |
| `reviews` | ReviewsScreen | نظرها |
| `aboutUs` | AboutUsScreen | درباره ما (تلگرام در نسخه مایکت پنهان) |
| `version` | (منطق) | چک نسخه و آپدیت اجباری |

مقصدهای ناوبری (`Screens.kt`): OnBoarding، Register، Login، SendOTP، ResetPassword، Home، Settings، Notifications، Visitors، CreateBusiness، AdvancedSettings، BusinessList، ClaimBusiness، VisitorSelection، CreateVisitor، EditVisitor، VisitorDetails، CreateAppointment، Messages، SmsReport، EmergencyNotice، Calendar، PaymentResult، AboutUs، PromoCampaign، Theme، ShareBusiness، AddOns، Reviews، Showcase، Stories، Wallet، PaymentWebView، LastVisitors.

Deep link اندروید (`noobatyar://`): میزبان‌های `home`، `notifications`، `visitors`، `customers`، `plans`، `subscription`، `addons`، `add-ons`، `payment` (بازگشت درگاه)، `reviews`، `settings`، `sms-report`، `smsreport`. پوش `NEW_REVIEW` مستقیم صفحه نظرها را باز می‌کند.

## ۳. endpointهایی که اپ صدا می‌زند
- auth: `auth/register/`، `login/`، `logout/`، `otp/send/`، `otp/verify/`، `token/refresh/`، `forgot-password/{send,verify,reset}/`، `users/<id>/`، `devices/{register,unregister}/`.
- accounting: `plans/`، `my-subscription/`، `my-entitlements/`، `plans/payment/`، `addons/`، `addons/buy/`، `checkout/quote/`، `wallet/history/`، `wallet/convert/`، `store/<store>/intent/`، `store/<store>/verify/`.
- business: `business/`، `business/<id>/`، `categories/`، `service-catalog/`، `claimable/`، `<id>/claim/`، `<id>/claim/verify/`، `<id>/sms-logs/` (+ `summary/`)، `<id>/push-logs/` (+ `summary/`)، `<id>/promo-sms/` (+ `preview/`، `<cid>/`)، `<id>/followers/`، `<id>/booking-capacity/`، `<id>/first-visit-discount/`.
- appointment: `appointment/`، `appointment/<id>/`، `stats/`، `daily-counts/`، `range-visitors/`، `query`.
- visitor: `visitor/`، `visitor/<id>/`، `<id>/messages/`.
- showcase و social: `owner/showcase/<portfolio|certificates>/` (+ `<id>/`، `reorder/`)، `owner/stories/` (+ `<id>/`)، `reviews/owner/`، `owner/banners/`.
- version: `version/check/`، `version/changelog/`.

endpointهای ظرفیت، تخفیف و فالوور در `ShowcaseApi` پیاده‌سازی پیش‌فرض دارند و صدا زدنشان در `runCatching` است (بدون آن قطعی شبکه کرش می‌داد).

## ۴. جریان‌ها
- **ورود:** رمز یا OTP → access و refresh در `TokenStore` (اندروید: prefs؛ wasm: localStorage) → refresh خودکار. خروج: اول API، بعد پاک کردن توکن، بعد `LocalCacheCleaner`.
- **محدودیت پلن:** `my-entitlements/` → `FeatureGate` روی قابلیت‌ها (`online_gateway`، `deposit`، `promotional_sms`، `capacity_control`، `advanced_reports`، `multi_channel`، `branded_page`، `priority_support`، `auto_reminder_sms`، `push_notifications`)؛ ردیف‌های `coming_soon` غیرفعال نمایش داده می‌شوند.
- **خرید:** `CheckoutConfirmation.confirm(quote, requote)` فاکتور (پایه، VAT، کارمزد) را نشان می‌دهد و کلید کیف پول quote تازه می‌گیرد → سایت و بازار: `PaymentWebViewScreen` به زیبال و بازگشت با `noobatyar://payment` (wasm: بازگشت به پنل) → مایکت: `MyketStoreBilling` → `store/myket/verify/`. بعد از خرید `CreditsChanged` کارت مصرف خانه را تازه می‌کند. در بیلد فروشگاهی `PurchaseUseCase` حالت `walletOnly=true` می‌فرستد.
- **پوش:** `PushMessagingService` توکن را با `devices/register/` ثبت می‌کند؛ نوع‌ها `NEW_BOOKING`، `APPOINTMENT_REMINDER`، `NEW_REVIEW`، `NEW_FOLLOWER`، `MARKETING`. روی wasm از Firebase وب (`FirebaseMessagingInterop`، `FirebaseWebConfig`). یادآوری محلی با `AndroidNotificationScheduler` و `WebNotificationScheduler`.
- **همگام‌سازی روز:** `AppointmentRepositoryImpl.serverDayWindow` همان روز سرور را می‌گیرد. `SERVER_ZONE` آفست ثابت UTC+3:30 است؛ روی wasm هرگز `TimeZone.of(...)` (دیتابیس js-joda نیست و کرش می‌کند).
- **نسخه:** `version/check/` با `app=OWNER`؛ `force_update` صفحه را قفل می‌کند. `SiteVersionPrompt` و دکمه آپدیت به سایت می‌برند و عمدا دست نخورند.

## ۵. flavorها و تنظیمات

| بعد | مقدار | اثر |
|---|---|---|
| `env` | `prod` | `BASE_URL=https://api.noobatyar.ir`، `BOOKING_BASE_URL=https://app.noobatyar.ir` |
| `env` | `local` | پسوند `.local` و `-local`؛ **پیش‌فرض به prod وصل است** مگر با `-PlocalBaseUrl=http://127.0.0.1:8000` (آدرس رزرو همان میزبان با پورت 3000) |
| `store` | `site` | `STORE=site`؛ دانلود از سایت و تلگرام؛ پرداخت زیبال |
| `store` | `bazaar` | `STORE=bazaar`؛ پرداخت زیبال (billing بازار ندارد) |
| `store` | `myket` | `STORE=myket`؛ پرداخت درون‌برنامه مایکت (`MYKET_IAB_PUBLIC_KEY`، `marketApplicationId=ir.mservices.market`)، هدر `X-Store: myket`، تلگرام در «درباره ما» پنهان |

- `applicationId` = `xyz.sattar.javid.proqueue` (لوکال `….local`).
- `versionCode`/`versionName` از env `ANDROID_VERSION_CODE`/`ANDROID_VERSION_NAME` (CI)؛ versionCode از `CURRENT_PROJECT_VERSION` در `mobile_owner/iosApp/Configuration/Config.xcconfig` + ۱.
- ABI split: `arm64-v8a`، `armeabi-v7a`، `x86_64`.
- `buildkonfig` همان دو آدرس prod را برای هدف‌های غیر اندرویدی دارد؛ wasm آدرس را از `AppConfig.wasmJs.kt` می‌گیرد.
- i18n: `composeResources/values/strings.xml` (فارسی) و `values-en/`؛ تسک `generateI18nStrings` کلاس `Str` را می‌سازد؛ کلید تازه در هر دو فایل.
- برند: آیکن مشکی کارفرما با `brand/apply-to-apps.js` (`OWNER_STOPS`)؛ تم پیش‌فرض روشن روی همه هدف‌ها.

## ۶. بیلد، تست و ریلیز

```bash
# اندروید لوکال (ویندوز/مک؛ JAVA_HOME = jbr اندروید استودیو، ANDROID_HOME لازم)
./gradlew :composeApp:assembleLocalSiteDebug -PlocalBaseUrl=http://127.0.0.1:8000
adb install -r composeApp/build/outputs/apk/localSite/debug/*arm64*debug.apk
adb reverse tcp:8000 tcp:8000 && adb reverse tcp:3000 tcp:3000
# تست (هرگز allTests)
./gradlew :composeApp:testLocalSiteDebugUnitTest
# پنل وب
./gradlew :composeApp:wasmJsBrowserDistribution    # خروجی را scripts/deploy-panel.sh بالا می‌برد
```
- ریلیز فقط از CI: push تگ `vX.Y.Z` → `.github/workflows/android-release.yml` سه APK امضاشده (`assembleProdSiteRelease`، `assembleProdBazaarRelease`، `assembleProdMyketRelease`) می‌سازد، روی سایت (FTP وردپرس) بالا می‌برد، GitHub Release و تلگرام می‌فرستد و xcconfig را روی develop sync می‌کند. بلوک `<!-- fa:start -->` در بخش همان نسخه در `CHANGELOG.md` الزامی است.
- پنل وب با `scripts/deploy-panel.sh` در `backend/panel` سرور بالا می‌رود (nginx آن را سرو می‌کند).
- مشکل رایج: خطای MD5 در کش build → `./gradlew :composeApp:clean`.

## ۷. نکته‌های پیاده‌سازی
- `ForwardChevron` = `Icons.AutoMirrored.Rounded.KeyboardArrowRight` («<» در فارسی). آیکن auto-mirrored را با `isRtl` جابه‌جا نکن.
- شماره در RTL داخل `⁦…⁩`؛ علامت +/− کیف پول داخل عدد ایزوله می‌شود.
- `CreateBusinessIntent.CreateBusiness.socialLinks` پیش‌فرض ندارد؛ هر سازنده intent باید `business.socialLinks` را رد کند، وگرنه لینک‌ها پاک می‌شوند.
- `Str` در تست JVM کرش می‌کند؛ helperهای بدون `Str` را تست کن.
- `labelSmall` ۱۲sp؛ دکمه‌های فقط‌آیکنی برچسب دسترس‌پذیری دارند.
- بیمه روی کارت نوبت فقط برای صنف پزشکی (`visibleInsuranceName`)؛ کد پیگیری فقط برای بیعانه CARD/GATEWAY پرداخت‌شده.
- `mobile_owner/composeApp/build.gradle.kts` نباید با تغییرهای محلی کامیت شود.
