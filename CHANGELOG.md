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

## v1.13.1 — 2026-11-05 (اپ اونر)

<!-- fa:start -->
تغییرات این نسخه:
- کارت منبع نوبت‌ها در صفحه‌ی خانه ساده شد: «نوبت‌های آنلاین این ماه: از اپ · از سایت».
- در پنل وب، دکمه‌های «مدیریت کسب‌وکار» در چند ردیف می‌آیند و عنوان‌ها کامل دیده می‌شوند.
- آیکن تازه‌ی مشکی «نوبت‌یار کارفرما» و تم روشن به‌عنوان پیش‌فرض.
- صفحه‌ی خانه‌ی تازه: با اسکرول، تقویم و اشتراک لینک به نوار بالا می‌روند و کارت «امروز» همه‌چیز را یک‌جا نشان می‌دهد.
- نمونه‌کار و مدارک را در قاب مربع، عمودی یا افقی بارگذاری کنید.
- شماره‌ی مشتری‌ها در گزارش ارسال‌ها امن‌تر و درست نمایش داده می‌شود.
<!-- fa:end -->

### mobile_owner
- `HomeSourceCard`: neutral title and a short body (was «مشتری‌هایی که نوبت‌یار آورد» and a sentence cut off on the panel).
- Settings (wide layout): `SettingsTiles` columns from a 190dp minimum width instead of all tiles in one row.

## v1.13.0 — 2026-11-05 (اپ اونر)

<!-- fa:start -->
تغییرات این نسخه:
- تب پروفایل گروه‌بندی شد و هر تنظیم یک ردیف جدا دارد.
- ظرفیت هم‌زمان هر نوبت: چند مشتری در یک ساعت (۱ تا ۵۰۰).
- «زمان نگه‌داشتن نوبت تا پرداخت» (۲ تا ۱۵ دقیقه) ردیف خودش را در منوی نوبت‌دهی دارد.
- تخفیف اولین مراجعه برای مشتری تازه، با برچسب «مشتری تازه» در صف.
- فهرست دنبال‌کننده‌های صفحه‌ی شما.
- مرحله‌ی پرداخت در فرم کسب‌وکار: بیعانه، ظرفیت و روش‌های پرداخت یک‌جا.
- موقعیت روی نقشه حالا لازم است؛ می‌توانید فقط محدوده را نشان دهید، نه جای دقیق.
- نقشه همان ظاهر نرم و خوانای اپ مشتری را دارد.
- آیکن نوبت‌یار مرتب‌تر وسط قرار گرفت.
<!-- fa:end -->

### mobile_owner
- Profile tab grouped into sections; every advanced setting is its own row (`AdvancedSection.CAPACITY/PAYMENT/DEPOSIT/HOLD`).
- Slot capacity (1-500) and deposit hold (2-15 min) on `/api/business/<id>/booking-capacity/`; payment step in the business form.
- First-visit discount settings, «مشتری تازه» badge in the queue, followers list.
- Business form: map pin required; `location_privacy` EXACT / AREA (segmented choice, circle preview). Room v24.
- Map: HOT tiles + `TileRecolor`, dark dim, `noobatyar.ir` chip (same as the client app).
- Brand mark lifted 16 units in every framed icon (launcher, round, adaptive foreground, `main_icon.png`) and on the nav ball.

### backend
- business/0039 first-visit discount, 0040 `slot_capacity` + `hold_minutes`, 0041 `location_privacy`; stories/0002. Area-only businesses: shifted public centre (HMAC of id), `location_approximate`, `location_radius_m`, distances to 0.5 km.

## client-v1.3.0 — 2026-11-05 (اپ مشتری)

<!-- fa:start -->
تغییرات این نسخه:
- استوری مرکزها و نوبت‌یار؛ برای مکث انگشت را نگه دارید.
- دکمه‌ی «اولین نوبت خالی» در صفحه‌ی مرکز.
- تخفیف اولین مراجعه در مرکزهایی که آن را دارند.
- بعد از نوبت از شما نظر خواسته می‌شود.
- صفحه‌ی شروع و معرفی تازه با انتخاب شهر.
- نوار پایین تازه و متحرک.
- مرکزهایی که فقط محدوده را نشان می‌دهند روی نقشه با یک دایره دیده می‌شوند.
- آیکن نوبت‌یار مرتب‌تر وسط قرار گرفت.
- صفحه‌ی پروفایل از نو طراحی شد: ساده‌تر و مرتب‌تر، با نام و «درباره من» در بالا.
- عکس پروفایل را قبل از ذخیره برش بزنید و جابه‌جا کنید.
- «درباره من»: تا ۱۵۰ حرف درباره خودتان بنویسید؛ فقط خودتان می‌بینید.
- صفحه‌ی مرکزها را دنبال کنید و فهرست «دنبال‌شده‌ها» را در پروفایل ببینید.
- عکس پروفایل افراد کنار نظرهایشان دیده می‌شود.
- شماره‌ی تلفن هیچ مشتری دیگر در نظرها نشان داده نمی‌شود.
<!-- fa:end -->

### mobile_client
- Profile tab redesign: neutral surfaces with hairline borders, single-colour icons, name + bio header, one stats strip (bookings / following / saved), «فعالیت من» group with the new following list.
- `ImageCropperDialog` (ported from the owner app, with `cropImageToSquare` actuals for Android and iOS) before the avatar upload.
- Bio field with a live `n / 150` counter (cut at 150 on paste); `ProfileApi.update` saves name and bio together.
- Follow / unfollow on the business page (`FollowRow`, optimistic, guest is sent to sign in), `FollowingScreen` (`AppScreens.Following`).
- Reviewer photo in review rows; version check sends `app: "CLIENT"`.

### backend
- `BusinessFollow` + `Business.followers_count` (business/0037), `Visitor.bio` (visitor/0015), follow endpoints and `following/` list, `is_following` on the client detail for a signed-in visitor.
- Public reviews: `visitor_avatar` added, `visitor_phone` always empty (owner list keeps the masked number).
- Version check: old client builds (no `app`) are recognised by their client-only version codes; versions/0004 seeds the client's released versions.

### front_client
- Follower count and follow button on the business page; reviewer photo (or initial) in reviews.

### mobile_owner
- Settings business card shows the follower count (Room v22).

## v1.12.0 — 2026-11-04 (اپ اونر)

<!-- fa:start -->
تغییرات این نسخه:
- صفحه‌ی شروع تازه با لوگوی متحرک نوبت‌یار.
- موجودی کیف‌پول حالا در پنجره‌ی پروفایل هم دیده می‌شود و با زدن روی آن صفحه‌ی کیف‌پول باز می‌شود.
- صفحه‌ی خانه هنگام اسکرول دیگر پرش ندارد.
- نمودار روند نوبت‌ها مثل ماه‌ها از راست به چپ است و دکمه‌ی هفتگی/ماهانه مرتب‌تر شد.
- مبلغ بیعانه حداکثر ۱٬۰۰۰٬۰۰۰ تومان است.
<!-- fa:end -->

### mobile_owner
- `LaunchSplash`: the web panel's boot animation on Android/iOS (violet ring with a running light band, ticking amber dot, strike and check, fade), on `WaitingNoobatyarMark`. Dark launch theme (`Theme.Noobatyar`, transparent system-splash icon on Android 12+); light status-bar icons while it shows. Not on wasm (index.html has its own loader).
- Home: the header folds through nested scroll (no more jump), trend chart runs right to left in RTL, weekly/monthly toggle no longer padded to a 48dp touch target.
- Profile sheet shows the toman wallet balance (from `my-entitlements`) and opens the wallet.
- Deposit field warns above 1,000,000 toman.

### backend (already deployed 2026-11-04)
- New-client reward into the owner's toman wallet (`TomanWalletSettings.new_client_reward`, 0 = off; lifetime cap per business, default 500,000; optional monthly cap per owner), logged in `NewClientReward` with risk flags (same IP as the owner, new account, quick completion, burst). Client bookings store `booking_ip`; owner IPs recorded by `IpRecordingJWTAuthentication`.
- `Business.MAX_DEPOSIT_AMOUNT` = 1,000,000 toman, enforced on change.

## v1.11.0

<!-- fa:start -->
تغییرات این نسخه:
- اعتبار بسته‌های افزودنی حالا با واحدش نمایش داده می‌شود (مثلاً «+۴۹ نوبت اعتبار اضافه»)، و روی پلن نوبت نامحدود دیگر اعتبار نوبتی که خرج نمی‌شود نشان داده نمی‌شود.
- صفحه‌ی تازه‌ی «کیف‌پول و اعتبار»: موجودی تومانی، اعتبار نوبت و پیامک و تاریخچه‌ی همه‌ی آن‌ها. از کارت «مصرف این ماه» در خانه یا از تنظیمات باز می‌شود.
- کیف‌پول تومانی: مبلغی که نوبت‌یار به شما برمی‌گرداند (مثل تخفیف نوبت‌ها). نقد نمی‌شود؛ هنگام خرید اشتراک یا بسته می‌توانید تمام یا بخشی از مبلغ را از کیف‌پول بپردازید، یا موجودی را به پیامک و نوبت تبدیل کنید. مالیات بر ارزش افزوده روی کل مبلغ حساب می‌شود و کارمزد درگاه فقط روی بخشی که از درگاه پرداخت می‌کنید.
- صفحه‌ی خانه از نو طراحی شد: یک کارت «امروز» با صف زنده و وضعیت نوبت‌ها، نمودار روند تمیزتر، کارت مصرف با کیف‌پول، همه مرتب و هم‌تراز، با انیمیشن‌های نرم.
- نام اپ به «نوبت‌یار کارفرما» تغییر کرد و آیکون تازه‌ای گرفت (سرمه‌ای با نشان کیف) تا از اپ مشتری‌ها جدا باشد.
<!-- fa:end -->

### mobile_owner
- Branding: launcher name «نوبت‌یار کارفرما» / "Noobatyar Business"; launcher icons (all densities, round, legacy, Play Store 512) redrawn on a navy gradient with an orange briefcase badge, so the owner app is told apart from the customer app («نوبت‌یار», purple).
- Home redesign (`feature/home`): shared `HomeCard`/`HomeCardHeader` (one radius, padding and header row), `HomeTodayCard` (today's count, live queue chip, four outcomes; 2×2 under 380dp) replacing the queue banner and tinted stat grid, trend card with total/caption/link on one row and a clean line chart that sweeps in, usage card with animated bars and a wallet row, flat plan cards. Motion: staggered entrance in the draw layer, count-up numbers, press feedback.
- Checkout dialog lists price, VAT, wallet deduction, then fee.
- Home usage card: wallet note reads «+N نوبت/پیامک اعتبار اضافه» (local digits) instead of the unit-less «کیف‌پول: N»; the appointment note is hidden on an unlimited booking quota (it can never be spent there). Tapping the note opens the wallet screen. The SMS report badge uses the same wording.
- New `feature/wallet` (MVI): both balances, a one-line explainer (spent only after the monthly allowance; shared across all businesses), a hint when appointment credit is idle on an unlimited plan, and the paginated history. Also reachable from Settings under the profile/plan card.
- `commonTest` source set (kotlin-test, coroutines-test, ktor-client-mock, turbine — same setup as the `mobile-client` branch) with `WalletViewModelTest` and `WalletLabelsTest`.
- Toman wallet: balance card + convert-to-SMS/bookings dialog on the wallet screen, «کیف‌پول: N تومان» on the home usage card, toman rows in the history. Checkout dialog gets a «پرداخت از کیف‌پول» switch that re-quotes (wallet line, tax and fee on the rest; a fully covered price skips the gateway). Store builds only ever pay fully from the wallet (`wallet_only`). `CreditsChanged` refreshes the home card after a purchase or conversion.

### backend
- `GET accounting/wallet/history/` (authenticated, `?page`, `?page_size`, optional `?metric=appointment_wallet|sms_wallet`): the user's own `CreditLedger` rows for the two wallet metrics with `delta`, `balance_after`, `reason` + Persian `reason_label`, plus current `balances` and quotas. `usage.REASON_ADDON_PURCHASE` / `wallet_reason_label`. Tests in `accounting/tests_wallet_history.py`.
- Toman wallet (`accounting/toman_wallet.py`, metric `toman_wallet` on `CreditBalance`/`CreditLedger`): `credit()`/`spend()` for other apps (booking discounts), `manage.py toman_wallet_credit`. `plans/payment/` and `addons/buy/` take `use_wallet` / `wallet_only`; the wallet part is held on the Transaction/AddOnPurchase (`wallet_amount`, migration `0016`) and given back when the gateway refuses, verification says not paid, or the payment is abandoned for an hour (`run_subscription_lifecycle`). `checkout/quote/` returns `wallet_balance`/`wallet_available`/`wallet`. `wallet/convert/` at the prices in `TomanWalletSettings` (admin). Revenue reports keep counting only cash (`amount`). Order ids get a random tail.
- The wallet pays its part of a price plus the VAT on it (`toman_wallet.with_vat`); the gateway fee is only on what the gateway collects. `checkout/quote/` `vat` is now the whole price's VAT and `wallet` includes it.

## v1.10.0 — 2026-11-02 (اپ اونر)

<!-- fa:start -->
تغییرات این نسخه:
- نمونه‌کار و مدارک: عکس کارها (۱۰۸۰×۱۰۸۰) و مدارک (۱۰۸۰×۱۳۵۰) را آپلود کنید؛ بعد از تأیید در صفحه‌ی عمومی‌تان نمایش داده می‌شوند.
- نظرات مشتری‌ها: صفحه‌ی نظرات و اعلان برای هر نظر جدید.
- روی هر نوبت می‌بینید از کجا آمده: اپ نوبت‌یار، سایت یا ثبت توسط خودتان.
- کارت «مشتری‌هایی که نوبت‌یار آورد» در صفحه‌ی اصلی: تعداد نوبت‌های این ماه از اپ و سایت.
- بنرهای صفحه‌ی اصلی بر اساس دسته‌ی کسب‌وکار.
- لوگو هنگام ساخت کسب‌وکار اجباری شد.
- شبکه‌های اجتماعی: اینستاگرام، تلگرام، واتساپ، ایتا، بله، روبیکا و سایت‌تان را در فرم کسب‌وکار وارد کنید تا مشتری‌ها در صفحه‌تان ببینند.
- هشدار بیعانه: بیعانه‌ی اجباری بدون کارت به کارت یا پرداخت آنلاین ذخیره نمی‌شود؛ دیگر مشتری در صفحه‌ی پرداخت گیر نمی‌کند.
- در انتخاب شهر، لیست شهرهای هر استان از بالا شروع می‌شود.
- رفع خطای صفحه‌ی اصلی پنل وب و چند مشکل همگام‌سازی نوبت‌ها.
<!-- fa:end -->

### mobile_owner
- Showcase: portfolio and certificate upload with crop + compression, pending/approved state; reviews page (read-only) and `NEW_REVIEW` push (Android opens the reviews page).
- Appointment source badge (`AppointmentSource`); home `SourceSummaryCard` from `appointment/stats/` `source_counts` over the current month (`GetSourceSummaryUseCase`).
- Rotating home banners per business category; logo required on the first create-business step.
- Social links (`Business.socialLinks`, `SocialLinksField`; Room v21); mandatory-deposit warning in the capacity tab; city picker keeps a scroll state per list.
- Fixes: wasm home crash on Asia/Tehran, visitor messages keyed by (visitor, business), logout/sync cache leaks, server day window mirrored.

### backend (ships with this release)
- P2 apps: `showcase`, `reviews`, `banners`, `marketing`; `Appointment.source`; `Business.rank_score` + nightly `compute_rank_scores`; `first_free_slot`; site services under `api/public/site/`.
- `Business.social_links` (`business/0036`); mandatory deposit with no card/online method is refused on change and no longer holds the slot.
- `AppVersion.app` (OWNER/CLIENT): set `force_update` on the OWNER row for this version in admin when releasing.

## client-v1.2.0 — 2026-11-04 (اپ مشتری)

<!-- fa:start -->
تغییرات این نسخه:
- صفحه‌ی شروع تازه با لوگوی متحرک نوبت‌یار.
<!-- fa:end -->

### mobile_client
- `LaunchSplash` (same as the owner app's), with `NoobatyarMark.kt` copied in; dark launch theme `Theme.Noobatyar`.

## client-v1.1.0 — 2026-11-03 (اپ مشتری)

<!-- fa:start -->
تغییرات این نسخه:
- صفحه‌ی کاوش از نو طراحی شد: دسته‌ها در یک ردیف، فهرست مراکز در یک شیت که سه حالت دارد، و عنوان نتایج با دسته (مثلاً «مراکز دندان‌پزشکی در تهران»).
- پیام خالی بودن نتایج و خطا ساده‌تر و روشن‌تر شد.
- شبکه‌های اجتماعی هر مرکز در صفحه‌ی مرکز دیده می‌شود.
- کد تأیید با نمایش متحرک وارد می‌شود.
- مشکلی که در نوبت‌های دارای بیعانه نوبت را گیر می‌انداخت برطرف شد.
- از این نسخه یک فایل جدا برای مایکت و بازار منتشر می‌شود.
<!-- fa:end -->

### mobile_client
- Explore redesign: seven categories in one row, three-state results sheet (lowest state shows only the title line), results title carries the category, plain empty/error state.
- Social links on the business page; animated OTP code entry; fix for the deposit deadlock on booking.
- Release flavors: `store` dimension with `site` (noobatyar.ir, Telegram, GitHub) and `store` (one APK for Myket and Bazaar). The app has no purchases, so the two differ only in `BuildConfig.STORE`.

## client-v1.0.0 — منتشر نشده (اپ مشتری)

<!-- fa:start -->
اولین نسخه‌ی اپ مشتری نوبت‌یار:
- پیدا کردن نزدیک‌ترین پزشک، دندان‌پزشک، سالن زیبایی و مشاور روی نقشه، با فیلتر بیمه و دسته‌بندی.
- رزرو نوبت داخل خود اپ: انتخاب خدمت، روز و ساعت، و پرداخت بیعانه.
- تب «نوبت‌های من»: پیگیری، لغو و ثبت واریز کارت به کارت یا پرداخت حضوری.
- «اولین نوبت خالی» روی کارت هر مرکز.
- صفحه‌ی هر مرکز: نمونه‌کار، مدارک، نظرات، ساعات کاری، بیعانه و روش‌های پرداخت.
- ثبت نظر و امتیاز، و گزارش نظر یا مرکز نامناسب.
<!-- fa:end -->

### mobile_client (P2)
- Native booking (`source=APP`), my-appointments tab with card/cash payment submission, first free slot on list cards.
- Business page: portfolio/certificates gallery with display-time watermark, reviews (write/report), deposit and payment-method section (`PaymentInfo`); booking confirm step now shows a MANDATORY deposit (was checking a non-existent `REQUIRED` value).
- Separate release train: `client-vX.Y.Z` tags (`android-release-client.yml`); `force_update` on the CLIENT `AppVersion` row.

### mobile_client (discovery, earlier)

### backend
- Claim flow for imported listings: `GET business/claimable/`, `POST business/<id>/claim/` (OTP to the listing's mobile, else manual review), `POST business/<id>/claim/verify/`; `BusinessClaimRequest` model + admin approve/reject actions (`business/claims.py`).
- `request-booking` is now one vote per device (`OnlineBookingRequest`, keyed on a hash of `X-Device-Id`/IP); response carries `already_requested`.
- Geo search radius capped at 100 km.
- `import_directory`: no placeholder phone for rows without one, invalid phones dropped, dedup falls back to (title, city, address) when there is no phone, out-of-range coordinates and bad ints ignored, JSON arrays streamed instead of `json.load`.

### mobile_client
- Owner-only screens removed; new tabs: Explore / Saved / More.
- Real one-shot GPS (`core/location`): Android `LocationManager` (works without Play Services), iOS CoreLocation; city picker and radius as fallback.
- Redesigned explore and business detail screens; pagination; report-listing dialog; share and map hand-off.
- Release builds are HTTPS-only; cleartext only in the `local` flavor. Alarm/notification permissions dropped.
- Network failures no longer crash the app: discovery calls go through `safeApiCall` and show an error state.
- UI rebuilt on a neutral design system (per docs/BRAND_GUIDE_FOR_AI.md: violet is the only accent): white/neutral surfaces, 8pt spacing, two radii, outlined monochrome icons (custom tooth glyph for dental), divider-separated result rows with monogram avatars, compact sticky search header, standard flat bottom navigation, segmented theme control. Gradients, emoji, per-category colours and the floating tab bar removed.
- Features: first-launch welcome screen (asks for location in context), recent + popular searches, open-now status (Tehran time), weekly hours table, sticky book/call bar on detail, consumer-facing About page with all channels on `noobatyar` + Telegram.

### mobile_owner
- «مرکزم از قبل ثبت شده؟» on the business list opens the claim flow.

### front_client
- `/privacy` page (needed for the store listing's location permission).

## v1.9.8 — 2026-10-17

<!-- fa:start -->
تغییرات این نسخه:
- برنامه حالا انگلیسی هم دارد؛ زبان را از تنظیمات عوض کنید.
- تصویرهای اشتراک (کد QR، استوری، شماره کارت و کارت یادآوری) آماده‌ی چاپ در اندازه‌ی ۱۴×۲۱ سانتی‌متر شدند و چیدمانشان مرتب‌تر شد؛ مناسب استند روی میز پذیرش.
- در نسخه‌ی مایکت، فاکتور فقط قیمت و مالیات را نشان می‌دهد (جمع پرداختی تغییری نکرده).
<!-- fa:end -->

### mobile_owner
- i18n: all UI strings in `composeResources/values` (fa) and `values-en`; generated `Str` follows the in-app `AppLocale` (direction, Nunito/Yekan, Gregorian calendar and Latin digits in English). Language picker in Settings.
- Share cards export at 1654 × 2480 px (14 × 21 cm, 300 dpi, pHYs-tagged); shared centred header/footer, signature no longer overlays the link, QR size adapts so long names fit.
- Myket checkout shows price + VAT only (Myket's cut folded into the price).
- iOS compiles again (`iosMain` now depends on `roomMain`).

### front_client
- Persian/English switch (`lang` cookie), logical CSS; SSR locale read per request from context instead of a shared module variable.

### ci
- Android versionCode = previous release's + 1 (from `Config.xcconfig` on develop), instead of epoch seconds.

## v1.9.7 — 2026-10-09

<!-- fa:start -->
تغییرات این نسخه:
- پیش از پرداخت، جزئیات مبلغ (قیمت، مالیات بر ارزش افزوده و کارمزد پرداخت) نمایش داده می‌شود و پرداخت فقط با تأیید شما انجام می‌شود.
- بهبودهای پنل وب و رفع چند ایراد.
<!-- fa:end -->

### mobile_owner
- Checkout confirm dialog: every plan/pack purchase fetches `accounting/checkout/quote/` and shows price, VAT and the Zibal fee or Myket cut before opening the gateway or Myket billing (`core/billing/CheckoutConfirmation.kt`, `PurchaseUseCase`).
- Web panel: fills the screen when installed, toolbar below the status bar, browser back stays in the panel, payers return to the panel.

### backend
- `accounting/pricing.py`: price is the net; VAT 10% and Zibal's fee are added on top, Myket store prices grossed up for VAT, Myket's 15% and Shaparak. `manage.py myket_prices` lists the Myket panel prices. Paid items can't be priced under 10,000 toman.
- Security: removed `accounting/plans/buy/`, which activated any plan without payment.
- Admin: renewal SMS to owners whose subscription ended.

## v1.9.6 — 2026-10-01

<!-- fa:start -->
تغییرات این نسخه:
- ثبت موقعیت کسب‌وکار روی نقشه؛ مشتری‌ها آن را در صفحه‌ی رزرو می‌بینند و با نشان یا بلد مسیریابی می‌کنند.
- معرفی کوتاه برنامه برای کاربران تازه، و صفحه‌های ورود و ثبت‌نام تازه.
- طراحی تازه‌ی ثبت نوبت و فهرست کسب‌وکارها.
- خروج از حساب حالا نشست را روی سرور هم می‌بندد.
- رفع ایراد: شمارش معکوس کد تأیید، نمایش‌ندادن خطای ورود، و آیکن ریز به‌روزرسانی صفحه.
<!-- fa:end -->

### mobile_owner
- Business location: optional map pin in the business form (MapCompose 0.13.2, OSM raster tiles), full-screen picker with "my location" on Android/web. Room version 19.
- `BusinessDto.toDomain()` maps the fields it used to drop (extra phones, minutes, closed weekdays, deposit mode, reminder template), which the next save wiped.
- Onboarding (phone width), animated auth headers, redesigned new-appointment and business-list screens, desktop sidebar on every screen, ripples clipped to rounded shapes.
- Security: the bearer token is no longer logged; logout sends the refresh token so the server revokes it; web links open with noopener.
- Web panel: API host chosen from the page's host (localhost → dev, otherwise production), installable (manifest, icons, iOS tags), boot script moved out of index.html for CSP.

### backend
- Business latitude/longitude; admin contact-visibility policy for owners without a subscription (`is_expired`, `has_location`).
- Admin campaigns page split into tabs; expiry SMS rework.
- Login throttle + per-phone lockout, one password change per 24h, user_detail needs model permissions, role read-only.
- nginx: security headers, sandboxed /media/, owner panel on panel.noobatyar.ir.

### front_client
- Map card with Neshan / Balad routing on the business page; blurred placeholders for withheld contact info.

## v1.9.5 — 2026-09-25

<!-- fa:start -->
⚠️ برای نصب این نسخه، ابتدا برنامه‌ی فعلی را حذف کنید و سپس نسخه‌ی جدید را نصب کنید.

تغییرات این نسخه:
- سه نسخه‌ی اندروید: سایت، بازار و مایکت.
- نسخه‌ی سایت و بازار (و iOS) با درگاه پرداخت نوبت‌یار؛ نسخه‌ی مایکت با پرداخت درون‌برنامه‌ای مایکت.
- پیام‌ها بر اساس نوع رنگ‌بندی شدند: موفق، خطا و هشدار.
- کارت‌های صف و مراجعین مرتب‌تر و خواناتر شدند.
- اشتراک‌گذاری: استوری آموزش نوبت‌گرفتن، و کارت شماره‌کارت راست‌چین و خواناتر.
- تنظیمات اعلان‌ها حالا روی سرور ذخیره می‌شود.
<!-- fa:end -->

### mobile_owner
- New `bazaar` store flavor (`STORE="bazaar"`, `src/androidBazaar` returns no store billing → Zibal gateway).
- Release workflow also publishes `Noobatyar-Owner-<version>-bazaar.apk` for upload to Cafe Bazaar.
- Typed toasts (`UiMessage`) on every screen that still showed plain red strings; 403s show the server's message.
- Notifications screen saves through `updateBusiness` (it only wrote the local Room cache before).
- Queue / visitors appointment cards restructured; share cards RTL, story card is the booking guide.
- Room database version 18 (no schema change; clean cache).
- `local` flavor derives `BOOKING_BASE_URL` from `-PlocalBaseUrl`.

### backend
- Business update only gates plan features that are newly enabled (a saved card number no longer blocks every edit).
- Dev OTP code works without a prior send and never counts towards a ban (DEBUG only).

### front_client
- Signed-out visitors go to sign in / sign up before booking; "نوبت‌های من" beside the booking actions.
- Appointment details show the business logo; `allowedDevOrigins` for emulator testing.

## v1.9.4 — 2026-09-25

<!-- fa:start -->
تغییرات این نسخه:
- نسخه‌ی ویژه‌ی مایکت: خرید اشتراک و بسته‌ها در نسخه‌ی مایکت از طریق پرداخت درون‌برنامه‌ای مایکت انجام می‌شود.
- نسخه‌ی سایت و iOS بدون تغییر، با همان درگاه پرداخت نوبت‌یار.
- اگر برنامه وسط خرید بسته شود، خرید پرداخت‌شده در اجرای بعدی خودکار ثبت می‌شود.
<!-- fa:end -->

### mobile_owner
- Android flavor dimension `store`: `site` (Zibal, as before) and `myket` (`myket-billing-client` 1.19, `MyketStoreBilling`).
  `PurchaseUseCase` picks the store only when `StoreBillingProvider.billing` is set, which only the myket flavor does.
- Release workflow also publishes `Noobatyar-Owner-<version>-myket.apk` for upload to Myket.

### backend
- `accounting/payment/myket.py` — intent/verify endpoints (`accounting/store/myket/...`), server-to-server verification and
  consume, `StorePurchase` model, `myket_sku` on `Plan`/`AddOnPack`; `MYKET_ACCESS_TOKEN` from `.env`.

## v1.9.3 — 2026-09-24

<!-- fa:start -->
تغییرات این نسخه:
- پیامک تبلیغاتی: کمپین پیامکی با نام خود مشتری، انتخاب مخاطب (همه، مراجعه‌ی اخیر، مشتری‌های غایب)، نمایش هزینه‌ی دقیق پیش از ارسال و گزارش هر کمپین.
- پیام خودکار: در ارسال خودکار از پنل، متن از الگوهای تأییدشده انتخاب می‌شود، «لغو۱۱» ثابت است و تعداد پیامک شمرده می‌شود؛ زمان یادآوری هم حالا واقعاً ذخیره می‌شود.
- تنظیمات پیشرفته با طراحی تازه؛ رفع ریست‌شدن حالت بیعانه به «بدون بیعانه».
- صفحه‌ی جدید «تم و ظاهر» با پیش‌نمایش؛ «درباره ما» با آیکن‌های رسمی و اینستاگرام نوبت‌یار.
<!-- fa:end -->

### mobile_owner
- `BusinessMapper.kt` — `depositMode` در هر چهار تبدیل (DTO↔Entity↔Domain↔Request) جا افتاده بود؛ کش محلی همیشه بدون
  حالت بیعانه برمی‌گشت و ذخیره‌ی بعدی در تنظیمات پیشرفته `NONE` را به سرور می‌فرستاد.
- `feature/promo/` — صفحه‌ی کمپین پیامک تبلیغاتی (API جدید `business/<id>/promo-sms/`).
- `feature/messages/` — بازطراحی پیام خودکار؛ PANEL فقط الگوهای ثابت (`ReminderTemplates.kt`، هم‌خوان با
  `backend/business/reminder_templates.py`) و ذخیره‌ی `notification_minutes_before`/`reminder_template` روی سرور.
- `AdvancedSettingsRoute.kt`/`AdvancedSettingsTabs.kt` — هدر قابلیت‌های پلن، تب‌های جدید، نوار ذخیره با وضعیت
  تغییرات؛ کارت «یادآوری چندکاناله» (پیاده‌سازی‌نشده) حذف شد.
- `ThemeScreen.kt` (جدید)، `AboutUsScreen.kt` (آیکن‌های رسمی بله/ایتا/روبیکا از وب‌اپ خودشان، گلیف رسمی اینستاگرام).
- Room schema 17 (`BusinessEntity.reminderTemplate`).

### backend
- `business/promo_sms.py`، `promo_views.py`، مدل‌های `PromoCampaign`/`PromoCampaignRecipient`، دستور
  `send_promo_campaigns` برای ادامه‌ی ارسال‌های نیمه‌کاره؛ `promotional_sms` از `COMING_SOON_FEATURES` خارج شد.
- `Business.reminder_template` و استفاده از آن در `send_appointment_reminders` (قبلاً متن ثابت و یکسان).

### infra
- `.github/workflows/android-release.yml` — آپلود APK به `noobatyar.ir` از مسیر اشتباه (`public_html/...`) به مسیر درست
  (ریشه‌ی همان اکانت FTP jail‌شده) اصلاح شد؛ ارسال هم از `curl` به `ftplib.FTP_TLS` تغییر کرد چون `curl` روی این
  هاست آپلودهای چند-مگابایتی را بی‌صدا ناقص می‌گذاشت.
- کانال مقصد ارسال در تلگرام از کانال مشتریان به کانال بکاپ (`TELEGRAM_CHAT_ID`) تغییر کرد.

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
