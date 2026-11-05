from rest_framework import serializers

from .models import Story


class StorySerializer(serializers.ModelSerializer):
    image = serializers.SerializerMethodField()

    class Meta:
        model = Story
        fields = ['id', 'image', 'caption', 'button', 'discount_type', 'discount_value', 'link_label', 'link_url',
                  'created_at', 'expires_at']
        read_only_fields = fields

    def get_image(self, obj):
        request = self.context.get('request')
        url = obj.image.url
        return request.build_absolute_uri(url) if request else url
