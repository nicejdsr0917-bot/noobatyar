# نوبت‌یار — اپ مشتری (mobile_client)

اپ عمومی برای پیدا کردن پزشک، دندان‌پزشک، سالن زیبایی، مشاور و… نزدیک کاربر.
بدون ورود کار می‌کند؛ رزرو نوبتِ مراکزی که در نوبت‌یار ثبت شده‌اند به صفحه وب
رزرو (`front_client`) سپرده می‌شود.

## صفحه‌ها

| تب / صفحه | پوشه | چه می‌کند |
|---|---|---|
| کاوش | `feature/explore` | جستجو، دسته‌بندی، فیلتر بیمه، «نزدیک من» با GPS، بخش «منتخب نوبت‌یار»، صفحه‌بندی |
| جزییات مرکز | `feature/businessDetail` | تماس، مسیریابی، رزرو آنلاین یا «درخواست نوبت آنلاین»، گزارش اطلاعات نادرست |
| نشان‌شده‌ها | `feature/saved` | مراکز نشان‌شده (فقط روی گوشی) |
| بیشتر | `feature/more` | تم، معرفی به دوستان، حریم خصوصی، دعوت صاحبان کسب‌وکار |

اجزای مشترک ظاهری (کارت‌ها، رنگ برند، دسته‌ها) در `feature/discovery/DiscoveryUi.kt` است.

## موقعیت مکانی

`core/location/LocationRequester` یک‌بار موقعیت می‌گیرد:
- اندروید با `LocationManager` (نه Play Services؛ روی گوشی‌های بدون GMS هم کار می‌کند).
- iOS با CoreLocation؛ متن مجوز در `iosApp/iosApp/Info.plist` (`NSLocationWhenInUseUsageDescription`).

اگر کاربر اجازه ندهد، شهر را دستی انتخاب می‌کند.

## اجرا

```bash
./gradlew :composeApp:assembleLocalSiteDebug   # به بک‌اند لوکال (127.0.0.1:8000 با adb reverse)
./gradlew :composeApp:assembleProdSiteRelease :composeApp:assembleProdStoreRelease  # نسخه انتشار (فقط HTTPS)
```

برای فلیور `local`: `adb reverse tcp:8000 tcp:8000`. فلیور `prod` ترافیک HTTP ساده را
اجازه نمی‌دهد (`res/xml/network_security_config.xml`).

داده نمونه: `python manage.py seed_directory_demo`؛ داده واقعی:
`python manage.py import_directory --file data.jsonl --source <نام-منبع>`.
