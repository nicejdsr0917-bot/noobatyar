from django.contrib.auth import get_user_model
from django.db import transaction
from django.utils import timezone

from business.models import Business

from .models import MarketingLead

SYSTEM_PHONE = '09000000000'  # same owner the directory import uses


def system_user():
    user, _ = get_user_model().objects.get_or_create(
        phone=SYSTEM_PHONE,
        defaults={'name': 'بانک اطلاعاتی نوبت‌یار', 'role': 'ADMIN', 'is_active': True})
    return user


@transaction.atomic
def create_lead(*, title, category, phone, address, assigned_to=None, next_follow_up=None, **business_fields):
    """Create an unclaimed, directory-visible Business plus its lead.

    Mirrors ``import_directory``: owned by the system user, ``is_claimed=False``,
    approved (it is directory data, not an owner submission), booking disabled.
    """
    business = Business.objects.create(
        user=system_user(), title=title, category=category, phone=phone, address=address,
        is_claimed=False, external_source='marketing-manual',
        moderation_status=Business.MODERATION_APPROVED, first_approved_at=timezone.now(),
        is_locked=False, booking_enabled=False,
        default_service_duration=30, work_start_hour=9, work_end_hour=18,
        **business_fields,
    )
    return MarketingLead.objects.create(
        business=business, assigned_to=assigned_to, next_follow_up=next_follow_up)
