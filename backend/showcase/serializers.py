from rest_framework import serializers

from .models import Certificate, PortfolioItem


def _url(request, f):
    if not f:
        return None
    try:
        url = f.url
    except ValueError:
        return None
    return request.build_absolute_uri(url) if request else url


class _Base(serializers.ModelSerializer):
    image = serializers.SerializerMethodField()
    thumbnail = serializers.SerializerMethodField()

    def get_image(self, obj):
        return _url(self.context.get('request'), obj.image)

    def get_thumbnail(self, obj):
        return _url(self.context.get('request'), obj.thumbnail) or self.get_image(obj)


class OwnerPortfolioSerializer(_Base):
    class Meta:
        model = PortfolioItem
        fields = ['id', 'image', 'thumbnail', 'caption', 'order', 'status', 'reject_reason',
                  'created_at', 'updated_at']
        read_only_fields = fields


class OwnerCertificateSerializer(_Base):
    class Meta:
        model = Certificate
        fields = ['id', 'image', 'thumbnail', 'title', 'issuer', 'year', 'order', 'status',
                  'reject_reason', 'created_at', 'updated_at']
        read_only_fields = fields


class PublicPortfolioSerializer(_Base):
    class Meta:
        model = PortfolioItem
        fields = ['id', 'image', 'thumbnail', 'caption', 'order']
        read_only_fields = fields


class PublicCertificateSerializer(_Base):
    class Meta:
        model = Certificate
        fields = ['id', 'image', 'thumbnail', 'title', 'issuer', 'year', 'order']
        read_only_fields = fields


class PortfolioInput(serializers.Serializer):
    caption = serializers.CharField(max_length=PortfolioItem.CAPTION_MAX, required=False, allow_blank=True)
    order = serializers.IntegerField(min_value=0, required=False)


class CertificateInput(serializers.Serializer):
    title = serializers.CharField(max_length=150)
    issuer = serializers.CharField(max_length=150, required=False, allow_blank=True)
    year = serializers.IntegerField(min_value=1300, max_value=2100, required=False, allow_null=True)
    order = serializers.IntegerField(min_value=0, required=False)


class CertificatePatchInput(CertificateInput):
    title = serializers.CharField(max_length=150, required=False)
