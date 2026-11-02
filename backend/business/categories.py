"""
business/categories.py

The business category vocabulary. ``Business.CATEGORY_GROUPS`` and
``Business.CATEGORY_CHOICES`` are aliases of the values defined here, so the
model field's ``choices`` (and therefore migrations) are unchanged.
"""

# ── Category vocabulary ──────────────────────────────────────────────────
# CATEGORY_GROUPS is the single definition; CATEGORY_CHOICES is derived
# from it below. The grouping is not decoration: this vocabulary is ~35
# entries, and the group headings are what make it browsable in the owner
# app's picker and scannable in the admin's flat <select>. Defining the
# groups here rather than in each client is what lets the categories
# endpoint (BusinessCategoriesView) serve them, so adding a category
# never needs a mobile release.
#
# Order is thematic (health → beauty → professional → education/sport →
# trades → other), not alphabetical, and every consumer renders it in
# this order.
#
# The four original keys (BEAUTY_SALON, DOCTOR, CONSULTANT, OTHER) keep
# their exact values *and* labels. Renaming one would silently
# re-categorize every existing business holding it, and re-labelling
# BEAUTY_SALON as women-only would mis-describe the men's barbershops
# already filed under it — BARBERSHOP is offered for new ones instead.
CATEGORY_GROUPS = [
    ('MEDICAL', 'پزشکی و تخصص‌ها', [
        # DOCTOR stays first and keeps its generic label: it is what every
        # existing clinic is filed under, and a business that has not
        # picked a specialty should still have somewhere correct to sit.
        ('DOCTOR', 'پزشک و کلینیک'),
        ('GENERAL_PRACTITIONER', 'پزشک عمومی'),
        ('INTERNAL_MEDICINE', 'داخلی'),
        ('CARDIOLOGY', 'قلب و عروق'),
        ('DERMATOLOGY', 'پوست، مو و زیبایی (پزشک)'),
        ('ORTHOPEDICS', 'ارتوپدی'),
        ('PEDIATRICS', 'اطفال و کودکان'),
        ('GYNECOLOGY', 'زنان و زایمان'),
        ('ENT', 'گوش، حلق و بینی'),
        ('OPHTHALMOLOGY', 'چشم‌پزشکی'),
        ('NEUROLOGY', 'مغز و اعصاب'),
        ('UROLOGY', 'اورولوژی'),
        ('ENDOCRINOLOGY', 'غدد و دیابت'),
        ('GASTROENTEROLOGY', 'گوارش و کبد'),
        ('ONCOLOGY', 'انکولوژی'),
        ('SURGERY', 'جراحی'),
        ('PSYCHIATRY', 'روان‌پزشکی'),
        ('INFERTILITY', 'ناباروری و IVF'),
    ]),
    ('DENTAL', 'دندان‌پزشکی', [
        ('DENTIST', 'دندان‌پزشکی'),
        ('ORTHODONTICS', 'ارتودنسی'),
        ('DENTAL_IMPLANT', 'ایمپلنت و جراحی دهان'),
        ('PEDIATRIC_DENTISTRY', 'دندان‌پزشکی کودکان'),
    ]),
    ('HEALTH', 'سلامت و درمان', [
        ('PSYCHOLOGY', 'روان‌شناسی و مشاوره'),
        ('PHYSIOTHERAPY', 'فیزیوتراپی و توان‌بخشی'),
        ('NUTRITION', 'تغذیه و رژیم‌درمانی'),
        ('LABORATORY', 'آزمایشگاه'),
        ('RADIOLOGY', 'رادیولوژی و سونوگرافی'),
        ('OPTOMETRY', 'بینایی‌سنجی و عینک'),
        ('SPEECH_THERAPY', 'گفتاردرمانی'),
        ('OCCUPATIONAL_THERAPY', 'کاردرمانی'),
        ('MIDWIFERY', 'مامایی و سلامت بانوان'),
        ('NURSING', 'پرستاری و خدمات در منزل'),
        ('PHARMACY', 'داروخانه'),
        ('VETERINARY', 'دامپزشکی'),
    ]),
    ('BEAUTY', 'آرایش و زیبایی', [
        # BEAUTY_SALON keeps its original value and label — it is what
        # every existing salon holds, and narrowing it now would
        # re-describe businesses nobody re-picked a category for.
        ('BEAUTY_SALON', 'آرایشگاه و سالن زیبایی'),
        ('WOMENS_SALON', 'سالن زیبایی زنانه'),
        ('BARBERSHOP', 'آرایشگاه مردانه'),
        ('HAIR_SALON', 'سالن مو (رنگ و کراتین)'),
        ('HAIR_TRANSPLANT', 'کاشت مو و ابرو'),
        ('MAKEUP', 'میکاپ و گریم'),
        ('BRIDAL', 'عروس و خدمات مجلسی'),
        ('NAIL_SALON', 'سالن ناخن'),
        ('EYEBROW_LASH', 'ابرو، مژه و میکروبلیدینگ'),
        ('SKIN_LASER', 'پوست و لیزر'),
        ('TATTOO', 'تتو و میکروپیگمنتیشن'),
        ('MASSAGE_SPA', 'ماساژ و اسپا'),
        ('SLIMMING', 'لاغری و تناسب اندام'),
    ]),
    ('PROFESSIONAL', 'خدمات حرفه‌ای', [
        ('CONSULTANT', 'مشاوره'),
        ('LAWYER', 'وکالت و مشاوره حقوقی'),
        ('ACCOUNTING', 'حسابداری و مالیات'),
        ('REAL_ESTATE', 'املاک و مستغلات'),
        ('INSURANCE', 'بیمه'),
        ('IMMIGRATION', 'مهاجرت و ویزا'),
    ]),
    ('EDUCATION', 'آموزش و ورزش', [
        ('TUTORING', 'تدریس و کلاس خصوصی'),
        ('LANGUAGE_SCHOOL', 'آموزشگاه زبان'),
        ('MUSIC_SCHOOL', 'آموزش موسیقی'),
        ('GYM', 'باشگاه ورزشی و مربی شخصی'),
        ('YOGA_PILATES', 'یوگا و پیلاتس'),
        ('DRIVING_SCHOOL', 'آموزشگاه رانندگی'),
    ]),
    ('TRADES', 'خدمات فنی و تعمیرات', [
        ('AUTO_SERVICE', 'تعمیرگاه و خدمات خودرو'),
        ('CAR_WASH', 'کارواش و دیتیلینگ'),
        ('HOME_SERVICE', 'خدمات و تعمیرات منزل'),
        ('DEVICE_REPAIR', 'تعمیر موبایل و لوازم برقی'),
        ('TAILORING', 'خیاطی و طراحی لباس'),
    ]),
    ('OTHER', 'سایر', [
        ('PHOTOGRAPHY', 'عکاسی و آتلیه'),
        ('EVENT_SERVICES', 'تالار و خدمات مراسم'),
        ('PET_GROOMING', 'آرایش و نگهداری حیوانات'),
        ('OTHER', 'سایر'),
    ]),
]

# Flat, in group order. Deliberately *not* Django's grouped-choices form
# (`[('گروه', [(k, v), ...])]`): several consumers iterate this as
# `for value, label in CATEGORY_CHOICES` — core/segments.py's
# BUSINESS_CATEGORY_CHOICES, the segment builder and campaign templates,
# ServiceCatalogItem.category — and grouped choices would hand them a list
# where they expect a label.
CATEGORY_CHOICES = [
    pair for _key, _label, pairs in CATEGORY_GROUPS for pair in pairs
]
