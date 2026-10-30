from django.core.exceptions import ValidationError
from django.core.validators import MaxValueValidator, MinValueValidator, RegexValidator
from django.db import models
from django.contrib.auth import get_user_model
from django.utils.crypto import get_random_string

from .. import reminder_templates
from ..categories import CATEGORY_CHOICES, CATEGORY_GROUPS

User = get_user_model()


# How long a business description may be. Enforced by the serializer rather
# than the column (see Business.bio), so raising it is a one-line change with
# no migration. The owner app shows a counter against this same number, and
# the client web's "درباره" card is built to clamp-and-expand whatever fits
# under it — both were sized from this value, so move it deliberately.
BIO_MAX_LENGTH = 1500


class Insurance(models.Model):
    """
    Represents an insurance provider (basic or supplementary) accepted by businesses.
    """
    INSURANCE_TYPE_CHOICES = [
        ('SUPPLEMENTARY', 'بیمه تکمیلی'),
        ('BASIC', 'بیمه پایه'),
    ]

    name = models.CharField(max_length=100, unique=True)
    slug = models.SlugField(max_length=50, unique=True, db_index=True)
    insurance_type = models.CharField(
        max_length=20,
        choices=INSURANCE_TYPE_CHOICES,
        default='SUPPLEMENTARY',
    )
    is_active = models.BooleanField(default=True)
    display_order = models.PositiveSmallIntegerField(default=100)

    class Meta:
        db_table = 'insurance'
        ordering = ['display_order', 'name']

    def __str__(self):
        return self.name


class Business(models.Model):
    """
    Represents a business profile for appointment management.
    Each user can have multiple businesses.
    """
    # The vocabulary lives in business/categories.py; kept as class attributes
    # so `Business.CATEGORY_GROUPS` / `Business.CATEGORY_CHOICES` keep working.
    CATEGORY_GROUPS = CATEGORY_GROUPS
    CATEGORY_CHOICES = CATEGORY_CHOICES

    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='businesses',
        help_text='مالک این کسب‌وکار',
        verbose_name='کاربر',
    )
    title = models.CharField(max_length=255, verbose_name='عنوان')
    category = models.CharField(max_length=50, choices=CATEGORY_CHOICES, default='OTHER', help_text='دسته‌بندی کسب‌وکار', verbose_name='دسته‌بندی')
    # The public identifier: /b/Noobatyar-<unique_code> resolves to this row.
    # Left blank it is generated as CODE_LENGTH random characters, but the field
    # is deliberately writable from the admin so a business can be given a
    # vanity code. max_length is only the ceiling for those hand-written codes —
    # the generator always produces CODE_LENGTH characters, so every business
    # that already exists keeps the 8-character code it was created with.
    # Charset is restricted to what is safe in a URL path segment; every code
    # generated so far is a strict subset of it, so no existing row is affected.
    CODE_LENGTH = 8
    CODE_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
    CODE_MAX_LENGTH = 64

    unique_code = models.CharField(
        max_length=CODE_MAX_LENGTH,
        unique=True,
        db_index=True,
        blank=True,
        validators=[RegexValidator(
            regex=r'^[A-Za-z0-9_-]+$',
            message='کد فقط می‌تواند شامل حروف انگلیسی، رقم، خط تیره (-) و زیرخط (_) باشد.',
        )],
        help_text=(
            'کد یکتای عمومی کسب‌وکار؛ همان چیزی که در آدرس صفحهٔ رزرو می‌آید '
            '(‏/b/Noobatyar-<کد>). اگر خالی بماند یک کد ۸ کاراکتری خودکار ساخته '
            'می‌شود. برای کد دلخواه تا ۶۴ کاراکتر مجاز است. توجه: تغییر کد، '
            'لینک‌های قبلیِ همین کسب‌وکار را از کار می‌اندازد.'
        ),
        verbose_name='کد یکتا',
    )
    phone = models.CharField(max_length=20, verbose_name='تلفن')
    # Additional contact numbers, beyond the primary `phone`. Kept separate
    # rather than turning `phone` itself into a list: `phone` is read as a
    # single string in far too many places (SMS delivery, the moderation
    # snapshot, every serializer, front_client) to safely widen its type, and
    # every one of those call sites' "the" contact number is still correct
    # once there is more than one — it is simply no longer the *only* one.
    extra_phones = models.JSONField(
        default=list, blank=True,
        help_text='شماره‌های تماس دیگر، با همان قالب «تلفن»',
        verbose_name='شماره‌های دیگر',
    )
    address = models.TextField(verbose_name='نشانی')
    # Where the business is on the map — picked by the owner in the app, shown
    # to clients on the booking page with "route there" links to Neshan and
    # Balad. Both or neither (BusinessSerializer.validate). Not moderated like
    # `address`: it is a point, not public wording, and an owner fixing a
    # wrong pin should not have to wait for review.
    latitude = models.FloatField(
        null=True, blank=True,
        validators=[MinValueValidator(-90), MaxValueValidator(90)],
        verbose_name='عرض جغرافیایی',
    )
    longitude = models.FloatField(
        null=True, blank=True,
        validators=[MinValueValidator(-180), MaxValueValidator(180)],
        verbose_name='طول جغرافیایی',
    )
    province = models.CharField(max_length=100, blank=True, default='', db_index=True, help_text="استان")
    city = models.CharField(max_length=100, blank=True, default='', db_index=True, help_text="شهر")
    neighborhood = models.CharField(max_length=100, blank=True, default='', help_text="محله / منطقه")
    # Divar's city id (business/iran_cities.py). Setting it fills `city` and
    # `province` from the list; the client app filters by it.
    city_code = models.PositiveIntegerField(null=True, blank=True, db_index=True, verbose_name='کد شهر')
    is_claimed = models.BooleanField(default=True, db_index=True, help_text="True if registered/claimed by an owner on Noobatyar; False if imported directory listing.")
    external_source = models.CharField(max_length=100, blank=True, default='', help_text="Source dataset identifier for imported listings")
    # Denormalised review summary (reviews app keeps it in step, see
    # reviews/services.py): lets the public list/detail show the rating with no
    # extra query and lets the nightly rank job read it cheaply.
    rating_avg = models.FloatField(default=0, verbose_name='میانگین امتیاز')
    rating_count = models.PositiveIntegerField(default=0, verbose_name='تعداد نظرات')
    rating_histogram = models.JSONField(default=dict, blank=True, verbose_name='توزیع امتیازها')
    online_booking_requests_count = models.PositiveIntegerField(default=0, help_text="Number of client requests to enable online booking for an unclaimed business")
    insurances = models.ManyToManyField('Insurance', blank=True, related_name='businesses', help_text="Supported basic and supplementary insurances")
    # TextField, not CharField, because this cap has now moved twice (50 → 300
    # → 1500) and every move cost a migration for a value that is really a
    # product decision. The column is now unbounded and BIO_MAX_LENGTH is
    # enforced in the serializer instead, so the next change is a one-line edit
    # — the clients (owner app's input counter, client web's "درباره" card)
    # read the same number.
    bio = models.TextField(blank=True, null=True, verbose_name='درباره')
    logo = models.ImageField(upload_to='business_logos/', blank=True, null=True, help_text='تصویر لوگوی کسب‌وکار', verbose_name='لوگو')
    # Header colour derived from `logo` at save time — see business/theme_color.py
    # for why this is computed here rather than sampled in the browser. Blank
    # means "never computed"; the clients fall back to the brand purple.
    theme_color = models.CharField(
        max_length=7, blank=True, default='',
        help_text='رنگ هگز برگرفته از لوگو، برای پس‌زمینه‌ی سربرگ صفحه‌ی عمومی',
        verbose_name='رنگ قالب',
    )
    default_service_duration = models.IntegerField(help_text='مدت پیش‌فرض به دقیقه', verbose_name='مدت پیش‌فرض خدمت (دقیقه)')
    work_start_hour = models.IntegerField(help_text="0-23", verbose_name='ساعت شروع کار')
    work_end_hour = models.IntegerField(help_text="0-23", verbose_name='ساعت پایان کار')
    # Minute component of work_start_hour/work_end_hour, e.g. 19:30 is
    # work_end_hour=19, work_end_minute=30. Separate fields rather than
    # folding into a single "minutes since midnight" column: work_start_hour/
    # work_end_hour are read as whole hours in several places already
    # (validation, display), and every existing business's data stays valid
    # and correct with minute=0 — no data migration needed for the ~14
    # businesses that predate this field.
    work_start_minute = models.IntegerField(default=0, help_text="0-59", verbose_name='دقیقه‌ی شروع کار')
    work_end_minute = models.IntegerField(default=0, help_text="0-59", verbose_name='دقیقه‌ی پایان کار')
    # Weekly recurring days off (e.g. every Friday) — NOT specific calendar
    # dates. Stored as Python's date.weekday() ints (Monday=0 .. Sunday=6) so
    # backend code can test membership with `date.weekday() in closed_weekdays`
    # directly, with no conversion table. An empty list means "open every day",
    # which is also the state a business is in before ever touching this
    # setting — there is deliberately no separate "no day off" flag, since that
    # would just be a second way to express the same thing and could drift out
    # of sync with an empty list.
    #
    # Setting this never touches existing Appointment rows: it only removes
    # future slots for that weekday going forward (available_slots_view.py).
    # An appointment already booked on a day later marked closed is left
    # exactly as it was — the owner decided to close going forward, not to
    # cancel a commitment already made.
    closed_weekdays = models.JSONField(
        default=list, blank=True,
        help_text='روزهای تعطیل هفته به‌صورت عدد (دوشنبه=۰ … یکشنبه=۶)، مثلاً [4] برای جمعه',
        verbose_name='روزهای تعطیل',
    )
    notification_enabled = models.BooleanField(default=True, verbose_name='اعلان فعال')
    notification_types = models.CharField(
        max_length=100,
        default='SMS',
        help_text='با ویرگول جدا کنید: SMS,WHATSAPP,TELEGRAM',
        verbose_name='انواع اعلان',
    )
    notification_minutes_before = models.IntegerField(default=30, verbose_name='یادآوری (دقیقه قبل)')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')
    allow_anonymous_view = models.BooleanField(
        default=True,
        help_text='اگر روشن باشد، بازدیدکننده‌ها اطلاعات تماس (تلفن، نشانی و …) را می‌بینند',
        verbose_name='نمایش عمومی',
    )

    # ── Subscription-driven lock (graceful downgrade) ──────────────────────
    # When a subscription expires or is downgraded below the number of
    # businesses the user owns, excess businesses are locked (read-only, hidden
    # from public booking) instead of deleted. Renewing/upgrading unlocks them.
    is_locked = models.BooleanField(
        default=False,
        db_index=True,
        help_text='اگر روشن باشد، کسب‌وکار به‌دلیل محدودیت اشتراک قفل است (داده‌ها حفظ می‌شوند ولی پنهان/فقط‌خواندنی‌اند).',
        verbose_name='قفل‌شده',
    )

    # ── Client-facing notice & booking control ────────────────────────────
    # The notice ("پیام اضطراری") and the booking switch are deliberately
    # independent: the common case is a shop that is still taking bookings but
    # needs to warn today's clients about something ("آب قطع است، با تاخیر
    # بیایید"). Tying the notice to booking_enabled would have forced the owner
    # to close their calendar just to say a sentence.
    notice_enabled = models.BooleanField(
        default=False,
        help_text='اگر روشن باشد، پیام اضطراری در صفحه‌ی نوبت‌دهی نشان داده می‌شود',
        verbose_name='پیام اضطراری فعال',
    )
    # CharField, not TextField: this is rendered inside a fixed banner on the
    # booking page and an unbounded text field let an owner paste an essay that
    # pushed the booking button off-screen. 300 chars is enforced here and again
    # at the serializer layer so every write path (owner app, admin) is capped.
    notice_message = models.CharField(
        max_length=300,
        blank=True,
        default='',
        help_text='پیام کوتاهی که در صفحه‌ی نوبت‌دهی به مشتری نشان داده می‌شود (حداکثر ۳۰۰ نویسه)',
        verbose_name='متن پیام اضطراری',
    )
    booking_enabled = models.BooleanField(
        default=True,
        help_text='اگر خاموش باشد، مشتری نمی‌تواند نوبت جدید بگیرد',
        verbose_name='نوبت‌دهی فعال',
    )

    # ── Service menu ──────────────────────────────────────────────────────
    # The list of services THIS business actually offers ("لیست خدمات من"),
    # picked by the owner in the business-definition screen from the
    # category-wide ServiceCatalogItem chips (plus anything they add).
    #
    # Deliberately a JSON list of names on the business rather than a
    # many-to-many to ServiceCatalogItem: the catalog is a shared vocabulary,
    # not an inventory. What matters downstream (appointment.selected_services)
    # is the *name*, and copying it here means an owner's menu is unaffected
    # when some other business in the category renames or adds a chip.
    #
    # Read by the public booking page too: a client picking "رنگ مو" from the
    # owner's own menu is the whole point — it turns an unparseable free-text
    # note into something the owner can plan the slot length around.
    services = models.JSONField(
        default=list,
        blank=True,
        help_text="خدماتی که این کسب‌وکار ارائه می‌دهد، مثلاً ['کوتاهی مو', 'رنگ مو']",
        verbose_name='خدمات',
    )
    # Off by default: the owner's menu is what makes a client's answer usable,
    # and a free "+" for clients quietly reintroduces the free-text mess this
    # feature exists to remove. Owners who genuinely take custom requests can
    # turn it on. A name a client adds this way is stored on that appointment
    # only — it never edits the owner's menu.
    allow_client_add_service = models.BooleanField(
        default=False,
        help_text='اگر روشن باشد، مشتری می‌تواند خدمتی خارج از فهرست کسب‌وکار اضافه کند',
        verbose_name='امکان افزودن خدمت توسط مشتری',
    )

    # ── Payment configuration ─────────────────────────────────────────────
    PAYMENT_METHOD_CHOICES = [
        ('NONE',    'رایگان / بدون پیش‌پرداخت'),
        ('CARD',    'کارت به کارت'),
        ('GATEWAY', 'درگاه آنلاین (زیبال)'),
    ]
    payment_method = models.CharField(
        max_length=10,
        choices=PAYMENT_METHOD_CHOICES,
        default='NONE',
        help_text='روش پرداخت مشتری هنگام رزرو (قدیمی)',
        verbose_name='روش پرداخت',
    )
    def _default_payment_methods():
        # Pay-at-location costs the owner nothing to offer and needs no setup
        # (no card number, no merchant id), so it is the one method every
        # business can accept from the moment it exists. An empty default here
        # left every business with zero working payment methods until the owner
        # found the advanced-settings screen and turned one on by hand —
        # checkout would offer nothing to a client at all.
        return ['CASH']

    accepted_payment_methods = models.JSONField(
        default=_default_payment_methods,
        blank=True,
        help_text="فهرست روش‌های پرداخت پذیرفته، مثلاً ['ONLINE', 'CARD', 'CASH']",
        verbose_name='روش‌های پرداخت پذیرفته',
    )

    # ── Advanced Capacity & Deposit Settings ───────────────────────────────
    max_appointments_per_hour = models.IntegerField(
        null=True,
        blank=True,
        help_text='حداکثر نوبت هم‌زمان در هر ساعت؛ خالی یعنی نامحدود.',
        verbose_name='حداکثر نوبت در ساعت',
    )
    
    DEPOSIT_MODE_CHOICES = [
        ('NONE', 'بدون بیعانه'),
        ('MANDATORY', 'بیعانه اجباری'),
        ('OPTIONAL', 'بیعانه اختیاری'),
    ]
    deposit_mode = models.CharField(
        max_length=20,
        choices=DEPOSIT_MODE_CHOICES,
        default='NONE',
        help_text='الزام دریافت بیعانه',
        verbose_name='حالت بیعانه',
    )
    deposit_amount = models.PositiveIntegerField(
        default=0,
        help_text='مبلغ بیعانه به تومان',
        verbose_name='مبلغ بیعانه',
    )

    merchant_id = models.CharField(
        max_length=100,
        blank=True,
        default='',
        help_text='شناسه پذیرنده زیبال — برای پرداخت از درگاه لازم است',
        verbose_name='شناسه پذیرنده زیبال',
    )
    payment_link = models.URLField(
        blank=True,
        default='',
        help_text='لینک مستقیم پرداخت (مثلاً zarinpal.com/pay/...)',
        verbose_name='لینک پرداخت',
    )
    card_number = models.CharField(
        max_length=19,
        blank=True,
        default='',
        help_text='شماره کارت مالک که به مشتری نشان داده می‌شود — برای پرداخت کارت‌به‌کارت لازم است',
        verbose_name='شماره کارت',
    )
    card_owner_name = models.CharField(
        max_length=100,
        blank=True,
        default='',
        help_text='نام روی کارت، کنار شماره کارت نمایش داده می‌شود',
        verbose_name='نام صاحب کارت',
    )

    # ── SMS preferences ───────────────────────────────────────────────────
    enable_reminder_sms = models.BooleanField(
        default=True,
        help_text='ارسال پیامک یادآوری نوبت به مشتریان',
        verbose_name='پیامک یادآوری فعال',
    )
    enable_promotional_sms = models.BooleanField(
        default=False,
        help_text='اجازه‌ی ارسال پیامک تبلیغاتی به مشتریان',
        verbose_name='پیامک تبلیغاتی فعال',
    )
    notify_owner_by_sms = models.BooleanField(
        default=False,
        help_text=(
            "Text the owner when a client books. Off by default: the owner is "
            "meant to learn about a booking from the app's own notification, "
            "not from an SMS billed to their own quota. Left switchable for "
            "owners who explicitly want to pay for it."
        ),
        verbose_name='پیامک به مالک',
    )

    # ── Reminder delivery channel ─────────────────────────────────────────
    # Two very different cost models, so this is a server-side setting rather
    # than a client preference: MANUAL costs nothing (the owner app opens the
    # phone's own SMS composer and the message leaves the owner's SIM), while
    # PANEL bills every reminder to the owner's plan quota through Melipayamak.
    # PANEL is therefore gated behind FEATURE_AUTO_REMINDER_SMS
    # (accounting/permissions.py) and MANUAL is the default so that nobody is
    # silently charged. The reminder cron only ever looks at PANEL businesses;
    # MANUAL ones are driven entirely from the owner app.
    REMINDER_DELIVERY_CHOICES = [
        ('MANUAL', 'ارسال دستی از سیم‌کارت اونر'),
        ('PANEL', 'ارسال خودکار از پنل پیامکی'),
    ]
    reminder_delivery = models.CharField(
        max_length=10,
        choices=REMINDER_DELIVERY_CHOICES,
        default='MANUAL',
        help_text='روش رسیدن یادآوری به مشتری: سیم‌کارت مالک (رایگان) یا پنل پیامکی (پولی)',
        verbose_name='روش ارسال یادآوری',
    )
    # Which fixed wording a PANEL reminder uses — see business/reminder_templates.py
    # for why the owner picks one instead of writing their own.
    reminder_template = models.CharField(
        max_length=20,
        choices=reminder_templates.CHOICES,
        default=reminder_templates.DEFAULT_TEMPLATE,
        help_text='متن پیامک یادآوری پنل (فقط قالب‌های ثابت)',
        verbose_name='قالب یادآوری',
    )

    # ── Content moderation ────────────────────────────────────────────────
    # Deliberately separate from `is_locked`. That flag is *billing* state:
    # sync_locks() drives it from the owner's plan quota. This is *editorial*
    # state: whether a human reviewer has cleared the business to appear in
    # public listings. A business can be perfectly paid-up and still not
    # approved, or approved and locked for non-payment — collapsing the two
    # into one flag would let a plan renewal silently republish content a
    # moderator rejected.
    MODERATION_PENDING = 'PENDING'
    MODERATION_APPROVED = 'APPROVED'
    MODERATION_REJECTED = 'REJECTED'
    MODERATION_SUSPENDED = 'SUSPENDED'
    MODERATION_STATUS_CHOICES = [
        (MODERATION_PENDING, 'در انتظار بررسی'),
        (MODERATION_APPROVED, 'تأیید شده'),
        (MODERATION_REJECTED, 'رد شده'),
        (MODERATION_SUSPENDED, 'معلق شده'),
    ]

    moderation_status = models.CharField(
        max_length=20,
        choices=MODERATION_STATUS_CHOICES,
        default=MODERATION_PENDING,
        db_index=True,
        help_text='وضعیت بررسی محتوا؛ فقط «تأیید شده» عمومی نمایش داده می‌شود.',
        verbose_name='وضعیت بررسی',
    )
    moderation_note = models.TextField(
        blank=True,
        default='',
        help_text='دلیل رد یا تعلیق از طرف بررسی‌کننده؛ به مالک نشان داده می‌شود.',
        verbose_name='یادداشت بررسی',
    )
    moderation_reviewed_by = models.ForeignKey(
        User,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='moderated_businesses',
        help_text='آخرین کارمندی که درباره‌ی وضعیت این کسب‌وکار تصمیم گرفته.',
        verbose_name='بررسی‌کننده',
    )
    moderation_reviewed_at = models.DateTimeField(null=True, blank=True, verbose_name='زمان بررسی')
    moderation_submitted_at = models.DateTimeField(
        null=True,
        blank=True,
        help_text="آخرین زمان ورود این کسب‌وکار به صف بررسی؛ ترتیب صف بر همین "
                  "اساس است (قدیمی‌ترین اول).",
                  verbose_name='زمان ارسال برای بررسی',
    )
    # Set once, the first time this business is ever approved, and never
    # cleared afterwards. Distinguishes "still waiting for its first review"
    # (nothing publicly true yet — stay hidden) from "already live, an edit
    # just sent it back for re-review" (the old, approved copy is still true
    # and should keep showing — see pending_* below).
    first_approved_at = models.DateTimeField(null=True, blank=True, verbose_name='اولین تأیید')

    # ── Staged edits, pending re-review ─────────────────────────────────────
    # Once a business has been approved at least once, an edit to a
    # MODERATED_FIELDS column is written here instead of onto the live column,
    # so the public booking page keeps showing the last-approved copy while the
    # edit is under review — instead of the business vanishing until a
    # moderator gets to it. A moderator's APPROVED decision (moderation.py)
    # copies these onto the live fields and clears them; a REJECTED decision
    # leaves them so the owner's next edit still has something to start from.
    pending_title = models.CharField(max_length=255, null=True, blank=True, verbose_name='عنوان در انتظار بررسی')
    # Mirrors `bio`'s type exactly: a moderated edit holds the same text, so a
    # tighter column here would silently truncate an edit that was valid when
    # the owner submitted it, and only for businesses that happen to be under
    # review.
    pending_bio = models.TextField(null=True, blank=True, verbose_name='درباره‌ی در انتظار بررسی')
    pending_address = models.TextField(null=True, blank=True, verbose_name='نشانی در انتظار بررسی')
    pending_logo = models.ImageField(upload_to='business_logos/', null=True, blank=True, verbose_name='لوگوی در انتظار بررسی')

    class Meta:
        verbose_name = 'کسب‌وکار'
        verbose_name_plural = 'کسب‌وکارها'
        db_table = 'business'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', '-created_at']),
            # Serves the public listing gate (is_locked + moderation_status)
            # and the moderation queue's "oldest pending first" ordering.
            models.Index(
                fields=['moderation_status', 'moderation_submitted_at'],
                name='biz_moderation_queue_idx',
            ),
        ]

    # Editing any of these changes what the public actually sees, so a change
    # after approval has to go back through review — otherwise an owner can be
    # approved with clean copy and then swap in anything they like.
    #
    # notice_message is deliberately NOT here: it's a time-sensitive, owner-
    # controlled announcement ("closed today"). Gating it on moderation took
    # the entire business offline while the notice sat in the review queue,
    # defeating the point of an emergency notice. It's still shown to
    # moderators via moderated_texts() so abuse is still visible, it just no
    # longer flips moderation_status or public visibility.
    MODERATED_FIELDS = ('title', 'bio', 'address', 'logo')

    @property
    def is_publicly_visible(self):
        """The single gate every public/client-facing query must pass through.

        Two independent reasons a business can be hidden — unpaid (`is_locked`)
        and not editorially cleared. The second one is not simply "APPROVED":
        a business that cleared review once and is now PENDING again because
        an edit staged a pending draft (see pending_* fields) is still showing
        its last-approved, live column values — those were true and cleared,
        so it stays visible. Only a business that has never once been approved
        (first_approved_at is still null) has nothing publicly true to show,
        so PENDING there means hidden. REJECTED/SUSPENDED are always hidden —
        those are real editorial/enforcement decisions, not just an edit
        waiting in the queue.
        """
        if self.is_locked:
            return False
        if self.moderation_status == self.MODERATION_APPROVED:
            return True
        return self.moderation_status == self.MODERATION_PENDING and self.first_approved_at is not None

    @staticmethod
    def public_filter():
        """Queryset equivalent of :attr:`is_publicly_visible`.

        Use as ``Business.objects.filter(Business.public_filter())`` so the
        listing and detail paths can never drift apart.
        """
        from django.db.models import Q
        return Q(is_locked=False) & (
            Q(moderation_status=Business.MODERATION_APPROVED)
            | Q(moderation_status=Business.MODERATION_PENDING, first_approved_at__isnull=False)
        )

    def clean(self):
        """Validate a hand-written `unique_code` before it can reach the table.

        `unique=True` is case-sensitive in PostgreSQL, but the public lookup is
        not (client_views searches `unique_code__iexact`), so "SalonA" and
        "salona" would both answer to the same URL. Reject that collision here
        rather than letting the admin create a pair that can never be told
        apart. Runs on the admin form (full_clean); the auto-generated path in
        save() does the same check against the same casing rule.
        """
        super().clean()
        code = (self.unique_code or '').strip()
        self.unique_code = code
        if not code:
            # Blank is legal — save() fills it in.
            return
        clash = Business.objects.filter(unique_code__iexact=code)
        if self.pk:
            clash = clash.exclude(pk=self.pk)
        if clash.exists():
            raise ValidationError({
                'unique_code': 'کسب‌وکار دیگری با همین کد (بدون در نظر گرفتن بزرگی و کوچکی حروف) وجود دارد.',
            })

    def save(self, *args, **kwargs):
        # Recompute only when the logo actually changed. Doing it on every save
        # would decode an image on every unrelated field edit — and a booking
        # toggle should not pay for image processing.
        update_fields = kwargs.get('update_fields')
        # A save restricted to columns other than `logo` cannot have changed it, so
        # skip the comparison SELECT altogether.
        if (update_fields is None or 'logo' in update_fields) and self._logo_changed():
            from ..theme_color import color_from_logo
            self.theme_color = color_from_logo(self.logo)
            # apply_decision() (moderation.py) promotes pending_logo onto logo
            # and saves with an explicit update_fields list built before this
            # method runs, so it has no way to know theme_color needs to be in
            # it too. With update_fields passed, Django's UPDATE only SETs the
            # named columns — recomputing self.theme_color above changes the
            # Python attribute but not what gets written, so the edit that
            # changed the photo silently kept the old header colour in the DB.
            # Add it here, the one place that knows the recompute happened,
            # rather than requiring every caller to remember to list it.
            if update_fields is not None and 'theme_color' not in update_fields:
                kwargs['update_fields'] = [*update_fields, 'theme_color']

        if not self.unique_code:
            # Generate a random CODE_LENGTH-character code of uppercase letters
            # and digits. Compared case-insensitively so a generated code can
            # never shadow a custom one that differs only in case.
            while True:
                code = get_random_string(
                    length=self.CODE_LENGTH, allowed_chars=self.CODE_ALPHABET,
                )
                if not Business.objects.filter(unique_code__iexact=code).exists():
                    self.unique_code = code
                    break
        super().save(*args, **kwargs)

    def _logo_changed(self) -> bool:
        """True when `logo` differs from what is stored (or this is a create).

        Compares against the DB rather than tracking state in __init__: the
        moderation flow copies `pending_logo` onto `logo` and saves, which an
        __init__ snapshot taken before that assignment would miss.
        """
        if not self.pk:
            return bool(self.logo)
        try:
            stored = Business.objects.only('logo', 'theme_color').get(pk=self.pk)
        except Business.DoesNotExist:
            return bool(self.logo)
        if stored.logo != self.logo:
            return True
        # Recompute whenever a logo exists but the colour is missing — on the
        # instance *or* in the DB. Checking only the stored value meant
        # clearing the field (a backfill script, or an admin blanking it to
        # force a refresh) saved the blank straight back instead of
        # regenerating, which is the opposite of what clearing it means.
        return bool(self.logo) and not (self.theme_color and stored.theme_color)

    def __str__(self):
        return f"{self.title} ({self.user.phone})"
