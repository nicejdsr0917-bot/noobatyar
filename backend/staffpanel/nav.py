"""The panel's side menu: short titles, four sections, per-permission items.

Same idea as the demo drawer (one- or two-word titles) and the same rule as
the unfold sidebar in core/settings.py: an item shows only to a user who may
open it, so a Support or Finance role sees its own slice.
"""

from django.core.cache import cache
from django.urls import reverse
from django.utils import timezone

from .access import has_perms

#: (section title, [(id, label, icon, url name, perms, badge key), …])
NAV = (
    ('اصلی', (
        ('dashboard', 'داشبورد', 'dashboard', 'staffpanel:dashboard', None, None),
        ('businesses', 'کسب‌وکارها', 'building', 'staffpanel:businesses', 'business.view_business', None),
        ('customers', 'مشتریان', 'users', 'staffpanel:customers', 'visitor.view_visitor', None),
    )),
    ('بررسی', (
        ('approvals', 'صف تأیید', 'check-circle', 'staffpanel:approvals', 'business.view_business', 'businesses'),
        ('showcase', 'نمونه‌کارها', 'image', 'staffpanel:showcase', 'showcase.view_portfolioitem', 'showcase'),
        ('reviews', 'نظرات', 'star', 'staffpanel:reviews', 'reviews.view_review', None),
        ('reports', 'تخلف‌ها', 'alert', 'staffpanel:reports', 'business.view_contentreport', 'reports'),
    )),
    ('رشد', (
        ('marketing', 'بازاریابی', 'phone', 'staffpanel:marketing', 'marketing.view_marketinglead', 'calls'),
        ('banners', 'بنرها', 'tag', 'staffpanel:banners', 'banners.view_banner', None),
        ('sms', 'پیامک', 'message', 'staffpanel:sms', 'visitor.view_smslog', None),
        ('finance', 'مالی', 'wallet', 'staffpanel:finance', 'accounting.view_transaction', None),
    )),
    # The raw unfold admin stays one click away so nothing it can do is lost.
    ('مدیریت', (
        ('admin', 'ادمین کامل', 'settings', 'admin:index', None, None),
        ('campaigns', 'کمپین‌ها', 'send', 'admin:core_campaigns', 'core.view_marketingpushlog', None),
        ('segments', 'گروه مخاطب', 'filter', 'admin:core_segment_builder', 'core.view_audiencesegment', None),
        ('roles', 'نقش‌ها', 'shield', 'admin:auth_group_changelist', 'auth.view_group', None),
    )),
)

BADGE_CACHE_KEY = 'staffpanel:badges:v1'
BADGE_TTL = 60


def badge_counts():
    """Queue sizes for the menu badges, cached for a minute: they render on
    every panel page and nobody needs them to the second."""
    counts = cache.get(BADGE_CACHE_KEY)
    if counts is not None:
        return counts
    from business.models import Business, ContentReport
    from marketing.models import MarketingLead
    from showcase.models import Certificate, ModerationStatus, PortfolioItem

    counts = {
        'businesses': Business.objects.filter(moderation_status=Business.MODERATION_PENDING).count(),
        'showcase': (PortfolioItem.objects.filter(status=ModerationStatus.PENDING).count()
                     + Certificate.objects.filter(status=ModerationStatus.PENDING).count()),
        'reports': ContentReport.objects.filter(status=ContentReport.STATUS_NEW).count(),
        'calls': (MarketingLead.objects.filter(next_follow_up__lte=timezone.localdate())
                  .exclude(status__in=MarketingLead.CLOSED_STATUSES).count()),
    }
    cache.set(BADGE_CACHE_KEY, counts, BADGE_TTL)
    return counts


def forget_badges():
    """Call after an action that changes a queue size."""
    cache.delete(BADGE_CACHE_KEY)


def build(user, current):
    """``[{'title', 'items': [{id, label, icon, href, current, badge}]}]``."""
    counts = None
    sections = []
    for title, items in NAV:
        visible = []
        for item_id, label, icon, url_name, perms, badge in items:
            if not has_perms(user, perms):
                continue
            if badge and counts is None:
                counts = badge_counts()
            visible.append({
                'id': item_id, 'label': label, 'icon': icon, 'href': reverse(url_name),
                'current': item_id == current,
                'badge': counts.get(badge) if badge else None,
            })
        if visible:
            sections.append({'title': title, 'items': visible})
    return sections
