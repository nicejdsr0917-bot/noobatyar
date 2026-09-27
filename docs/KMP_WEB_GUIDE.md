# پنل وب با Kotlin Multiplatform — راهنمای کامل

> این سند برای کسی نوشته شده که KMP موبایل را می‌شناسد ولی تجربه‌ی وب با آن
> ندارد. همه‌ی مثال‌ها از خودِ همین ریپو هستند، نه مثال عمومی.
>
> اگر می‌خواهی بدانی *چرا* این معماری انتخاب شد و چه گزینه‌هایی رد شدند،
> `docs/OWNER_WEB_PLAN.md` را بخوان. این سند می‌گوید *چطور کار می‌کند*.

---

## ۰. تصویر کلی در یک نگاه

یک ماژول (`composeApp`) روی سه تارگت کامپایل می‌شود:

```
composeApp/src/
├── commonMain/     ← ~۲۶٬۷۰۰ خط. UI، ViewModel، مدل، شبکه. مشترک هر سه.
├── roomMain/       ← فقط اندروید و iOS به آن fallback می‌کنند (بخش ۳)
├── androidMain/
├── iosMain/
└── wasmJsMain/     ← ~۲۲ فایل. فقط لایه‌ی تماس با مرورگر.
```

نکته‌ای که معمولاً باور نمی‌شود: **تمام UI مشترک است.** همان
`LastVisitorsScreen.kt` که روی گوشی اجرا می‌شود، همان است که در مرورگر
اجرا می‌شود. هیچ نسخه‌ی وبِ جداگانه‌ای از صفحه‌ها وجود ندارد. آنچه فرق
می‌کند فقط دو چیز است:

1. **لایه‌ی پلتفرم** (`expect/actual`) — چیزهایی که مرورگر متفاوت انجام
   می‌دهد: ذخیره‌سازی، شبکه، دوربین، اعلان.
2. **چیدمان** — همان کد UI بر اساس عرض پنجره تصمیم می‌گیرد چطور بچیند
   (بخش ۵).

---

## ۱. Compose روی وب یعنی چه؟ (مهم‌ترین بخش برای درک ذهنی)

اینجا جایی است که بیشتر آدم‌ها اشتباه می‌کنند. Compose Multiplatform روی وب
**HTML تولید نمی‌کند.** هیچ `<div>`ی وجود ندارد.

اتفاقی که واقعاً می‌افتد:

1. کد Kotlin به **WebAssembly** کامپایل می‌شود (نه JavaScript).
2. یک موتور گرافیکی به اسم **Skia** (همان که اندروید استفاده می‌کند) هم به
   wasm کامپایل شده و همراه اپ لود می‌شود (`skiko.wasm`).
3. کل اپ داخل **یک `<canvas>`** نقاشی می‌شود.

در `wasmJsMain/resources/index.html`:

```html
<div id="compose-root"></div>
...
<script src="composeApp.js"></script>
```

و در `main.kt`:

```kotlin
ComposeViewport(document.getElementById("compose-root") as HTMLElement) {
    App()
}
```

`ComposeViewport` یک canvas داخل آن div می‌سازد و از آن به بعد، **هر پیکسل
روی صفحه را Compose می‌کشد.**

### پیامدهای عملی این تصمیم

این را باید کاملاً درونی کنی، چون تقریباً همه‌ی چالش‌های بخش ۸ از همین
یک واقعیت می‌آیند:

| چیزی که در وب عادی داری | اینجا |
|---|---|
| `Ctrl+F` مرورگر متن را پیدا می‌کند | ❌ متن داخل canvas است، DOM نیست |
| Inspect element روی دکمه | ❌ فقط یک canvas می‌بینی |
| SEO / خزنده‌ی گوگل | ❌ صفحه از نظر خزنده خالی است |
| اسکرین‌ریدر | ⚠️ محدود |
| انتخاب و کپی متن | ⚠️ محدود |
| اسکرول‌بار بومی مرورگر | ❌ Compose خودش می‌کشد |

**برای همین پنل مالک (نه سایت مشتری) با این روش ساخته شده.** سایت رزرو
مشتری (`front_client`) عمداً Next.js است: آنجا SEO و لینک‌های عمومی مهم‌اند.
پنل مالک پشت لاگین است و هیچ‌کدام از این‌ها اهمیتی ندارند.

### یک تله‌ی ظریف که در همین پروژه به آن خوردیم

چون Compose *همه‌چیز* را می‌کشد، هر ناحیه‌ای که Compose روی آن چیزی نکشد،
**سفیدِ خام canvas** می‌ماند — نه رنگ پس‌زمینه‌ی صفحه. برای همین در
`App.kt` یک Box با پس‌زمینه در ریشه هست:

```kotlin
// Painted at the root so no region can ever fall through to the
// web canvas's white clear colour — on wasmJs anything Compose
// does not draw over shows up as a white gap, not as the page background.
Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
```

---

## ۲. expect / actual — لایه‌ی تماس با پلتفرم

قلب KMP همین است: در `commonMain` می‌گویی «چنین چیزی *وجود دارد*» و در هر
تارگت می‌گویی «*چطور* کار می‌کند».

### مثال ۱: ذخیره‌ی توکن

مشترک (`commonMain`) فقط قرارداد را می‌داند:

```kotlin
expect object TokenManager {
    fun getAccessToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clearTokens()
}
```

- اندروید → `SharedPreferences`
- iOS → `NSUserDefaults`
- وب → `localStorage` مرورگر

نسخه‌ی وب (`wasmJsMain/.../TokenManager.wasmJs.kt`) — و به کامنتش دقت کن،
یک درس واقعی در آن هست:

```kotlin
actual object TokenManager {
    actual fun getAccessToken(): String? = safeGet(KEY_ACCESS_TOKEN)

    // getAccessToken runs on every outgoing request (AuthInterceptor). In a
    // browser configuration where localStorage throws (Safari private mode,
    // storage disabled, quota exceeded) an unguarded call here would crash
    // every request instead of just failing auth.
    private fun safeGet(key: String): String? =
        try { localStorage.getItem(key) } catch (e: Throwable) { null }
}
```

**درس:** روی موبایل، storage تقریباً همیشه در دسترس است. روی وب، کاربر
می‌تواند آن را خاموش کند، یا در حالت ناشناس Safari باشد. هر تماس با
`localStorage` باید `try/catch` داشته باشد.

### مثال ۲: کلاینت شبکه

```kotlin
// اندروید: OkHttp   |   iOS: Darwin   |   وب: موتور خود مرورگر
actual object HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(Js) {   // ← ktor-client-js
        install(ContentNegotiation) { json(...) }
        install(AuthInterceptor)
        install(DefaultRequest) { url("${AppConfig.BASE_URL}/api/") }
    }
}
```

روی وب، Ktor از `fetch` خود مرورگر استفاده می‌کند. یعنی **CORS اعمال
می‌شود** — چیزی که روی موبایل اصلاً وجود ندارد. این یکی از آن تفاوت‌هایی
است که تا لحظه‌ی دیپلوی خودش را نشان نمی‌دهد (بخش ۹).

### مثال ۳: کاری که روی وب معنا ندارد

```kotlin
// ContactActions.wasmJs.kt
private fun openInNewTab(url: String) { window.open(url, "_blank") }

actual fun openWhatsApp(phone: String, message: String?) {
    openInNewTab("https://wa.me/$phone...")
}
```

روی اندروید این یک Intent است. روی وب فقط یک تب جدید. `sms:` و `tel:` روی
دسکتاپ عملاً بی‌اثرند ولی روی موبایل‌وب کار می‌کنند — پس به‌جای
`TODO()` (که کرش می‌کند) یک پیاده‌سازی بی‌ضرر گذاشته شده.

**درس:** برای هر `expect`، به‌جای اینکه روی وب `TODO()` بگذاری، یک رفتار
بی‌ضرر بگذار. وگرنه یک مسیر کد مشترکِ فراموش‌شده روزی به آن می‌رسد و اپ
می‌ترکد.

### فهرست کامل expect های این پروژه

فقط ۱۵ تا `expect` در کل پروژه هست — و این عدد کوچک دقیقاً دلیلی است که
پورت کردن به وب شدنی بود:

`HttpClientFactory`، `TokenManager`، `PreferencesManager`، `AppInfo`،
`ContactActions` (۷ تابع)، `ImageCropper`، `ImagePicker`، `DbFactory`،
`SystemAppearance`، `yakanFontFamily`، `rememberNotificationPermissionLauncher`،
`AppConfig`، `PushTokenProvider`، `PaymentWebViewScreen`.

---

## ۳. بزرگ‌ترین چالش معماری: Room روی وب وجود ندارد

اپ موبایل از Room (دیتابیس SQLite) به‌عنوان کش لوکال استفاده می‌کند. Room
هیچ تارگت وبی ندارد — نه اینکه کند باشد، اصلاً کامپایل نمی‌شود.

مشکل: اگر `AppDatabase` و DAOها در `commonMain` باشند، تارگت wasmJs
هم آن‌ها را می‌بیند و بیلد می‌شکند.

### راه‌حل: source set میانی

در `build.gradle.kts`:

```kotlin
// Room (and sqlite-bundled under it) has no web target. "roomMain" is an
// intermediate source set that only androidMain and iosMain fall back to,
// so every Room type can live somewhere a future wasmJs target never
// compiles against.
applyDefaultHierarchyTemplate {
    common {
        group("room") {
            withAndroidTarget()
            withIos()
        }
    }
}
```

نتیجه‌ی درخت source set:

```
commonMain
├── roomMain          ← AppDatabase، DAOها، @Entity ها
│   ├── androidMain
│   └── iosMain
└── wasmJsMain        ← اینجا Room اصلاً دیده نمی‌شود
```

این یک الگوی بسیار مهم در KMP است: **وقتی یک کتابخانه فقط روی بعضی
تارگت‌ها هست، به‌جای پر کردن کد از `expect/actual`، یک source set میانی
بساز.**

### و سمت وب چه جایگزینی گذاشته شد؟

اینترفیس‌های `*LocalSource` در `commonMain` می‌مانند (چون ریپازیتوری‌ها به
آن‌ها وابسته‌اند)، و وب یک پیاده‌سازی حافظه‌ای می‌دهد:

```kotlin
class InMemoryUserLocalSource : UserLocalSource {
    private val userState = MutableStateFlow<User?>(null)
    override suspend fun insertUser(user: User) { userState.value = user }
    override fun getCurrentUser(): Flow<User?> = userState
}
```

و در Koin وب (`KoinModule.wasmJs.kt`):

```kotlin
val webPlatformModule = module {
    single<UserLocalSource> { InMemoryUserLocalSource() }
    single<VisitorLocalSource> { InMemoryVisitorLocalSource() }
    // ...
    // `single` (not `factory`) matters here the same way it does for Room —
    // one shared instance per app run.
}
```

سپس در `main.kt`:

```kotlin
startKoin {
    // No dbModule/roomModule here — wasmJs has no Room
    modules(webPlatformModule, appModule)
}
```

**چرا این قابل قبول است؟** چون در این پروژه دیتابیس لوکال از اول
*source of truth* نبوده، فقط کش بوده. هر دو پلتفرم موبایل هم با
`fallbackToDestructiveMigration` بیلد می‌شوند، یعنی از قبل پذیرفته شده که
کش هر لحظه ممکن است دور ریخته و دوباره fetch شود.

> ⚠️ **اگر اپ تو آفلاین-فرست واقعی است، این راه‌حل جواب نمی‌دهد.** آنجا
> باید سراغ IndexedDB بروی یا SQLDelight (که تارگت وب دارد).

### پیامدی که در همین پروژه به آن خوردیم

کش حافظه‌ای فقط چیزی را دارد که همین حالا fetch شده. برای همین وقتی
«گزارش بازه‌ای» را ساختیم، عمداً از کش نخواندیم:

```kotlin
// AppointmentRepository.kt
/**
 * Server-only, with no local-cache read: the summary is a whole-window
 * aggregate and the customer rows are grouped in the database, neither of
 * which can be rebuilt from the paged rows the local cache holds (and on
 * web that cache is only an in-memory source anyway).
 */
suspend fun getRangeReport(...): RangeReport?
```

---

## ۴. Koin و ViewModel — یک تله‌ی مشترک

Koin روی هر سه پلتفرم یکسان کار می‌کند، ولی یک نکته‌ی ظریف دارد که در وب
بیشتر خودش را نشان می‌دهد.

`koinViewModel()` نمونه را بر اساس `ViewModelStoreOwner` جاری برمی‌گرداند.
داخل `NavHost`، هر مقصد **owner خودش** را دارد. یعنی:

```kotlin
// داخل AppSidebar (بیرون از NavHost)
val settingsViewModel: SettingsViewModel = koinViewModel()   // نمونه A

// داخل composable<AppScreens.Settings> (داخل NavHost)
val viewModel: SettingsViewModel = koinViewModel()           // نمونه B ← متفاوت!
```

در `AppSidebar.kt` این تصمیم صریحاً مستند شده:

```
Owns its own SettingsViewModel/UserViewModel instances rather than sharing
the ones SettingsScreen creates — both instances read through to the same
repository-backed state, so they stay in sync without needing to be the
same object.
```

**قاعده‌ی عملی:** اگر دو نمونه‌ی ViewModel داری، مطمئن شو هر دو از یک
منبع مشترک (repository / StateHolder) می‌خوانند. اگر `init` آن‌ها درخواست
شبکه می‌زند، دو برابر ترافیک می‌گیری — قبل از اضافه کردن یک ViewModel
دوم، `init` را چک کن.

---

## ۵. ریسپانسیو — بخش عملی

این بخشی است که بیشترین کار را می‌برد. اپ برای گوشی نوشته شده و باید روی
مانیتور ۲۴ اینچ هم درست باشد.

### گام ۱: تعریف باکت‌های عرض

`core/ui/WindowSize.kt`:

```kotlin
enum class WindowSize {
    Compact,    // < 600dp   — گوشی
    Medium,     // 600–1000  — تبلت / پنجره‌ی کوچک
    Expanded;   // > 1000dp  — دسکتاپ

    companion object {
        fun of(width: Dp): WindowSize = when {
            width < 600.dp -> Compact
            width < 1000.dp -> Medium
            else -> Expanded
        }
    }
}

val LocalWindowSize = staticCompositionLocalOf { WindowSize.Compact }
```

نکته‌ی مهم: پیش‌فرض `Compact` است، نه اینکه crash کند. یعنی هر کامپوننتی
که بیرون از ریشه رندر شود (مثلاً Preview) به چیدمان گوشی برمی‌گردد.

### گام ۲: اندازه‌گیری یک‌بار در ریشه

در `App.kt`:

```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val windowSize = WindowSize.of(maxWidth)      // maxWidth از قبل dp است
    CompositionLocalProvider(LocalWindowSize provides windowSize) {
        // بقیه‌ی اپ
    }
}
```

**فقط یک‌جا اندازه می‌گیریم.** هیچ صفحه‌ای خودش پنجره را اندازه نمی‌گیرد؛
همه از `LocalWindowSize` می‌خوانند. این تفاوتِ یک سیستم ریسپانسیو با
یک مشت `if` پراکنده است.

### گام ۳: چهار الگوی استفاده

#### الگوی الف — عوض کردن کل ناوبری

`MainNavHost.kt`:

```kotlin
if (windowSize == WindowSize.Compact) {
    Scaffold(bottomBar = { BottomNavigationBar(...) }) { ... }   // گوشی
} else {
    Row {
        AppSidebar(...)                                          // وب/تبلت
        AppScaffold(modifier = Modifier.weight(1f)) { ... }
    }
}
```

#### الگوی ب — سقف عرض محتوا (مهم‌ترین ابزار)

بدون این، یک فرمِ ساخته‌شده برای گوشی روی مانیتور به یک نوار ۱۹۰۰ پیکسلی
تبدیل می‌شود. `core/ui/components/AppScaffold.kt`:

```kotlin
object ContentWidth {
    val Form = 420.dp    // فرم تک‌ستونی — لاگین، ثبت‌نام
    val List = 760.dp    // لیست کارت — انتخاب کسب‌وکار، تنظیمات
    val Wide = 1100.dp   // داشبورد داده-محور
}

@Composable
fun AppScaffold(maxWidth: Dp = ContentWidth.Wide, content: ...) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        Box(content = content)                    // گوشی: بدون تغییر
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
        }
    }
}
```

انتخاب عدد درست مهم است. در همین پروژه یک اشتباه واقعی کردیم: برای صفحه‌ی
«تنظیمات پیشرفته» اول `ContentWidth.List` (۷۶۰dp) گذاشتیم، ولی چون پنجره‌ی
تست ۷۶۸dp بود عملاً هیچ اثری نداشت و دکمه همچنان کشیده به‌نظر می‌رسید.
جواب درست `Form` (۴۲۰dp) بود، چون آن صفحه یک فرم تک‌ستونی است نه گرید کارت.

> **قاعده:** عرض را از روی *شکل محتوا* انتخاب کن، نه از روی حدس. چک‌باکس و
> فیلد و دکمه ← `Form`. گرید کارت ← `List`. داشبورد ← `Wide`.

#### الگوی ج — تعداد ستون گرید

```kotlin
val columns = if (LocalWindowSize.current == WindowSize.Expanded) 2 else 1

LazyVerticalGrid(
    columns = GridCells.Fixed(columns),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) { gridItems(appointments) { AppointmentCard(it) } }
```

#### الگوی د — عوض کردن نوع کامپوننت

`AdaptiveSheet.kt` — همان محتوا، ظرف متفاوت:

```kotlin
when (LocalWindowSize.current) {
    WindowSize.Compact -> ModalBottomSheet(...) { content() }        // شیت پایین
    else -> Dialog(onDismissRequest) { Surface { content() } }       // دیالوگ وسط
}
```

BottomSheet روی دسکتاپ غلط است — کاربر دسکتاپ انتظار دیالوگ دارد.

نمونه‌های دیگر همین الگو در پروژه:
- `PullToRefresh.kt` — از Medium به بالا یک دکمه‌ی رفرش شناور اضافه می‌کند،
  چون کشیدن به پایین با ماوس معنا ندارد.
- `Toasty.kt` — `TopCenter` روی گوشی، `TopStart` روی دسکتاپ.
- `AuthWebLayout.kt` — پنل برند سمت چپ فقط در `Expanded`.

### گام ۴: RTL و اینکه چطور چیدمان را برعکس می‌کند

کل اپ در `Theme.kt` روی RTL قفل شده:

```kotlin
LocalLayoutDirection provides LayoutDirection.Rtl
```

این یعنی در هر `Row`، **فرزند اول سمت راست می‌نشیند.** برای همین در
`MainNavHost` سایدبار به‌عنوان فرزند اول Row گذاشته شده و خودبه‌خود راست
می‌افتد — بدون هیچ آینه‌کاری دستی.

همچنین `Alignment.Start` = راست و `Alignment.End` = چپ. اگر می‌خواهی چیزی
سمت راست (شروع خواندن فارسی) بچسبد، `Start` بده نه `End`.

---

## ۶. تله‌های چیدمانی که واقعاً در این پروژه به آن‌ها خوردیم

این‌ها را از تجربه‌ی واقعی همین ریپو می‌نویسم، نه از تئوری.

### ۶.۱ `weight()` داخل ستونِ wrap-content ارتفاع صفر می‌دهد

می‌خواستیم نمودار خانه فضای اضافه را پر کند. `Modifier.weight(1f)` روی
canvas گذاشتیم و روی گوشی نمودار **کاملاً ناپدید شد**.

دلیل: `weight` فضای *باقیمانده* را می‌گیرد. اگر ارتفاع والد نامحدود
(wrap-content) باشد، باقیمانده صفر است.

راه‌حل — پارامتر صریح، نه حدس:

```kotlin
// NeonLineChart.kt
/**
 * [fillHeight] is opt-in because the chart area uses `weight` when it's on.
 * A weighted child of a wrap-content Column measures to zero height, so this
 * may only be set by a caller that actually gives the chart a bounded height.
 */
fun NeonLineChart(..., fillHeight: Boolean = false)
```

### ۶.۲ هم‌ارتفاع کردن کارت‌ها → `IntrinsicSize.Max`

دو کارت کنار هم ارتفاع متفاوت داشتند و ردیف کج به‌نظر می‌رسید:

```kotlin
Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
    Column(Modifier.weight(1f).fillMaxHeight()) { /* کارت ۱ */ }
    Column(Modifier.weight(1f).fillMaxHeight()) { /* کارت ۲ */ }
}
```

همین الگو برای گرید پلن‌ها هم استفاده شد تا دکمه‌های «خرید اشتراک» همه روی
یک خط بیفتند (`Spacer(Modifier.weight(1f))` قبل از دکمه، داخل کارتِ
هم‌ارتفاع).

### ۶.۳ `background()` مقدار `LocalContentColor` را ست نمی‌کند

متن برند سایدبار **مشکی** رندر می‌شد روی پس‌زمینه‌ی تیره.

دلیل: سایدبار پس‌زمینه‌اش را با `Modifier.background()` می‌کشید، نه با
کامپوننت `Surface`. فرقشان این است که `Surface` مقدار `LocalContentColor`
را هم ست می‌کند، ولی modifier نه — پس رنگ پیش‌فرض متن مشکی ماند.

```kotlin
Text(
    text = stringResource(Res.string.appName),
    // Explicit: the sidebar paints its background with a modifier rather
    // than a Surface, so nothing provides LocalContentColor.
    color = MaterialTheme.colorScheme.onSurface
)
```

**قاعده:** یا `Surface` استفاده کن، یا رنگ متن را صریح بده.

### ۶.۴ تعاملات لمسی روی دسکتاپ نمی‌چرخند

انتخابگر تاریخ یک «چرخ» بود که با درگ کار می‌کرد. روی دسکتاپ کاربر چیزی
برای درگ کردن ندارد و اسکرول ماوس هم روی یک `LazyColumn` با snap داخل
دیالوگ قابل اتکا نیست.

راه‌حل در `NumberPicker.kt` — افزودن دکمه‌ی صریح فقط برای اشاره‌گر:

```kotlin
// A spin wheel is a touch gesture. On a desktop panel there is nothing to
// drag with — so pointer sizes get explicit step buttons above and below.
val showSteppers = LocalWindowSize.current != WindowSize.Compact
```

**درس عمومی:** هر تعاملی که «کشیدن» یا «سوایپ» است، روی دسکتاپ باید یک
معادل کلیکی داشته باشد.

### ۶.۵ کامپوننت بیرون از ظرفِ محدودشده

دکمه‌ی «ثبت» صفحه‌ی تنظیمات پیشرفته به‌عنوان *خواهرِ* تب‌ها رندر می‌شد، پس
محدودیت عرضی که روی محتوای تب گذاشته بودیم اصلاً شاملش نمی‌شد و تا کل
عرض کشیده می‌شد.

راه‌حل: دکمه را به‌صورت یک slot به داخل همان ظرف پاس دادیم تا عرضش را
**به ارث ببرد** به‌جای اینکه عدد ثابتی برایش بگذاریم که بعداً از فرم جدا
بیفتد.

**درس:** وقتی چیزی «هم‌عرض چیز دیگر» باید باشد، آن را داخل همان ظرف ببر —
عدد ثابت دادن یعنی بدهی‌ای که در تغییر بعدی خودش را نشان می‌دهد.

---

## ۷. جریان داده — یکسان روی هر سه پلتفرم

اینجا هیچ چیز وب-خاصی وجود ندارد، و این دقیقاً نکته‌ی مثبت است:

```
Screen (Composable)
   ↓ Intent
ViewModel (MVI: Intent → PartialState → State)
   ↓
UseCase
   ↓
Repository ──→ ApiService (Ktor)          ← موتور فقط اینجا فرق می‌کند
           └─→ LocalSource (Room | InMemory)  ← پیاده‌سازی فقط اینجا فرق می‌کند
```

مثال واقعی از فیچر «گزارش بازه‌ای» که اخیراً اضافه شد — **یک‌بار نوشته شد،
روی هر سه پلتفرم کار می‌کند:**

```kotlin
// commonMain — هیچ چیز پلتفرمی در آن نیست
class GetRangeReportUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(businessId: Long, dateFrom: Long, dateTo: Long, ...) =
        repository.getRangeReport(...)

    companion object {
        const val MAX_RANGE_DAYS = 31   // آینه‌ی سقف سرور
    }
}
```

و باگی که در همین مسیر پیدا شد، یک‌جا فیکس شد و **هر دو پلتفرم** درست
شدند: کلیک روی نمودار خانه به تب «صف» می‌رفت به‌جای «مراجعین»، چون
`tab` پاس داده نمی‌شد و به مقدار پیش‌فرض `1` می‌افتاد. یک خط تغییر، دو
پلتفرم درست.

---

## ۸. اجرا و بیلد

### توسعه‌ی روزمره

```bash
./gradlew wasmJsBrowserDevelopmentRun
```

روی `http://localhost:8080` بالا می‌آید. در این ریپو از `.claude/launch.json`
هم قابل اجراست (`mobile_owner_web`).

پیکربندی در `build.gradle.kts` بسیار کم است:

```kotlin
@OptIn(ExperimentalWasmDsl::class)
wasmJs {
    browser()
    binaries.executable()   // ← بدون این فقط کتابخانه می‌سازد، نه اپ قابل اجرا
}

val wasmJsMain by getting {
    dependencies {
        implementation(libs.ktor.client.js)
        implementation(libs.kotlinx.browser)   // window/document/localStorage
    }
}
```

### ⚠️ تله‌ی کش که وقت زیادی از ما گرفت

فایل خروجی `composeApp.js` **هش ندارد** (برخلاف فایل‌های `.wasm` که
content-hash دارند). یعنی مرورگر راحت کشش می‌کند و بعد از ری‌بیلد، تب
قدیمی همان نسخه‌ی قبلی را نشان می‌دهد.

در همین پروژه دو بار فکر کردیم فیکس کار نکرده، در حالی که فقط تب رفرش
نشده بود.

**همیشه بعد از ری‌بیلد `Ctrl+Shift+R` بزن.**

### زمان بیلد و حجم

- بیلد افزایشی: ~۱۰ تا ۴۰ ثانیه.
- خروجی wasm: **۴۱ مگابایت** (خام، از لاگ webpack همین پروژه).

آن ۴۱ مگابایت با brotli/gzip به چند مگابایت می‌رسد، ولی **باید روی سرور
تنظیم شود** — بدون آن، کاربر ۴۱ مگابایت خام دانلود می‌کند. این یکی از
آیتم‌های باز فاز ۴ در `OWNER_WEB_PLAN.md` است.

### حداقل نسخه‌ی مرورگر (این را جدی بگیر)

Compose روی **WebAssembly GC** اجرا می‌شود:

| مرورگر | حداقل |
|---|---|
| Chrome / Edge | ۱۱۹ |
| Firefox | ۱۲۰ |
| Safari | ۱۸.۲ |

**تله:** آخرین کرومی که روی ویندوز ۷/۸ نصب می‌شود نسخه‌ی ۱۰۹ است. برای آن
کاربر پنل **صفحه‌ی سفید** می‌دهد — نه کند، اصلاً بالا نمی‌آید. اگر بخشی
از مخاطبت کامپیوتر قدیمی دارد، این را قبل از انتخاب معماری بدان.

---

## ۹. چیزهایی که تا دیپلوی خودشان را نشان نمی‌دهند

### CORS

روی موبایل وجود ندارد. روی وب، مرورگر هر درخواست cross-origin را می‌بندد
مگر اینکه سرور اجازه دهد. دامنه‌ی پنل باید به `CORS_ALLOWED_ORIGINS`
اضافه شود.

### آدرس سرور قابل تغییر در زمان اجرا

روی موبایل، `BASE_URL` از BuildKonfig می‌آید (فلیور). روی وب این کار
نمی‌کند، چون فلیور یک مفهوم Gradle/اندرویدی است. الان در این پروژه:

```kotlin
// AppConfig.wasmJs.kt — هاردکد به لوکال
actual object AppConfig {
    actual val BASE_URL: String = "http://127.0.0.1:8000"
}
```

الگوی درست برای پروداکشن این است که از `index.html` خوانده شود
(`window.__NOOBATYAR_CONFIG__`) تا **بدون ری‌بیلد** قابل تغییر باشد. این
هنوز انجام نشده و بلاکر دیپلوی است.

### HTTPS اجباری

سرویس‌ورکر (برای پوش FCM) فقط روی HTTPS یا `localhost` کار می‌کند.

### توکن در localStorage

روی موبایل توکن در storage اپ است و اپ دیگری به آن دسترسی ندارد. روی وب
در `localStorage` است، یعنی هر XSS = سرقت کامل سشن. چون Compose روی canvas
است و DOM ندارد، سطح حمله کمتر است، ولی هر اسکریپت third-party داخل
`index.html` (مثل همان firebase-compat) دسترسی دارد. راه سخت‌گیرانه‌تر:
رفرش‌توکن در کوکی `HttpOnly`.

---

## ۱۰. تست کردن اپ canvas-ای

چون DOM نداری، ابزارهای معمول تست وب کار نمی‌کنند:

- ❌ انتخاب المان با CSS selector
- ❌ درخت دسترسی‌پذیری (خالی برمی‌گردد)
- ✅ اسکرین‌شات + کلیک روی مختصات
- ✅ لاگ کنسول و تب Network (این‌ها کار می‌کنند، چون Ktor از fetch مرورگر
  استفاده می‌کند)

در عمل، تست همین فیچرها در این پروژه این شکلی بود: اسکرین‌شات بگیر،
مختصات را از تصویر بخوان، کلیک کن، دوباره اسکرین‌شات بگیر. و چون رندر
async است، اغلب باید یک فریم صبر کنی.

---

## ۱۱. جمع‌بندی: قوت‌ها و ضعف‌ها

### قوت‌ها

| | |
|---|---|
| **یک منبع حقیقت** | ~۲۶٬۷۰۰ خط UI و منطق مشترک بین هر سه پلتفرم |
| **باگ یک‌بار فیکس می‌شود** | مثال واقعی: فیکس تب نمودار، یک خط، دو پلتفرم |
| **تم و RTL خودبه‌خود یکی است** | چون *همان کد* است، نه «هماهنگ‌شده» |
| **فیچر جدید رایگان روی وب** | «گزارش بازه‌ای» یک‌بار نوشته شد |
| **سطح تماس با پلتفرم کوچک** | فقط ۱۵ `expect` در کل پروژه |
| **بدون بدهی دو-فرانت‌اندی** | جایگزینش نوشتن دوباره‌ی ~۱۶٬۵۰۰ خط UI بود |

### ضعف‌ها

| | |
|---|---|
| **باندل سنگین** | ۴۱MB خام؛ فشرده‌سازی سرور اجباری است |
| **بدون SEO / بدون DOM** | فقط برای اپِ پشت لاگین مناسب است |
| **مرورگر مدرن لازم** | ویندوز ۷/۸ = صفحه‌ی سفید |
| **دیباگ سخت‌تر** | نه inspect element، نه selector |
| **کش گمراه‌کننده در توسعه** | `composeApp.js` هش ندارد |
| **کار ریسپانسیو واقعی** | UI گوشی خودبه‌خود دسکتاپ نمی‌شود (بخش ۶) |
| **بدون دیتابیس لوکال** | Room نیست؛ آفلاین‌-فرست راه‌حل دیگری می‌خواهد |

### این معماری کِی درست است؟

✅ **بله** اگر: اپ موبایل بالغی داری، پنل پشت لاگین است، کاربر مرورگر
مدرن دارد، و آفلاین‌بودن روی وب لازم نیست.

❌ **نه** اگر: SEO لازم داری، صفحه‌ی عمومی و لینک‌پذیر است، مخاطبت مرورگر
قدیمی دارد، یا اولین بار است که اپ را می‌نویسی (در آن صورت باندل سنگین را
بدون هیچ کد مشترکی می‌پردازی).

دقیقاً به همین دلیل در این ریپو **هر دو** وجود دارند: `front_client` با
Next.js برای صفحه‌ی عمومی رزرو، و `mobile_owner` با KMP/wasm برای پنل مالک.

---

## ۱۲. نقشه‌ی فایل‌ها برای شروع کاوش

اگر می‌خواهی خودت کد را دنبال کنی، به این ترتیب بخوان:

| موضوع | فایل |
|---|---|
| نقطه‌ی شروع وب | `wasmJsMain/.../main.kt` |
| HTML میزبان | `wasmJsMain/resources/index.html` |
| تنظیم تارگت | `composeApp/build.gradle.kts` (بخش `wasmJs`) |
| ریشه‌ی اپ + اندازه‌گیری عرض | `commonMain/.../App.kt` |
| باکت‌های عرض | `core/ui/WindowSize.kt` |
| سقف عرض محتوا | `core/ui/components/AppScaffold.kt` |
| شیت/دیالوگ تطبیقی | `core/ui/components/AdaptiveSheet.kt` |
| انشعاب ناوبری | `core/navigation/navHost/MainNavHost.kt` |
| سایدبار وب | `core/ui/components/AppSidebar.kt` |
| جایگزین Room | `wasmJsMain/.../di/KoinModule.wasmJs.kt` |
| نمونه‌ی expect/actual | `core/network/TokenManager.wasmJs.kt` |
| تصمیم‌های معماری و فازها | `docs/OWNER_WEB_PLAN.md` |
