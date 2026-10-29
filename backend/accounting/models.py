from django.db import models
from django.utils import timezone
from datetime import timedelta
from api.models import User
from django.db import models
from django.contrib.auth import get_user_model

class Plan(models.Model):
    """پلن‌های اشتراک — داده‌ها از اینجا میان"""
    class Meta:
        verbose_name = 'پلن'
        verbose_name_plural = 'پلن‌ها'

    DURATION_UNIT = [
        ('day', 'روز'),
        ('month', 'ماه'),
    ]

    name = models.CharField(max_length=100, verbose_name='نام')           # نام پلن
    price = models.PositiveIntegerField(verbose_name='قیمت')              # قیمت اصلی به تومان
    discount_price = models.PositiveIntegerField(null=True, blank=True, verbose_name='قیمت با تخفیف')  # قیمت با تخفیف
    description = models.JSONField(default=list, verbose_name='توضیحات')       # لیست توضیحات پلن
    duration_value = models.PositiveIntegerField(verbose_name='مدت')     # عدد مدت
    duration_unit = models.CharField(max_length=10, choices=DURATION_UNIT, verbose_name='واحد مدت')
    is_vip = models.BooleanField(default=False, verbose_name='ویژه')        # آیا VIP میشه؟
    is_active = models.BooleanField(default=True, verbose_name='فعال')      # نمایش داده بشه؟
    # شناسه‌ی همین پلن در پنل «محصولات درون‌برنامه‌ای» مایکت (مصرف‌شدنی).
    # خالی = این پلن در نسخه‌ی مایکت قابل خرید نیست.
    myket_sku = models.CharField(max_length=100, blank=True, default='',
                                 help_text="شناسه‌ی محصول در مایکت؛ خالی یعنی در نسخه‌ی مایکت فروخته نمی‌شود", verbose_name='شناسه محصول مایکت')

    # قابلیت‌ها و سقف‌هایی که این پلن باز می‌کند (نردبان تعهد).
    # کلیدها در accounting/entitlements.py تعریف شده‌اند. مثال:
    #   {"max_businesses": 3, "monthly_appointments": -1, "online_gateway": true}
    # مقدار -1 در سقف‌ها یعنی «نامحدود». هرچه خالی بماند از مقدار پایه پر می‌شود.
    features = models.JSONField(default=dict, blank=True, verbose_name='قابلیت‌ها')

    def __str__(self):
        return f"{self.name} - {self.price:,} تومان"

    def clean(self):
        # Free plans (the trial) stay allowed; a paid one has a floor.
        from django.core.exceptions import ValidationError
        from accounting.pricing import MIN_SELLABLE_PRICE
        for field in ("price", "discount_price"):
            value = getattr(self, field)
            if value and value < MIN_SELLABLE_PRICE:
                raise ValidationError({field: f"قیمت پلن پولی نباید کمتر از {MIN_SELLABLE_PRICE:,} تومان باشد."})

    def get_end_date(self, start_date=None):
        """تاریخ پایان اشتراک رو حساب میکنه"""
        if start_date is None:
            start_date = timezone.now()

        if self.duration_unit == 'day':
            return start_date + timedelta(days=self.duration_value)
        elif self.duration_unit == 'month':
            # هر ماه = 30 روز
            return start_date + timedelta(days=self.duration_value * 30)


class Subscription(models.Model):
    """اشتراک فعال هر کاربر"""

    STATUS = [
        ('active', 'فعال'),
        ('expired', 'منقضی'),
        ('cancelled', 'لغو شده'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='subscriptions', verbose_name='کاربر')
    plan = models.ForeignKey(Plan, on_delete=models.PROTECT, verbose_name='پلن')
    status = models.CharField(max_length=20, choices=STATUS, default='active', verbose_name='وضعیت')
    started_at = models.DateTimeField(auto_now_add=True, verbose_name='شروع')
    ends_at = models.DateTimeField(verbose_name='پایان')                   # تاریخ پایان
    # آیا پیامک یادآوری تمدید برای این اشتراک ارسال شده؟ (چرخه‌ی عمر پلن)
    reminder_sent = models.BooleanField(default=False, verbose_name='یادآوری ارسال‌شده')
    # آیا بعد از پایان اشتراک، پیامک «تمدید نکرده‌اید» برایش ارسال شده؟ (ارسال دستی از پنل ادمین)
    expired_reminder_sent = models.BooleanField(default=False, verbose_name='پیام پس از انقضا ارسال‌شده')

    class Meta:
        verbose_name = 'اشتراک'
        verbose_name_plural = 'اشتراک‌ها'
        ordering = ['-started_at']
        indexes = [
            models.Index(fields=['user', 'status'], name='sub_user_status_idx'),
        ]

    def __str__(self):
        return f"{self.user.name} → {self.plan.name}"

    def is_valid(self):
        """چک میکنه اشتراک هنوز معتبره یا نه"""
        return self.status == 'active' and self.ends_at > timezone.now()

    def days_left(self):
        """تعداد روز باقی‌مانده تا انقضا (می‌تواند منفی باشد اگر گذشته باشد)."""
        delta = self.ends_at - timezone.now()
        return delta.days

class Transaction(models.Model):
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('success', 'Success'),
        ('failed', 'Failed'),
        ('cancelled', 'Cancelled'),
    ]

    user = models.ForeignKey(get_user_model(), on_delete=models.CASCADE, related_name='transactions', verbose_name='کاربر')
    plan = models.ForeignKey(Plan, on_delete=models.PROTECT, related_name='transactions', verbose_name='پلن')  # Changed from plan_id
    amount = models.PositiveIntegerField(verbose_name='مبلغ')
    track_id = models.CharField(max_length=100, unique=True, db_index=True, verbose_name='شناسه پیگیری')
    order_id = models.CharField(max_length=100, unique=True, verbose_name='شماره سفارش')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending', verbose_name='وضعیت')
    zibal_response = models.JSONField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')

    class Meta:
        verbose_name = 'تراکنش'
        verbose_name_plural = 'تراکنش‌ها'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['track_id']),
            models.Index(fields=['order_id']),
            models.Index(fields=['status']),
        ]

    def __str__(self):
        return f"Transaction {self.order_id} - {self.status}"


class AddOnPack(models.Model):
    """
    بسته‌ی افزودنی — خرید تکی روی هر پلن، بدون ارتقای کل اشتراک.
    دو نوع دارد:
      - sms_pack         : اعتبار پیامک به کیف‌پول کاربر اضافه می‌کند (sms_amount)
      - appointment_pack : اعتبار نوبت به کیف‌پول کاربر اضافه می‌کند (appointment_amount)

    نوع «feature» (قابلیت موقت) دیگر برای فروش ساخته نمی‌شود؛ فقط برای سازگاری با
    خریدهای قدیمی باقی مانده است.
    """
    class Meta:
        verbose_name = 'بسته‌ی افزودنی'
        verbose_name_plural = 'بسته‌های افزودنی'

    KIND_SMS = 'sms_pack'
    KIND_APPOINTMENT = 'appointment_pack'
    KIND_FEATURE = 'feature'
    KIND_CHOICES = [
        (KIND_SMS, 'بسته پیامک'),
        (KIND_APPOINTMENT, 'بسته نوبت'),
        (KIND_FEATURE, 'قابلیت موقت'),
    ]

    name = models.CharField(max_length=100, verbose_name='نام')
    price = models.PositiveIntegerField(help_text="قیمت به تومان", verbose_name='قیمت')
    kind = models.CharField(max_length=20, choices=KIND_CHOICES, verbose_name='نوع')
    sms_amount = models.PositiveIntegerField(default=0, help_text="تعداد پیامک برای بسته‌ی پیامکی", verbose_name='تعداد پیامک')
    appointment_amount = models.PositiveIntegerField(default=0, help_text="تعداد نوبت برای بسته‌ی نوبت", verbose_name='تعداد نوبت')
    feature_key = models.CharField(max_length=50, blank=True, default='', help_text="کلید قابلیت برای بسته‌ی قابلیتی", verbose_name='کلید قابلیت')
    duration_days = models.PositiveIntegerField(default=30, help_text="مدت اعتبار بسته‌ی قابلیتی (روز)", verbose_name='مدت (روز)')
    is_active = models.BooleanField(default=True, verbose_name='فعال')
    myket_sku = models.CharField(max_length=100, blank=True, default='',
                                 help_text="شناسه‌ی محصول در مایکت؛ خالی یعنی در نسخه‌ی مایکت فروخته نمی‌شود", verbose_name='شناسه محصول مایکت')

    def __str__(self):
        return f"{self.name} - {self.price:,} تومان"

    def clean(self):
        from django.core.exceptions import ValidationError
        from accounting.pricing import MIN_SELLABLE_PRICE
        if self.is_active and self.price < MIN_SELLABLE_PRICE:
            raise ValidationError({"price": f"قیمت بسته‌ی فعال نباید کمتر از {MIN_SELLABLE_PRICE:,} تومان باشد."})


class AddOnPurchase(models.Model):
    """خرید یک بسته‌ی افزودنی توسط کاربر (فلوی پرداخت مشابه اشتراک، از طریق زیبال)."""
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('success', 'Success'),
        ('failed', 'Failed'),
    ]

    user = models.ForeignKey(get_user_model(), on_delete=models.CASCADE, related_name='addon_purchases', verbose_name='کاربر')
    pack = models.ForeignKey(AddOnPack, on_delete=models.PROTECT, related_name='purchases', verbose_name='بسته')
    amount = models.PositiveIntegerField(verbose_name='مبلغ')
    track_id = models.CharField(max_length=100, unique=True, db_index=True, verbose_name='شناسه پیگیری')
    order_id = models.CharField(max_length=100, unique=True, verbose_name='شماره سفارش')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending', verbose_name='وضعیت')
    zibal_response = models.JSONField(null=True, blank=True)
    # برای بسته‌های قابلیتی: بازه‌ی اعتبار پس از پرداخت موفق
    activated_at = models.DateTimeField(null=True, blank=True, verbose_name='زمان فعال‌سازی')
    expires_at = models.DateTimeField(null=True, blank=True, db_index=True, verbose_name='انقضا')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')

    class Meta:
        verbose_name = 'خرید بسته'
        verbose_name_plural = 'خرید بسته‌ها'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['track_id']),
            models.Index(fields=['status']),
            models.Index(fields=['user', 'status']),
        ]

    def __str__(self):
        return f"AddOn {self.order_id} - {self.status}"


class CreditLedger(models.Model):
    """
    Append-only audit trail underneath ``accounting/usage.py``'s Redis-backed
    wallets/counters.

    Redis (via ``usage.py``) remains the *fast path* that actually gates
    booking/SMS behaviour — this table never gates anything, matching the
    fail-open philosophy of the system it backs (see ``usage.py``'s module
    docstring). What this table adds is history: Redis has no memory of how a
    balance got to its current value, and a no-TTL wallet key that is bought
    with real money has no way to be reconstructed if it is ever lost (a
    botched migration, a ``FLUSHDB``, a misconfigured replica promotion). Every
    row here is one state-changing event against one of the four buckets
    ``usage.py`` tracks, so ``rebuild_wallets_from_ledger`` can replay them
    back into Redis and an operator can answer "how did this balance get to
    zero" without guessing.

    ``metric`` covers both monthly counters (which reset every calendar month
    in Redis, but are never rewritten in the ledger — the ledger is the one
    place a past month's activity is still visible) and the two persistent,
    no-TTL wallets bought via add-on packs.

    ``delta`` / ``balance_after`` convention — read carefully, it is *not*
    "positive always means the user gained credit":

      * For the two wallet metrics (``sms_wallet`` / ``appointment_wallet``),
        the Redis key IS the balance (credit remaining), so delta and
        balance_after behave exactly as intuition suggests: positive delta =
        credit granted, negative = credit spent, balance_after = wallet
        balance after the event.

      * For the two monthly metrics (``sms_monthly`` / ``appointment_monthly``),
        the underlying Redis key is a *consumed-this-month counter*, not a
        remaining balance (see ``usage.py``'s ``get_usage``/``add_usage``). To
        keep ``balance_after`` a literal, trustworthy mirror of "what belongs
        in that Redis key right now" — which is exactly what lets
        ``rebuild_wallets_from_ledger`` restore Redis by writing the most
        recent row's ``balance_after`` straight back, with no arithmetic and
        no risk of silently drifting from cache reality — ``delta`` for these
        two metrics mirrors the counter's own movement: a booking/SMS send
        (consumption) increments the counter, so it is recorded as a
        *positive* delta; a cancellation/refund decrements it, so it is a
        *negative* delta. This is the one place "positive/negative" flips
        from the intuitive credit-granted/credit-spent reading — documented
        here so it is never mistaken for a bug.

    ``ref_type`` / ``ref_id`` are a plain, ungated pointer to whatever caused
    the event (an ``AddOnPurchase`` id, an ``Appointment`` id, a ``SmsLog`` id,
    an admin username for a manual grant) — not a real ``GenericForeignKey``,
    since nothing here ever needs to traverse back into the referenced object,
    only display "what caused this" next to the row.
    """

    METRIC_SMS_MONTHLY = 'sms_monthly'
    METRIC_SMS_WALLET = 'sms_wallet'
    METRIC_APPOINTMENT_MONTHLY = 'appointment_monthly'
    METRIC_APPOINTMENT_WALLET = 'appointment_wallet'
    METRIC_CHOICES = [
        (METRIC_SMS_MONTHLY, 'پیامک — سهمیه ماهانه'),
        (METRIC_SMS_WALLET, 'پیامک — کیف‌پول'),
        (METRIC_APPOINTMENT_MONTHLY, 'نوبت — سهمیه ماهانه'),
        (METRIC_APPOINTMENT_WALLET, 'نوبت — کیف‌پول'),
    ]

    user = models.ForeignKey(get_user_model(), on_delete=models.CASCADE, related_name='credit_ledger_entries', verbose_name='کاربر')
    metric = models.CharField(max_length=20, choices=METRIC_CHOICES, verbose_name='نوع اعتبار')
    delta = models.IntegerField(help_text="تغییر (می‌تواند منفی باشد) — به کنوانسیون در docstring مدل توجه کنید", verbose_name='تغییر')
    balance_after = models.IntegerField(help_text="مقدار دقیقی که باید بلافاصله پس از این رویداد در Redis باشد", verbose_name='مانده پس از تغییر')
    reason = models.CharField(max_length=50, help_text="کد ماشین‌خوان، مثل booking / sms_send / addon_purchase", verbose_name='دلیل')
    ref_type = models.CharField(max_length=50, blank=True, default='', verbose_name='نوع مرجع')
    ref_id = models.IntegerField(null=True, blank=True, verbose_name='شناسه مرجع')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')

    class Meta:
        verbose_name = 'ردیف دفتر اعتبار'
        verbose_name_plural = 'دفتر اعتبار'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', 'metric', 'created_at']),
            models.Index(fields=['user', 'created_at']),
        ]

    def __str__(self):
        return f"{self.user_id} {self.metric} {self.delta:+d} ({self.reason})"


class SubscriptionReminderSettings(models.Model):
    """Row ``pk=1`` and only ``pk=1`` — same singleton pattern as
    ``bale.models.BaleSettings``: an operator-tunable knob wired up from the
    admin panel on a running server, no redeploy needed.

    Read by ``run_subscription_lifecycle`` in place of the old
    ``SUBSCRIPTION_REMINDER_DAYS`` env var and hardcoded message, so staff can
    turn the reminder on/off and edit its wording without touching code.
    """

    SINGLETON_PK = 1

    is_enabled = models.BooleanField(
        default=False,
        help_text='تا وقتی خاموش است، هیچ پیامک یادآوری تمدید ارسال نمی‌شود',
        verbose_name='فعال',
    )
    days_before = models.PositiveIntegerField(
        default=3,
        help_text='چند روز قبل از پایان اشتراک پیامک ارسال شود',
        verbose_name='چند روز قبل',
    )
    message_template = models.TextField(
        default='{full_name} عزیز، {days} روز دیگر تا پایان اشتراک شما در نوبت‌یار باقی مانده است\n'
                'برای جلوگیری از قطع خدمات، همین حالا تمدید کنید',
        help_text='متن پیامک. جاهای خالی مجاز: {full_name} نام صاحب‌حساب، '
                  '{days} تعداد روز باقی‌مانده، {plan} نام پلن.',
                  verbose_name='قالب پیام',
    )
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')
    # Stamped by run_subscription_lifecycle every time it runs, on or off, so
    # the admin can tell "the switch is on but nothing is scheduling the job"
    # apart from "the job runs and simply had nobody to remind".
    last_run_at = models.DateTimeField(null=True, blank=True, verbose_name='آخرین اجرای زمان‌بند')

    class Meta:
        db_table = 'accounting_subscription_reminder_settings'
        verbose_name = 'تنظیمات یادآوری تمدید اشتراک'
        verbose_name_plural = 'تنظیمات یادآوری تمدید اشتراک'

    def __str__(self):
        return 'تنظیمات یادآوری تمدید اشتراک'

    def save(self, *args, **kwargs):
        self.pk = self.SINGLETON_PK
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):
        """Deleting the singleton is a no-op; blank/disable it instead."""
        return 0, {}

    @classmethod
    def load(cls):
        obj, _ = cls.objects.get_or_create(pk=cls.SINGLETON_PK)
        return obj

    def render_message(self, *, plan_name, days_left, owner_name=''):
        template = self.message_template or self._meta.get_field('message_template').default
        return render_reminder_text(
            template, owner_name=owner_name, days_left=days_left, plan_name=plan_name,
        )


class ContactVisibilityPolicy(models.Model):
    """What customers get to see of a business whose owner has no active
    subscription (the trial ran out, or a paid plan lapsed without renewal).

    Singleton (``pk=1``), same pattern as ``SubscriptionReminderSettings``:
    switched from the admin panel on a running server.

    With ``hide_when_expired`` on, the public business payloads carry the
    hidden fields as null — the values never leave the server — and every
    payload says ``is_expired`` so the client can tell this apart from the
    owner's own "show contact info to guests" switch (which also nulls them)
    and draw the blurred placeholder instead of hiding the section. Applied
    in ``business.serializers.apply_contact_rules``.
    """

    SINGLETON_PK = 1

    hide_when_expired = models.BooleanField(
        default=False,
        help_text='وقتی روشن است، برای کسب‌وکاری که صاحبش اشتراک فعال ندارد، موارد انتخاب‌شده‌ی زیر '
                  'اصلاً به مشتری ارسال نمی‌شوند و در صفحه‌ی کسب‌وکار به‌صورت محو و ناخوانا نمایش داده می‌شوند.',
        verbose_name='پنهان‌کردن اطلاعات تماس کسب‌وکارهای بدون اشتراک',
    )
    hide_phone = models.BooleanField(
        default=True,
        help_text='شماره‌ی اصلی و شماره‌های دیگر کسب‌وکار',
        verbose_name='شماره تلفن',
    )
    hide_address = models.BooleanField(
        default=True,
        help_text='آدرس نوشتاری و موقعیت روی نقشه (که خودش آدرس را لو می‌دهد)',
        verbose_name='آدرس و موقعیت روی نقشه',
    )
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')

    class Meta:
        db_table = 'accounting_contact_visibility_policy'
        verbose_name = 'سیاست نمایش اطلاعات تماس'
        verbose_name_plural = 'سیاست نمایش اطلاعات تماس'

    def __str__(self):
        return 'سیاست نمایش اطلاعات تماس'

    def save(self, *args, **kwargs):
        self.pk = self.SINGLETON_PK
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):
        """Deleting the singleton is a no-op; switch it off instead."""
        return 0, {}

    @classmethod
    def load(cls):
        obj, _ = cls.objects.get_or_create(pk=cls.SINGLETON_PK)
        return obj


# The tokens a renewal reminder may use, in the order the admin offers them.
REMINDER_TOKENS = (
    ('{full_name}', 'نام صاحب‌حساب'),
    ('{days}', 'روز باقی‌مانده'),
    ('{plan}', 'نام پلن'),
)


def render_reminder_text(template, *, owner_name, days_left, plan_name):
    """Fill a renewal-reminder template — the one renderer behind both the
    cron-run reminder and the manual send in the admin, so a template reads
    the same whichever way it goes out.

    Plain token replacement rather than ``str.format``: a stray brace or an
    unknown ``{token}`` stays as typed instead of raising, so one typo can
    neither crash the cron run nor silently swap in a different message.
    """
    return (
        template
        .replace('{full_name}', owner_name or '')
        .replace('{days}', str(days_left))
        .replace('{plan}', plan_name or '')
    )

class StorePurchase(models.Model):
    """One in-app purchase made through an Android store (Myket for now).

    Store builds of the owner app are not allowed to use the Zibal gateway —
    Myket requires its own in-app billing for "digital products" like our
    plans and packs. The flow is: the app asks for an *intent* (we reserve a
    row with a random ``payload``), the store's billing UI takes the money,
    the app sends back the purchase token, and the server verifies that token
    with the store's API before granting anything (see
    accounting/payment/myket.py). A token is accepted once, ever.

    The benefit itself is granted through the same code as a Zibal payment —
    a ``Transaction`` for plans, an ``AddOnPurchase`` for packs — so revenue
    reports, subscription ladders and wallets see no difference.
    """

    STORE_MYKET = 'myket'
    STORE_CHOICES = [(STORE_MYKET, 'مایکت')]
    KIND_PLAN = 'plan'
    KIND_ADDON = 'addon'
    KIND_CHOICES = [(KIND_PLAN, 'پلن'), (KIND_ADDON, 'بسته افزودنی')]
    STATUS_CHOICES = [
        ('pending', 'در انتظار پرداخت'),
        ('success', 'موفق'),
        ('failed', 'ناموفق'),
    ]

    user = models.ForeignKey(get_user_model(), on_delete=models.CASCADE, related_name='store_purchases', verbose_name='کاربر')
    store = models.CharField(max_length=20, choices=STORE_CHOICES, verbose_name='فروشگاه')
    kind = models.CharField(max_length=10, choices=KIND_CHOICES, verbose_name='نوع')
    plan = models.ForeignKey(Plan, on_delete=models.PROTECT, null=True, blank=True, related_name='store_purchases', verbose_name='پلن')
    pack = models.ForeignKey('AddOnPack', on_delete=models.PROTECT, null=True, blank=True, related_name='store_purchases', verbose_name='بسته')
    sku = models.CharField(max_length=100, verbose_name='شناسه محصول')
    payload = models.CharField(max_length=64, unique=True, verbose_name='داده‌ها')
    purchase_token = models.CharField(max_length=255, null=True, blank=True, unique=True, verbose_name='توکن خرید')
    order_id = models.CharField(max_length=255, blank=True, default='', verbose_name='شماره سفارش')
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default='pending', db_index=True, verbose_name='وضعیت')
    store_response = models.JSONField(null=True, blank=True, verbose_name='پاسخ فروشگاه')
    error_detail = models.TextField(blank=True, default='', verbose_name='جزئیات خطا')
    consumed_at = models.DateTimeField(null=True, blank=True, verbose_name='زمان مصرف')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')
    updated_at = models.DateTimeField(auto_now=True, verbose_name='آخرین تغییر')

    class Meta:
        verbose_name = 'خرید مایکت'
        verbose_name_plural = 'خریدهای مایکت'
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.get_store_display()} · {self.sku} · {self.status}"
