# backend/business/management/commands/seed_demo_businesses.py
"""
Seeds a few hundred realistic businesses for local and staging work: spread
across every category group, across Tehran's neighbourhoods and a handful of
other cities, each with an owner, services, hours, a map pin and, for the
health groups, insurances. Owners get a subscription so booking works.

Deterministic and idempotent: business ``DEMO0001``… are updated in place, so
running it again (or with a larger ``--count``) never duplicates.

    manage.py seed_demo_businesses            # 100 businesses
    manage.py seed_demo_businesses --count 250
"""
import random
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand
from django.utils import timezone

from accounting.models import Plan, Subscription
from business.models import Business, Insurance

User = get_user_model()

FIRST = ['علی', 'محمد', 'رضا', 'حسین', 'امیر', 'مهدی', 'سعید', 'کاوه', 'بهرام', 'پویا', 'آرش', 'نیما',
         'سارا', 'مریم', 'فاطمه', 'زهرا', 'نگار', 'الهام', 'مینا', 'شیما', 'نرگس', 'ندا', 'پریسا', 'ساناز']
LAST = ['احمدی', 'محمدی', 'رضایی', 'حسینی', 'کریمی', 'موسوی', 'صادقی', 'جعفری', 'نوری', 'سلطانی',
        'فرهادی', 'کاظمی', 'مرادی', 'رستمی', 'اکبری', 'قاسمی', 'یزدانی', 'عباسی', 'شریفی', 'طاهری']
BRANDS = ['رز', 'لیدی', 'آرتمیس', 'نیلوفر', 'یاس', 'سپید', 'ماهان', 'آراد', 'پارس', 'سیمرغ', 'آوین',
          'مهتاب', 'بهار', 'ستاره', 'نگین', 'رویا', 'آرمان', 'پرنیان', 'سروش', 'هیراد', 'اطلس', 'افق']

# (name, lat, lng) neighbourhood centres in Tehran
TEHRAN = [
    ('ونک', 35.7580, 51.4105), ('سعادت‌آباد', 35.7870, 51.3700), ('جردن', 35.7720, 51.4150),
    ('الهیه', 35.7950, 51.4280), ('پاسداران', 35.7700, 51.4500), ('نیاوران', 35.8100, 51.4650),
    ('یوسف‌آباد', 35.7300, 51.4000), ('شهرک غرب', 35.7600, 51.3600), ('میرداماد', 35.7640, 51.4300),
    ('ولیعصر', 35.7100, 51.4130), ('انقلاب', 35.7010, 51.3920), ('شریعتی', 35.7400, 51.4400),
    ('تجریش', 35.8040, 51.4300), ('پونک', 35.7600, 51.3300), ('تهرانپارس', 35.7350, 51.5000),
    ('نارمک', 35.7400, 51.5200), ('شهران', 35.7500, 51.2800), ('صادقیه', 35.7150, 51.3300),
    ('امیرآباد', 35.7300, 51.3900), ('خیابان آزادی', 35.7020, 51.3600),
]
# (name, province, city id in business/data/iran_cities.json, lat, lng)
OTHER_CITIES = [
    ('اصفهان', 'اصفهان', 4, 32.6546, 51.6680), ('شیراز', 'فارس', 6, 29.5918, 52.5837),
    ('مشهد', 'خراسان رضوی', 3, 36.2970, 59.6062), ('تبریز', 'آذربایجان شرقی', 5, 38.0800, 46.2919),
    ('کرج', 'البرز', 2, 35.8327, 50.9915), ('رشت', 'گیلان', 12, 37.2808, 49.5832),
]

# Group → weight in the mix, and the category templates inside it:
# key: (title kind, services, bio, minutes per visit, takes insurance)
PERSON, BRAND = 'person', 'brand'
CATS = {
    'MEDICAL': (20, {
        'GENERAL_PRACTITIONER': (PERSON, 'پزشک عمومی', ['ویزیت عمومی', 'تزریقات', 'پانسمان', 'مشاوره'], 20),
        'INTERNAL_MEDICINE': (PERSON, 'متخصص داخلی', ['ویزیت داخلی', 'بررسی آزمایش', 'چکاپ'], 20),
        'CARDIOLOGY': (PERSON, 'متخصص قلب و عروق', ['ویزیت قلب', 'نوار قلب', 'اکو'], 30),
        'DERMATOLOGY': (PERSON, 'متخصص پوست و مو', ['ویزیت پوست', 'درمان آکنه', 'مزوتراپی'], 20),
        'PEDIATRICS': (PERSON, 'متخصص کودکان', ['ویزیت کودک', 'واکسیناسیون', 'مشاوره رشد'], 20),
        'GYNECOLOGY': (PERSON, 'متخصص زنان', ['ویزیت زنان', 'سونوگرافی', 'مشاوره بارداری'], 30),
        'ORTHOPEDICS': (PERSON, 'متخصص ارتوپدی', ['ویزیت ارتوپدی', 'گچ‌گیری', 'تزریق مفصل'], 20),
        'ENT': (PERSON, 'متخصص گوش، حلق و بینی', ['ویزیت', 'شستشوی گوش', 'آندوسکوپی بینی'], 20),
        'OPHTHALMOLOGY': (PERSON, 'متخصص چشم', ['معاینه چشم', 'فشار چشم', 'تجویز عینک'], 20),
        'PSYCHIATRY': (PERSON, 'روان‌پزشک', ['ویزیت اول', 'پیگیری دارو', 'مشاوره'], 45),
    }),
    'DENTAL': (10, {
        'DENTIST': (PERSON, 'دندان‌پزشک', ['معاینه', 'جرم‌گیری', 'ترمیم', 'عصب‌کشی'], 30),
        'ORTHODONTICS': (PERSON, 'متخصص ارتودنسی', ['مشاوره ارتودنسی', 'ارتودنسی ثابت', 'ارتودنسی شفاف'], 30),
        'DENTAL_IMPLANT': (PERSON, 'جراح و ایمپلنت', ['ایمپلنت', 'جراحی دهان', 'کشیدن دندان عقل'], 45),
        'PEDIATRIC_DENTISTRY': (PERSON, 'دندان‌پزشک کودکان', ['معاینه کودک', 'فلوراید', 'پرکردگی'], 30),
    }),
    'HEALTH': (15, {
        'PSYCHOLOGY': (PERSON, 'روان‌شناس', ['جلسه مشاوره', 'زوج‌درمانی', 'مشاوره کودک'], 60),
        'PHYSIOTHERAPY': (BRAND, 'کلینیک فیزیوتراپی', ['فیزیوتراپی', 'لیزر درمانی', 'ماساژ درمانی'], 45),
        'NUTRITION': (PERSON, 'متخصص تغذیه', ['مشاوره رژیم', 'آنالیز بدن', 'پیگیری'], 30),
        'LABORATORY': (BRAND, 'آزمایشگاه', ['آزمایش خون', 'آزمایش ادرار', 'نمونه‌گیری در منزل'], 15),
        'RADIOLOGY': (BRAND, 'مرکز رادیولوژی و سونوگرافی', ['سونوگرافی', 'رادیولوژی', 'ماموگرافی'], 20),
        'OPTOMETRY': (BRAND, 'مرکز بینایی‌سنجی', ['بینایی‌سنجی', 'عینک طبی', 'لنز'], 20),
        'SPEECH_THERAPY': (PERSON, 'گفتاردرمانگر', ['ارزیابی گفتار', 'جلسه درمان'], 45),
    }),
    'BEAUTY': (20, {
        'WOMENS_SALON': (BRAND, 'سالن زیبایی', ['کوتاهی مو', 'رنگ و مش', 'کراتین', 'مانیکور'], 60),
        'BARBERSHOP': (BRAND, 'آرایشگاه مردانه', ['اصلاح سر', 'اصلاح صورت', 'رنگ مو', 'پاکسازی'], 30),
        'HAIR_SALON': (BRAND, 'سالن مو', ['رنگ مو', 'کراتین', 'بوتاکس مو', 'کوتاهی'], 90),
        'NAIL_SALON': (BRAND, 'سالن ناخن', ['مانیکور', 'پدیکور', 'کاشت ناخن', 'طراحی ناخن'], 60),
        'EYEBROW_LASH': (BRAND, 'استودیو ابرو و مژه', ['میکروبلیدینگ', 'لیفت مژه', 'اصلاح ابرو'], 60),
        'SKIN_LASER': (BRAND, 'کلینیک پوست و لیزر', ['لیزر موهای زائد', 'پاکسازی پوست', 'مزوتراپی'], 45),
        'MASSAGE_SPA': (BRAND, 'اسپا', ['ماساژ سوئدی', 'ماساژ ریلکسی', 'سونا'], 60),
        'MAKEUP': (BRAND, 'استودیو میکاپ', ['میکاپ مجلسی', 'گریم عروس', 'شینیون'], 90),
    }),
    'PROFESSIONAL': (10, {
        'LAWYER': (PERSON, 'وکیل پایه یک', ['مشاوره حقوقی', 'تنظیم قرارداد', 'وکالت'], 45),
        'ACCOUNTING': (BRAND, 'دفتر حسابداری', ['اظهارنامه مالیاتی', 'دفترداری', 'مشاوره مالی'], 45),
        'REAL_ESTATE': (BRAND, 'مشاور املاک', ['مشاوره خرید', 'مشاوره اجاره', 'ارزیابی ملک'], 30),
        'CONSULTANT': (PERSON, 'مشاور کسب‌وکار', ['جلسه مشاوره', 'برنامه‌ریزی', 'بررسی طرح'], 60),
        'IMMIGRATION': (BRAND, 'مشاور مهاجرت', ['مشاوره اولیه', 'بررسی پرونده', 'تکمیل مدارک'], 45),
    }),
    'EDUCATION': (10, {
        'LANGUAGE_SCHOOL': (BRAND, 'آموزشگاه زبان', ['تعیین سطح', 'کلاس مکالمه', 'آمادگی آیلتس'], 30),
        'GYM': (BRAND, 'باشگاه ورزشی', ['جلسه آشنایی', 'مربی شخصی', 'بدنسازی'], 60),
        'YOGA_PILATES': (BRAND, 'استودیو یوگا و پیلاتس', ['کلاس یوگا', 'پیلاتس', 'جلسه خصوصی'], 60),
        'MUSIC_SCHOOL': (BRAND, 'آموزشگاه موسیقی', ['جلسه آشنایی', 'کلاس پیانو', 'کلاس گیتار'], 45),
        'DRIVING_SCHOOL': (BRAND, 'آموزشگاه رانندگی', ['ثبت‌نام', 'جلسه عملی', 'آمادگی آزمون'], 60),
    }),
    'TRADES': (8, {
        'AUTO_SERVICE': (BRAND, 'تعمیرگاه خودرو', ['تعویض روغن', 'بررسی فنی', 'تنظیم موتور'], 60),
        'CAR_WASH': (BRAND, 'کارواش', ['شست‌وشوی کامل', 'واکس', 'دیتیلینگ'], 45),
        'DEVICE_REPAIR': (BRAND, 'تعمیرات موبایل', ['تعویض صفحه', 'تعویض باتری', 'عیب‌یابی'], 30),
        'TAILORING': (BRAND, 'خیاطی', ['دوخت لباس', 'اصلاح لباس', 'طراحی'], 30),
    }),
    'OTHER': (7, {
        'PHOTOGRAPHY': (BRAND, 'آتلیه عکاسی', ['عکس پرسنلی', 'عکس خانوادگی', 'عکس نوزاد'], 45),
        'PET_GROOMING': (BRAND, 'مرکز نگهداری حیوانات', ['شست‌وشو', 'اصلاح موی حیوان', 'ویزیت'], 45),
        'EVENT_SERVICES': (BRAND, 'خدمات مراسم', ['بازدید تالار', 'مشاوره مراسم', 'رزرو تاریخ'], 30),
    }),
}
INSURED_GROUPS = {'MEDICAL', 'DENTAL', 'HEALTH'}
CLOSED_FRIDAY = {'MEDICAL', 'DENTAL', 'HEALTH', 'PROFESSIONAL'}
COLORS = ['#2563EB', '#7C3AED', '#059669', '#DB2777', '#EA580C', '#0891B2', '#4F46E5', '#B45309']


class Command(BaseCommand):
    help = 'Seeds realistic demo businesses across every category group (idempotent).'

    def add_arguments(self, parser):
        parser.add_argument('--count', type=int, default=100)

    def handle(self, *args, count, **options):
        rng = random.Random(20261031)
        insurances = list(Insurance.objects.filter(is_active=True))
        plan = Plan.objects.filter(is_active=True).order_by('-price').first() or Plan.objects.create(
            name='نمایشی', price=0, duration_value=12, duration_unit='month',
        )

        # One slot per business, groups interleaved in proportion to their weight.
        slots = []
        for group, (weight, cats) in CATS.items():
            keys = list(cats)
            slots += [(group, keys[i % len(keys)]) for i in range(round(count * weight / 100) + 1)]
        rng.shuffle(slots)
        slots = slots[:count]
        while len(slots) < count:
            group = rng.choice(list(CATS))
            slots.append((group, rng.choice(list(CATS[group][1]))))

        created = updated = 0
        for i, (group, key) in enumerate(slots, start=1):
            kind, noun, services, minutes = CATS[group][1][key]
            person = f'{rng.choice(FIRST)} {rng.choice(LAST)}'
            if kind == PERSON:
                title = f'{noun} {person}'
                owner_name = person
            else:
                title = f'{noun} {rng.choice(BRANDS)}'
                owner_name = person

            if rng.random() < 0.82:
                hood, lat, lng = rng.choice(TEHRAN)
                province, city, city_code = 'تهران', 'تهران', 1
            else:
                city, province, city_code, lat, lng = rng.choice(OTHER_CITIES)
                hood = rng.choice(['مرکز شهر', 'بلوار اصلی', 'میدان اصلی'])
            lat += rng.uniform(-0.006, 0.006)
            lng += rng.uniform(-0.006, 0.006)

            deposit = rng.choice(['NONE', 'NONE', 'OPTIONAL', 'MANDATORY'])
            owner, _ = User.objects.get_or_create(
                phone=f'0913{i:07d}',
                defaults={'name': owner_name, 'role': 'BUSINESS_OWNER', 'is_active': True},
            )
            fields = {
                'user': owner,
                'title': title,
                'category': key,
                'phone': f'021{rng.randint(20000000, 88999999)}' if city == 'تهران' else f'0{rng.randint(31, 41)}{rng.randint(30000000, 39999999)}',
                'address': f'{city}، {hood}، خیابان {rng.choice(BRANDS)}، پلاک {rng.randint(1, 120)}',
                'province': province, 'city': city, 'city_code': city_code, 'neighborhood': hood,
                'latitude': round(lat, 6), 'longitude': round(lng, 6),
                'bio': f'{noun} با سابقه و رضایت بالای مراجعان؛ نوبت را آنلاین بگیرید.',
                'theme_color': rng.choice(COLORS),
                'is_claimed': True,
                'moderation_status': Business.MODERATION_APPROVED,
                'first_approved_at': timezone.now(),
                'is_locked': False,
                'booking_enabled': True,
                'allow_anonymous_view': rng.random() < 0.85,
                'default_service_duration': minutes,
                'work_start_hour': rng.choice([8, 9, 10]),
                'work_end_hour': rng.choice([17, 19, 20, 21]),
                'closed_weekdays': [4] if group in CLOSED_FRIDAY else [],
                'services': services,
                'deposit_mode': deposit,
                'deposit_amount': rng.choice([100000, 200000, 300000]) if deposit != 'NONE' else 0,
            }
            business, was_created = Business.objects.update_or_create(unique_code=f'DEMO{i:04d}', defaults=fields)
            if group in INSURED_GROUPS and insurances:
                business.insurances.set(rng.sample(insurances, k=min(len(insurances), rng.randint(2, 5))))
            if not Subscription.objects.filter(user=owner, ends_at__gt=timezone.now()).exists():
                Subscription.objects.create(user=owner, plan=plan, ends_at=timezone.now() + timedelta(days=365))
            created += was_created
            updated += not was_created

        self.stdout.write(self.style.SUCCESS(f'{created} created, {updated} updated ({count} total).'))
