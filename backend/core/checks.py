from django.conf import settings
from django.core.checks import Warning, register


@register()
def zibal_merchant_check(app_configs, **kwargs):
    if not settings.DEBUG and not getattr(settings, 'ZIBAL_MERCHANT_ID', ''):
        return [Warning(
            'ZIBAL_MERCHANT_ID is empty; Zibal payments will be rejected.',
            hint='Set ZIBAL_MERCHANT_ID in the server environment.',
            id='core.W001',
        )]
    return []
