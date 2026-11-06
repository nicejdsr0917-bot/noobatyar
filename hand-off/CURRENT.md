---
updated: 2026-11-06 12:20
tags: [gf, current]
---
# وضعیت فعلی

## هدف
اجتماعی‌تر کردن نوبت‌یار و جذب مشتری تازه برای کسب‌وکارها. مرحله بعد طبق تصمیم 11-05: **تخفیف مخصوص اپ** و **دعوت دوست**، با هزینه اونر. قاعده همیشگی: کاربر واقعی داریم، پس فقط تغییر افزایشی. با کاربر فارسی حرف بزن.

## وضعیت
- **prod** (`194.146.69.137`) روی `develop` است، که شامل این‌هاست:
  - بیمه تکمیلی مراجع برای کسب‌وکارهای پزشکی، کد پیگیری پرداخت روی کارت صف و کارت‌های کوتاه‌تر.
  - فلش‌های RTL و حذف همه همزه‌ها.
  - پنل وب اونر هم دیپلوی شد.
- **APK اپ‌ها هنوز منتشر نشده:** تغییرات اپ اونر فقط با تگ در CI منتشر می‌شوند.
- **باگ A3 رفع شده ولی merge نشده:** برنچ `refs/heads/a3-tehran-day-boundary` (`ae748ce`). تقویم اونر روز قبل را می‌گیرد و خانه فقط ۲۰ نوبت اول را می‌خواند.
- **در `mobile_client`** هنوز انتخاب بیمه وجود ندارد. فقط وب مشتری بیمه را می‌فرستد.

## قدم‌های بعدی
1. تگ نسخه بعدی برای APK اونر و مشتری (با تایید کاربر، فقط از CI).
2. review و merge برنچ A3، تست، دیپلوی.
3. اضافه کردن چیپ بیمه تکمیلی به `BookingScreen` در `mobile_client` (فیلد `insurance` = slug، فقط وقتی `takes_insurance`).
4. طراحی «تخفیف مخصوص اپ» و «دعوت دوست».

## مسایل باز
- 🔒 رمز root سرور در تاریخچه git است.
- **بعد از rebuild وب روی prod حتماً `nginx -s reload` بزن** (502).
- **همزه ممنوع است.** هیچ متنی با همزه (U+0621 و U+0623 تا U+0626؛ «آ» مجاز است، U+0654، U+06C0) ننویس (تایید، جزییات، سوال).
- **جهت فلش «رفتن» در فارسی «<» است.** از `ForwardChevron` استفاده کن و Left/Right را بر اساس `isRtl` روی آیکن auto-mirrored انتخاب نکن.
- `favicon.ico` در ادمین api خطای ۵۰۰ می‌دهد. کش ۳۰ ثانیه‌ای اسلات هم آزاد شدن نوبت را دیر نشان می‌دهد.
- **این‌ها commit نشوند:**
  - `.claude/launch.json`، `mobile_owner/composeApp/build.gradle.kts`، lockهای `mobile_client/.gradle/`
  - `marketing/` و فایل‌های untracked کاربر
  - فایل‌ها را تک‌تک add کن.
- **محیط macOS:**
  - adb در `~/Library/Android/sdk/platform-tools/adb` است.
  - Turbopack با `node_modules` symlink کار نمی‌کند (از `cp -Rc` استفاده کن).

## فایل‌های کلیدی
- `backend/appointment/services/client_booking.py` (`_supplementary_insurance_id`)، `business/models/guilds.py` (`takes_insurance`)
- `mobile_owner/.../core/ui/components/AppointmentInfoLines.kt`، `ForwardChevron.kt`، `QueueItemCard.kt`
- `front_client/app/b/[slug]/book/page.tsx`: چیپ بیمه
- `backend/common/timeutils.py`: مرز روز (A3)

## سشن‌های اخیر
- [[sessions/2026-11-06-1211]]: بیمه تکمیلی و کد پیگیری، فلش RTL، حذف همزه، ui-ux-pro-max، دیپلوی
- [[sessions/2026-11-06-0112]]: استوری، ماسک شماره، قاب نمونه‌کار، خانه اونر، کشف A3
- [[sessions/2026-11-05-2242]]: تم دارک و روشن، لوگو و جستجوی بیمه، دیپلوی
- [[sessions/2026-11-05-1626]]: استوری، اینترو، تخفیف اولین مراجعه، فالوورها
- [[sessions/2026-11-05-1135]]: ردیف استوری در کاوش

ببین: [نقشه پروژه](../knowledge/INDEX.md)
