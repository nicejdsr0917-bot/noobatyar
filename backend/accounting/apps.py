from django.apps import AppConfig


class AccountingConfig(AppConfig):
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'accounting'
    verbose_name = 'مالی'

    def ready(self):
        from . import signals  # noqa: F401  (registers entitlement-cache invalidation)
