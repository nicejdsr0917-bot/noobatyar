# صنف‌ها، دسته‌ها و بیمه‌ها

> منبع: `backend/business/models` (`Business.category`، `CATEGORY_GROUPS`)، `business/models/guilds.py`، `business/insurance_catalog.py`، `business/categories.py`.

## ۱. صنف‌ها (هشت گروه، ۶۸ دسته)

| کلید صنف | نام | دسته‌ها |
|---|---|---|
| `MEDICAL` | پزشکی و تخصص‌ها | DOCTOR پزشک و کلینیک، GENERAL_PRACTITIONER عمومی، INTERNAL_MEDICINE داخلی، CARDIOLOGY قلب و عروق، DERMATOLOGY پوست و مو (پزشک)، ORTHOPEDICS ارتوپدی، PEDIATRICS اطفال، GYNECOLOGY زنان و زایمان، ENT گوش و حلق و بینی، OPHTHALMOLOGY چشم‌پزشکی، NEUROLOGY مغز و اعصاب، UROLOGY اورولوژی، ENDOCRINOLOGY غدد و دیابت، GASTROENTEROLOGY گوارش و کبد، ONCOLOGY انکولوژی، SURGERY جراحی، PSYCHIATRY روان‌پزشکی، INFERTILITY ناباروری و IVF |
| `DENTAL` | دندان‌پزشکی | DENTIST، ORTHODONTICS ارتودنسی، DENTAL_IMPLANT ایمپلنت و جراحی دهان، PEDIATRIC_DENTISTRY کودکان |
| `HEALTH` | سلامت و درمان | PSYCHOLOGY روان‌شناسی و مشاوره، PHYSIOTHERAPY فیزیوتراپی، NUTRITION تغذیه، LABORATORY آزمایشگاه، RADIOLOGY رادیولوژی و سونوگرافی، OPTOMETRY بینایی‌سنجی و عینک، SPEECH_THERAPY گفتاردرمانی، OCCUPATIONAL_THERAPY کاردرمانی، MIDWIFERY مامایی، NURSING پرستاری در منزل، PHARMACY داروخانه، VETERINARY دامپزشکی |
| `BEAUTY` | آرایش و زیبایی | BEAUTY_SALON، WOMENS_SALON، BARBERSHOP، HAIR_SALON، HAIR_TRANSPLANT، MAKEUP، BRIDAL، NAIL_SALON، EYEBROW_LASH، SKIN_LASER، TATTOO، MASSAGE_SPA، SLIMMING |
| `PROFESSIONAL` | خدمات حرفه‌ای | CONSULTANT مشاوره، LAWYER وکالت، ACCOUNTING حسابداری و مالیات، REAL_ESTATE املاک، INSURANCE بیمه، IMMIGRATION مهاجرت |
| `EDUCATION` | آموزش و ورزش | TUTORING تدریس، LANGUAGE_SCHOOL زبان، MUSIC_SCHOOL موسیقی، GYM باشگاه و مربی، YOGA_PILATES یوگا و پیلاتس، DRIVING_SCHOOL رانندگی |
| `TRADES` | خدمات فنی و تعمیرات | AUTO_SERVICE تعمیرگاه خودرو، CAR_WASH کارواش، HOME_SERVICE تعمیرات منزل، DEVICE_REPAIR تعمیر موبایل و لوازم برقی، TAILORING خیاطی |
| `OTHER` | سایر | PHOTOGRAPHY عکاسی، EVENT_SERVICES تالار و مراسم، PET_GROOMING حیوانات، OTHER سایر |

کاربردها:
- هر صنف یک proxy ادمین جدا دارد (منوی «ثبت مرکز»: پزشکی، دندان‌پزشکی، سلامت، زیبایی، حرفه‌ای، آموزش، فنی، سایر) با فرم بخش‌بندی‌شده و قالب فایل ورود (`?guild=`). `_GUILD_MENU` در `settings.py` دستی است و تست با `GUILDS` مقایسه می‌کند.
- بنرهای اپ کارفرما روی صنف (`BannerCategoryGroup`) یا یک دسته تعریف می‌شوند.
- `ServiceCatalogItem` خدمات پیشنهادی هر دسته را می‌دهد؛ `SERVICE_EXAMPLES` نمونه‌ها.
- دسته در API `subtitle` استوری و برچسب صفحه است.

## ۲. بیمه
`takes_insurance(category)` فقط برای صنف‌های `MEDICAL`، `DENTAL`، `HEALTH` درست است. بیرون از این صنف‌ها بیمه نه نمایش داده می‌شود و نه روی نوبت ثبت.

### فهرست (`seed_insurances`، idempotent)
| نوع | بیمه‌ها (slug) |
|---|---|
| تکمیلی (۱۹) | دانا `dana`، آسیا `asia`، البرز `alborz`، سامان `saman`، ملت `mellat`، ایران `iran`، پاسارگاد `pasargad`، کوثر `kosar`، دی `dey`، آتیه‌سازان حافظ `atiyeh`، کارآفرین `karafarin`، پارسیان `parsian`، سینا `sina`، معلم `moallem`، آسماری `asmari`، تعاون `taavon`، نوین `novin`، رازی `razi`، میهن `mihan` |
| پایه (۳) | تامین اجتماعی `tamin`، سلامت `salamat`، نیروهای مسلح `armed-forces` |

- لوگو: `Insurance.logo` از ادمین یا `seed_insurance_logos [--force]` از `business/insurance_logos/<slug>.png` (۲۵۶ پیکسل). روی prod ۲۰ از ۲۲ لوگو دارند؛ آتیه‌سازان حافظ و نیروهای مسلح ندارند.
- کسب‌وکار بیمه‌های طرف قرارداد را در `Business.insurances` نگه می‌دارد؛ مرکزهای واردشده بیمه را از منبع (مثلا دانا) می‌گیرند.
- مشتری در اپ بیمه تکمیلی خودش را انتخاب می‌کند و کاوش با آن فیلتر می‌شود؛ در رزرو وب و اپ، بیمه تکمیلی‌ای که کسب‌وکار قبول دارد قابل انتخاب است (`Appointment.insurance`). کارفرما لوگو و نام بیمه را روی نوبت می‌بیند (فقط صنف پزشکی در اپ کارفرما).
- منبع لوگوها: SVGهای سایت مقایسه بیمه رندرشده، تامین اجتماعی از Wikimedia، سلامت از فایل کاربر.

## ۳. متن بدون همزه
`directory_import._plain` همزه را یکدست می‌کند (U+0623/U+0625 → ا، U+0624 → و، U+0626 → ی، حذف U+0621 و U+0654، U+06C0 → ه). نام شهرها در `iran_cities.json` و نام بیمه‌ها همزه ندارند. همین قاعده برای همه متن‌های فارسی پروژه است.
