# business/admin/__init__.py
"""
Staff tooling for businesses and the content-moderation workflow.

The centre of gravity here is the review queue at
``<admin>/business/business/moderation-queue/``: a card view of everything
waiting for a decision, oldest first, with any banned-keyword hits highlighted
in the owner's own copy so the reviewer sees *why* a listing surfaced without
reading every field.

Two rules shape the rest of this module:

  * **Every status change goes through business.moderation.apply_decision().**
    That is the only place a BusinessModerationLog row is written, so
    `moderation_status` and `moderation_note` are readonly on the change form —
    a hand-typed status would be a silent, unattributable edit to what the
    public sees. The queue view and the bulk actions are the supported paths.
  * **A GET never mutates.** The decision endpoints are POST-only and rely on
    Django's CSRF middleware; a reviewer's browser prefetching a link must not
    approve anything.

The package is split by concern (``business`` = Business + moderation queue,
``moderation`` = audit log and banned keywords, ``reports``, ``claims``,
``insurance``); importing every module here is what registers the admins.
"""

from . import business, claims, guilds, insurance, moderation, reports  # noqa: F401
from .business import BusinessAdmin, BusinessModerationLogInline  # noqa: F401
from .claims import BusinessClaimRequestAdmin  # noqa: F401
from .insurance import InsuranceAdmin  # noqa: F401
from .moderation import BannedKeywordAdmin, BusinessModerationLogAdmin  # noqa: F401
from .reports import ContentReportAdmin  # noqa: F401
