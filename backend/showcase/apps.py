from django.apps import AppConfig


class ShowcaseConfig(AppConfig):
    name = 'showcase'
    default_auto_field = 'django.db.models.BigAutoField'
    verbose_name = 'نمونه‌کار و مدارک'

    def ready(self):
        from . import signals  # noqa: F401
