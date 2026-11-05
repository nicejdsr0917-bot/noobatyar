from rest_framework import serializers

from .models import Review


def mask_phone(phone):
    phone = phone or ''
    return f'{phone[:4]}***{phone[-4:]}' if len(phone) >= 8 else '***'


def first_name(full_name):
    return (full_name or '').strip().split(' ')[0]


class PublicReviewSerializer(serializers.ModelSerializer):
    """A review as any customer sees it: first name and profile photo only.

    Never anything a stranger could contact the reviewer with. ``visitor_phone``
    stays in the payload, always empty, because app builds before this change
    read it; it used to carry a masked number (0912***4567), which with the
    first name and the business was enough to find someone.
    """

    visitor_name = serializers.SerializerMethodField()
    visitor_phone = serializers.SerializerMethodField()
    visitor_avatar = serializers.SerializerMethodField()

    class Meta:
        model = Review
        fields = ['id', 'rating', 'text', 'created_at', 'visitor_name', 'visitor_phone', 'visitor_avatar']
        read_only_fields = fields

    def get_visitor_name(self, obj):
        return first_name(obj.visitor.full_name)

    def get_visitor_phone(self, obj):
        return ''

    def get_visitor_avatar(self, obj):
        if not obj.visitor.avatar:
            return None
        request = self.context.get('request')
        url = obj.visitor.avatar.url
        return request.build_absolute_uri(url) if request else url


class OwnReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = Review
        fields = ['id', 'business_id', 'rating', 'text', 'created_at', 'updated_at']
        read_only_fields = fields


class OwnerReviewSerializer(PublicReviewSerializer):
    """The business owner's view: they already have their customers' numbers,
    so the masked one they always saw stays."""

    def get_visitor_phone(self, obj):
        return mask_phone(obj.visitor.phone_number)

    class Meta(PublicReviewSerializer.Meta):
        fields = PublicReviewSerializer.Meta.fields + ['is_hidden']
        read_only_fields = fields
