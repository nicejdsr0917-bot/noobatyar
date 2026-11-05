"""business models, split by aggregate.

Everything that used to live in the single ``business/models.py`` is
re-exported here, so ``from business.models import X`` (and the migrations'
``business.models.Business._default_payment_methods``) keep working. All
models keep ``app_label = 'business'`` because they live under this package.
"""
from .core import BIO_MAX_LENGTH, Business, Insurance, User
from .moderation import BannedKeyword, BusinessModerationLog, ContentReport
from .catalog import ServiceCatalogItem
from .booking import BusinessClaimRequest, OnlineBookingRequest
from .follow import BusinessFollow
from .promo import PromoCampaign, PromoCampaignRecipient
from .guilds import GUILDS  # noqa: F401  (registers the per-guild proxy models)

__all__ = [
    'BIO_MAX_LENGTH', 'User',
    'Insurance', 'Business',
    'BusinessModerationLog', 'BannedKeyword', 'ContentReport',
    'ServiceCatalogItem',
    'OnlineBookingRequest', 'BusinessClaimRequest', 'BusinessFollow',
    'PromoCampaign', 'PromoCampaignRecipient',
]
