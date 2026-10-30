from django.apps import AppConfig


class StaffPanelConfig(AppConfig):
    """The «نوا» staff panel: daily work (queues, customer files, marketing)
    on top of the same models, services and permissions the unfold admin uses.

    No models of its own. Mounted under ADMIN_URL + 'panel/' (core/urls.py) so
    nginx's existing ``^/(api|admin|…)`` proxy rule already covers it.
    """
    default_auto_field = 'django.db.models.BigAutoField'
    name = 'staffpanel'
    verbose_name = 'پنل کارکنان'
