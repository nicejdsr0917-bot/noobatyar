from rest_framework import serializers

from .models import Review


def mask_phone(phone):
    phone = phone or ''
    return f'{phone[:4]}***{phone[-4:]}' if len(phone) >= 8 else '***'


def first_name(full_name):
    return (full_name or '').strip().split(' ')[0]


class PublicReviewSerializer(serializers.ModelSerializer):
    visitor_name = serializers.SerializerMethodField()
    visitor_phone = serializers.SerializerMethodField()

    class Meta:
        model = Review
        fields = ['id', 'rating', 'text', 'created_at', 'visitor_name', 'visitor_phone']
        read_only_fields = fields

    def get_visitor_name(self, obj):
        return first_name(obj.visitor.full_name)

    def get_visitor_phone(self, obj):
        return mask_phone(obj.visitor.phone_number)


class OwnReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = Review
        fields = ['id', 'business_id', 'rating', 'text', 'created_at', 'updated_at']
        read_only_fields = fields


class OwnerReviewSerializer(PublicReviewSerializer):
    class Meta(PublicReviewSerializer.Meta):
        fields = PublicReviewSerializer.Meta.fields + ['is_hidden']
        read_only_fields = fields
