from django.contrib import admin
from unfold.admin import ModelAdmin
from .models import Appointment

@admin.register(Appointment)
class AppointmentAdmin(ModelAdmin):
    list_display = ('id', 'business', 'visitor', 'status', 'created_at')
    list_filter = ('status', 'created_at')
    search_fields = ('visitor__phone_number', 'business__title')
