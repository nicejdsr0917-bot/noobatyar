# Changelog

<!--
  Each version section must include a Persian summary block delimited by
  `<!-- fa:start -->` / `<!-- fa:end -->` right after its `## vX.Y.Z` header.
  The Android release workflow (.github/workflows/android-release.yml)
  extracts exactly that block for the tag being released and uses it as
  both the GitHub Release body and the Telegram APK caption — so keep it
  short, user-facing, and in Persian. The rest of the section (English,
  per-package, technical) is for developers/agents and isn't shown to users.
-->

## v1.9.3 — 2026-09-24

<!-- fa:start -->
این نسخه تغییری در اپلیکیشن ندارد — فقط برای تست عملکرد مسیر انتشار
(آپلود خودکار روی سایت و ارسال به کانال تلگرام) منتشر شده است.
<!-- fa:end -->

### infra
- `.github/workflows/android-release.yml` — آپلود APK به `noobatyar.ir` از مسیر اشتباه (`public_html/...`) به مسیر درست
  (ریشه‌ی همان اکانت FTP jail‌شده) اصلاح شد؛ ارسال هم از `curl` به `ftplib.FTP_TLS` تغییر کرد چون `curl` روی این
  هاست آپلودهای چند-مگابایتی را بی‌صدا ناقص می‌گذاشت.
- کانال مقصد ارسال در تلگرام از کانال مشتریان به کانال بکاپ (`TELEGRAM_CHAT_ID`) تغییر کرد.
- کد اپ (`mobile_owner`) نسبت به v1.9.2 بدون تغییر است؛ این تگ فقط برای آزمودن مسیر جدید انتشار زده شده.

Full commit range: [`v1.9.2...v1.9.3`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.9.2...v1.9.3)

## v1.9.2 — 2026-09-22

<!-- fa:start -->
تغییرات این نسخه:
- لوگوی کسب‌وکار حالا روی هر چهار کارت اشتراک‌گذاری کنار نام آن می‌آید — پیش‌تر کارت شماره‌ی کارت فقط نام را داشت و تب «لینک» اصلاً نام کسب‌وکار را نشان نمی‌داد.
- نمایش دسته‌بندی کسب‌وکار (مثل «دندان‌پزشکی» یا «آرایشگاه») از کارت QR و از ردیف کسب‌وکار فعال در تنظیمات برداشته شد؛ نام و لوگو کافی است.
- آدرس لینک از کارت پروفایل/استوری حذف شد. استوری یک عکس است و آدرس روی آن فقط قابل تایپ‌کردن است؛ راه‌های واقعی یعنی لینک بایو و کد QR هر دو روی کارت هستند. آدرس کامل فقط روی کارت QR می‌ماند، چون همان کارتی است که چاپ می‌شود.
- اصلاح چیدمان: عنوان «شماره کارت» در نوار تب‌ها وسط حرف بریده می‌شد؛ آدرس لینک در تب «لینک» کج و جابه‌جا نمایش داده می‌شد.
- رفع خرابی متن فارسی در سراسر برنامه (منوها، عنوان تب‌ها، پیام‌ها) که در اثر خواندن اشتباه فایل متن‌های فارسی به‌وجود آمده بود — روی گوشی به‌جای حروف فارسی، کاراکترهای لاتین به‌هم‌ریخته نمایش داده می‌شد.
<!-- fa:end -->

### mobile_owner
- `ShareCardKit.kt` — `BusinessAvatar` پارامتر `ringColor` گرفت: حلقه‌ی سفیدِ پیش‌فرض روی کارت‌های بنفش درست است ولی روی کارت روشنِ تب «لینک» دیده نمی‌شد.
- `ShareBusinessScreen.kt` — `LinkShareCard` حالا `business` می‌گیرد و با لوگو و نام کسب‌وکار شروع می‌شود، نه آیکون عمومی لینک. سربرگ `CardNumberPosterCard` هم دقیقاً هم‌اندازه‌ی سربرگ کارت QR شد (لوگوی ۴۴dp، فاصله‌ی ۱۰dp، عنوان ۱۷sp) تا جابه‌جا شدن بین دو تب پرش نداشته باشد.
- `LtrText` فقط `maxLines = 1` داشت و بدون `overflow`، پس لینک یا شماره‌کارتِ بلند بی‌صدا بریده می‌شد؛ `softWrap = false` و `TextOverflow.Ellipsis` اضافه شد.
- آدرس در تب «لینک» یک `Text` ساده بود، پس الگوریتم bidi در این اپِ راست‌به‌چپ بخش‌های جداشده با `/` را جابه‌جا می‌کرد؛ حالا از همان `LtrText` بقیه‌ی صفحه رد می‌شود و داخل `Box` وسط‌چین شده.
- `SettingsScreen.kt` — حذف خط `categoryLabel` از ردیف کسب‌وکار فعال.
- `strings.xml` — کل فایل به‌صورت UTF-8 که به‌اشتباه Windows-1252 خوانده و دوباره ذخیره شده بود خراب شده بود (نزدیک به ۲۸۰ رشته از ۳۳۰ تا). با معکوس‌کردن همان مسیر تبدیل (رمزگذاری هر کاراکتر با جدول Windows-1252 و رمزگشایی نتیجه با UTF-8) اصلاح شد؛ این خرابی قبل از این نسخه هم در کد وجود داشت، نه نتیجه‌ی تغییرات این نسخه.

Full commit range: [`v1.9.1...v1.9.2`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.9.1...v1.9.2)

## v1.9.1 — 2026-09-19

<!-- fa:start -->
تغییرات این نسخه:
- انتقال کامل اپلیکیشن به سرورهای عملیاتی (Production) و خروج از حالت توسعه محلی.
- رفع مشکل تداخل گرافیکی در کارت‌های اشتراک‌گذاری: در لینک‌های طولانی، آدرس سایت و لوگوی نوبت‌یار در پایین تصویر روی هم می‌افتادند که اصلاح شد.
- جلوگیری از شکستن نام «نوبت‌یار» در فوتر کارت‌ها که باعث به‌هم‌ریختگی چیدمان در برخی گوشی‌ها می‌شد.
<!-- fa:end -->

### mobile_owner
- آدرس‌های `BASE_URL` و `BOOKING_BASE_URL` در فایل `build.gradle.kts` به آدرس‌های نهایی (`api.noobatyar.ir` و `app.noobatyar.ir`) تغییر یافتند.
- اصلاح چیدمان فوتر در `ShareBusinessScreen.kt` با استفاده از `weight(1f)` برای ستون لینک و افزودن `Spacer` قبل از امضا.
- افزودن `maxLines = 1` به نام برند در `AppSignature` برای جلوگیری از چندخطی شدن متن.
- افزودن مدل دامنه `AppointmentStatus` که در نسخه قبل جا مانده بود.

Full commit range: [`v1.9.0...v1.9.1`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.9.0...v1.9.1)

## v1.9.0 — 2026-09-19

<!-- fa:start -->
تغییرات این نسخه:
- صفحه‌ی جدید «اشتراک‌گذاری نوبت‌دهی» با چهار حالت: کارت QR، لینک ساده، کارت پروفایل (مناسب استوری اینستاگرام) و کارت شماره‌ی کارت بانکی.
- کارت‌های QR، پروفایل و شماره‌ی کارت به‌صورت عکس واقعی (۱۰۸۰ پیکسل، اندازه‌ی پست و استوری اینستاگرام) اشتراک گذاشته می‌شوند — نه فقط متن.
- «کارت یادآوری» برای هر نوبت: نام مشتری، تاریخ و ساعت، خدمات و زمان باقی‌مانده، به‌صورت عکسی که مشتری نگه می‌دارد.
- شماره‌ی کارت را می‌شود همین‌جا ثبت کرد؛ لوگوی بانک هم حین تایپ شماره تشخیص داده می‌شود.
- در صفحه‌ی رزرو مشتری، لوگوی بانک کنار شماره‌ی کارت نمایش داده می‌شود تا مشخص باشد پول به کجا می‌رود.
- رفع اشکال: تایید تغییرات از طریق بله، نوتیفیکیشن روی اپ نمی‌فرستاد.
- رفع اشکال: در صفحه‌ی رزرو مشتری، دکمه‌ی بازگشت مرورگر در مرحله‌ی پرداخت باعث ثبت دوباره‌ی نوبت می‌شد؛ حالا به‌جای آن دکمه‌ی «انصراف» و «×» گذاشته شده.
<!-- fa:end -->

### mobile_owner
- `feature/share/` (جدید) — صفحه‌ی اشتراک‌گذاری با چهار تب، و `ReminderCard` برای یادآوری هر نوبت (از منوی ⋮ همان نوبت).
- خروجی تصویر روی هر دو پلتفرم: `core/ui/Capturable.kt` محتوا را در یک `GraphicsLayer` خود Compose ضبط می‌کند (نه از روی ویوی اندروید، که معادل iOS ندارد و کل پنجره را می‌گیرد)، و `core/utils/ShareImage.kt` آن را به‌صورت PNG به share sheet می‌دهد.
- اندازه‌ی خروجی ثابت است، نه وابسته به دستگاه: کارت‌ها در فضای ثابت ۳۶۰dp با density پین‌شده روی ۳ چیده می‌شوند، پس روی هر گوشی دقیقاً ۱۰۸۰ پیکسل عرض می‌دهند. `fontScale` هم روی ۱ قفل است تا اندازه‌ی فونت سیستمِ صاحب کسب‌وکار عکسی که برای مشتری می‌رود را به‌هم نریزد.
- `IranianBankDetector` (جدید) — تشخیص بانک از شش رقم اول کارت (BIN) برای ۲۷ بانک، با لوگوی خود بانک.
- `FileProvider` فقط روی `cache/shared_images/` تعریف شده، نه کل کش — ریشه‌ی فراگیر به اپی که URI را می‌گیرد اجازه می‌داد کش Ktor و Coil را هم بگردد.
- شمارش معکوس کارت یادآوری تابع جداگانه‌ای دارد: `DateTimeUtils.calculateWaitingTime` برای صف صفحه‌ی اصلی نوشته شده و فقط ساعت می‌شمارد، پس یادآوری سه روز جلوتر «۷۲ ساعت» می‌شد.
- `AppInfo.ios.kt` — افزودن `@OptIn(ExperimentalNativeApi)` که `Platform.isDebugBinary` لازم دارد؛ تارگت iOS از `9f87b0c` به بعد اصلاً کامپایل نمی‌شد.
- وابستگی جدید: `qrose 1.1.2` (MIT). عمداً نه ۱.۳.۰ — آن نسخه compose-ui 1.12.0 را اعلام می‌کند و Gradle آن را *بالاتر* از ۱.۱۱.۱ این پروژه حل می‌کرد و compose-ui را از بقیه‌ی artifactهای هم‌تراز جدا می‌کرد.

### backend
- `business/push_moderation.py` (جدید) — نوتیفیکیشن پوش برای تصمیم‌های بررسی محتوا (تایید/رد/تعلیق) و برای ورود به صف بررسی.
- `bale/notify.py` — رفع باگ: `bale/views.py` همیشه `apply_moderation_decision(..., notify=False)` صدا می‌زد، پس تایید از داخل بله هیچ پوشی نمی‌فرستاد.

### front_client
- `lib/bankLogos.ts` + `public/bank-logos/` (جدید) — جفت تایپ‌اسکریپتیِ `IranianBankDetector`؛ لوگوی بانک کنار شماره‌ی کارت در صفحه‌ی پرداخت.
- صفحه‌های `checkout` و `booking-success` دیگر به دکمه‌ی back مرورگر تکیه نمی‌کنند؛ «انصراف» نوبت را واقعاً آزاد می‌کند.
- جای معرفی (Bio) بر اساس تعداد *خط* تصمیم گرفته می‌شود نه تعداد کاراکتر — یک بیوی پنج‌خطیِ کوتاه هم در hero سرِ خط دوم بریده می‌شد.

Full commit range: [`v1.8.0...v1.9.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.8.0...v1.9.0)

## v1.8.0 — 2026-09-18

<!-- fa:start -->
تغییرات این نسخه:
- فرم تعریف/ویرایش کسب‌وکار به یک ذوستپر (شناسنامه، زمان‌بندی، خدمات، تماس) تبدیل شد تا فیلدهای زیاد در یک اسکرول بلند گم نشوند.
- رفع اشکال مهم: شماره‌های تماس اضافه و ساعت کار با دقیقه (مثلاً ۱۹:۳۰) با اینکه در فرم وارد می‌شدند، هیچ‌وقت واقعاً به سرور ارسال و ذخیره نمی‌شدند.
- امکان نمایش رمز عبور با آیکون چشم در تمام صفحه‌های ورود و ثبت‌نام.
- سقف معرفی کسب‌وکار (Bio) از ۳۰۰ به ۱۵۰۰ کاراکتر افزایش یافت، همراه با نمایش تعداد کاراکتر باقی‌مانده.
- ساعت‌های کار حالا با یک فیلد لمسی و چرخ ساعت انتخاب می‌شوند به‌جای چهار جعبه‌ی عددی شلوغ.
- دیالوگ اجباری آپدیت دیگر روی اجرای مستقیم از Android Studio نمایش داده نمی‌شود.
<!-- fa:end -->

### mobile_owner
- `BusinessFormStepper` (جدید) — استپر چهارمرحله‌ای با اعتبارسنجی هر مرحله قبل از رفتن به بعدی؛ مرحله‌های طی‌شده قابل بازگشت‌اند، مرحله‌های جلوتر قفل‌اند.
- `BusinessApiService.buildMultipart()` — رفع باگ: `extra_phones`، `work_start_minute` و `work_end_minute` هیچ‌وقت به بدنه‌ی درخواست multipart اضافه نمی‌شدند؛ سرور هم چون partial update است، غیاب فیلد را «دست‌نخورده بمان» تفسیر می‌کرد، پس این مقادیر از روز اول ثابت مانده بودند.
- `WorkHoursField` (جدید) — دو فیلد لمسی روی همان `TimeBottomSheet` چرخ‌دار به‌جای چهار جعبه‌ی عددی ساعت/دقیقه.
- `AppTextField` — دکمه‌ی نمایش/پنهان‌کردن رمز عبور (جایگزین دکمه‌ی پاک‌کردن روی فیلد رمز) و شمارنده‌ی کاراکتر اختیاری (`showCounter`).
- سقف بیو از ۳۰۰ به ۱۵۰۰ کاراکتر (هماهنگ با بکند)، ارتفاع فیلد به ۱۰ خط.
- رفع باگ فلیور «local»: به‌خاطر ناسازگاری پلاگین buildkonfig با فلیورهای اندروید، بیلد «local» همیشه واقعاً به سرور پروداکشن وصل می‌شد نه به بکند لوکال؛ حالا از `buildConfigField` واقعیِ اندروید (از طریق `AppConfig` جدید) استفاده می‌شود.
- `AppInfo.isDebug` — دیالوگ اجباری آپدیت روی اجرای دیباگ از Android Studio نمایش داده نمی‌شود.
- تب «روزهای تعطیل» از «تنظیمات پیشرفته» به مرحله‌ی «زمان‌بندی» همین استپر منتقل شد، و تب پرداخت در «تنظیمات پیشرفته» با کارت و آیکون بازطراحی شد.

### backend
- `Business.bio` از `CharField(300)` به `TextField` تغییر کرد؛ سقف اکنون `BIO_MAX_LENGTH=1500` در سریالایزر است، نه عرض ستون — تغییر بعدی یک خط کد است، بدون migration.

### front_client
- ست آیکون از Material دستی به Tabler تغییر کرد؛ نگاشت اشتباه دسته‌بندی (۱۴ تخصص پزشکی روی یک آیکون کلیپ‌بورد) حذف شد.
- هدر مرورگر و مانیفست PWA هر کسب‌وکار حالا رنگ خودش را می‌گیرند، نه بنفش برند را.
- دور آواتار یک کمان نئونیِ چرخان به رنگ کسب‌وکار اضافه شد؛ گرادیان hero عمیق‌تر شد تا برای رنگ‌های تیره یک‌دست و گلی نشود.
- لیست شماره‌های تماس حالا wrap می‌شود (قبلاً با بیش از یک شماره از عرض hero بیرون می‌زد).
- معرفی (Bio) طولانی به‌جای بزرگ‌کردن hero، در یک کارت «درباره‌ی ما» جداگانه با قابلیت باز/بسته شدن نمایش داده می‌شود.

Full commit range: [`v1.7.1...v1.8.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.7.1...v1.8.0)

## v1.7.1 — 2026-09-14

<!-- fa:start -->
تغییرات این نسخه:
- رفع اشکال: تنظیم روزهای تعطیل هفتگی (مثلاً جمعه) ذخیره نمی‌شد و آن روز همچنان در تقویم اپ و صفحه‌ی رزرو مشتری قابل نوبت‌گیری می‌ماند.
<!-- fa:end -->

### mobile_owner
- `BusinessApiService.buildMultipart()` never appended `closed_weekdays` to the update/create request body, so the setting silently never reached the server regardless of what was toggled in "روزهای تعطیل" — the request still returned 200 because the field was merely absent, not invalid.

Full commit range: [`v1.7.0...v1.7.1`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.7.0...v1.7.1)

## v1.7.0 — 2026-09-14

<!-- fa:start -->
تغییرات این نسخه:
- امکان تعیین روزهای تعطیل هفتگی برای کسب‌وکار (مثلاً هر جمعه) از بخش «تنظیمات پیشرفته» — این روزها هم در تقویم اپ و هم در صفحه‌ی رزرو مشتری نمایش داده می‌شود و نوبت‌گیری جدید در آن‌ها انجام نمی‌شود.
- بازه‌ی زمانی تقویم اپ دیگر ثابت روی ۱۰ دقیقه نیست و بر اساس مدت‌زمان واقعی سرویس هر کسب‌وکار تنظیم می‌شود.
<!-- fa:end -->

### mobile_owner
- `Business.closedWeekdays` end to end (domain model, DTO, Room entity/mapper, create/update request), with a new "روزهای تعطیل" tab in "تنظیمات پیشرفته" — weekday checkboxes plus a single toggle that clears the set (no separate "no day off" flag; an empty set already means that).
- Calendar day strip tints a closed weekday distinctly (selection still wins visually), and the slot list shows a non-blocking banner on a closed day — informational, not a lock, since the owner can still add a walk-in themselves.
- `TimeSlotsList` now generates slots at `business.defaultServiceDuration` instead of a hardcoded 10 minutes, fixing a 3-6x mismatch between the slots offered and what the business can actually book. The "scroll to now" index and appointment-coverage window were both hardcoded to the same 10 and are fixed the same way.

### backend
- `Business.closed_weekdays` — weekly recurring days off, stored as `date.weekday()` ints (Mon=0..Sun=6). Recurring only, not specific calendar-date holidays; affects only future availability and never touches an appointment already booked on a day later marked closed.
- `AvailableSlotsView` returns `closed: true` with an empty slot list for a closed weekday, so a client can tell "the business is closed" apart from "every slot today is booked".
- `closed_weekdays` is writable on the owner's `BusinessSerializer` and read-only on `PublicBusinessSerializer`.

### front_client
- The booking calendar greys out closed weekdays in the date-picker strip (converted from the backend's Mon=0..Sun=6 to JS's Sun=0..Sat=6 once at the API boundary) and shows "این روز تعطیل است" instead of the generic "no free hour" message when a closed day is selected.

Full commit range: [`v1.6.0...v1.7.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.6.0...v1.7.0)

## v1.6.0 — 2026-09-03

<!-- fa:start -->
تغییرات این نسخه:
- رفع اشکال: ثبت نوبت جدید برای مشتری‌ای که قبلاً از طریق وب نوبت گرفته بود با پیام «مشتری یافت نشد» رد می‌شد.
- تقویم حالا یک ماه قبل و یک ماه بعد را هم نشان می‌دهد؛ قبلاً فقط ماه جاری قابل مشاهده بود و نوبت‌گیری برای ماه بعد ممکن نبود.
- صفحه‌ی اصلی اپ بازطراحی شد: دسترسی سریع‌تر به تقویم و لینک نوبت‌گیری، و نمودار روند نوبت‌ها با دو حالت هفتگی/ماهانه.
- رفع اشکال نمایش نوار پایین صفحه در تم روشن.
<!-- fa:end -->

### mobile_owner
- Calendar day strip now spans a month behind and a month ahead of today (`DateTimeUtils.getDayWindow`), replacing `getNextDays`, which ignored its argument and returned only the current Jalali month — also fixing a latent bug where day 30 of a leap-year Esfand was silently dropped.
- Home screen rebuilt around a collapsing header (calendar + booking-link actions promoted above the fold) and a weekly/monthly line-chart trend card, replacing the old six-section stack and its bar-per-day month view.
- Bottom navigation bar given its own on-brand colour and a real border, fixing near-invisibility in the light theme; added `LocalIsDarkTheme` so components no longer guess dark/light from surface luminance.

### backend
- `Visitor.readable_by()` — the same "owner-created or has an appointment with this owner" rule visitor/views.py already used for reads, now shared with the appointment-create path, which had its own stricter `user=owner` filter and rejected booking for any customer who had signed up through the client web app.
- `daily-counts` gained a `days_ahead` parameter so the home screen's month card can report bookings that have not happened yet, not just past days.

Full commit range: [`v1.5.0...v1.6.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.5.0...v1.6.0)

## v1.5.0 — 2026-09-03

<!-- fa:start -->
تغییرات این نسخه:
- دسته‌بندی مشاغل کامل‌تر و دقیق‌تر شد (آرایشگاه زنانه/مردانه و رشته‌های پزشکی از هم تفکیک شدند)، و افزودن دسته‌های جدید در آینده دیگر نیازی به آپدیت اپ ندارد.
- رنگ صفحه‌ی عمومی کسب‌وکار حالا خودکار از روی لوگوی شما انتخاب می‌شود و نماد مرتبط با نوع شغلتان در صفحه نمایش داده می‌شود.
- امکان ارسال پیام تبلیغاتی هدفمند از سمت نوبت‌یار به مشتریان یا صاحبان کسب‌وکار.
- رفع اشکال: کسب‌وکارهایی که سابقه‌ی بررسی داشتند از پنل مدیریت قابل حذف نبودند.
- چند رفع اشکال جزئی در نمایش رنگ‌ها و بنر صفحه‌ی وب.
<!-- fa:end -->

### mobile_owner
- Category picker now fetches the live vocabulary from `GET business/categories/` and falls back to a bundled list only when the server is unreachable — adding a category on the backend no longer requires an app release.

### backend
- Business categories expanded from 37 to 68 and regrouped for precision (salon vs. barbershop, medical specialties split out individually).
- `Business.theme_color` derived from the logo at save time (WCAG-contrast-aware against white), replacing the fixed purple business header; `category_display` and the new `GET business/categories/` endpoint let clients read the vocabulary and its Persian labels without hardcoding either.
- Promotional push campaign system: a shared `PushCampaign` definition for admin-initiated, segmented pushes to customers or business owners, with optional image/link/deep-link.
- Fixed: a business with any moderation history — every business past its first review decision — could not be deleted from the admin, because the audit-log admin's blanket delete refusal was also consulted for the cascade and blocked it for the business itself, not just the log.

### front_client
- Business hero background colour follows `theme_color`; buttons, focus rings, and shadows across the themed page now derive from it instead of a hardcoded purple.
- Category-specific icon art (Tabler icon set) drawn in the hero, sized and positioned fully inside the header.
- Noobatyar promo banner recoloured to match the business's theme on business pages, and added to the booking-success and appointments screens (previously home + business page only).

Full commit range: [`v1.4.0...v1.5.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.4.0...v1.5.0)

## v1.4.0 — 2026-09-01

<!-- fa:start -->
تغییرات این نسخه:
- اعلان پوش (نوتیفیکیشن) برای نوبت جدید، تأیید/رد نوبت، و یادآوری نوبت، هم توی اپ و هم توی وب — رایگان و بدون نیاز به پیامک.
- پرداخت بیعانه از طریق درگاه زیبال حالا داخل خود اپ باز می‌شه (به‌جای مرورگر جدا)، هم اندروید هم iOS.
- تب جدید «پوش» در گزارش پیامک‌ها برای دیدن اعلان‌های ارسال‌شده.
- رفع باگ: ویرایش کسب‌وکار دیگه باعث آفلاین‌شدن موقتش نمی‌شه.
- رفع باگ امنیتی: پیام‌های مشتری بین کسب‌وکارهای مختلف یک صاحب دیگه با هم قاطی نمی‌شن.
- امکان تعیین کد یکتای دلخواه برای صفحه‌ی کسب‌وکار.
<!-- fa:end -->

### mobile_owner
- Push tab in the SMS report screen showing delivery status of owner/customer push notifications (Phase 1.5).
- App-wide notification permission prompt on first launch; warns when the saved reminder setting outruns the browser's actual permission state.
- Push notifications (booking/confirm/reject/reminder) gated behind پرو/پرو پلاس plans, matching the existing SMS reminder gate.
- In-app WebView for Zibal deposit payments on both Android and iOS, fixing a Referer-header issue the external browser flow had.
- Notification channel now created at app startup rather than on first use, so Android 8+ never silently drops the very first push.

### backend
- End-to-end FCM push system: booking/confirm/reject/reminder push to owners and customers, an owner-facing push-log report endpoint, admin-to-owner messaging (push and/or SMS), and promotional push to customer segments.
- Reminder delivery status (SMS + push) now exposed on the visitor's own appointment records.
- Fixed a data-isolation bug where an owner running multiple businesses saw one business's customer messages mixed into another's.
- Fixed a bug where editing an approved business could return it to moderation review and take it offline mid-edit.
- Accounting plan ladder pricing/quotas reworked; businesses can now be given a custom `unique_code` from the admin.
- Bale bot support for reviewing and deciding on the content-moderation queue directly from Telegram/Bale.

### front_client
- Redesigned client web UI: Material icons throughout (replacing emoji), animated hero/banner, per-field validation with Persian-digit normalization, 4-digit-grouped card number with Luhn checksum warning, receipt upload with crop + client-side compression, and an animated booking-confirmation screen.
- Home page now shows the customer's own upcoming appointments instead of a static promo screen.
- FCM web push for appointment reminders; the app is now an installable PWA.
- Business pages made discoverable/indexable for SEO; booking button shows a loading spinner while navigating to checkout.

Full commit range: [`v1.3.0...v1.4.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.3.0...v1.4.0)

## v1.3.0 — 2026-08-11

<!-- fa:start -->
تغییرات این نسخه:
- سیستم نظارت بر محتوای کسب‌وکارها اضافه شد: کسب‌وکارهای جدید و ویرایش‌های حساس (نام، توضیحات، آدرس، لوگو) پیش از نمایش عمومی بررسی می‌شن.
- در اپ صاحب کسب‌وکار، وضعیت بررسی و دلیل رد (در صورت وجود) روی صفحه‌ی تنظیمات نمایش داده می‌شه.
- تیک «تأیید شده»ی قبلی که صرفاً تزئینی بود و ممکن بود گمراه‌کننده باشه، حذف شد.
<!-- fa:end -->

### mobile_owner
- Business moderation status surfaced in Settings: badge (pending/approved/rejected/suspended), rejection reason when present, and a warning shown before an edit that would return an approved business to review.
- Removed the decorative "Verified" checkmark from the settings hero (it wasn't tied to real moderation state and read as a false claim); the real moderation badge replaces it.
- `is_locked` wired end-to-end (DTO → entity → mapper → domain) so `Business.isPubliclyVisible` matches the backend's combined billing+moderation check instead of moderation alone.
- Room DB bumped to version 11 for the new `isLocked` column (schema 14 exported).

### backend
- New content moderation system for businesses: review queue (oldest-submitted first, keyword matches highlighted), banned-keyword list (advisory only, never auto-rejects), POST-only/CSRF-protected/logged decisions, and automatic re-queue when a moderated field (title/bio/address/logo) is edited.
- Public business/slot endpoints now consistently check both billing lock and moderation state; a suspended business returns the same response as one that never existed.
- New admin panel foundation (`NobatyarAdminSite`) — RTL, self-hosted Vazirmatn font, role setup command (Superadmin/Moderator/Support/Finance).
- Redis `maxmemory-policy` changed from `allkeys-lru` to `volatile-lru` so paid SMS/appointment credit (stored with no TTL) can no longer be silently evicted under memory pressure.

### front_client
- Fixed a 404-from-slots bug that made a suspended business look "fully booked" instead of unavailable; shared `BusinessUnavailable` empty state and status-aware `ApiError`.

Full commit range: [`v1.2.0...v1.3.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.2.0...v1.3.0)

## v1.2.0 — 2026-08-07

<!-- fa:start -->
تغییرات این نسخه:
- بهبودهای جزئی پشت‌صحنه (بدون تغییر در تجربه‌ی کاربری).
<!-- fa:end -->

### mobile_owner
- Untracked Kotlin/Gradle build artifacts from git (`.gitignore` update); no user-facing change.

## v1.1.1 — 2026-08-07

<!-- fa:start -->
تغییرات این نسخه:
- داشبورد خانه بازطراحی شد: ساعت فعلی حذف شد، تعداد نوبت‌های امروز کنار تاریخ نمایش داده می‌شه، مستطیل‌های آمار به ۴ تا رسیدن، و یک ردیف جدید «افراد در صف» اضافه شد.
- همه‌ی کارت‌های آمار خانه حالا قابل کلیکن و با فیلتر درست شما رو به تب «مراجعین» می‌برن.
- محدودیت جدید برای «خدمات دریافت‌شده»: به‌جای متن آزاد، از یک لیست آماده (قابل افزودن) بر اساس دسته‌بندی کسب‌وکار انتخاب می‌کنید؛ خدمت جدید برای بقیه‌ی کسب‌وکارهای همون دسته هم قابل استفاده می‌شه.
- حداکثر مدت زمان سرویس از ۳ ساعت به ۸ ساعت افزایش پیدا کرد.
- پیام اضطراری از صفحه‌ی ساخت/ویرایش کسب‌وکار حذف شد (در تنظیمات همچنان در دسترسه).
- گزارش پیامک‌ها حالا فیلتر جست‌وجو بر اساس مشتری و بازه‌ی تاریخ داره.
- وقتی اعتبار پیامک این ماه تموم بشه، پیامک‌های ارسال‌نشده حالا در گزارش پیامک ثبت می‌شن و یک هشدار توی خانه نشون داده می‌شه.
- باگ لینک اینستاگرام «درباره‌ی ما» برطرف شد (آدرس اشتباه بود).
- باگ swipe نکردن بنرهای پلن که در نسخه‌ی قبل تازه اضافه شده بود، برطرف شد.
- دکمه‌ی «بروزرسانی» در پیام نسخه‌ی جدید حالا به سایت نوبت‌یار می‌ره.
- انتشار هر نسخه‌ی جدید حالا در GitHub Release و کپشن تلگرام هم به‌صورت خودکار توضیح داده می‌شه.
<!-- fa:end -->

### mobile_owner
- Home dashboard rework: removed the live clock, moved today's appointment count inline next to the date header, dropped the redundant "نوبت‌های امروز" card (4 stat cards remain), and added a full-width "افراد در صف" row sourced from data the ViewModel already fetched.
- All four stat cards plus the queue row now navigate to the existing "آخرین نوبت‌ها" tab with the right filter applied — previously this pushed a second, separately-stacked screen that lost the bottom nav bar; fixed to reuse the same tab-switch call the bottom bar itself uses (`PendingVisitorsFilter`, a one-shot holder consumed on tab entry instead of route arguments). A related bug where the status filter applied correctly but landed on the wrong sub-tab ("صف" instead of "مراجعین") was also fixed.
- Visitors tab now defaults to a 7-day window instead of showing everything; the Home 7-day trend chart navigates in with its own explicit past-date range since it summarizes the *past* week while the tab otherwise looks forward.
- Fixed two bugs rooted in Home's payment/business-switch handling: the payment-result dialog could replay itself after switching business (a stale deep-link intent being reprocessed on nav-graph rebuild), and today's stats could show a stale business's numbers if a slow-finishing load for the previously-selected business completed after the new one's.
- New service catalog: "خدمات دریافت‌شده" on the appointment-intake screen is now a chip picker (with an "add new" option) scoped to the business's category and shared across every business in that category, instead of unrestricted free text — the existing description field stays alongside it, unchanged.
- Service duration cap raised from 180 to 480 minutes (8 hours).
- Removed the inline emergency-notice section from the create/edit-business screen (the dedicated Settings screen is unaffected).
- SMS report: added search (customer name/phone) and date-range (today/this week/this month) filters. Messages skipped because the month's SMS quota ran out are now actually logged (a new `SKIPPED_QUOTA` status) instead of silently dropped, rendered with their own amber status and filter chip instead of misleadingly appearing as "sent".
- Home now shows a one-time warning toast per business when any messages were skipped this month for quota reasons, driven by a real server-side count, not a guess from the remaining-balance number.
- Fixed the Instagram link in "درباره‌ی ما" (`ajviddev` → `javiddev` — wrong since the very first commit).
- Fixed the plan banner regression from v1.1.0: wrapping `HorizontalPager` in a `SubcomposeLayout` for height-equalization silently broke swipe entirely. Replaced with an `onSizeChanged`-based max-height tracker that doesn't intercept touch input. Also fixed card height consistency and sort order (trial first, then ascending by duration) directly in the same pass.
- The out-of-date-version update button now opens noobatyar.ir instead of a Cafe Bazaar developer listing page.

### backend
- New `ServiceCatalogItem` model (category-scoped, not business-scoped — the mechanism for cross-business sharing) plus `Appointment.selected_services`, both backing the new service catalog feature.
- SMS report search/date filters on `GET business/<id>/sms-logs/`.
- `visitor.SmsLog` gains a `SKIPPED_QUOTA` status, written by all four `consume_sms()` call sites when quota is exhausted; `accounting.usage.sms_balance()` exposes a real `skipped_this_month` count via `GET accounting/my-entitlements/`.

### CI/CD
- Release workflow now creates an actual GitHub Release (previously it only built and sent to Telegram) and includes a Persian summary — sourced from this file's `fa:start`/`fa:end` block — in both the Release body and the Telegram APK caption. A tag with no matching block now fails the build loudly instead of shipping a placeholder release.

Full commit range: [`v1.1.0...v1.1.1`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.1.0...v1.1.1)

## v1.1.0 — 2026-08-06

<!-- fa:start -->
تغییرات این نسخه:
- نوتیفیکیشن‌ها (toast) حالا رنگ درستی دارن: موفقیت سبز، خطا قرمز، هشدار زرد — دیگه همه‌چیز قرمز نشون داده نمی‌شه.
- خطای «نشست منقضی شده» و «محدودیت درخواست» حالا پیام واقعی نشون می‌ده، نه ریست بی‌صدای برنامه.
- بنرهای خرید اشتراک در صفحه‌ی خانه: به‌جای چرخش خودکار (که قبل از خوندن رد می‌شد)، حالا با دست swipe می‌شن و لبه‌ی بنر بعدی هم دیده می‌شه.
- باگ Pull-to-refresh برطرف شد: متن «در حال بروزرسانی…» زیر محتوای صفحه بریده نمی‌شد.
- صفحه‌ی «درباره‌ی ما» بازطراحی شد؛ آیکون‌های شبکه‌های اجتماعی جدید و باکیفیت‌تر.
- پیام اضطراری کسب‌وکار، یادآوری چندکاناله، گزارش پیامک با فیلتر، و ثبت بیعانه‌ی پرداختی به نوبت اضافه شد.
- آمار «تکمیل‌شده» در داشبورد که اشتباه محاسبه می‌شد، درست شد.
- صفحه‌ی اصلی وب دیگه لیست کسب‌وکارها رو بدون ورود نشون نمی‌ده؛ حالا صفحه‌ی معرفی اپلیکیشنه.
<!-- fa:end -->

### mobile_owner
- Typed toasts: success/error/warning now render with distinct styling instead of every screen showing the same error-red toast. Settings and LastVisitors, which had no toast host at all, now surface their ViewModel error messages. Several actions (delete/complete/no-show/approve/reject/send-message in LastVisitors) previously succeeded with zero user feedback — they now confirm.
- Session-expired / rate-limit errors show an actual message instead of silently resetting app state.
- Home screen plan banners: replaced the auto-rotating carousel with manual swipe — each banner is now narrower than the screen so the next one peeks at the edge, plus a hint telling the user they can swipe. Auto-rotation was disorienting and skipped past banners before anyone could read them.
- Pull-to-refresh: fixed the resting indicator height being smaller than its own label text, which clipped the bottom of "در حال بروزرسانی…" under the page content after a release.
- About Us screen redesign: animated gradient hero card, and the "ارتباط با ما" social links moved from an uneven icon grid to a vertical list with handle text, redesigned icons (bale/eitaa/rubika/instagram/website) replacing heavy, illegible bitmap auto-traces.
- Version check now sends the real numeric `versionCode` instead of deriving one by stripping dots out of the version name (`"1.2.3"` → `123`).
- Release build config fixed: CI now builds `assembleProdRelease` (the `prod` flavor) explicitly, since the module gained a `local`/`prod` flavor split and the untargeted build was producing both and moving the APK output path.
- New: emergency notice banner (owner-configurable, shown to clients), tiered reminder delivery, SMS report screen with quota + status filtering, deposit-payment tracking on appointments, dashboard "completed" stat fixed to actually count completed appointments, appointment edit no longer blocks on the appointment's own occupied slot, list filters (date range / sort) now apply correctly after sync.

### front_client
- Home page (`/`) no longer lists every registered business unauthenticated; it's now a landing/promo page for the app itself, since `/` was never the real booking entry point (`/b/<code>` is).
- Emergency notice banner shown on business profile and booking pages when the owner has one enabled.
- Fixed a dark-theme color token (`--color-warning-tint`) missing from the dark media block.

### backend
- Emergency notice, reminder delivery, and SMS report endpoints/models.
- Deposit payment method + tracking code/payment reference now exposed on appointment serialization.

### tooling / docs
- Documented the `-Pbuildkonfig.flavor` requirement for local Android builds — building the `local` product flavor without this flag silently produces prod's `BASE_URL` instead of `10.0.2.2`.
- Local vs. production config separated for backend (`.env.local`) and mobile (Gradle flavors), documented in `docs/ENVIRONMENTS.md`.

Full commit range: [`v1.0.7...v1.1.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.0.7...v1.1.0)
