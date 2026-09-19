from rest_framework import serializers
from api.phone import is_iran_mobile, normalize_phone
from .models import Visitor, SmsLog, VisitorActivity


class VisitorSerializer(serializers.ModelSerializer):
    class Meta:
        model = Visitor
        fields = ['id', 'full_name', 'phone_number', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']

    def validate_phone_number(self, value):
        """Normalise and validate an Iranian mobile number.

        Normalising first means a number typed on a Persian keyboard, or pasted
        with dashes or a +98 prefix, is converted instead of rejected. Mobile
        only here (unlike Business.phone): this is the number we send SMS to.
        """
        normalized = normalize_phone(value)
        if not is_iran_mobile(normalized):
            raise serializers.ValidationError(
                "شماره تلفن باید با 09 شروع شده و 11 رقم باشد"
            )
        return normalized

    def validate_full_name(self, value):
        """Ensure name is not empty or whitespace"""
        if not value or not value.strip():
            raise serializers.ValidationError("نام نمی‌تواند خالی باشد")
        return value.strip()


class VisitorAttachSerializer(serializers.Serializer):
    """Input for "put this person in my contacts", by phone number.

    Deliberately not `VisitorSerializer`. That one is a ModelSerializer over a
    model whose ``phone_number`` is ``unique=True``, so DRF attaches a
    UniqueValidator — which made adding an existing person fail validation with
    "این شماره از قبل موجود است" *before* the view's own get_or_create could
    attach them. The view was already written to handle the existing-person
    case; it simply never ran, because the request never got past is_valid().

    A plain Serializer, not a ModelSerializer with the validator removed: the
    uniqueness check still matters on PUT, where changing a contact's number to
    one that belongs to somebody else must be refused, and that path keeps
    using VisitorSerializer untouched.
    """

    full_name = serializers.CharField(max_length=255)
    phone_number = serializers.CharField(max_length=20)

    # Same rules as VisitorSerializer's — one behaviour for one field.
    validate_phone_number = VisitorSerializer.validate_phone_number
    validate_full_name = VisitorSerializer.validate_full_name


class SmsLogSerializer(serializers.ModelSerializer):
    # default=None: an owner-directed message (e.g. "new booking") has no
    # visitor, and without a default DRF raises walking the null relation.
    visitor_name = serializers.CharField(source='visitor.full_name', read_only=True, default=None)
    visitor_phone = serializers.CharField(source='visitor.phone_number', read_only=True, default=None)

    class Meta:
        model = SmsLog
        fields = ['id', 'visitor', 'visitor_name', 'visitor_phone', 'business', 'message_text', 'status', 'sent_at']
        read_only_fields = ['id', 'sent_at']


class VisitorActivitySerializer(serializers.ModelSerializer):
    """Read-only activity row for the visitor's own profile page.

    Ships human-readable Persian labels alongside the raw codes so the client
    does not have to keep its own copy of the choice tables.
    """

    action_label = serializers.CharField(source='get_action_display', read_only=True)
    actor_label = serializers.CharField(source='get_actor_type_display', read_only=True)
    business_title = serializers.CharField(source='business.title', read_only=True, default=None)

    class Meta:
        model = VisitorActivity
        fields = [
            'id', 'action', 'action_label', 'actor_type', 'actor_label',
            'business', 'business_title', 'appointment', 'detail', 'created_at',
        ]
        read_only_fields = fields