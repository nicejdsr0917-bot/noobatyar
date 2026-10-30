---
updated: 2026-10-31 00:30
tags: [gf, current]
---
# وضعیت فعلی

## هدف
تبدیل نقشه‌های اپ کلاینت (`mobile_client/`، برنچ `mobile-client`) به نقشهٔ برداری **Map.ir** روی **MapLibre**، به‌جای کاشی تار OSM.

دلیل انتخاب Map.ir: سرویس باید ایرانی باشد، چون شرایط اینترنت ایران ممکن است عوض شود.

قاعدهٔ همیشگی: فقط تغییر افزایشی، چون کاربر واقعی داریم.

## وضعیت
- **`mobile-client`:** push و دیپلوی نشده. develop و main دست نخورده‌اند.
- **این سشن تمام شد** (جزئیات در یادداشت سشن):
  - رزرو متحرک و هم‌ترازی کامل با وب (`docs/BOOKING_PARITY.md`)
  - فیش نوبت و عکس رسید
  - صفحهٔ مرکز به شکل کارت‌های نیتیو
  - صفحهٔ نقشهٔ مرکز: خط منحنی، فاصله و تخمین رانندگی
  - نوار تبلیغ `ClientPromo`
- **نیمه‌کار: MapLibre**
  - انجام شد: `VectorMap.kt`، `installMapAuth` (اندروید و iOS)، صفحهٔ نقشهٔ مرکز (`BusinessMapScreen`) و پیش‌نمایش (`DetailMap`). روی AVD بالا می‌آیند.
  - مانده: **صفحهٔ کاوش هنوز MapCompose است.**
- **کلید Map.ir هنوز نیامده.** بدون کلید، MapLibre کاشی OSM را نشان می‌دهد.
- **تست‌ها:**
  - بک‌اند: ۱۰۰۲ تست سبز
  - اپ کلاینت: ۱۲۶ تست سبز (`testProdDebugUnitTest`)
  - اندروید و iOS (فقط Kotlin) کامپایل می‌شوند.

## قدم‌های بعدی
1. **`feature/explore/ExploreMap.kt` را به MapLibre منتقل کن.**
   - نسخهٔ آمادهٔ `ExploreMapCamera.kt` در پوشهٔ یادداشت سشن‌ها با نام `ExploreMapCamera.maplibre.kt` است. توابعش: `clusterOnScreen`، `fitCamera(camera, points, bottomInset)`، `visibleArea`، `areaAround`، و `circlePoints` با مختصات جغرافیایی.
   - پین‌ها با `PinnedAt` از `BusinessMapScreen.kt`.
   - حلقهٔ شعاع با `LineLayer`.
   - «جستجو در این محدوده» با `camera.moveReason == GESTURE` و `isCameraMoving`.
   - برای `clusterOnScreen` تست بنویس.
2. MapCompose (`TileMap.kt`، `TileRecolor`، `loadedTileCount`) و وابستگی `mapcompose` را حذف کن.
   - `MapAttribution` متنش را از `VectorMapStyle.attribution` بگیرد.
3. **کلید Map.ir:**
   - کاربر در corp.map.ir ثبت‌نام کند و کلید بسازد.
   - کلید در `mobile_client/local.properties` با نام `mapir.apiKey=...` و در CI به‌عنوان secret `MAPIR_API_KEY`.
   - بعد استایل‌های روشن و تاریک Map.ir روی دستگاه بررسی شود.
4. **iOS:** فریم‌ورک MapLibre باید با Swift Package به پروژهٔ Xcode اضافه شود. تا الان فقط Kotlin کامپایل شده و لینک نشده.
5. API مسیریابی Map.ir (`map.ir/routes/...`) برای فاصله و زمان واقعی رانندگی، به‌جای `driveEstimate`.
6. بعد از آن: صفحه‌های کاوش و «نوبت‌های من»، فایل Firebase اپ کلاینت، و کارهای P3 (پایین‌تر).

## مسائل باز
- ⚠️ **قبل از دیپلوی:**
  - migrationهای جدید: `appointment/0017_appointment_travel_estimate`، `banners/0002_client_promo`
  - migrationهای قبلی P2 هنوز اجرا نشده‌اند.
  - `ZIBAL_MERCHANT_ID` باید در `.env` سرور باشد.
- **هزینهٔ Map.ir:**
  - هر کاشی یک درخواست حساب می‌شود.
  - پلن رایگان روزی ۱٬۰۰۰ درخواست است و پلن استارت‌آپ (ماهی ۱٫۸ میلیون تومان) روزی ۱۵٬۰۰۰.
  - فرم استارت‌آپ رایگانشان را پر کنید.
- **کرش MapLibre در اندروید:** `HttpRequestUtil.setOkHttpClient` قبل از `MapLibre.getInstance` کرش می‌کند. رفع شده است؛ ترتیب را حفظ کنید.
- **چینش overlay در RTL:** layoutهای overlay باید اندازهٔ کامل والد را بگیرند، وگرنه در RTL جابه‌جا می‌شوند.
- **دادهٔ نمونه در دیتابیس محلی:** یک `ClientPromo` با «تا ۱۲ امشب…» و لینک به «ارز نو».
- 🔒 رمز root سرور `93.127.223.93` در تاریخچهٔ git است و کاربر باید عوضش کند.
- `PushMessagingService` اپ کلاینت ثبت نشده است؛ منتظر فایل Firebase.
- **ابزار:**
  - `gradlew` را با `sh` اجرا کن و `ANDROID_HOME` را ست کن.
  - APK با MapLibre حدود ۴۹ مگابایت شده است.
  - امولاتور گاهی از کمبود حافظه ANR می‌دهد.

## فایل‌های کلیدی
- `mobile_client/.../core/ui/components/map/VectorMap.kt` — نقشهٔ MapLibre و استایل Map.ir یا OSM
- `mobile_client/.../feature/businessDetail/BusinessMapScreen.kt` — نقشهٔ کامل، `PinnedAt`، خط مسیر
- `mobile_client/.../feature/explore/ExploreMap.kt` — **هدف بعدی**
- `mobile_client/composeApp/build.gradle.kts` — `mapirApiKey()` و BuildKonfig
- `docs/BOOKING_PARITY.md` — جدول هم‌ترازی وب و اپ

## سشن‌های اخیر
- [[sessions/2026-10-31-0030]] — رزرو متحرک، هم‌ترازی با وب، صفحهٔ مرکز، نقشهٔ مسیر، تبلیغ، شروع MapLibre
- [[sessions/2026-10-30-1940]] — ادمین موبایل، رتبه در ادمین، بیعانه در کلاینت، آمار منبع در اونر
- [[sessions/2026-10-30-1808]] — پنل نوا؛ ادمین برای موبایل
- [[sessions/2026-10-30-1352]] — قابلیت‌های P2، تست روی AVD، بنرها
- [[sessions/2026-10-30-0959]] — اجرای P1، بازسازی، شروع P2 بک‌اند

ببین: [نقشهٔ پروژه](../knowledge/INDEX.md)
