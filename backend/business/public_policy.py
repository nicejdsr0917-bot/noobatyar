"""Customer-facing payload policy for a business (audit R11).

What a public / client response may reveal about a business, applied on top of
the plain field serialisation by PublicBusinessSerializer and
ClientBusinessSerializer: contact masking (owner switch + admin subscription
policy), notice gating and the subscription-driven booking block. Kept apart
from the serializers so the rules read - and are tested - as one unit.
"""

# Shown to clients when the owner still has booking_enabled=True but their plan
# quota / subscription can no longer pay for a new appointment.
BOOKING_BLOCKED_NOTICE = "پذیرش نوبت جدید برای این کسب‌وکار موقتاً غیرفعال است."


def _mask_phone(data):
    data['phone'] = None
    data['extra_phones'] = []
    # A WhatsApp number or a Telegram handle reaches the owner just as the
    # phone does, so it is withheld under the same rules.
    data['social_links'] = {}


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
    # ``has_active_subscription`` is attached for a whole page at once by
    # business.selectors.attach_owner_state(); without it, ask per object.
    if not instance.is_claimed:
        is_expired = False
    elif hasattr(instance, 'has_active_subscription'):
        is_expired = not instance.has_active_subscription
    else:
        is_expired = get_active_subscription(instance.user_id) is None
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

    if data.get('booking_enabled'):
        # ``can_book`` is attached in one batch per page (selectors.py).
        if hasattr(instance, 'can_book'):
            blocked = not instance.can_book
        else:
            from accounting.usage import can_book_appointment
            blocked = not can_book_appointment(instance.user_id)
    else:
        blocked = False
    if blocked:
        data['booking_enabled'] = False
        if not data.get('notice_message'):
            data['notice_message'] = BOOKING_BLOCKED_NOTICE
        if 'notice_enabled' in data:
            data['notice_enabled'] = True

    return data


class PublicPayloadMixin:
    """Shared by the two customer-facing business serializers.

    Declare ``distance_km = serializers.SerializerMethodField()`` on the
    serializer (declared fields are only collected from Serializer bases); the
    getter and the policy-applying ``to_representation`` live here once.
    """

    def get_distance_km(self, obj):
        val = getattr(obj, 'distance_km', None)
        return round(val, 2) if val is not None else None

    def _build_public_representation(self, instance):
        data = super().to_representation(instance)
        data = apply_contact_rules(data, instance, self.context)
        # Emergency-notice gating + the subscription-driven booking block.
        return apply_notice_rules(data, instance)

    def to_representation(self, instance):
        # Sync-only: async views must call this inside sync_to_async (one hop
        # for the whole payload) with insurances prefetched.
        return self._build_public_representation(instance)
