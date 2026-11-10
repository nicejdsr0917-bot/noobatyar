"""
business/directory_sync.py

Periodic refresh of the doctor / clinic directory from outside sources (the
n8n workflow in ``docker-compose.n8n.yml``). It is deliberately narrow:

* new listings are created exactly as the admin import does (``run_import``:
  unclaimed, booking off, owned by the system user);
* a listing that already exists and is still **unclaimed** only gets the
  insurances it was missing. Nothing is ever removed or overwritten, and a
  listing a real owner has claimed is never touched.

So a bad or hostile feed can at worst add a listing or an insurance link to
the system-owned directory; it cannot edit a real customer's business.
"""
from dataclasses import dataclass

from api.phone import is_iran_phone, normalize_phone

from .directory_import import (
    _FA_DIGITS, _Lookups, _items, _plain, canonical_row, get_system_user, run_import,
)
from .models import Business

MAX_ROWS = 500


@dataclass
class SyncReport:
    dry_run: bool
    total: int = 0
    created: int = 0
    duplicates: int = 0
    invalid: int = 0
    linked: int = 0          # insurance links added to existing unclaimed listings
    claimed_skipped: int = 0  # matched a claimed business, left alone

    def as_dict(self):
        return {
            'dry_run': self.dry_run, 'total': self.total, 'created': self.created,
            'duplicates': self.duplicates, 'invalid': self.invalid,
            'linked': self.linked, 'claimed_skipped': self.claimed_skipped,
        }


def _identity(row):
    """(title, phone, city, address) the same way ``run_import`` derives them."""
    title = str(row.get('title') or '').strip()
    city = str(row.get('city') or '').strip()
    neighborhood = str(row.get('neighborhood') or '').strip()
    address = str(row.get('address') or '').strip() or f'{city} {neighborhood}'.strip() or 'آدرس ثبت نشده'
    raw_phone = str(row.get('phone') or '').translate(_FA_DIGITS).strip()
    phone = normalize_phone(raw_phone) if raw_phone else ''
    if phone and not is_iran_phone(phone):
        phone = ''
    return title, phone, city, address


def sync_listings(rows, source, *, dry_run=False):
    """Create new listings and top up insurances on existing unclaimed ones."""
    rows = list(rows)[:MAX_ROWS]
    imported = run_import(rows, source, dry_run=dry_run)
    report = SyncReport(
        dry_run=dry_run, total=imported.total, created=imported.created,
        duplicates=imported.duplicates, invalid=imported.invalid,
    )
    if dry_run:
        return report

    lookups = _Lookups()
    system_user = get_system_user()
    through = Business.insurances.through
    for raw in rows:
        row = canonical_row(raw)
        insurances = []
        for token in _items(row.get('insurances'), [';', '،']):
            found = lookups.insurance_by_slug.get(token.lower()) or lookups.insurance_by_name.get(_plain(token))
            if found:
                insurances.append(found)
        title, phone, city, address = _identity(row)
        if not insurances or not title:
            continue
        match = Business.objects.filter(title=title, city=city)
        match = match.filter(phone=phone) if phone else match.filter(address=address)
        for business in match:
            if business.is_claimed or business.user_id != system_user.id:
                report.claimed_skipped += 1
                continue
            have = set(business.insurances.values_list('id', flat=True))
            new = [through(business_id=business.id, insurance_id=i.id) for i in insurances if i.id not in have]
            if new:
                through.objects.bulk_create(new, ignore_conflicts=True)
                report.linked += len(new)
    return report
