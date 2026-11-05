"""
accounting/permissions.py

Entitlement-aware building blocks used to gate features behind the user's plan.

  * RequiresFeature('online_gateway')  — a DRF permission class factory.
  * validate_business_settings(...)     — checks the settings an owner is trying
    to enable on a Business against their plan, returning a Persian error string
    (or None if everything is allowed).
"""

from rest_framework.permissions import BasePermission

from . import entitlements


def RequiresFeature(feature_key, message=None):
    """
    Build a DRF permission class that allows the request only if the user's plan
    unlocks ``feature_key``. Staff always pass.

        permission_classes = [IsAuthenticated, RequiresFeature('advanced_reports')]
    """
    label = entitlements.FEATURE_LABELS.get(feature_key, feature_key)
    default_message = message or f"قابلیت «{label}» در پلن فعلی شما فعال نیست. برای استفاده، پلن خود را ارتقا دهید."

    class _RequiresFeature(BasePermission):
        message = default_message

        def has_permission(self, request, view):
            user = request.user
            if not user or not user.is_authenticated:
                return False
            if user.is_staff:
                return True
            return entitlements.has_feature(user, feature_key)

    _RequiresFeature.__name__ = f"RequiresFeature_{feature_key}"
    return _RequiresFeature


def _as_bool(value):
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in ("1", "true", "yes", "on")
    return bool(value)


def _as_list(value):
    if isinstance(value, (list, tuple)):
        return list(value)
    if isinstance(value, str) and value:
        # multipart sends JSON arrays as a string, or a single value
        cleaned = value.strip()
        if cleaned.startswith("["):
            import json
            try:
                return json.loads(cleaned)
            except ValueError:
                return [cleaned]
        return [p.strip() for p in cleaned.split(",") if p.strip()]
    return []


def _gated_features(get, has):
    """
    Plan features a set of business settings relies on, in the order they
    should be reported. ``has(key)`` says whether the setting is part of the
    source at all; ``get(key)`` reads it (strings under multipart).
    """
    features = []

    def add(feature_key):
        if feature_key not in features:
            features.append(feature_key)

    # Online gateway
    payment_method = get("payment_method") if has("payment_method") else None
    accepted = _as_list(get("accepted_payment_methods")) if has("accepted_payment_methods") else []
    if payment_method in ("GATEWAY", "ONLINE") or "ONLINE" in accepted or "GATEWAY" in accepted:
        add(entitlements.FEATURE_ONLINE_GATEWAY)

    # Card-to-card belongs to the paid (deposit/commitment) tier — basic plans
    # are cash-only. A CARD method or a card number both imply it.
    card_number = (get("card_number") or "").strip() if has("card_number") else ""
    if payment_method == "CARD" or "CARD" in accepted or card_number:
        add(entitlements.FEATURE_DEPOSIT)

    # Deposit
    if has("deposit_mode") and get("deposit_mode") in ("MANDATORY", "OPTIONAL"):
        add(entitlements.FEATURE_DEPOSIT)

    # Promotional SMS
    if has("enable_promotional_sms") and _as_bool(get("enable_promotional_sms")):
        add(entitlements.FEATURE_PROMOTIONAL_SMS)

    # Automatic reminder delivery from the SMS panel. MANUAL costs nothing (the
    # message leaves the owner's own SIM), PANEL bills every reminder to the
    # owner's plan quota — so only the tier that pays for panel sending may
    # switch it on.
    if has("reminder_delivery") and get("reminder_delivery") == "PANEL":
        add(entitlements.FEATURE_AUTO_REMINDER_SMS)

    # Hourly capacity control
    if has("max_appointments_per_hour") and get("max_appointments_per_hour") not in (None, "", "null"):
        add(entitlements.FEATURE_CAPACITY_CONTROL)
    # Several clients per time slot is the same paid capacity control.
    if has("slot_capacity"):
        try:
            if int(get("slot_capacity")) > 1:
                add(entitlements.FEATURE_CAPACITY_CONTROL)
        except (TypeError, ValueError):
            pass

    return features


def validate_business_settings(user, data, business=None):
    """
    Return a Persian error message if ``data`` tries to enable a capability the
    user's plan does not include; otherwise None.

    ``data`` is the raw request payload (values may be strings under multipart).
    Only keys that are present are checked, so partial updates are fine.

    ``business`` is the stored instance on an update. A capability it already
    uses is not re-checked: the owner apps post the whole business back on
    every save, so an owner whose plan lapsed after saving a card number would
    otherwise be refused *every* edit — the business name, an emergency notice
    — over a setting they did not touch. Turning a capability off is always
    allowed for the same reason. Whether a lapsed capability still takes effect
    is decided where it is used (see appointment/client_views.py), not here.
    """
    if getattr(user, "is_staff", False):
        return None

    def need(feature_key):
        label = entitlements.FEATURE_LABELS.get(feature_key, feature_key)
        return f"قابلیت «{label}» در پلن فعلی شما فعال نیست. برای استفاده، پلن خود را ارتقا دهید."

    already_used = set()
    if business is not None:
        already_used = set(_gated_features(
            lambda key: getattr(business, key, None),
            lambda key: hasattr(business, key),
        ))

    for feature_key in _gated_features(data.get, lambda key: key in data):
        if feature_key in already_used:
            continue
        if not entitlements.has_feature(user, feature_key):
            return need(feature_key)

    # A deposit with no amount leaves the client staring at
    # "مبلغ بیعانه: نامشخص" with nothing meaningful to pay. Only checked when
    # the amount is part of this payload, so partial updates that do not touch
    # it keep working (see the docstring contract).
    if data.get("deposit_mode") in ("MANDATORY", "OPTIONAL") and "deposit_amount" in data:
        try:
            amount = int(data.get("deposit_amount") or 0)
        except (TypeError, ValueError):
            amount = 0
        if amount <= 0:
            return "برای فعال کردن بیعانه، مبلغ بیعانه را وارد کنید."

    return None
