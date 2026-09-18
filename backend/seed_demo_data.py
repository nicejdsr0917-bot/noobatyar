"""Demo data for marketing screenshots — one dental clinic and one beauty salon.

Run with: python manage.py shell < seed_demo_data.py

Separate from seed_test_data.py on purpose: that one exists to give
front_client *something* to render while developing, and its five businesses
are deliberately thin. This one is shot-list data — the appointments are
spread across two months so the home screen's month card and trend chart have
a shape to draw, the statuses cover every chip the queue can render, and every
name/service is one that reads correctly in a Persian screenshot.

Idempotent: re-running replaces this owner's appointments rather than stacking
a second copy on top of them.
"""
import os
import random
from datetime import timedelta

import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')

from django.contrib.auth import get_user_model
from django.utils import timezone

from business.models import Business
from business.moderation import apply_decision
from business.theme_color import color_from_logo
from visitor.models import Visitor
from appointment.models import Appointment
from accounting.models import Plan, Subscription

User = get_user_model()

random.seed(1404)  # stable output: the same screenshots on every re-seed

OWNER_PHONE = '09120000010'
OWNER_PASSWORD = 'demo123456'

# ── Owner ────────────────────────────────────────────────────────────────
owner, created = User.objects.get_or_create(
    phone=OWNER_PHONE,
    defaults={'name': 'مریم رستگار', 'is_active': True},
)
owner.name = 'مریم رستگار'
owner.is_active = True
owner.set_password(OWNER_PASSWORD)
owner.save()
print(f"{'✅ created' if created else 'ℹ️  updated'} owner: {owner.phone} / {OWNER_PASSWORD}")

# A brand-new owner has DEFAULT_ENTITLEMENTS (accounting/entitlements.py):
# 0 monthly appointments, no deposit/gateway. Every appointment this script
# creates would otherwise trip accounting.usage.can_book_appointment and the
# owner app / public booking page would show
# "پذیرش نوبت جدید برای این کسب‌وکار موقتاً غیرفعال است." instead of the
# populated screens this data exists to produce. "پرو" (6-month) unlocks the
# online gateway, deposits and the branded page too — all things a
# screenshot-worthy business plausibly has turned on.
pro_plan = Plan.objects.filter(name='پرو', is_active=True).first()
if pro_plan:
    sub, sub_created = Subscription.objects.update_or_create(
        user=owner,
        status='active',
        defaults={'plan': pro_plan, 'ends_at': timezone.now() + timedelta(days=180)},
    )
    print(f"{'✅ created' if sub_created else 'ℹ️  updated'} subscription: {pro_plan.name}")
else:
    print("⚠️  plan 'پرو' not found — run seed_plans.py first, or booking will stay blocked")

# ── Businesses ───────────────────────────────────────────────────────────
BUSINESSES = [
    {
        'title': 'دندان‌پزشکی دکتر سمیعی',
        'category': 'DENTIST',
        'unique_code': 'DENTDEMO',
        'phone': '02188442310',
        'address': 'تهران، سعادت‌آباد، بلوار دریا، پلاک ۴۵، طبقه سوم',
        'bio': 'خدمات عمومی و زیبایی دندان',
        'default_service_duration': 30,
        'work_start_hour': 9,
        'work_end_hour': 20,
        'notification_minutes_before': 60,
        'services': [
            'معاینه و مشاوره',
            'جرم‌گیری و بروساژ',
            'ترمیم و پرکردن',
            'عصب‌کشی',
            'کشیدن دندان',
            'بلیچینگ و سفید کردن',
            'کامپوزیت ونیر',
        ],
        'deposit_mode': 'OPTIONAL',
        'deposit_amount': 200000,
        'accepted_payment_methods': ['CASH', 'GATEWAY'],
    },
    {
        'title': 'سالن زیبایی رزمریم',
        'category': 'WOMENS_SALON',
        'unique_code': 'SALONDEM',
        'phone': '02166701184',
        'address': 'تهران، جردن، خیابان تندیس، پلاک ۱۲، واحد ۲',
        'bio': 'مو، ناخن و میکاپ',
        'default_service_duration': 60,
        'work_start_hour': 10,
        'work_end_hour': 21,
        'notification_minutes_before': 120,
        'services': [
            'کوتاهی مو',
            'رنگ مو',
            'مش و لایت',
            'کراتین و احیا',
            'میکاپ عروس',
            'اصلاح صورت و ابرو',
            'مانیکور و پدیکور',
            'لمینت مژه',
        ],
        'deposit_mode': 'MANDATORY',
        'deposit_amount': 500000,
        'accepted_payment_methods': ['CASH', 'CARD'],
    },
]

businesses = {}
for spec in BUSINESSES:
    code = spec.pop('unique_code')
    biz, biz_created = Business.objects.get_or_create(
        user=owner,
        title=spec['title'],
        defaults={**spec, 'unique_code': code},
    )
    for field, value in spec.items():
        setattr(biz, field, value)
    biz.notification_enabled = True
    biz.notification_types = 'SMS'
    biz.allow_anonymous_view = True
    biz.booking_enabled = True
    biz.save()

    # Skip editorial moderation for demo data: a freshly created business, or
    # one whose owner just edited a MODERATED_FIELDS column (title/bio/address/
    # logo — business/models.py), sits in MODERATION_PENDING with the edit
    # only staged on pending_logo/pending_title/etc. until a moderator approves
    # it — not useful for screenshots, so approve it here the way a moderator
    # would. apply_decision() (not a raw status flip) is what actually
    # promotes a staged pending_logo onto the live `logo` field; setting
    # moderation_status=APPROVED by hand leaves logo empty even though the
    # business reads as approved.
    if biz.moderation_status != Business.MODERATION_APPROVED or biz.pending_logo:
        apply_decision(biz, Business.MODERATION_APPROVED, actor=None, note='دمو — تأیید خودکار')
        biz.refresh_from_db()
    if biz.logo:
        biz.theme_color = color_from_logo(biz.logo)
        biz.save(update_fields=['theme_color'])
    businesses[biz.title] = biz
    print(f"{'✅ created' if biz_created else 'ℹ️  updated'} business: {biz.title} (/b/Noobatyar-{biz.unique_code})")

dental = businesses['دندان‌پزشکی دکتر سمیعی']
salon = businesses['سالن زیبایی رزمریم']

# ── Visitors ─────────────────────────────────────────────────────────────
# Phone numbers sit in a reserved-looking 0912000xxxx block so no seed row can
# ever collide with a real person's number (Visitor.phone_number is unique
# platform-wide, not per business).
VISITORS = [
    ('زهرا کاظمی', '09120000101'),
    ('نگار موسوی', '09120000102'),
    ('سارا احمدی', '09120000103'),
    ('الهام نوری', '09120000104'),
    ('مینا شریفی', '09120000105'),
    ('پریسا رحیمی', '09120000106'),
    ('فاطمه صادقی', '09120000107'),
    ('شیما کریمی', '09120000108'),
    ('امیر حسینی', '09120000109'),
    ('رضا فتحی', '09120000110'),
    ('محمد بهرامی', '09120000111'),
    ('علی مرادی', '09120000112'),
    ('سعید جعفری', '09120000113'),
    ('حمید عباسی', '09120000114'),
    ('نیلوفر یزدانی', '09120000115'),
    ('مهسا قاسمی', '09120000116'),
]

visitors = []
for name, phone in VISITORS:
    v, _ = Visitor.objects.get_or_create(
        phone_number=phone,
        defaults={'full_name': name, 'user': owner},
    )
    if v.full_name != name or v.user_id != owner.id:
        v.full_name = name
        v.user = owner
        v.save()
    visitors.append(v)
print(f"✅ {len(visitors)} visitors ready")

# ── Appointments ─────────────────────────────────────────────────────────
# Wiped and rebuilt so a re-run is a replacement, not an accumulation.
removed, _ = Appointment.objects.filter(business__in=[dental, salon]).delete()
print(f"🧹 cleared {removed} existing demo appointments")

now = timezone.now()
today = now.replace(hour=0, minute=0, second=0, microsecond=0)


def make(business, visitor, day_offset, hour, minute, status, services, duration=None, note=''):
    when = today + timedelta(days=day_offset, hours=hour, minutes=minute)
    return Appointment(
        user=owner,
        business=business,
        visitor=visitor,
        appointment_date=when,
        service_duration=duration or business.default_service_duration,
        status=status,
        selected_services=services,
        description=note,
    )


rows = []

# Today's queue — the shot the home screen and the queue list are built around.
# Statuses run in the order a real day moves through them so the list reads as
# a morning that has already happened plus an afternoon that has not.
rows += [
    make(dental, visitors[0], 0, 9, 0, 'COMPLETED', 'جرم‌گیری و بروساژ'),
    make(dental, visitors[8], 0, 9, 30, 'COMPLETED', 'معاینه و مشاوره'),
    make(dental, visitors[1], 0, 10, 0, 'COMPLETED', 'ترمیم و پرکردن', 45),
    make(dental, visitors[9], 0, 10, 45, 'NO_SHOW', 'معاینه و مشاوره'),
    make(dental, visitors[2], 0, 11, 15, 'IN_PROGRESS', 'عصب‌کشی', 60,
         'ادامه درمان جلسه دوم'),
    make(dental, visitors[10], 0, 12, 30, 'WAITING', 'کشیدن دندان', 45),
    make(dental, visitors[3], 0, 16, 0, 'CONFIRMED', 'بلیچینگ و سفید کردن', 60),
    make(dental, visitors[11], 0, 17, 0, 'WAITING', 'معاینه و مشاوره'),
    make(dental, visitors[4], 0, 18, 0, 'PENDING_APPROVAL', 'کامپوزیت ونیر', 90,
         'رزرو از صفحه اینترنتی'),
]

rows += [
    make(salon, visitors[5], 0, 10, 0, 'COMPLETED', 'کوتاهی مو', 45),
    make(salon, visitors[6], 0, 11, 0, 'COMPLETED', 'رنگ مو,کوتاهی مو', 120),
    make(salon, visitors[14], 0, 13, 30, 'IN_PROGRESS', 'مش و لایت', 150),
    make(salon, visitors[7], 0, 16, 0, 'WAITING', 'اصلاح صورت و ابرو', 30),
    make(salon, visitors[15], 0, 17, 0, 'CONFIRMED', 'کراتین و احیا', 120,
         'موی بلند — وقت بیشتری بگیر'),
    make(salon, visitors[0], 0, 19, 30, 'PENDING_APPROVAL', 'لمینت مژه', 60,
         'رزرو از صفحه اینترنتی'),
]

# Upcoming week — gives the calendar screen something to show on every day.
upcoming = [
    (dental, 1, [(9, 0, 'معاینه و مشاوره'), (11, 0, 'ترمیم و پرکردن'), (17, 30, 'جرم‌گیری و بروساژ')]),
    (dental, 2, [(10, 0, 'عصب‌کشی'), (15, 0, 'معاینه و مشاوره')]),
    (dental, 3, [(9, 30, 'بلیچینگ و سفید کردن'), (12, 0, 'کشیدن دندان'), (18, 0, 'کامپوزیت ونیر')]),
    (dental, 5, [(10, 30, 'جرم‌گیری و بروساژ'), (16, 0, 'معاینه و مشاوره')]),
    (dental, 6, [(11, 0, 'ترمیم و پرکردن')]),
    (salon, 1, [(11, 0, 'رنگ مو'), (14, 0, 'مانیکور و پدیکور'), (18, 0, 'کوتاهی مو')]),
    (salon, 2, [(10, 30, 'میکاپ عروس'), (16, 30, 'اصلاح صورت و ابرو')]),
    (salon, 3, [(12, 0, 'کراتین و احیا'), (17, 0, 'لمینت مژه')]),
    (salon, 4, [(11, 30, 'مش و لایت'), (15, 30, 'کوتاهی مو'), (19, 0, 'رنگ مو')]),
    (salon, 6, [(13, 0, 'مانیکور و پدیکور')]),
]
for business, offset, slots in upcoming:
    for hour, minute, service in slots:
        rows.append(make(
            business, random.choice(visitors), offset, hour, minute,
            'CONFIRMED' if offset <= 2 else 'WAITING', service,
        ))

# Past two months — the volume behind the month card and the trend chart.
# Weekends (Thursday/Friday in Iran) are deliberately lighter so the chart has
# a weekly rhythm instead of a flat line.
for business in (dental, salon):
    menu = business.services
    for offset in range(-60, 0):
        day = today + timedelta(days=offset)
        weekday = day.weekday()  # 3 = Thursday, 4 = Friday
        if weekday == 4:
            count = random.randint(0, 1)
        elif weekday == 3:
            count = random.randint(2, 4)
        else:
            count = random.randint(4, 9)
        used_hours = set()
        for _ in range(count):
            hour = random.randint(business.work_start_hour, business.work_end_hour - 1)
            while hour in used_hours:
                hour = random.randint(business.work_start_hour, business.work_end_hour - 1)
            used_hours.add(hour)
            status = random.choices(
                ['COMPLETED', 'NO_SHOW', 'CANCELLED'],
                weights=[88, 7, 5],
            )[0]
            rows.append(make(
                business, random.choice(visitors), offset, hour,
                random.choice([0, 15, 30, 45]), status, random.choice(menu),
            ))

Appointment.objects.bulk_create(rows)
print(f"✅ {len(rows)} appointments created")

for business in (dental, salon):
    total = Appointment.objects.filter(business=business).count()
    todays = Appointment.objects.filter(
        business=business,
        appointment_date__gte=today,
        appointment_date__lt=today + timedelta(days=1),
    ).count()
    print(f"   {business.title}: {total} total, {todays} today")

print(f"\n🔑 login: {OWNER_PHONE} / {OWNER_PASSWORD}  (OTP dev code: 123456)")
print(f"🔗 booking pages: /b/Noobatyar-{dental.unique_code}  &  /b/Noobatyar-{salon.unique_code}")
