"""Every appointment state the owner app can render, on one business.

Run with (not `shell < file`: the interactive console trips over the
blank lines inside the multi-line calls below):
    python manage.py shell -c "exec(open('seed_status_matrix.py', encoding='utf-8').read())"
Target another business by setting SEED_BUSINESS_ID=<id> first.

seed_demo_data.py is screenshot data and only uses the statuses a tidy demo
day has. This is UI-test data: it puts each of the nine statuses on screen,
plus the combinations that change how a row is drawn — a card-to-card deposit
with a receipt image, a gateway deposit with a tracking reference, an overdue
WAITING row, a long name, a long note, short and long durations, and bookings
on days other than today (the queue only prints a date for those).

Everything lands inside the نوبت‌ها screen's default window (today + six
days), with a handful in the last week so the "۳۰ روز گذشته" preset has
deposits and receipts to show as well.

Idempotent: rows it creates carry a SEED- tracking code, and a re-run deletes
those before creating them again, leaving the business's other data alone.
"""
import os
from datetime import timedelta

import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')

from django.conf import settings
from django.utils import timezone
from PIL import Image, ImageDraw

from appointment.models import Appointment
from business.models import Business
from visitor.models import Visitor

BUSINESS_ID = int(os.getenv('SEED_BUSINESS_ID', '61'))
TAG = 'SEED-'

business = Business.objects.get(id=BUSINESS_ID)
owner = business.user
print(f"🏥 {business.title} (#{business.id}) — owner {owner.phone}")

# ── Receipt images ───────────────────────────────────────────────────────
# Drawn here rather than borrowed from media/: the slips already in there were
# uploaded by hand and one is a screenshot of an unrelated app. Latin text
# only — PIL doesn't shape Persian, and a receipt only needs to *look* like one.
receipt_dir = os.path.join(settings.MEDIA_ROOT, 'payment_receipts')
os.makedirs(receipt_dir, exist_ok=True)


def make_receipt(name, amount, ref, accent):
    path = os.path.join(receipt_dir, name)
    img = Image.new('RGB', (720, 1080), (246, 247, 251))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 720, 180], fill=accent)
    d.text((48, 60), 'CARD TO CARD TRANSFER', fill='white')
    d.text((48, 110), 'Successful', fill='white')
    d.rounded_rectangle([40, 230, 680, 900], radius=28, fill='white', outline=(220, 222, 230), width=3)
    rows = [
        ('Amount', f'{amount:,} IRR'),
        ('Reference', ref),
        ('From card', '6037-99** ****-4821'),
        ('To card', '6219-86** ****-1107'),
        ('Date', timezone.localtime().strftime('%Y-%m-%d %H:%M')),
    ]
    y = 290
    for label, value in rows:
        d.text((80, y), label, fill=(120, 124, 140))
        d.text((360, y), value, fill=(20, 24, 40))
        d.line([80, y + 60, 640, y + 60], fill=(236, 237, 242), width=2)
        y += 110
    d.text((80, 960), 'noobatyar test receipt - not a real payment', fill=(160, 164, 176))
    img.save(path, 'PNG')
    return f'payment_receipts/{name}'


receipt_a = make_receipt('seed_receipt_a.png', 2_000_000, '482913', (124, 58, 237))
receipt_b = make_receipt('seed_receipt_b.png', 1_500_000, '771045', (5, 150, 105))
receipt_c = make_receipt('seed_receipt_c.png', 3_000_000, '190377', (220, 38, 38))
print('🧾 3 receipt images written')

# ── Visitors ─────────────────────────────────────────────────────────────
# The business's own visitors first, so the report tab's customer roll-up
# keeps its shape; two extra ones exist only to stress the layout.
existing = list(
    Visitor.objects.filter(appointments__business=business).distinct().order_by('id')[:12]
)
extra = []
for name, phone in [
    ('عبدالرضا حسین‌زاده خراسانی‌نژاد', '09129100901'),  # long name → ellipsis
    ('آوا', '09129100902'),                               # very short name
]:
    v, _ = Visitor.objects.get_or_create(phone_number=phone, defaults={'full_name': name, 'user': owner})
    if v.full_name != name:
        v.full_name = name
        v.save(update_fields=['full_name'])
    extra.append(v)
visitors = extra + existing
if len(visitors) < 8:
    raise SystemExit('Not enough visitors on this business to seed from.')
long_name, short_name = extra[0], extra[1]
v = visitors[2:]  # the business's own visitors

# ── Appointments ─────────────────────────────────────────────────────────
removed, _ = Appointment.objects.filter(business=business, tracking_code__startswith=TAG).delete()
print(f"🧹 removed {removed} rows from a previous run")

now = timezone.localtime()
today = now.replace(hour=0, minute=0, second=0, microsecond=0)
seq = 0


def at(day_offset, hour, minute=0):
    return today + timedelta(days=day_offset, hours=hour, minutes=minute)


def make(visitor, when, status, duration=None, note=None, services='', **extra_fields):
    global seq
    seq += 1
    return Appointment(
        user=owner,
        business=business,
        visitor=visitor,
        appointment_date=when,
        service_duration=duration,
        status=status,
        description=note,
        selected_services=services,
        tracking_code=f'{TAG}{seq:04d}',
        **extra_fields,
    )


rows = [
    # ── Today ──────────────────────────────────────────────────────────
    # Already over, still WAITING → the red "زمان رد شده" badge.
    make(v[0], now - timedelta(hours=2), 'WAITING', 30, services='معاینه و مشاوره'),
    # Being served right now.
    make(v[1], now - timedelta(minutes=20), 'IN_PROGRESS', 60, services='عصب‌کشی'),
    # Next up in half an hour.
    make(v[2], now + timedelta(minutes=30), 'WAITING', 15, services='معاینه و مشاوره'),
    # A note long enough to need truncating on the card.
    make(long_name, now + timedelta(hours=1, minutes=15), 'WAITING', 45,
         note='بیمار سابقه حساسیت به پنی‌سیلین دارد؛ قبل از شروع درمان حتماً با پزشک معالج هماهنگ شود و فشار خون اندازه‌گیری شود',
         services='ترمیم و پرکردن'),
    # Booked online, waiting for the owner to accept.
    make(v[3], now + timedelta(hours=2), 'PENDING_APPROVAL', 30,
         note='رزرو از صفحه اینترنتی', services='جرم‌گیری و بروساژ'),
    # Card-to-card deposit uploaded, waiting for the owner to check the slip.
    make(v[4], now + timedelta(hours=3), 'PENDING_VERIFICATION', 90,
         services='کامپوزیت ونیر', deposit_payment_method='CARD',
         payment_reference='482913', payment_receipt=receipt_a),
    # Mid-payment on the gateway: the slot is held for fifteen minutes.
    make(short_name, now + timedelta(hours=4), 'LOCKED', 30,
         services='معاینه و مشاوره',
         locked_at=now - timedelta(minutes=3), expires_at=now + timedelta(minutes=12)),
    # Paid through the gateway — tracking reference, no image.
    make(v[5], now + timedelta(hours=5), 'CONFIRMED', 60,
         services='بلیچینگ و سفید کردن', deposit_payment_method='GATEWAY',
         payment_reference='ZB-9921834470'),
    # This morning's finished and missed bookings.
    make(v[6], at(0, 8, 30), 'COMPLETED', 30, services='جرم‌گیری و بروساژ',
         deposit_payment_method='GATEWAY', payment_reference='ZB-9921800012'),
    make(v[7] if len(v) > 7 else v[0], at(0, 9, 15), 'NO_SHOW', 30, services='معاینه و مشاوره'),
    # Slip rejected — cancelled, receipt still attached.
    make(v[8] if len(v) > 8 else v[1], at(0, 10, 0), 'CANCELLED', 45,
         note='فیش نامعتبر بود', services='ترمیم و پرکردن',
         deposit_payment_method='CARD', payment_reference='190377', payment_receipt=receipt_c),

    # ── The rest of the week ─────────────────────────────────────────
    make(v[0], at(1, 9, 0), 'PENDING_VERIFICATION', 60, services='عصب‌کشی',
         deposit_payment_method='CARD', payment_reference='771045', payment_receipt=receipt_b),
    make(v[1], at(1, 11, 30), 'CONFIRMED', 30, services='معاینه و مشاوره',
         deposit_payment_method='CARD', payment_reference='550218', payment_receipt=receipt_a),
    make(v[2], at(1, 16, 0), 'WAITING', 120, services='ایمپلنت'),
    make(v[3], at(2, 10, 0), 'PENDING_APPROVAL', 30, note='رزرو از صفحه اینترنتی',
         services='جرم‌گیری و بروساژ'),
    make(long_name, at(2, 14, 30), 'CONFIRMED', 45, services='کشیدن دندان'),
    make(v[4], at(3, 9, 30), 'WAITING', 30, services='ترمیم و پرکردن'),
    make(v[5], at(3, 12, 0), 'LOCKED', 30, services='معاینه و مشاوره',
         locked_at=now, expires_at=now + timedelta(minutes=15)),
    make(v[6], at(4, 17, 0), 'CONFIRMED', 90, services='کامپوزیت ونیر',
         deposit_payment_method='GATEWAY', payment_reference='ZB-9921877301'),
    make(short_name, at(5, 18, 30), 'WAITING', 15, services='معاینه و مشاوره'),
    make(v[0], at(6, 10, 0), 'CANCELLED', 30, note='لغو توسط مشتری', services='جرم‌گیری و بروساژ'),

    # ── Last week (the "۳۰ روز گذشته" preset) ────────────────────────
    make(v[1], at(-1, 11, 0), 'COMPLETED', 60, services='عصب‌کشی',
         deposit_payment_method='CARD', payment_reference='330981', payment_receipt=receipt_b),
    make(v[2], at(-2, 15, 0), 'NO_SHOW', 30, services='معاینه و مشاوره',
         deposit_payment_method='GATEWAY', payment_reference='ZB-9921700455'),
    make(long_name, at(-3, 10, 30), 'COMPLETED', 45,
         note='جلسه دوم درمان ریشه؛ کنترل بعدی دو هفته دیگر', services='عصب‌کشی'),
    make(v[3], at(-5, 13, 0), 'CANCELLED', 30, services='کشیدن دندان'),
]

Appointment.objects.bulk_create(rows)

by_status = {}
for r in rows:
    by_status[r.status] = by_status.get(r.status, 0) + 1
print(f"✅ {len(rows)} appointments created")
for status, _label in Appointment.STATUS_CHOICES:
    print(f"   {status:22} {by_status.get(status, 0)}")
missing = [s for s, _ in Appointment.STATUS_CHOICES if s not in by_status]
print('⚠️  missing: ' + ', '.join(missing) if missing else '🎯 every status covered')
