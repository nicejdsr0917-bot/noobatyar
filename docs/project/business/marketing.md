# بازاریابی و رشد

> منبع: `backend/marketing/`، `core` (`AudienceSegment`، `MarketingPushLog`، `ExpirySmsLog`، `AdminMessageLog`)، `business/promo_sms.py`، `stories/`، `banners/`، `business/site_views.py`، `business/models/centers.py`، `docs/BRAND_GUIDE_FOR_AI.md`، `brand/`.

## ۱. دو بازار
- **کارفرما** (پول‌دهنده): هدف جذب و فعال کردن کسب‌وکارها و تمدید اشتراک.
- **مشتری** (اثر شبکه‌ای): هرچه مشتری بیشتر، صفحه کسب‌وکار باارزش‌تر.
- اولویت فعلی: فعال شدن کسب‌وکارهای ثبت‌شده، قبل از جذب سرمایه.

## ۲. ابزارهای جذب کارفرما (ادمین)
| ابزار | کد | کار |
|---|---|---|
| لید فروش | `marketing.MarketingLead` (NOT_CALLED، CALLED، INTERESTED، JOINED، NOT_INTERESTED)، `LeadNote`، فیلتر «تماس‌های امروز»، `next_follow_up`، `assigned_to` | پیگیری تلفنی کسب‌وکارها |
| مراکز و پیگیری تماس | `CenterOutreach`، CSV تماس، `online_booking_requests_count` | تبدیل مرکز واردشده به کسب‌وکار ([centers](centers-and-n8n.md)) |
| بخش‌بندی مخاطب | `AudienceSegment` (visitor یا owner، تعریف JSON)، خروجی `AudienceSegmentExport` | هدف‌گیری کمپین |
| کمپین پوش | `MarketingPushLog` (پوش `MARKETING` با تصویر، لینک، deep link و آمار تحویل) | از job `core.push_campaign_chunk` |
| پیامک انقضا | `ExpirySmsLog` (manual، auto، test) و `SubscriptionReminderSettings` | تمدید اشتراک |
| پیام مستقیم | `AdminMessageLog` (PUSH/SMS به یک کسب‌وکار) | پشتیبانی و فروش |
| بنر اپ کارفرما | `banners.Banner` (۱۲۰۰×۴۰۰، صنف یا دسته، زمان‌بندی)، `SiteSetting.banner_link_url` | معرفی قابلیت و پلن |
| پاداش مشتری تازه | `NewClientReward` | انگیزه دادن تخفیف اولین مراجعه |

## ۳. ابزارهای رشد که کارفرما خودش دارد
- **پیامک تبلیغاتی** به همه، اخیر، غیرفعال یا فالوورها (`marketing_opt_out=False` و بایگانی‌نشده)؛ پیش‌نمایش هزینه؛ بررسی متن با کلمه‌های ممنوع؛ هر بخش پیامک از سهمیه یا کیف پول.
- **استوری ۲۴ ساعته** با دکمه تخفیف درصدی یا مبلغی؛ اعلان روزانه به فالوورها (`stories.notify_followers`، `NEW_STORY`).
- **تخفیف اولین مراجعه** (۰ تا ۵۰٪) که در لیست و رزرو اپ مشتری نشان داده می‌شود.
- **لینک صفحه و اشتراک** (`ShareBusinessScreen`)، رنگ برند خودکار از لوگو، صفحه SSR قابل جستجو، PWA با آیکن کسب‌وکار.
- **نمونه‌کار، مدرک، نظرها و امتیاز** و درخواست خودکار نظر بعد از هر نوبت انجام‌شده.

## ۴. ابزارهای جذب و نگه‌داشت مشتری
- استوری نوبت‌یار (بدون کسب‌وکار، دکمه لینک) در بالای فید.
- نوار تبلیغ (`ClientPromo`) در کاوش و نقشه.
- فید استوری: نوبت‌یار، فالوشده‌ها، همان شهر.
- اعلان فالوور تازه به کارفرما و درخواست نظر به مشتری.
- رتبه‌بندی لیست عمومی (`compute_rank_scores`).

## ۵. سایت وردپرس
`api/public/site/active-customers/` و `newest-businesses/` (عمومی، throttle جدا) لیست کسب‌وکارهای فعال و تازه را برای صفحه‌های سایت می‌دهند. صفحه دانلود اپ‌ها روی همین سایت است و لینک مایکت هر دو اپ را دارد.

## ۶. برند و محتوا
- راهنمای بصری برای تولید تصویر: `docs/BRAND_GUIDE_FOR_AI.md`؛ فایل‌های برند در `brand/` (آیکن مشکی کارفرما با `apply-to-apps.js`، `brand/png/noobatyar-owner-store-512.png`).
- ویدیوی معرفی اپ مشتری تمام (v3)، اپ کارفرما در حال ساخت (`marketing/` ریشه، بیرون از git).
- قاعده محتوا: در اینستاگرام قیمت گفته نمی‌شود؛ متن فارسی بدون همزه.
- ایده‌ها و بازار ایران: `IDEAS_IRAN_MARKET.md`.

## ۷. کارهای بعدی
تخفیف مخصوص اپ، دعوت دوست، ویدیوی کارفرما، بیمه‌های بعدی در فهرست مراکز.
