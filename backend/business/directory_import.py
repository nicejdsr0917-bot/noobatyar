"""
business/directory_import.py

Bulk import of directory listings (businesses nobody has claimed yet) from a
spreadsheet or a data dump. The admin's «ورود از فایل» page and the
``import_directory`` command both run :func:`run_import`, so a file behaves the
same either way.

An imported listing is unclaimed: no Noobatyar tick, booking off, owned by the
system user. A real owner takes it over later through the claim flow
(``business/claims.py``), which keeps its public link.

Only a name is needed; a city is what puts it in the city lists, and a row
without a phone or a map pin is still imported (it just has no call button or
no pin), reported as a warning so the gap can be filled in later.
"""
import csv
import io
import json
import os
import re
from collections import Counter
from dataclasses import dataclass, field

from django.contrib.auth import get_user_model
from django.utils import timezone
from django.utils.crypto import get_random_string

from api.phone import is_iran_phone, normalize_phone

from .iran_cities import cities_by_id
from .models import Business, Insurance

SYSTEM_USER_PHONE = '09000000000'
SAMPLE_LIMIT = 12
PROBLEM_LIMIT = 200

# canonical key: (Persian label for the template, accepted header spellings)
COLUMNS = {
    'title': ('نام', ['title', 'name', 'نام', 'عنوان', 'نام مرکز', 'نام کسب و کار', 'نام کسب‌وکار']),
    'category': ('صنف', ['category', 'صنف', 'دسته', 'دسته بندی', 'دسته‌بندی', 'نوع']),
    'city': ('شهر', ['city', 'شهر']),
    'province': ('استان', ['province', 'استان']),
    'neighborhood': ('محله', ['neighborhood', 'محله', 'منطقه']),
    'address': ('آدرس', ['address', 'آدرس', 'نشانی']),
    'phone': ('تلفن', ['phone', 'tel', 'mobile', 'تلفن', 'شماره', 'شماره تماس', 'موبایل']),
    'latitude': ('عرض جغرافیایی', ['latitude', 'lat', 'عرض', 'عرض جغرافیایی']),
    'longitude': ('طول جغرافیایی', ['longitude', 'lng', 'lon', 'long', 'طول', 'طول جغرافیایی']),
    'services': ('خدمات', ['services', 'خدمات']),
    'insurances': ('بیمه‌ها', ['insurances', 'insurance', 'بیمه', 'بیمه ها', 'بیمه‌ها']),
    'bio': ('معرفی', ['bio', 'معرفی', 'توضیحات']),
    # Not in the template; accepted for files from the old command.
    'default_service_duration': ('', ['default_service_duration']),
    'work_start_hour': ('', ['work_start_hour']),
    'work_end_hour': ('', ['work_end_hour']),
}

_FA_DIGITS = str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩٫٬', '01234567890123456789.,')


def _plain(text):
    """Header / name comparison form: one script variant, no spaces or ZWNJ."""
    text = str(text or '').translate(str.maketrans('يك', 'یک'))
    return re.sub(r'[\s‌_\-]+', '', text).lower()


_ALIAS = {_plain(a): key for key, (_label, aliases) in COLUMNS.items() for a in aliases}


def canonical_row(raw):
    """A raw row with its headers mapped to the canonical keys (unknown ones dropped)."""
    out = {}
    for header, value in raw.items():
        key = _ALIAS.get(_plain(header))
        if key and key not in out:
            out[key] = value
    return out


def template_csv():
    """The downloadable sample: Persian headers, two example rows, UTF-8 with BOM for Excel."""
    labels = [COLUMNS[k][0] for k in ('title', 'category', 'city', 'province', 'neighborhood', 'address',
                                     'phone', 'latitude', 'longitude', 'services', 'insurances', 'bio')]
    rows = [
        ['کلینیک دندان‌پزشکی نمونه', 'دندان‌پزشکی', 'تهران', 'تهران', 'ونک', 'خیابان ملاصدرا، پلاک ۱۰',
         '02188000000', '35.7580', '51.4105', 'جرم‌گیری، ترمیم، ایمپلنت', 'بیمه دانا، تامین اجتماعی', ''],
        ['سالن زیبایی نمونه', 'آرایشگاه و سالن زیبایی', 'اصفهان', '', '', '', '', '', '', '', '', ''],
    ]
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(labels)
    writer.writerows(rows)
    return '﻿' + buf.getvalue()


# ── Reading ─────────────────────────────────────────────────────────────────

def _read_csv(path):
    with open(path, 'r', encoding='utf-8-sig', newline='') as f:
        head = f.readline()
        f.seek(0)
        # Excel in a Persian locale writes «;», some exports use tabs.
        delimiter = max(',;\t', key=head.count) if head else ','
        yield from csv.DictReader(f, delimiter=delimiter)


def _read_json(path):
    with open(path, 'r', encoding='utf-8-sig') as f:
        data = json.load(f)
    yield from (data if isinstance(data, list) else [data])


def _read_jsonl(path):
    with open(path, 'r', encoding='utf-8-sig') as f:
        for line in f:
            line = line.strip()
            if line:
                yield json.loads(line)


READERS = {'.csv': _read_csv, '.json': _read_json, '.jsonl': _read_jsonl}


def read_rows(path):
    """Raw rows from a .csv, .json or .jsonl file (Excel: save as «CSV UTF-8»)."""
    ext = os.path.splitext(path)[1].lower()
    if ext not in READERS:
        raise ValueError("فقط فایل‌های CSV، JSON و JSONL پذیرفته می‌شود. در اکسل از «Save As ← CSV UTF-8» استفاده کنید.")
    return READERS[ext](path)


# ── Cleaning ────────────────────────────────────────────────────────────────

def _int_in(value, default, lo, hi):
    try:
        n = int(float(str(value).translate(_FA_DIGITS)))
    except (ValueError, TypeError):
        return default
    return n if lo <= n <= hi else default


def _coord(value, lo, hi):
    text = str(value if value is not None else '').translate(_FA_DIGITS).strip()
    if not text:
        return None
    try:
        v = float(text)
    except ValueError:
        return None
    return v if lo <= v <= hi else None


def _items(value, separators):
    if isinstance(value, list):
        return [str(v).strip() for v in value if str(v).strip()]
    text = str(value or '')
    for sep in separators:
        text = text.replace(sep, ',')
    return [p.strip() for p in text.split(',') if p.strip()]


def _dedup_key(title, phone, city, address):
    # Same title in one city is two clinics when they have different phones, or
    # (without a phone) different addresses.
    return ('p', title, phone, city) if phone else ('a', title, city, address)


@dataclass
class ImportReport:
    dry_run: bool
    total: int = 0
    created: int = 0
    duplicates: int = 0
    invalid: int = 0
    warnings: Counter = field(default_factory=Counter)
    problems: list = field(default_factory=list)   # (row number, title, reason)
    samples: list = field(default_factory=list)    # (title, city, category label, [warnings])

    @property
    def warning_list(self):
        """(text, count), most common first; a list because a Counter answers `.items` with 0 in a template."""
        return self.warnings.most_common()

    @property
    def skipped(self):
        return self.duplicates + self.invalid


class _Lookups:
    """Everything a row is matched against, loaded once."""

    def __init__(self):
        self.insurance_by_slug = {}
        self.insurance_by_name = {}
        for ins in Insurance.objects.all():
            self.insurance_by_slug[ins.slug.lower()] = ins
            self.insurance_by_name[_plain(ins.name)] = ins
            # «بیمه دانا» is also found as «دانا»
            self.insurance_by_name.setdefault(_plain(ins.name.removeprefix('بیمه ')), ins)

        choices = Business.CATEGORY_CHOICES
        self.category_labels = dict(choices)
        self.category_by_code = {code.upper(): code for code, _ in choices}
        self.category_by_label = {_plain(label): code for code, label in choices}

        self.cities = {}
        for city_id, city in cities_by_id().items():
            self.cities.setdefault(_plain(city['name']), []).append((city_id, _plain(city['province'])))

    def category(self, raw):
        raw = str(raw or '').strip()
        return self.category_by_code.get(raw.upper()) or self.category_by_label.get(_plain(raw))

    def city_code(self, city, province):
        matches = self.cities.get(_plain(city), [])
        if len(matches) == 1:
            return matches[0][0]
        for city_id, prov in matches:
            if province and _plain(province) == prov:
                return city_id
        return matches[0][0] if matches and not province else None


def get_system_user():
    return get_user_model().objects.get_or_create(
        phone=SYSTEM_USER_PHONE,
        defaults={'name': 'بانک اطلاعاتی نوبت‌یار', 'role': 'ADMIN', 'is_active': True},
    )[0]


def run_import(rows, source, *, dry_run=False, require_city=True, batch_size=500, start_row=2):
    """Imports (or, with ``dry_run``, only checks) [rows]; returns an :class:`ImportReport`.

    [rows] are raw dicts as read from the file; [start_row] is the spreadsheet row
    of the first one, so a problem is reported at the row the person sees.
    """
    report = ImportReport(dry_run=dry_run)
    lookups = _Lookups()
    system_user = None if dry_run else get_system_user()
    seen = {
        _dedup_key(t, p, c, a)
        for t, p, c, a in Business.objects.values_list('title', 'phone', 'city', 'address').iterator()
    }
    codes = set(Business.objects.values_list('unique_code', flat=True))
    now = timezone.now()
    batch, batch_insurances = [], []

    def flush():
        nonlocal batch, batch_insurances
        if not batch:
            return
        created = Business.objects.bulk_create(batch)
        through = Business.insurances.through
        links = [through(business_id=b.id, insurance_id=i.id) for b, ins in zip(created, batch_insurances) for i in ins]
        if links:
            through.objects.bulk_create(links)
        batch, batch_insurances = [], []

    def problem(number, title, reason):
        report.invalid += 1
        if len(report.problems) < PROBLEM_LIMIT:
            report.problems.append((number, title, reason))

    for offset, raw in enumerate(rows):
        number = start_row + offset
        report.total += 1
        row = canonical_row(raw)

        title = str(row.get('title') or '').strip()
        if not title:
            problem(number, '', 'نام خالی است')
            continue
        city = str(row.get('city') or '').strip()
        if require_city and not city:
            problem(number, title, 'شهر خالی است')
            continue

        warnings = []
        province = str(row.get('province') or '').strip()
        neighborhood = str(row.get('neighborhood') or '').strip()
        address = str(row.get('address') or '').strip() or f'{city} {neighborhood}'.strip() or 'آدرس ثبت نشده'

        raw_phone = str(row.get('phone') or '').translate(_FA_DIGITS).strip()
        phone = normalize_phone(raw_phone) if raw_phone else ''
        if phone and not is_iran_phone(phone):
            phone = ''
            warnings.append('تلفن نامعتبر')
        elif not phone:
            warnings.append('بدون تلفن')

        key = _dedup_key(title, phone, city, address)
        if key in seen:
            report.duplicates += 1
            continue

        raw_category = str(row.get('category') or '').strip()
        category = lookups.category(raw_category)
        if category is None:
            category = 'OTHER'
            warnings.append(f'صنف «{raw_category}» ناشناخته (سایر)' if raw_category else 'بدون صنف (سایر)')

        lat = _coord(row.get('latitude'), -90.0, 90.0)
        lng = _coord(row.get('longitude'), -180.0, 180.0)
        if lat is None or lng is None:
            lat = lng = None
            warnings.append('بدون موقعیت روی نقشه')

        city_code = lookups.city_code(city, province) if city else None
        if city and city_code is None:
            warnings.append('شهر در فهرست نیست')

        insurances = []
        for token in _items(row.get('insurances'), [';', '،']):
            found = lookups.insurance_by_slug.get(token.lower()) or lookups.insurance_by_name.get(_plain(token))
            if found:
                insurances.append(found)
            else:
                warnings.append(f'بیمه «{token}» ناشناخته')

        for w in warnings:
            report.warnings[re.sub(r'«.*?»', '«…»', w)] += 1
        report.created += 1
        if len(report.samples) < SAMPLE_LIMIT:
            report.samples.append((title, city, lookups.category_labels.get(category, category), warnings))
        seen.add(key)

        if dry_run:
            continue
        while True:
            code = get_random_string(length=Business.CODE_LENGTH, allowed_chars=Business.CODE_ALPHABET)
            if code not in codes:
                codes.add(code)
                break
        batch.append(Business(
            user=system_user,
            title=title,
            category=category,
            unique_code=code,
            phone=phone,
            address=address,
            province=province,
            city=city,
            city_code=city_code,
            neighborhood=neighborhood,
            latitude=lat,
            longitude=lng,
            bio=str(row.get('bio') or '').strip(),
            is_claimed=False,
            external_source=source,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=now,
            is_locked=False,
            booking_enabled=False,
            default_service_duration=_int_in(row.get('default_service_duration'), 30, 5, 480),
            work_start_hour=_int_in(row.get('work_start_hour'), 9, 0, 23),
            work_end_hour=_int_in(row.get('work_end_hour'), 18, 1, 24),
            services=_items(row.get('services'), ['،']),
        ))
        batch_insurances.append(insurances)
        if len(batch) >= batch_size:
            flush()
    flush()
    return report
