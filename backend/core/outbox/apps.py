from django.apps import AppConfig


class OutboxConfig(AppConfig):
    name = 'core.outbox'
    label = 'outbox'
    default_auto_field = 'django.db.models.BigAutoField'
    verbose_name = 'صف ارسال'
