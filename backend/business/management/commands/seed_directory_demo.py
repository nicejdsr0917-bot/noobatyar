# backend/business/management/commands/seed_directory_demo.py
"""
Seeds Iranian Supplementary & Basic Insurances and ~15 realistic businesses
across Tehran (claimed and unclaimed) for marketplace discovery, lead-gen,
and map search demonstrations.

Idempotent: safe to run multiple times.
"""

from django.core.management.base import BaseCommand
from django.contrib.auth import get_user_model
from django.utils import timezone

from business.insurance_catalog import INSURANCES
from business.models import Business, Insurance

User = get_user_model()


BUSINESSES = [
    {
        'title': 'کلینیک دندان‌پزشکی دکتر احمدی',
        'is_claimed': True,
        'owner_phone': '09120000021',
        'owner_name': 'دکتر امیر احمدی',
        'category': 'DENTIST',
        'unique_code': 'AHMADIDT',
        'phone': '02188771122',
        'address': 'تهران، میدان ونک، خیابان خدامی، پلاک ۲۵، ساختمان پزشکان ونک',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'ونک',
        'latitude': 35.7580,
        'longitude': 51.4105,
        'bio': 'مرکز تخصصی دندان‌پزشکی ترمیمی، زیبایی و ایمپلنت با پیشرفته‌ترین تجهیزات',
        'theme_color': '#2563EB',
        'default_service_duration': 30,
        'work_start_hour': 9,
        'work_end_hour': 20,
        'services': ['معاینه و طرح درمان', 'جرم‌گیری', 'کامپوزیت', 'عصب‌کشی', 'ایمپلنت'],
        'insurances': ['dana', 'saman', 'iran', 'tamin'],
    },
    {
        'title': 'مرکز تخصصی پوست و مو دکتر سهرابی',
        'is_claimed': True,
        'owner_phone': '09120000022',
        'owner_name': 'دکتر بهاره سهرابی',
        'category': 'DERMATOLOGY',
        'unique_code': 'SOHRABID',
        'phone': '02188665544',
        'address': 'تهران، ونک، خیابان ملاصدرا، پلاک ۸۰، طبقه ۴',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'ونک',
        'latitude': 35.7555,
        'longitude': 51.4120,
        'bio': 'خدمات جوانسازی، لیزر درمانی و رفع تخصصی بیماری‌های پوستی',
        'theme_color': '#7C3AED',
        'default_service_duration': 30,
        'work_start_hour': 10,
        'work_end_hour': 19,
        'services': ['تزریق ژل و بوتاکس', 'لیزر موهای زائد', 'مزوتراپی', 'پاکسازی پوست'],
        'insurances': ['asia', 'alborz', 'iran', 'salamat'],
    },
    {
        'title': 'سالن زیبایی آوا',
        'is_claimed': True,
        'owner_phone': '09120000023',
        'owner_name': 'آوا حسینی',
        'category': 'WOMENS_SALON',
        'unique_code': 'AVASALON',
        'phone': '02122003344',
        'address': 'تهران، سعادت‌آباد، میدان کاج، خیابان سرو شرقی، پلاک ۳۲',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'سعادت‌آباد',
        'latitude': 35.7810,
        'longitude': 51.3715,
        'bio': 'کلیه خدمات زیبایی، رنگ و مش تخصصی، گریم و مراقبت ناخن',
        'theme_color': '#EC4899',
        'default_service_duration': 60,
        'work_start_hour': 10,
        'work_end_hour': 20,
        'services': ['رنگ و مش', 'کوپ تخصصی', 'میکاپ و شینیون', 'کاشت ناخن', 'کراتین'],
        'insurances': [],
    },
    {
        'title': 'کلینیک قلب و عروق دکتر رادمنش',
        'is_claimed': True,
        'owner_phone': '09120000024',
        'owner_name': 'دکتر کوروش رادمنش',
        'category': 'CARDIOLOGY',
        'unique_code': 'RADMANSH',
        'phone': '02188204050',
        'address': 'تهران، خیابان ولیعصر، بالاتر از توانیر، کوچه مروارید، پلاک ۵',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'ولیعصر',
        'latitude': 35.7320,
        'longitude': 51.4090,
        'bio': 'تشخیص و درمان بیماری‌های قلبی، فشار خون، آنژیوگرافی و اکوکاردیوگرافی پیشرفته',
        'theme_color': '#DC2626',
        'default_service_duration': 30,
        'work_start_hour': 14,
        'work_end_hour': 21,
        'services': ['نوار قلب', 'اکوکاردیوگرافی', 'تست ورزش', 'هولتر ریتم و فشار'],
        'insurances': ['dana', 'alborz', 'mellat', 'tamin', 'armed-forces'],
    },
    {
        'title': 'کلینیک فیزیوتراپی توان‌بخش پاسداران',
        'is_claimed': True,
        'owner_phone': '09120000025',
        'owner_name': 'دکتر مسعود توان‌بخش',
        'category': 'PHYSIOTHERAPY',
        'unique_code': 'PASDPT01',
        'phone': '02122558899',
        'address': 'تهران، خیابان پاسداران، نبش بوستان چهارم، پلاک ۱۱۰',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'پاسداران',
        'latitude': 35.7680,
        'longitude': 51.4670,
        'bio': 'فیزیوتراپی و توانبخشی اسکلتی عضلانی با لیزر پرتوان و تکنولوژی تکار',
        'theme_color': '#0D9488',
        'default_service_duration': 45,
        'work_start_hour': 9,
        'work_end_hour': 20,
        'services': ['فیزیوتراپی ستون فقرات', 'لیزر پرتوان', 'مگنت‌تراپی', 'الکتروتراپی'],
        'insurances': ['dana', 'asia', 'saman', 'parsian', 'tamin', 'salamat'],
    },
    {
        'title': 'سالن زیبایی آقایان آرسس',
        'is_claimed': True,
        'owner_phone': '09120000026',
        'owner_name': 'سامان فرهمند',
        'category': 'BARBERSHOP',
        'unique_code': 'ARSESBAR',
        'phone': '02144005511',
        'address': 'تهران، فلکه دوم صادقیه، ابتدای بلوار فردوس شرق، پلاک ۴',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'صادقیه',
        'latitude': 35.7220,
        'longitude': 51.3360,
        'bio': 'آرایشگاه مدرن مردانه با محیطی آرام، خدمات تخصصی گریم داماد و مراقبت پوست',
        'theme_color': '#374151',
        'default_service_duration': 40,
        'work_start_hour': 10,
        'work_end_hour': 22,
        'services': ['اصلاح کلاسیک', 'پاکسازی پوست آقایان', 'کراتینه مو', 'طراحی ریش'],
        'insurances': [],
    },
    {
        'title': 'کلینیک دندان‌پزشکی صبا صادقیه',
        'is_claimed': True,
        'owner_phone': '09120000027',
        'owner_name': 'دکتر صبا حسینی',
        'category': 'DENTIST',
        'unique_code': 'SABADENT',
        'phone': '02144229988',
        'address': 'تهران، فلکه اول صادقیه، گلناز دهم، ساختمان صبا، واحد ۳',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'صادقیه',
        'latitude': 35.7190,
        'longitude': 51.3340,
        'bio': 'خدمات عمومی و تخصصی دندان‌پزشکی خانواده با طرف قرارداد بیمه‌های معتبر',
        'theme_color': '#0284C7',
        'default_service_duration': 30,
        'work_start_hour': 10,
        'work_end_hour': 20,
        'services': ['ترمیم و جرم‌گیری', 'دندان‌پزشکی اطفال', 'کامپوزیت ونیر', 'پروتز متحرک'],
        'insurances': ['dey', 'kosar', 'atiyeh', 'tamin'],
    },
    {
        'title': 'مطب روان‌شناسی و مشاوره دکتر ستایش',
        'is_claimed': True,
        'owner_phone': '09120000028',
        'owner_name': 'دکتر نیلوفر ستایش',
        'category': 'PSYCHOLOGY',
        'unique_code': 'SETAYESH',
        'phone': '02122334455',
        'address': 'تهران، سعادت‌آباد، خیابان علامه طباطبایی شمالی، پلاک ۱۸',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'سعادت‌آباد',
        'latitude': 35.7760,
        'longitude': 51.3680,
        'bio': 'مشاوره تخصصی فردی، زوج درمانی و روان‌درمانی تحلیلی',
        'theme_color': '#10B981',
        'default_service_duration': 50,
        'work_start_hour': 11,
        'work_end_hour': 20,
        'services': ['مشاوره فردی', 'زوج درمانی', 'روان‌درمانی اضطراب', 'مشاوره خانواده'],
        'insurances': ['dana', 'saman', 'iran'],
    },
    # Unclaimed Directory Listings
    {
        'title': 'مرکز تصویربرداری و رادیولوژی کاخ ولیعصر',
        'is_claimed': False,
        'category': 'RADIOLOGY',
        'unique_code': 'KRAD0001',
        'phone': '02188902030',
        'address': 'تهران، خیابان ولیعصر، بالاتر از زرتشت، پلاک ۶۵۰',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'ولیعصر',
        'latitude': 35.7285,
        'longitude': 51.4075,
        'bio': 'تصویربرداری پیشرفته ام آر آی، سی‌تی اسکن و سونوگرافی کالر داپلر',
        'external_source': 'tehran_medical_council',
        'default_service_duration': 20,
        'work_start_hour': 8,
        'work_end_hour': 22,
        'services': ['MRI', 'سی‌تی اسکن', 'سونوگرافی کالر داپلر', 'ماموگرافی'],
        'insurances': ['dana', 'asia', 'alborz', 'saman', 'iran', 'parsian', 'tamin', 'salamat', 'armed-forces'],
    },
    {
        'title': 'مطب چشم‌پزشکی دکتر بهرامی',
        'is_claimed': False,
        'category': 'OPHTHALMOLOGY',
        'unique_code': 'BAHRAMIO',
        'phone': '02188785522',
        'address': 'تهران، ونک، خیابان نگار، پلاک ۳، ساختمان پزشکان نگار',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'ونک',
        'latitude': 35.7590,
        'longitude': 51.4110,
        'bio': 'جراحی عیوب انکساری، آب مروارید و خدمات بینایی‌سنجی تخصصی',
        'external_source': 'tehran_medical_council',
        'default_service_duration': 20,
        'work_start_hour': 15,
        'work_end_hour': 20,
        'services': ['معاینه بینایی', 'لیزیک و لازک', 'درمان آب مروارید', 'تعیین نمره عینک'],
        'insurances': ['iran', 'asia', 'dana', 'tamin', 'salamat'],
    },
    {
        'title': 'کلینیک ارتوپدی و مفاصل دکتر فرهودی',
        'is_claimed': False,
        'category': 'ORTHOPEDICS',
        'unique_code': 'FARHOUD1',
        'phone': '02122884411',
        'address': 'تهران، پاسداران، خیابان دولت، بین اختیاریه و دیباجی، پلاک ۹۲',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'پاسداران',
        'latitude': 35.7630,
        'longitude': 51.4630,
        'bio': 'درمان مشکلات اسکلتی، عضلانی، آسیب‌های ورزشی و جراحی مفاصل',
        'external_source': 'tehran_medical_council',
        'default_service_duration': 25,
        'work_start_hour': 16,
        'work_end_hour': 21,
        'services': ['درمان درد مفاصل', 'آرتروز زانو', 'ستون فقرات', 'تزریق پی‌آرپی'],
        'insurances': ['dana', 'alborz', 'pasargad', 'karafarin', 'tamin'],
    },
    {
        'title': 'آزمایشگاه پاتوبیولوژی مهرگان',
        'is_claimed': False,
        'category': 'LABORATORY',
        'unique_code': 'MEHRGLAB',
        'phone': '02144056677',
        'address': 'تهران، ستارخان، بین فلکه اول و دوم صادقیه، جنب بانک ملی',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'صادقیه',
        'latitude': 35.7215,
        'longitude': 51.3370,
        'bio': 'آزمایشگاه جامع تشخیص طبی و پاتولوژی مجهز به بخش‌های ایمونولوژی و بیوشیمی',
        'external_source': 'tehran_medical_council',
        'default_service_duration': 15,
        'work_start_hour': 7,
        'work_end_hour': 21,
        'services': ['آزمایش‌های چکاپ کامل', 'تست هورمونی', 'غربالگری بارداری', 'آزمایش ژنتیک'],
        'insurances': ['dana', 'asia', 'alborz', 'saman', 'mellat', 'iran', 'pasargad', 'tamin', 'salamat'],
    },
    {
        'title': 'سالن زیبایی و عروس پریناز',
        'is_claimed': False,
        'category': 'WOMENS_SALON',
        'unique_code': 'PARINAZS',
        'phone': '02122778899',
        'address': 'تهران، پاسداران، میدان هروی، خیابان وفامنش، پلاک ۴۰',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'پاسداران',
        'latitude': 35.7690,
        'longitude': 51.4660,
        'bio': 'خدمات تخصصی رنگ و لایت، ناخن، بافت مو و میکاپ مجلسی',
        'external_source': 'guild_directory',
        'default_service_duration': 60,
        'work_start_hour': 10,
        'work_end_hour': 19,
        'services': ['میکاپ عروس', 'رنگ و هایلایت', 'کاشت ناخن', 'کوپ'],
        'insurances': [],
    },
    {
        'title': 'کلینیک کودکان و نوزادان دکتر تقوی',
        'is_claimed': False,
        'category': 'PEDIATRICS',
        'unique_code': 'TAGHAVIP',
        'phone': '02122091122',
        'address': 'تهران، سعادت‌آباد، بالاتر از میدان کاج، خیابان هفتم، پلاک ۱۴',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'سعادت‌آباد',
        'latitude': 35.7795,
        'longitude': 51.3705,
        'bio': 'مراقبت‌های دوره نوزادی و کودکی، بیماری‌های عفونی و تنفسی اطفال',
        'external_source': 'tehran_medical_council',
        'default_service_duration': 25,
        'work_start_hour': 14,
        'work_end_hour': 20,
        'services': ['پایش رشد و تکامل', 'واکسیناسیون', 'تغذیه کودک', 'درمان بیماری‌های اطفال'],
        'insurances': ['dana', 'saman', 'atiyeh', 'dey', 'tamin', 'salamat'],
    },
    {
        'title': 'مطب گوش و حلق و بینی دکتر شمس',
        'is_claimed': False,
        'category': 'ENT',
        'unique_code': 'SHAMSENT',
        'phone': '02188796633',
        'address': 'تهران، میدان ونک، ابتدای خیابان برزیل شرقی، پلاک ۱۶',
        'province': 'تهران',
        'city': 'تهران',
        'neighborhood': 'ونک',
        'latitude': 35.7560,
        'longitude': 51.4085,
        'bio': 'متخصص جراحی سر و گردن، جراحی زیبایی بینی و رفع مشکلات تنفسی',
        'external_source': 'tehran_medical_council',
        'default_service_duration': 20,
        'work_start_hour': 16,
        'work_end_hour': 20,
        'services': ['جراحی زیبایی بینی', 'آندوسکوپی سینوس', 'شنوایی‌سنجی', 'درمان حساسیت'],
        'insurances': ['iran', 'dana', 'asia', 'tamin', 'armed-forces'],
    },
]


class Command(BaseCommand):
    help = "Seeds standard Iranian insurances and demo directory businesses in Tehran."

    def handle(self, *args, **options):
        self.stdout.write("1. Seeding standard insurances...")
        insurance_map = {}
        for name, slug, ins_type, order in INSURANCES:
            ins, _ = Insurance.objects.update_or_create(
                slug=slug,
                defaults={
                    'name': name,
                    'insurance_type': ins_type,
                    'is_active': True,
                    'display_order': order,
                }
            )
            insurance_map[slug] = ins
        self.stdout.write(self.style.SUCCESS(f"   Successfully seeded {len(insurance_map)} insurances."))

        self.stdout.write("2. Creating or fetching system user for unclaimed businesses...")
        system_user, _ = User.objects.get_or_create(
            phone='09000000000',
            defaults={
                'name': 'بانک اطلاعاتی نوبت‌یار',
                'role': 'ADMIN',
                'is_active': True,
            }
        )

        now = timezone.now()
        created_count = 0
        updated_count = 0

        self.stdout.write("3. Seeding businesses...")
        for item in BUSINESSES:
            is_claimed = item['is_claimed']
            if is_claimed:
                owner, _ = User.objects.get_or_create(
                    phone=item['owner_phone'],
                    defaults={
                        'name': item['owner_name'],
                        'role': 'BUSINESS_OWNER',
                        'is_active': True,
                    }
                )
            else:
                owner = system_user

            unique_code = item['unique_code']
            ins_slugs = item.get('insurances', [])

            defaults = {
                'user': owner,
                'title': item['title'],
                'category': item['category'],
                'phone': item['phone'],
                'address': item['address'],
                'province': item.get('province', 'تهران'),
                'city': item.get('city', 'تهران'),
                'neighborhood': item.get('neighborhood', ''),
                'latitude': item.get('latitude'),
                'longitude': item.get('longitude'),
                'bio': item.get('bio', ''),
                'theme_color': item.get('theme_color', ''),
                'is_claimed': is_claimed,
                'external_source': item.get('external_source', ''),
                'moderation_status': Business.MODERATION_APPROVED,
                'first_approved_at': now,
                'is_locked': False,
                'booking_enabled': is_claimed,
                'default_service_duration': item.get('default_service_duration', 30),
                'work_start_hour': item.get('work_start_hour', 9),
                'work_end_hour': item.get('work_end_hour', 20),
                'services': item.get('services', []),
            }

            business, created = Business.objects.update_or_create(
                unique_code=unique_code,
                defaults=defaults,
            )

            # Link insurances
            matched_insurances = [insurance_map[slug] for slug in ins_slugs if slug in insurance_map]
            business.insurances.set(matched_insurances)

            if created:
                created_count += 1
            else:
                updated_count += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"   Done! Businesses seeded: {created_count} created, {updated_count} updated."
            )
        )
