"""Two public read-only services for the marketing site (noobatyar.ir):
active customers and newest businesses. JSON by default, ``?format=html`` for a
ready fragment. Cached 10 minutes; anonymous, GET only, throttled per IP."""
import hashlib

from django.conf import settings
from django.core.cache import cache
from django.utils import timezone
from django.utils.html import format_html, format_html_join
from rest_framework.permissions import AllowAny
from rest_framework.renderers import JSONRenderer, StaticHTMLRenderer
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle
from rest_framework.views import APIView

from .models import Business

TTL = 600
ACTIVE_LIMIT = 50
NEWEST_LIMIT = 12


class SitePublicThrottle(AnonRateThrottle):
    scope = 'site_public'
    rate = '60/min'


def _site_url():
    return getattr(settings, 'SITE_PUBLIC_URL', 'https://noobatyar.ir').rstrip('/')


def _row(b, request):
    return {
        'name': b.title,
        'category': b.category,
        'category_display': b.get_category_display(),
        'city': b.city,
        # Business has no separate slug: unique_code is the public URL key.
        'slug': b.unique_code,
        'logo': request.build_absolute_uri(b.logo.url) if b.logo else None,
        'url': f'{_site_url()}/b/{b.unique_code}',
    }


def _html(rows):
    if not rows:
        return '<ul class="nby-businesses"></ul>'
    items = format_html_join(
        '', '<li class="nby-business"><a href="{}">{}<span class="nby-name">{}</span>'
            '<span class="nby-meta">{}</span></a></li>',
        ((r['url'],
          format_html('<img src="{}" alt="" loading="lazy">', r['logo']) if r['logo'] else '',
          r['name'],
          ' - '.join(x for x in (r['category_display'], r['city']) if x))
         for r in rows),
    )
    return format_html('<ul class="nby-businesses">{}</ul>', items)


class _SiteListView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]
    throttle_classes = [SitePublicThrottle]
    http_method_names = ['get', 'head', 'options']
    # ?format=html is DRF's own format switch: StaticHTMLRenderer answers it.
    renderer_classes = [JSONRenderer, StaticHTMLRenderer]
    cache_name = ''

    def queryset(self):
        raise NotImplementedError

    def get(self, request):
        fmt = 'html' if request.query_params.get('format') == 'html' else 'json'
        host = hashlib.sha1(request.get_host().encode()).hexdigest()[:10]
        key = f'site:{self.cache_name}:{fmt}:{host}'
        payload = cache.get(key)
        if payload is None:
            rows = [_row(b, request) for b in self.queryset()]
            payload = str(_html(rows)) if fmt == 'html' else {'count': len(rows), 'results': rows}
            cache.set(key, payload, TTL)
        if fmt == 'html':
            resp = Response(payload, content_type='text/html; charset=utf-8')
        else:
            resp = Response(payload)
        # Public, credential-less, read-only: any origin may embed it.
        resp['Access-Control-Allow-Origin'] = '*'
        resp['Cache-Control'] = f'public, max-age={TTL}'
        return resp


class ActiveCustomersView(_SiteListView):
    """GET /api/public/site/active-customers/ — businesses whose owner has a
    live paid subscription."""
    cache_name = 'active'

    def queryset(self):
        return (Business.objects.filter(
            Business.public_filter(), is_claimed=True,
            user__subscriptions__status='active',
            user__subscriptions__ends_at__gt=timezone.now(),
            user__subscriptions__plan__price__gt=0,
        ).distinct().order_by('-rank_score', '-created_at')[:ACTIVE_LIMIT])


class NewestBusinessesView(_SiteListView):
    """GET /api/public/site/newest-businesses/ — newest approved businesses."""
    cache_name = 'newest'

    def queryset(self):
        return (Business.objects.filter(
            Business.public_filter(), is_claimed=True,
            moderation_status=Business.MODERATION_APPROVED,
        ).order_by('-created_at', '-id')[:NEWEST_LIMIT])
