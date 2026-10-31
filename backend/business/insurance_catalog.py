"""
business/insurance_catalog.py

The insurers the directory lists: (name, slug, type, display order).
Supplementary (تکمیلی) names come from the public comparison and group-insurance
guides (bimeh.com, 2026) plus the ones seeded earlier; basic (پایه) are the
three public funds. Load with ``manage.py seed_insurances`` (idempotent).
"""

INSURANCES = [
    # Supplementary (تکمیلی)
    ('بیمه دانا', 'dana', 'SUPPLEMENTARY', 1),
    ('بیمه آسیا', 'asia', 'SUPPLEMENTARY', 2),
    ('بیمه البرز', 'alborz', 'SUPPLEMENTARY', 3),
    ('بیمه سامان', 'saman', 'SUPPLEMENTARY', 4),
    ('بیمه ملت', 'mellat', 'SUPPLEMENTARY', 5),
    ('بیمه ایران', 'iran', 'SUPPLEMENTARY', 6),
    ('بیمه پاسارگاد', 'pasargad', 'SUPPLEMENTARY', 7),
    ('بیمه کوثر', 'kosar', 'SUPPLEMENTARY', 8),
    ('بیمه دی', 'dey', 'SUPPLEMENTARY', 9),
    ('بیمه آتیه‌سازان حافظ', 'atiyeh', 'SUPPLEMENTARY', 10),
    ('بیمه کارآفرین', 'karafarin', 'SUPPLEMENTARY', 11),
    ('بیمه پارسیان', 'parsian', 'SUPPLEMENTARY', 12),
    ('بیمه سینا', 'sina', 'SUPPLEMENTARY', 13),
    ('بیمه معلم', 'moallem', 'SUPPLEMENTARY', 14),
    ('بیمه آسماری', 'asmari', 'SUPPLEMENTARY', 15),
    ('بیمه تعاون', 'taavon', 'SUPPLEMENTARY', 16),
    ('بیمه نوین', 'novin', 'SUPPLEMENTARY', 17),
    ('بیمه رازی', 'razi', 'SUPPLEMENTARY', 18),
    ('بیمه میهن', 'mihan', 'SUPPLEMENTARY', 19),
    # Basic (پایه)
    ('تامین اجتماعی', 'tamin', 'BASIC', 20),
    ('بیمه سلامت', 'salamat', 'BASIC', 21),
    ('نیروهای مسلح', 'armed-forces', 'BASIC', 22),
]
