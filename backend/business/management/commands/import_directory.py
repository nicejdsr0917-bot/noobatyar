# backend/business/management/commands/import_directory.py
"""
Bulk directory importer for unclaimed business listings from external sources
(e.g., Medical Council directory, syndicate datasets).

Supports JSON, JSONL, and CSV streaming with phone normalization, category
mapping, insurance matching, deduplication, and batch database creation.

Deduplication key: (title, phone, city) when the row has a phone, otherwise
(title, city, address) — without a phone two different clinics with the same
name in one city must not collapse into one.

A row with no usable phone is stored with phone='' (the client hides the call
button); it is never given a placeholder number, which would send callers to a
stranger.

Usage:
    python manage.py import_directory --file <path> --source <name> [--batch-size 1000]
"""

import csv
import json
import os
from django.core.management.base import BaseCommand, CommandError
from django.contrib.auth import get_user_model
from django.utils import timezone
from django.utils.crypto import get_random_string

from api.phone import is_iran_phone, normalize_phone
from business.models import Business, Insurance

User = get_user_model()


def _iter_json(f, chunk_size=1 << 20):
    """Yield the elements of a top-level JSON array (or a single object)
    without loading the whole file: reads fixed-size chunks and peels complete
    values off the front with raw_decode, so memory stays bounded by the
    largest single record rather than the file size."""
    decoder = json.JSONDecoder()
    buf = f.read(chunk_size).lstrip('﻿').lstrip()
    if not buf:
        return
    if buf[0] == '{':
        rest = buf + f.read()
        yield decoder.raw_decode(rest)[0]
        return
    if buf[0] != '[':
        raise ValueError("JSON file must contain an array or an object.")
    buf = buf[1:]
    eof = False
    while True:
        buf = buf.lstrip().lstrip(',').lstrip()
        if buf.startswith(']'):
            return
        if not buf and eof:
            raise ValueError("Unexpected end of JSON array.")
        try:
            obj, end = decoder.raw_decode(buf)
        except json.JSONDecodeError:
            if eof:
                raise
            more = f.read(chunk_size)
            if not more:
                eof = True
            buf += more
            continue
        yield obj
        buf = buf[end:]
        if len(buf) < chunk_size and not eof:
            more = f.read(chunk_size)
            if not more:
                eof = True
            buf += more


def _safe_int(value, default, lo, hi):
    try:
        n = int(float(value))
    except (ValueError, TypeError):
        return default
    return n if lo <= n <= hi else default


def _safe_coord(value, lo, hi):
    if value is None or not str(value).strip():
        return None
    try:
        v = float(value)
    except (ValueError, TypeError):
        return None
    return v if lo <= v <= hi else None


def _dedup_key(title, phone, city, address):
    if phone:
        return ('p', title, phone, city)
    return ('a', title, city, address)


def read_data_stream(file_path):
    """Generator streaming raw row dicts from JSON, JSONL, or CSV file."""
    ext = os.path.splitext(file_path)[1].lower()
    if ext == '.json':
        with open(file_path, 'r', encoding='utf-8') as f:
            yield from _iter_json(f)
    elif ext == '.jsonl':
        with open(file_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line:
                    yield json.loads(line)
    elif ext == '.csv':
        with open(file_path, 'r', encoding='utf-8-sig', newline='') as f:
            reader = csv.DictReader(f)
            for row in reader:
                yield row
    else:
        raise ValueError(f"Unsupported file extension '{ext}'. Must be .json, .jsonl, or .csv")


class Command(BaseCommand):
    help = "Bulk imports directory business listings from JSON, JSONL, or CSV files."

    def add_arguments(self, parser):
        parser.add_argument(
            '--file',
            type=str,
            required=True,
            help="Path to the file to import (.json, .jsonl, .csv)",
        )
        parser.add_argument(
            '--source',
            type=str,
            default='directory_import',
            help="Source dataset identifier for imported listings (e.g. tehran_medical_council)",
        )
        parser.add_argument(
            '--batch-size',
            type=int,
            default=1000,
            help="Number of records to insert per batch (default: 1000)",
        )

    def handle(self, *args, **options):
        file_path = options['file']
        source = options['source']
        batch_size = max(1, options['batch_size'])

        if not os.path.exists(file_path):
            raise CommandError(f"File not found: {file_path}")

        # 1. System User
        system_user, _ = User.objects.get_or_create(
            phone='09000000000',
            defaults={
                'name': 'بانک اطلاعاتی نوبت‌یار',
                'role': 'ADMIN',
                'is_active': True,
            }
        )

        # 2. Pre-load Insurances
        insurances = list(Insurance.objects.all())
        slug_map = {ins.slug.lower(): ins for ins in insurances}
        name_map = {ins.name.lower(): ins for ins in insurances}

        # 3. Category Vocabulary Mappings
        valid_codes = {code: code for code, _ in Business.CATEGORY_CHOICES}
        upper_codes = {code.upper(): code for code, _ in Business.CATEGORY_CHOICES}
        label_map = {label.strip(): code for code, label in Business.CATEGORY_CHOICES}

        # 4. Pre-load existing (title, phone, city) for deduplication
        existing_businesses = {
            _dedup_key(t, p, c, a)
            for t, p, c, a in Business.objects.values_list('title', 'phone', 'city', 'address').iterator()
        }
        existing_codes = set(
            Business.objects.values_list('unique_code', flat=True)
        )

        now = timezone.now()
        total_read = 0
        imported_count = 0
        skipped_count = 0

        batch_items = []
        batch_matched_insurances = []

        def _flush_batch():
            nonlocal batch_items, batch_matched_insurances, imported_count
            if not batch_items:
                return

            created_objs = Business.objects.bulk_create(batch_items)

            # Link insurances via the through table
            through_records = []
            ThroughModel = Business.insurances.through
            for biz, ins_list in zip(created_objs, batch_matched_insurances):
                for ins in ins_list:
                    through_records.append(
                        ThroughModel(business_id=biz.id, insurance_id=ins.id)
                    )

            if through_records:
                ThroughModel.objects.bulk_create(through_records)

            for b in batch_items:
                existing_codes.add(b.unique_code)

            imported_count += len(batch_items)
            self.stdout.write(f"   Saved batch of {len(batch_items)} items. Total imported: {imported_count}")
            batch_items = []
            batch_matched_insurances = []

        self.stdout.write(f"Starting import from {file_path} (source='{source}', batch_size={batch_size})...")

        for row in read_data_stream(file_path):
            total_read += 1

            title = str(row.get('title') or '').strip()
            if not title:
                skipped_count += 1
                continue

            raw_phone = str(row.get('phone') or '').strip()
            norm_phone = normalize_phone(raw_phone) if raw_phone else ''
            if norm_phone and not is_iran_phone(norm_phone):
                norm_phone = ''

            city = str(row.get('city') or '').strip()
            province = str(row.get('province') or '').strip()
            neighborhood = str(row.get('neighborhood') or '').strip()
            address = str(row.get('address') or '').strip()
            if not address:
                address = f"{city} {neighborhood}".strip() or "آدرس ثبت نشده"

            # Deduplication by (title, phone, city)
            dedup_key = _dedup_key(title, norm_phone, city, address)
            if dedup_key in existing_businesses:
                skipped_count += 1
                continue

            # Category mapping
            raw_cat = str(row.get('category') or '').strip()
            if raw_cat in valid_codes:
                cat = valid_codes[raw_cat]
            elif raw_cat.upper() in upper_codes:
                cat = upper_codes[raw_cat.upper()]
            elif raw_cat in label_map:
                cat = label_map[raw_cat]
            else:
                cat = 'OTHER'

            # Coordinates (out-of-range values, e.g. swapped lat/lng, are dropped
            # rather than placing the listing on the wrong continent).
            lat = _safe_coord(row.get('latitude'), -90.0, 90.0)
            lng = _safe_coord(row.get('longitude'), -180.0, 180.0)
            if lat is None or lng is None:
                lat = lng = None

            # Services
            raw_services = row.get('services') or []
            services = []
            if isinstance(raw_services, list):
                services = [str(s).strip() for s in raw_services if str(s).strip()]
            elif isinstance(raw_services, str):
                services = [s.strip() for s in raw_services.split(',') if s.strip()]

            # Insurances
            raw_ins = row.get('insurances') or []
            ins_tokens = []
            if isinstance(raw_ins, list):
                ins_tokens = [str(i).strip() for i in raw_ins if str(i).strip()]
            elif isinstance(raw_ins, str):
                ins_tokens = [i.strip() for i in raw_ins.replace(';', ',').split(',') if i.strip()]

            matched_ins = []
            for token in ins_tokens:
                lower_tok = token.lower()
                if lower_tok in slug_map:
                    matched_ins.append(slug_map[lower_tok])
                elif lower_tok in name_map:
                    matched_ins.append(name_map[lower_tok])

            # Unique code generation
            while True:
                code = get_random_string(
                    length=Business.CODE_LENGTH,
                    allowed_chars=Business.CODE_ALPHABET,
                )
                if code not in existing_codes:
                    existing_codes.add(code)
                    break

            biz = Business(
                user=system_user,
                title=title,
                category=cat,
                unique_code=code,
                phone=norm_phone,
                address=address,
                province=province,
                city=city,
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
                default_service_duration=_safe_int(row.get('default_service_duration'), 30, 5, 480),
                work_start_hour=_safe_int(row.get('work_start_hour'), 9, 0, 23),
                work_end_hour=_safe_int(row.get('work_end_hour'), 18, 1, 24),
                services=services,
            )

            existing_businesses.add(dedup_key)
            batch_items.append(biz)
            batch_matched_insurances.append(matched_ins)

            if len(batch_items) >= batch_size:
                _flush_batch()

        # Final flush
        _flush_batch()

        self.stdout.write(
            self.style.SUCCESS(
                f"Directory import completed! Total rows read: {total_read}, "
                f"Imported: {imported_count}, Skipped/Duplicates: {skipped_count}."
            )
        )
