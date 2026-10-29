# Compatibility shim (remove in R15): moved to common/phone.py.
from common.phone import (  # noqa: F401
    IRAN_ANY_RE,
    IRAN_MOBILE_RE,
    is_iran_mobile,
    is_iran_phone,
    normalize_digits,
    normalize_phone,
)
