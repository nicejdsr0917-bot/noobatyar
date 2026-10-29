"""The add-on pack kinds, split out so accounting does not import core.dashboard.

The kind lives on the pack, not the purchase — a purchase is split by
``pack__kind``.
"""

from accounting.models import AddOnPack

ADDON_KINDS = (
    AddOnPack.KIND_SMS,
    AddOnPack.KIND_APPOINTMENT,
    AddOnPack.KIND_FEATURE,
)
