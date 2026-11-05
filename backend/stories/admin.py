from django.contrib import admin

from .models import Story


@admin.register(Story)
class StoryAdmin(admin.ModelAdmin):
    list_display = ('business', 'caption', 'button', 'created_at', 'expires_at')
    list_select_related = ('business',)
    raw_id_fields = ('business',)
    search_fields = ('business__title', 'caption')
