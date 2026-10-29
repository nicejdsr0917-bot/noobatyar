from concurrent.futures import ThreadPoolExecutor
from django.core.exceptions import SynchronousOnlyOperation
from rest_framework import serializers
from rest_framework.exceptions import ValidationError

from api.phone import is_iran_phone, normalize_phone
from visitor.models import PushLog, SmsLog

from .iran_cities import get_city
from .models import BIO_MAX_LENGTH, Business, Insurance, ServiceCatalogItem

# Shown to clients when the owner still has booking_enabled=True but their plan
# quota / subscription can no longer pay for a new appointment.
BOOKING_BLOCKED_NOTICE = "پذیرش نوبت جدید برای این کسب‌وکار موقتاً غیرفعال است."


class InsuranceSerializer(serializers.ModelSerializer):
    class Meta:
        model = Insurance
        fields = ['id', 'name', 'slug', 'insurance_type']

# Caps on Business.services. The menu is a picker on a phone screen, not a
# catalogue: past a few dozen chips it stops being usable for the client it
# exists to help, and the field is stored as JSON on the business row.
MAX_SERVICE_NAME_LENGTH = 100
MAX_SERVICES_PER_BUSINESS = 60


def normalize_service_names(value):
    """Clean a list of service names into the canonical form we store.

    Trims, drops blanks, and de-duplicates while preserving the owner's
    ordering — the same normalisation has to run on the owner's menu and on the
    ``selected_services`` a client sends back, or a name would fail to match
    itself over a stray space.

    Raises ``ValidationError`` (Persian, client-facing) on anything unusable.
    """
    if value is None:
        return []
    if isinstance(value, str):
        # Multipart/form posts send a comma-separated string; JSON sends a list.
        value = [part for part in value.split(',')]
    if not isinstance(value, list):
        raise serializers.ValidationError("لیست خدمات باید یک آرایه باشد")

    cleaned = []
    for item in value:
        if not isinstance(item, str):
            raise serializers.ValidationError("نام خدمت باید متن باشد")
        # Commas are the separator in Appointment.selected_services, so a name
        # containing one would come back as two services. Folded to a space
        # rather than rejected — the owner typed a fine name, just not one this
        # storage format can round-trip.
        name = item.replace(',', ' ').strip()
        if not name:
            continue
        if len(name) > MAX_SERVICE_NAME_LENGTH:
            raise serializers.ValidationError(
                "نام خدمت نباید بیشتر از ۱۰۰ کاراکتر باشد"
            )
        if name not in cleaned:
            cleaned.append(name)

    if len(cleaned) > MAX_SERVICES_PER_BUSINESS:
        raise serializers.ValidationError("حداکثر ۶۰ خدمت می‌توانید ثبت کنید")
    return cleaned


def _mask_phone(data):
    data['phone'] = None
    data['extra_phones'] = []


def _mask_address(data):
    # The pin goes with the address: a map pin *is* the address.
    data['address'] = None
    data['latitude'] = None
    data['longitude'] = None


def apply_contact_rules(data, instance, context):
    """Withhold a business's contact details from a customer-facing payload.

    Two independent reasons, and the payload keeps them apart so a client
    never has to guess which one applied:

    1. The owner's own switch: ``allow_anonymous_view`` off hides phone and
       address from guests (signed-in customers still see them).
    2. The admin policy (``accounting.ContactVisibilityPolicy``): an owner
       without an active subscription has the chosen fields withheld from
       everyone.

    Withheld fields go out as null — the values never leave the server, so
    a "blur" on the client can't be undone from devtools. ``is_expired`` is
    always present: with it a client can show the subscription case as a
    blurred placeholder while still simply leaving out what the owner chose
    to keep private.

    ``has_location`` is always present too, and says only whether a map pin
    exists — never where. The pin is optional, so once it is withheld a null
    latitude can't tell "hidden" from "never set"; without this the client
    would draw a blurred map for every expired business, and the map would
    vanish on renewal for the ones that never had a pin.
    """
    data['has_location'] = instance.latitude is not None and instance.longitude is not None

    request = context.get('request')
    if (not request or not request.user or request.user.is_anonymous) \
            and not instance.allow_anonymous_view:
        _mask_phone(data)
        _mask_address(data)

    from accounting.entitlements import get_active_subscription
    from accounting.models import ContactVisibilityPolicy

    # An imported directory listing (is_claimed=False) belongs to the system
    # user, who never has a subscription: it is public directory data, not a
    # lapsed customer, so the subscription policy does not apply to it.
    is_expired = instance.is_claimed and get_active_subscription(instance.user_id) is None
    data['is_expired'] = is_expired
    if is_expired:
        # Once per response, not once per business in a listing: a
        # ListSerializer's children share the root's context dict.
        policy = context.get('_contact_policy')
        if policy is None:
            policy = context['_contact_policy'] = ContactVisibilityPolicy.load()
        if policy.hide_when_expired:
            if policy.hide_phone:
                _mask_phone(data)
            if policy.hide_address:
                _mask_address(data)
    return data


def apply_notice_rules(data, instance):
    """Normalise the client-facing notice + booking flags on an outgoing payload.

    Two rules, both about never leaking state the client should not act on:

    1. ``notice_enabled=False`` ⇒ ``notice_message`` is returned as an empty
       string, never the stored text. An owner who switches the notice off
       expects it to disappear; keeping the text in the payload meant a client
       that rendered the message without also checking the flag (and every
       client did, before the flag existed) kept showing a stale "امروز تعطیلیم"
       days later.
    2. The subscription-driven booking block still fills a default notice, but
       it now also turns ``notice_enabled`` on — the payload has to stay
       self-consistent with rule 1, otherwise the one notice we actually need
       the client to display would be the one it is told to hide.

    Note that the two are independent in the other direction on purpose: an
    owner posting a notice does not close their calendar.
    """
    if not getattr(instance, 'is_claimed', True):
        data['booking_enabled'] = False

    if not getattr(instance, 'notice_enabled', False):
        data['notice_message'] = ''
        if 'notice_enabled' in data:
            data['notice_enabled'] = False

    from accounting.usage import can_book_appointment
    if data.get('booking_enabled') and not can_book_appointment(instance.user_id):
        data['booking_enabled'] = False
        if not data.get('notice_message'):
            data['notice_message'] = BOOKING_BLOCKED_NOTICE
        if 'notice_enabled' in data:
            data['notice_enabled'] = True

    return data


class BusinessSerializer(serializers.ModelSerializer):
    """
    Full serializer for the Business OWNER.
    Includes ALL fields, including sensitive payment and SMS config.
    NEVER expose this to unauthenticated clients.
    """

    # The owner has to be able to see *why* their business is invisible. Without
    # this the moderation layer is silent from the app's side: a new business
    # sits at PENDING and simply never appears anywhere, with nothing in the API
    # to explain it. The Persian label ships alongside the raw code so the mobile
    # app does not keep its own copy of MODERATION_STATUS_CHOICES and drift from
    # it the next time a status is added.
    moderation_status_display = serializers.CharField(
        source='get_moderation_status_display', read_only=True,
    )
    # Same reasoning as moderation_status_display above, for the same reason:
    # the category vocabulary is ~35 entries and grows, and a client keeping
    # its own copy of the labels shows "سایر" for every category added after
    # the installed build. Clients should render this and fall back to their
    # local map only when it is absent (an older server).
    category_display = serializers.CharField(
        source='get_category_display', read_only=True,
    )
    # moderation_status alone no longer tells the owner app whether customers
    # can currently see the business: PENDING covers both "never reviewed,
    # hidden" and "approved before, an edit is staged, still showing the old
    # copy" (see Business.is_publicly_visible / pending_*). This is the one
    # field the app can trust without re-deriving that logic itself.
    is_publicly_visible = serializers.BooleanField(read_only=True)

    insurances = InsuranceSerializer(many=True, read_only=True)
    insurance_ids = serializers.PrimaryKeyRelatedField(
        many=True,
        queryset=Insurance.objects.filter(is_active=True),
        source='insurances',
        write_only=True,
        required=False,
    )

    class Meta:
        model = Business
        fields = [
            # Identity
            'id', 'title', 'category', 'category_display', 'unique_code', 'phone', 'extra_phones',
            'address', 'logo', 'theme_color',
            # Location & Discovery
            'latitude', 'longitude', 'province', 'city', 'neighborhood', 'city_code',
            'is_claimed', 'online_booking_requests_count', 'insurances', 'insurance_ids',
            # Schedule
            'default_service_duration', 'work_start_hour', 'work_end_hour',
            'work_start_minute', 'work_end_minute', 'closed_weekdays',
            # Notifications (legacy)
            'notification_enabled', 'notification_types', 'notification_minutes_before',
            # Payment config
            'payment_method', 'accepted_payment_methods', 'merchant_id',
            'payment_link', 'card_number', 'card_owner_name',
            # SMS preferences
            'enable_reminder_sms', 'enable_promotional_sms', 'notify_owner_by_sms',
            'reminder_delivery', 'reminder_template',
            # Service menu shown to the owner when booking and to clients on the
            # public booking page
            'services', 'allow_client_add_service',
            # Misc
            'allow_anonymous_view', 'bio', 'created_at', 'updated_at',
            # Booking control + emergency notice
            'notice_message', 'notice_enabled', 'booking_enabled',
            # Subscription lock (graceful downgrade) — read-only status flag
            'is_locked',
            # Advanced capacity & deposit settings. These were validated by
            # validate_business_settings() and gated behind plan entitlements, but
            # were missing here — so the owner app's advanced settings passed
            # validation, returned 200, and were then silently dropped by the
            # serializer instead of being saved.
            'max_appointments_per_hour', 'deposit_mode', 'deposit_amount',
            # Editorial review state — owner-visible, never owner-writable.
            # `moderation_note` carries the reviewer's reason verbatim; it is
            # written for the owner to read, so it is exposed as-is.
            'moderation_status', 'moderation_status_display',
            'moderation_note', 'moderation_submitted_at',
            # A staged edit awaiting re-review — see Business.pending_* and
            # services.stage_pending_moderated_fields. 'title'/'bio'/'address'/
            # 'logo' above keep showing the last-*approved* copy (what
            # customers currently see) while these carry the owner's proposed,
            # not-yet-cleared change, if any.
            'pending_title', 'pending_bio', 'pending_address', 'pending_logo',
            'is_publicly_visible',
        ]
        read_only_fields = [
            'id', 'unique_code', 'created_at', 'updated_at', 'owner', 'is_locked',
            'is_claimed', 'online_booking_requests_count',
            # Only business/moderation.py may move these. If they were writable
            # an owner could PATCH themselves to APPROVED and walk straight past
            # review — the whole point of the layer.
            'moderation_status', 'moderation_status_display',
            'moderation_note', 'moderation_submitted_at',
            # Only ever written server-side, via services.stage_pending_moderated_fields.
            'pending_title', 'pending_bio', 'pending_address', 'pending_logo',
            'is_publicly_visible',
            # Derived from the logo in Business.save(). Accepting it from the
            # client would let an owner set a header colour unrelated to their
            # logo, and it would be overwritten on their next logo change anyway.
            'theme_color',
        ]
        extra_kwargs = {
            # Restated even though the model field is already CharField(300):
            # the cap is part of the API contract the owner app validates
            # against, and stating it here keeps it true if the model column is
            # ever widened for some unrelated reason.
            'notice_message': {'max_length': 300, 'allow_blank': True},
        }

    def validate_phone(self, value):
        """Normalise and check the business contact number.

        This field had no validation at all, which is how a phone typed on a
        Persian keyboard (۰۲۱۳۹۰۹۳۰۹۳) reached Melipayamak and came back as
        «شماره گیرنده نامعتبر است». Persian/Arabic digits and the usual
        separators are accepted and converted rather than rejected — the owner
        typed a perfectly good number, it just wasn't in ASCII.

        Landlines are allowed: this is the number clients call, not necessarily
        one that can receive SMS.
        """
        if not value:
            return value
        normalized = normalize_phone(value)
        if not is_iran_phone(normalized):
            raise serializers.ValidationError(
                "شماره باید ۱۱ رقم و با ۰ شروع شود (مثل ۰۲۱۱۲۳۴۵۶۷۸ یا ۰۹۱۲۱۲۳۴۵۶۷)"
            )
        return normalized

    def validate_services(self, value):
        return normalize_service_names(value)

    def validate_extra_phones(self, value):
        """Same shape and normalization as `phone`, plural.

        A blank entry is dropped rather than rejected: the mobile UI is a
        repeatable list of text fields, and an owner who adds a row and then
        leaves it empty (or deletes its text without removing the row) should
        not be blocked from saving everything else.
        """
        if not isinstance(value, list):
            raise serializers.ValidationError("باید فهرستی از شماره‌ها باشد")
        normalized = []
        for raw in value:
            text = str(raw or '').strip()
            if not text:
                continue
            candidate = normalize_phone(text)
            if not is_iran_phone(candidate):
                raise serializers.ValidationError(
                    f"شماره «{raw}» نامعتبر است — باید ۱۱ رقم و با ۰ شروع شود"
                )
            normalized.append(candidate)
        return normalized

    def validate_bio(self, value):
        """Length is enforced here, not by the column — see Business.bio.

        Without this the field became effectively unbounded when it moved to
        TextField: DRF derives no max_length from one, so a client could post
        a megabyte of text.
        """
        if value and len(value) > BIO_MAX_LENGTH:
            raise serializers.ValidationError(
                f"معرفی کسب‌وکار حداکثر می‌تواند {BIO_MAX_LENGTH} کاراکتر باشد"
            )
        return value

    def validate_work_start_hour(self, value):
        if not 0 <= value <= 23:
            raise serializers.ValidationError("Must be between 0-23")
        return value

    def validate_work_end_hour(self, value):
        if not 0 <= value <= 23:
            raise serializers.ValidationError("Must be between 0-23")
        return value

    def validate_work_start_minute(self, value):
        if not 0 <= value <= 59:
            raise serializers.ValidationError("Must be between 0-59")
        return value

    def validate_work_end_minute(self, value):
        if not 0 <= value <= 59:
            raise serializers.ValidationError("Must be between 0-59")
        return value

    def validate_closed_weekdays(self, value):
        if not isinstance(value, list):
            raise serializers.ValidationError("باید فهرستی از روزهای هفته باشد")
        if any(not isinstance(v, int) or not 0 <= v <= 6 for v in value):
            raise serializers.ValidationError(
                "هر روز باید عددی بین ۰ (دوشنبه) تا ۶ (یکشنبه) باشد"
            )
        # De-duplicate rather than reject: the owner app sends the full set on
        # every save, so a duplicate is a client-side bug, not something the
        # owner should be shown an error for.
        return sorted(set(value))

    def validate_city_code(self, value):
        if value is not None and get_city(value) is None:
            raise serializers.ValidationError("شهر انتخاب‌شده در فهرست شهرها نیست")
        return value

    def to_internal_value(self, data):
        ret = super().to_internal_value(data)
        
        # DRF's HTML form parser maps missing booleans to False.
        # Since the mobile app doesn't send some boolean fields during creation,
        # we remove them from validated data if they weren't explicitly provided,
        # so the model defaults (e.g. True) take effect.
        for field in ['booking_enabled', 'enable_reminder_sms', 'enable_promotional_sms', 'allow_anonymous_view', 'notification_enabled', 'notify_owner_by_sms', 'notice_enabled', 'allow_client_add_service']:
            if field not in data and field in ret:
                ret.pop(field)
                
        return ret

    def to_representation(self, instance):
        try:
            return super().to_representation(instance)
        except SynchronousOnlyOperation:
            with ThreadPoolExecutor(max_workers=1) as executor:
                return executor.submit(super().to_representation, instance).result()

    def validate(self, data):
        # Effective value after this write: partial updates (every PUT here is
        # partial=True) keep whatever the rest of the row already had, so a
        # check touching several fields has to look past just what changed.
        def current(field):
            if field in data:
                return data[field]
            return getattr(self.instance, field, None)

        # Compared as minutes-since-midnight, not just the hour: work_end_hour
        # alone used to let 19:00-19:30 (start hour 19, end hour 19) through as
        # "equal, therefore invalid" even though it is a real 30-minute window
        # once minutes exist — and would just as wrongly accept 19:45-19:15 as
        # valid because 19 == 19 was never compared at all.
        start_hour = current('work_start_hour')
        end_hour = current('work_end_hour')
        if start_hour is not None and end_hour is not None:
            start_total = start_hour * 60 + (current('work_start_minute') or 0)
            end_total = end_hour * 60 + (current('work_end_minute') or 0)
            if start_total >= end_total:
                raise serializers.ValidationError(
                    "ساعت پایان کار باید بعد از ساعت شروع باشد"
                )

        # A payment method must be *usable*, not merely switched on. Storing CARD
        # with no card number, or ONLINE with neither a Zibal merchant nor a
        # payment link, leaves the client on a checkout screen with nothing to
        # pay to — which read as «شماره کارت ثبت نشده» and dead-ended the booking.
        accepted = current('accepted_payment_methods') or []

        if 'CARD' in accepted and not str(current('card_number') or '').strip():
            raise serializers.ValidationError({
                'card_number': 'برای پرداخت کارت به کارت، شماره کارت الزامی است'
            })

        if 'ONLINE' in accepted and not (
            str(current('merchant_id') or '').strip()
            or str(current('payment_link') or '').strip()
        ):
            raise serializers.ValidationError({
                'merchant_id': 'برای پرداخت آنلاین، مرچنت آیدی زیبال یا لینک پرداخت الزامی است'
            })

        if current('deposit_mode') in ('MANDATORY', 'OPTIONAL') and not current('deposit_amount'):
            raise serializers.ValidationError({
                'deposit_amount': 'برای دریافت بیعانه، مبلغ آن باید بیشتر از صفر باشد'
            })

        # The city's names come from the list, never from the client, so the
        # code and the text shown beside it cannot disagree.
        if 'city_code' in data:
            city = get_city(data['city_code']) if data['city_code'] is not None else None
            data['city'] = city['name'] if city else ''
            data['province'] = city['province'] if city else ''

        # A map pin is a pair: half of one would put the business on the
        # equator or the prime meridian. Clearing sends both empty.
        if (current('latitude') is None) != (current('longitude') is None):
            raise serializers.ValidationError({
                'latitude': 'موقعیت روی نقشه باید هر دو مختصات (عرض و طول) را داشته باشد'
            })
        # Six decimals is ~10cm — finer is noise from the device, not precision.
        for field in ('latitude', 'longitude'):
            if data.get(field) is not None:
                data[field] = round(data[field], 6)

        return data

    def validate_logo(self, value):
        if value.size > 500 * 1024:  # 500KB
            raise ValidationError("حجم عکس نباید بیشتر از 500 کیلوبایت باشد.")
        return value


class ServiceCatalogItemSerializer(serializers.ModelSerializer):
    """A single pickable service-name chip, shared across a category."""
    class Meta:
        model = ServiceCatalogItem
        fields = ['id', 'category', 'name']
        read_only_fields = ['id']


class SmsLogVisitorSerializer(serializers.Serializer):
    """The recipient shown next to a logged SMS.

    Nested rather than flattened because the field is nullable: owner
    notifications are logged with ``visitor=None`` (they are billed to the same
    quota, so they belong in the report), and a null object says "this one went
    to you" far more clearly than three independently-null columns.
    """
    id = serializers.IntegerField()
    full_name = serializers.CharField()
    phone_number = serializers.CharField()


class SmsLogSerializer(serializers.ModelSerializer):
    visitor = SmsLogVisitorSerializer(read_only=True)

    class Meta:
        model = SmsLog
        fields = ['id', 'message_text', 'status', 'error_detail', 'sent_at', 'visitor']
        read_only_fields = fields


class PushLogSerializer(serializers.ModelSerializer):
    """Same shape as SmsLogSerializer, deliberately, so the owner-facing
    report screen can show both channels with one shared row layout. Unlike
    SmsLog, PushLog.visitor is never null — there is no "owner notification"
    concept on this channel yet (see send_appointment_reminders._push_owner,
    which only stamps a timestamp and never writes a PushLog row)."""
    visitor = SmsLogVisitorSerializer(read_only=True)

    class Meta:
        model = PushLog
        fields = ['id', 'title', 'body', 'status', 'error_detail', 'sent_at', 'visitor']
        read_only_fields = fields


class PublicBusinessSerializer(serializers.ModelSerializer):
    """
    STRICTLY ISOLATED public-facing serializer.

    This is the ONLY serializer that must ever be used when returning
    business data to an unauthenticated end-client (the booking page).

    Explicitly whitelisted fields — any new field added to the model
    must be CONSCIOUSLY added here. It must NEVER include:
      - phone, merchant_id, card_owner_name, card_number (shown separately via logic)
      - notification_*, enable_*_sms, allow_anonymous_view
      - created_at, updated_at, unique_code (internal)
      - moderation_* — editorial state is internal. A business only reaches a
        public response at all once it is APPROVED (Business.public_filter()),
        so the fields would be constant here, and `moderation_note` is a
        reviewer's private wording addressed to the owner, not to the public.
    """
    # Persian label alongside the raw code, so the booking page and any other
    # public client render a category added after their build shipped instead
    # of falling back to "سایر". Same rationale as BusinessSerializer's.
    category_display = serializers.CharField(
        source='get_category_display', read_only=True,
    )
    insurances = InsuranceSerializer(many=True, read_only=True)
    distance_km = serializers.SerializerMethodField()

    def get_distance_km(self, obj):
        val = getattr(obj, 'distance_km', None)
        return round(val, 2) if val is not None else None

    class Meta:
        model = Business
        fields = [
            'id',
            'unique_code',
            'category',
            'category_display',
            'title',
            'logo',
            'theme_color',
            'address',
            # The map pin + "route there" links. Masked together with the
            # address in to_representation.
            'latitude',
            'longitude',
            'province',
            'city',
            'city_code',
            'neighborhood',
            'is_claimed',
            'online_booking_requests_count',
            'insurances',
            'distance_km',
            'phone',
            'extra_phones',
            'work_start_hour',
            'work_end_hour',
            'work_start_minute',
            'work_end_minute',
            'default_service_duration',
            # Weekly days off, so the booking calendar can grey them out before
            # ever fetching slots for that date — see Business.closed_weekdays.
            'closed_weekdays',
            'payment_method',   # clients need to know which flow to show (Free/Card/Gateway)
            'accepted_payment_methods',
            'deposit_mode',
            'deposit_amount',
            'card_number',
            'card_owner_name',
            'payment_link',
            'bio',
            'booking_enabled',
            'notice_enabled',
            'notice_message',
            'allow_anonymous_view',
            # The owner's service menu, so the booking page can ask "what do you
            # want done?" as chips instead of a free-text box. Safe to expose:
            # these are the same service names the business advertises.
            'services',
            'allow_client_add_service',
        ]
        # All fields are read-only for this serializer
        read_only_fields = fields

    def _build_public_representation(self, instance):
        data = super().to_representation(instance)
        data = apply_contact_rules(data, instance, self.context)
        # Emergency-notice gating + the subscription-driven booking block.
        return apply_notice_rules(data, instance)

    def to_representation(self, instance):
        try:
            return self._build_public_representation(instance)
        except SynchronousOnlyOperation:
            with ThreadPoolExecutor(max_workers=1) as executor:
                return executor.submit(self._build_public_representation, instance).result()


class ClientBusinessSerializer(serializers.ModelSerializer):
    """
    Kept for backward compatibility with appointment/client_serializers.py.
    Used to display business info inside a client's own appointment record.

    Includes the payment fields the checkout screen needs (card number, owner
    name, deposit, accepted methods). Without them the client was shown
    «شماره کارت ثبت نشده» and «مبلغ را با کسب‌وکار هماهنگ کنید» and had no way to
    pay at all. These are the same fields PublicBusinessSerializer already
    returns on the public booking page, so nothing new is exposed here.

    Still excluded: merchant_id, notification_*, enable_*_sms, created_at/updated_at,
    and moderation_* (internal editorial state — see PublicBusinessSerializer).
    ``online_gateway_enabled`` is how the checkout screen learns a real Zibal
    gateway is available without the merchant id itself crossing the wire.
    """
    online_gateway_enabled = serializers.SerializerMethodField()
    category_display = serializers.CharField(
        source='get_category_display', read_only=True,
    )
    insurances = InsuranceSerializer(many=True, read_only=True)
    distance_km = serializers.SerializerMethodField()

    def get_distance_km(self, obj):
        val = getattr(obj, 'distance_km', None)
        return round(val, 2) if val is not None else None

    class Meta:
        model = Business
        fields = [
            'id', 'title', 'category', 'category_display', 'unique_code', 'phone', 'extra_phones',
            'address', 'latitude', 'longitude', 'province', 'city', 'neighborhood', 'city_code',
            'is_claimed', 'online_booking_requests_count', 'insurances', 'distance_km',
            'logo', 'theme_color',
            'default_service_duration', 'work_start_hour', 'work_end_hour',
            'work_start_minute', 'work_end_minute',
            'allow_anonymous_view',
            'notice_message', 'notice_enabled', 'booking_enabled',
            'payment_method', 'accepted_payment_methods',
            'deposit_mode', 'deposit_amount',
            'card_number', 'card_owner_name', 'payment_link',
            'online_gateway_enabled',
            'services', 'allow_client_add_service',
        ]

    def get_online_gateway_enabled(self, obj) -> bool:
        """True when the owner configured a merchant, i.e. the client can be sent
        to a real gateway instead of the manual link-and-tracking-number flow."""
        return bool((obj.merchant_id or '').strip())

    def _build_public_representation(self, instance):
        data = super().to_representation(instance)
        data = apply_contact_rules(data, instance, self.context)
        # Emergency-notice gating + the subscription-driven booking block.
        return apply_notice_rules(data, instance)

    def to_representation(self, instance):
        try:
            return self._build_public_representation(instance)
        except SynchronousOnlyOperation:
            with ThreadPoolExecutor(max_workers=1) as executor:
                return executor.submit(self._build_public_representation, instance).result()
