from django.apps import AppConfig


class BusinessConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'business'
    verbose_name = 'کسب‌وکارها'

    def ready(self):
        from . import signals
        signals.connect()
