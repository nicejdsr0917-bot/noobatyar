# core/settings.py
from pathlib import Path
from datetime import timedelta

BASE_DIR = Path(__file__).resolve().parent.parent

import os

# Local-only overrides. Production sets real env vars via docker-compose (which
# reads its own .env at the repo root) and never carries this file, so this is a
# no-op there. `override=False` (the load_dotenv default) means anything already
# set in the real environment always wins over the file — see docs/ENVIRONMENTS.md.
from dotenv import load_dotenv
load_dotenv(BASE_DIR / '.env.local')

SECRET_KEY = os.getenv('SECRET_KEY', 'insecure-dev-key-change-in-production')
DEBUG = os.getenv('DEBUG', 'False') == 'True'
ALLOWED_HOSTS = os.getenv('ALLOWED_HOSTS', '127.0.0.1,10.0.2.2,localhost').split(',')

# Fail fast in production if the secret key was left at its insecure default.
if not DEBUG and SECRET_KEY == 'insecure-dev-key-change-in-production':
    raise RuntimeError('SECRET_KEY must be set via environment in production (DEBUG=False)')

INSTALLED_APPS = [
    # Not 'django.contrib.admin': the custom AdminConfig swaps in
    # NobatyarAdminSite as the *default* site, so every existing
    # @admin.register(...) decorator keeps working untouched while the panel
    # gets Persian branding and a place to hang dashboard views.
    # See core/apps.py and core/admin_site.py.
    #
    # django-unfold supplies the admin's look (sidebar, tables, forms, dark
    # mode). Its apps must come before the admin config so its templates win
    # over django.contrib.admin's; the site class itself subclasses
    # UnfoldAdminSite (core/admin_site.py). Configured by UNFOLD at the bottom.
    # BasicAppConfig, not plain 'unfold': the default config's ready() swaps
    # admin.site for a bare UnfoldAdminSite, which would drop NobatyarAdminSite
    # and every custom view on it.
    'unfold.apps.BasicAppConfig',
    'unfold.contrib.filters',
    'core.apps.NobatyarAdminConfig',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    # For `intcomma` in the admin report templates. Toman amounts run to eight
    # digits, and "15501000" is not a number anybody reads off a financial
    # report correctly at a glance. No models, no migrations.
    'django.contrib.humanize',
    'corsheaders',
    'rest_framework',
    'rest_framework_simplejwt.token_blacklist',
    # No models — installed so Django discovers core/management/commands/.
    'core',
    # DB-backed outbox + run_outbox worker (core/outbox/).
    'core.outbox',
    'api',
    'accounting',
    'versions',
    'business',
    'visitor',
    'appointment',
    'reviews',
    'bale',
    'showcase',
    'banners',
    'marketing',
]

MIDDLEWARE = [
    # First, so the request id/duration cover every other middleware.
    'core.observability.RequestLogMiddleware',
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    # NOTE: django.middleware.locale.LocaleMiddleware is deliberately NOT here.
    # The admin picks RTL from the *active* language (get_language_bidi() →
    # dir="rtl" + admin/css/rtl.css), and with no LocaleMiddleware the active
    # language is always LANGUAGE_CODE — i.e. fa-ir, always RTL, which is what
    # this Persian-only product wants. Adding LocaleMiddleware makes the panel
    # follow the browser's Accept-Language instead: a staff laptop set to en-US
    # then gets an English left-to-right admin, verified in testing. If it is
    # ever needed for a real API i18n story, pin LANGUAGES = [('fa', ...)] at
    # the same time, and put it after SessionMiddleware / before
    # CommonMiddleware.
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'core.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [BASE_DIR / 'templates'],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

if os.getenv('POSTGRES_DB'):
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',
            'NAME': os.getenv('POSTGRES_DB'),
            'USER': os.getenv('POSTGRES_USER'),
            'PASSWORD': os.getenv('POSTGRES_PASSWORD'),
            'HOST': os.getenv('POSTGRES_HOST', 'db'),
            'PORT': os.getenv('POSTGRES_PORT', '5432'),
            # Connection pooling is handled by PgBouncer (transaction mode), so we
            # keep Django connections short-lived. Override via DB_CONN_MAX_AGE if
            # you connect straight to Postgres without a pooler.
            'CONN_MAX_AGE': int(os.getenv('DB_CONN_MAX_AGE', '0')),
            'CONN_HEALTH_CHECKS': True,
            # PgBouncer in transaction mode hands a different server connection
            # to each transaction, so a server-side cursor (named cursor used
            # by .iterator()) would vanish between fetches. Must be True.
            'DISABLE_SERVER_SIDE_CURSORS': True,
            # TODO(statement_timeout): deliberately NOT set. Passing
            # `-c statement_timeout=...` through OPTIONS sends it as a startup
            # parameter, which PgBouncer rejects ("unsupported startup
            # parameter: options") unless IGNORE_STARTUP_PARAMETERS includes
            # `options`; and a session-level SET is unreliable under
            # transaction pooling. Safest fix once verified on prod: set it
            # per-role in Postgres instead:
            #   ALTER ROLE <user> SET statement_timeout = '15s';
            #   ALTER ROLE <user> SET idle_in_transaction_session_timeout = '30s';
        }
    }
else:
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.sqlite3',
            'NAME': BASE_DIR / 'db.sqlite3',
        }
    }

# ── Cache (Redis) ─────────────────────────────────────────────────────────────
# Shared across all Gunicorn/Uvicorn workers. The OTP service (api/services/otp.py)
# and DRF throttling both depend on this being a real shared cache — the previous
# implicit LocMemCache default was per-process and silently broke OTP under
# multi-worker deployments.
REDIS_URL = os.getenv('REDIS_URL', 'redis://redis:6379/1')
if DEBUG and not os.getenv('REDIS_URL'):
    # Local single-process runserver with no Redis around: fall back to LocMemCache.
    # Without this the django-redis client silently swallows every set/get
    # (IGNORE_EXCEPTIONS), which breaks OTP and register tokens in a way that
    # looks like "کد منقضی شده" rather than a connection error.
    CACHES = {
        'default': {
            'BACKEND': 'django.core.cache.backends.locmem.LocMemCache',
            'LOCATION': 'nobatyar-dev',
        }
    }
else:
    CACHES = {
        'default': {
            'BACKEND': 'django_redis.cache.RedisCache',
            'LOCATION': REDIS_URL,
            'OPTIONS': {
                'CLIENT_CLASS': 'django_redis.client.DefaultClient',
                'IGNORE_EXCEPTIONS': True,  # cache outage must not take down the API
                # A hung Redis must not hang workers.
                'SOCKET_CONNECT_TIMEOUT': 2,
                'SOCKET_TIMEOUT': 3,
            },
            'KEY_PREFIX': 'nobatyar',
        }
    }
DJANGO_REDIS_IGNORE_EXCEPTIONS = True

# ── Logging ───────────────────────────────────────────────────────────────────
# JSON lines on stdout outside DEBUG (or LOG_JSON=1); readable text in DEBUG.
LOG_JSON = os.getenv('LOG_JSON', 'False' if DEBUG else 'True') == 'True'
# Opt-in slow-query logging (SLOW_QUERY_LOG=True, threshold SLOW_QUERY_MS).
SLOW_QUERY_LOG = os.getenv('SLOW_QUERY_LOG', 'False') == 'True'
SLOW_QUERY_MS = int(os.getenv('SLOW_QUERY_MS', '200'))
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {
        'default': {'format': '%(asctime)s %(levelname)s %(name)s: %(message)s'},
        'json': {'()': 'core.observability.JsonFormatter'},
    },
    'filters': {
        'request_id': {'()': 'core.observability.RequestIdFilter'},
    },
    'handlers': {
        'console': {
            'class': 'logging.StreamHandler',
            'stream': 'ext://sys.stdout',
            'formatter': 'json' if LOG_JSON else 'default',
            'filters': ['request_id'],
        },
    },
    'root': {
        'handlers': ['console'],
        'level': os.getenv('LOG_LEVEL', 'INFO'),
    },
}

# مدل کاربر سفارشی
AUTH_USER_MODEL = 'api.User'

# Argon2 برای hash کردن پسوردها (قوی‌ترین الگوریتم)
PASSWORD_HASHERS = [
    'django.contrib.auth.hashers.Argon2PasswordHasher',
    'django.contrib.auth.hashers.PBKDF2PasswordHasher',
]

# JWT authentication
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'api.authentication.IpRecordingJWTAuthentication',
    ),
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.IsAuthenticated',
    ),
    'EXCEPTION_HANDLER': 'api.exceptions.custom_exception_handler',
    # Behind nginx: trust the (overwritten) X-Forwarded-For so throttles key on
    # the real client IP instead of nginx's. 0 in DEBUG (no proxy).
    'NUM_PROXIES': int(os.getenv('NUM_PROXIES', '0' if DEBUG else '1')),
    # Throttling is backed by the Redis cache above, so counters are shared
    # across workers. Scoped rates (otp, public_slots) are enforced per-view.
    'DEFAULT_THROTTLE_CLASSES': (
        'rest_framework.throttling.AnonRateThrottle',
        'rest_framework.throttling.UserRateThrottle',
    ),
    'DEFAULT_THROTTLE_RATES': {
        'anon': os.getenv('THROTTLE_ANON', '60/min'),
        'user': os.getenv('THROTTLE_USER', '300/min'),
        'otp': os.getenv('THROTTLE_OTP', '5/min'),
        'otp_verify': os.getenv('THROTTLE_OTP_VERIFY', '30/min'),
        'login': os.getenv('THROTTLE_LOGIN', '10/min'),
        'public_slots': os.getenv('THROTTLE_PUBLIC_SLOTS', '120/min'),
        # Abuse-report submission (business/client_views.py) — an anonymous,
        # no-account endpoint with no other rate limit on it, so a tighter
        # scope than the general 'anon' bucket is needed to keep it from
        # being a spam/DoS vector (see business/client_views.py's docstring).
        'content_report': os.getenv('THROTTLE_CONTENT_REPORT', '5/hour'),
        'request_booking': os.getenv('THROTTLE_REQUEST_BOOKING', '10/hour'),
        # Claiming an imported listing (business/claim_views.py) — each start
        # can send an SMS to the listing's number.
        'claim': os.getenv('THROTTLE_CLAIM', '10/hour'),
    },
}

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(hours=int(os.getenv('JWT_ACCESS_HOURS', '24'))),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=int(os.getenv('JWT_REFRESH_DAYS', '14'))),
    'ROTATE_REFRESH_TOKENS': True,
    'BLACKLIST_AFTER_ROTATION': True,
}

LANGUAGE_CODE = 'fa-ir'
# Project translations — currently only Persian strings for django-unfold,
# which ships no fa catalog (locale/fa/LC_MESSAGES/django.po).
LOCALE_PATHS = [BASE_DIR / 'locale']
TIME_ZONE = 'Asia/Tehran'
USE_I18N = True
USE_TZ = True

# ── Static files ──────────────────────────────────────────────────────────────
# Two distinct directories, deliberately not the same one:
#
#   assets/  — *source* files we author (assets/admin_custom/{css,js,fonts}).
#              Listed in STATICFILES_DIRS so collectstatic picks them up and
#              runserver serves them in DEBUG.
#   static/  — the *output* of collectstatic (STATIC_ROOT), mounted into nginx
#              as the static_volume in docker-compose.
#
# They must stay separate: if a STATICFILES_DIRS entry equals STATIC_ROOT,
# Django raises staticfiles.E002 and collectstatic would otherwise copy files
# onto themselves.
STATIC_URL = 'static/'
STATIC_ROOT = BASE_DIR / 'static'
STATICFILES_DIRS = [BASE_DIR / 'assets']
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# Where the Django admin is mounted (see core/urls.py). Moving it off the
# guessable default cuts most credential-stuffing noise.
# NOTE: nginx/nginx.conf proxies `location ~ ^/(api|admin|plans|...)` to the web
# service, so changing ADMIN_URL also requires updating that regex in both
# server blocks — otherwise the new path never reaches Django.
ADMIN_URL = os.getenv('ADMIN_URL', 'admin/').strip().strip('/')
ADMIN_URL = f'{ADMIN_URL}/' if ADMIN_URL else 'admin/'

# CORS: allow all only in DEBUG; in production use an explicit allowlist from env.
_cors_origins = os.getenv('CORS_ALLOWED_ORIGINS', '').strip()
if DEBUG:
    CORS_ALLOW_ALL_ORIGINS = True
else:
    CORS_ALLOW_ALL_ORIGINS = False
    CORS_ALLOWED_ORIGINS = [o.strip() for o in _cors_origins.split(',') if o.strip()]

# ── Production security headers (only enforced when DEBUG is off) ──────────────
if not DEBUG:
    SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')
    SESSION_COOKIE_SECURE = True
    CSRF_COOKIE_SECURE = True
    SECURE_CONTENT_TYPE_NOSNIFF = True

    # Django 4+ enforces Origin checking for unsafe methods (including the admin
    # login POST). A 403 "CSRF verification failed" on /admin/ almost always
    # means the request's origin was not trusted. Start from the CORS origins,
    # then auto-add scheme-qualified origins for every ALLOWED_HOST so the admin
    # host is always trusted without a separate env var to keep in sync.
    CSRF_TRUSTED_ORIGINS = [o.strip() for o in _cors_origins.split(',') if o.strip()]
    for _host in ALLOWED_HOSTS:
        _host = _host.strip()
        if not _host or _host == '*':
            continue
        for _scheme in ('https', 'http'):
            _origin = f'{_scheme}://{_host}'
            if _origin not in CSRF_TRUSTED_ORIGINS:
                CSRF_TRUSTED_ORIGINS.append(_origin)

# Zibal Payment Gateway
# Myket in-app billing (store builds of the owner app). The access token is
# the "X-Access-Token" from the Myket developer panel's in-app products page;
# it authorises server-side purchase verification/consumption and must never
# ship inside the app. Empty = Myket purchases are refused with a clear error.
MYKET_ACCESS_TOKEN = os.getenv('MYKET_ACCESS_TOKEN', '')
MYKET_PACKAGE_NAME = os.getenv('MYKET_PACKAGE_NAME', 'xyz.sattar.javid.proqueue')

ZIBAL_MERCHANT_ID = os.getenv('ZIBAL_MERCHANT_ID') or ''
if not ZIBAL_MERCHANT_ID:
    if DEBUG:
        ZIBAL_MERCHANT_ID = 'zibal'  # Zibal sandbox merchant
    else:
        # No real merchant id is hard-coded: payments will be rejected by the
        # gateway until the env var is set. Fail loudly in the logs.
        import logging as _logging
        _logging.getLogger(__name__).error(
            'ZIBAL_MERCHANT_ID is not set; Zibal payments will fail. '
            'Set it via the environment.')

# Where the deposit gateway sends the client's browser back to. Only the client
# web app has an appointments screen to land on, so this is app.noobatyar.ir
# rather than the API host.
CLIENT_WEB_URL = os.getenv('CLIENT_WEB_URL', 'https://app.noobatyar.ir')
# The owner web panel: where its payers are sent back to after the gateway.
PANEL_WEB_URL = os.getenv('PANEL_WEB_URL', 'https://panel.noobatyar.ir')
SITE_URL = os.getenv('SITE_URL', 'http://localhost:8000')  # Your site's base URL
MEDIA_URL = '/media/'
MEDIA_ROOT = BASE_DIR / 'media'

# Melipayamak SMS Config
MELIPAYAMAK_OTP_TOKEN = os.getenv('MELIPAYAMAK_OTP_TOKEN', '')
MELIPAYAMAK_FROM = os.getenv('MELIPAYAMAK_FROM', '')

# Dev-only OTP bypass. When DEBUG is on and OTP_DEV_CODE is set, the OTP service
# skips Melipayamak (no SMS, no credit burned) and accepts this fixed code.
# Forced empty whenever DEBUG is off, so it can never be enabled in production.
OTP_DEV_CODE = os.getenv('OTP_DEV_CODE', '') if DEBUG else ''

# A password may be changed (forgot-password reset) once per this many hours
# per account — api/views.py. Stops a hijacker who got hold of the phone for a
# moment from flipping the password back and forth.
PASSWORD_CHANGE_COOLDOWN_HOURS = int(os.getenv('PASSWORD_CHANGE_COOLDOWN_HOURS', '24'))

# Dev-only SMS bypass. When on, api/sms.py logs outgoing messages instead of
# dispatching them, so local test runs never text a real phone number. Covers
# every sender (booking, approval/rejection, reminders, subscription lifecycle).
# Forced off whenever DEBUG is off.
SMS_DEV_MODE = (os.getenv('SMS_DEV_MODE', 'False') == 'True') if DEBUG else False

# ── Push notifications (Firebase Cloud Messaging, HTTP v1) ───────────────────
# The owner app learns about new bookings and upcoming appointments through FCM.
# Authentication is a *service account*, not a server key: Google retired legacy
# server keys, so `/fcm/send` no longer exists.
#
#   FCM_CREDENTIALS_FILE  absolute path to the service-account JSON from
#                         Firebase console → Project settings → Service accounts
#   FCM_PROJECT_ID        optional; read out of that JSON when left empty
#
# Left unset, api/services/push.py logs and returns False instead of raising —
# push is a convenience channel next to the SMS that carries the real message,
# so a missing key must never break a reminder run.
FCM_CREDENTIALS_FILE = os.getenv('FCM_CREDENTIALS_FILE', '')
FCM_PROJECT_ID = os.getenv('FCM_PROJECT_ID', '')
# Must match the channel the Android app creates, or Android 8+ silently drops
# every notification (see ProQueueApp / PushMessagingService in mobile_owner).
FCM_ANDROID_CHANNEL_ID = os.getenv('FCM_ANDROID_CHANNEL_ID', 'appointment_reminders')
# Where a browser sends the owner when they click a web-panel push notification
# (FCM's webpush.fcm_options.link). The owner web panel (docs/OWNER_WEB_PLAN.md,
# phase 2.5) has no fixed domain yet — section ۱۴ of that doc lists it as an open
# question between panel.noobatyar.ir and app.noobatyar.ir/owner — so this is a
# provisional default, safe to override without a code change once it is decided.
FCM_WEBPUSH_CLICK_URL = os.getenv('FCM_WEBPUSH_CLICK_URL', 'https://panel.noobatyar.ir/')


# ── Admin panel (django-unfold) ──────────────────────────────────────────────
# The sidebar *is* the panel's navigation: every model and custom page, grouped
# by what staff come to do, with Persian labels. Each entry only shows to a user
# who may view it, so a Support or Finance role sees their own slice.
from django.templatetags.static import static  # noqa: E402
from django.urls import reverse_lazy  # noqa: E402


def _can(perm):
    return lambda request: request.user.is_superuser or request.user.has_perm(perm)


def _item(title, icon, link, perm=None):
    item = {'title': title, 'icon': icon, 'link': link}
    if perm:
        item['permission'] = _can(perm)
    return item


def _optional_item(title, icon, url_name, perm):
    """Sidebar entry for an app that may not be installed: hidden (permission
    callable returns False) when the URL name does not resolve."""
    from django.urls import NoReverseMatch, reverse

    def _resolves():
        try:
            reverse(url_name)
            return True
        except NoReverseMatch:
            return False

    return {
        'title': title, 'icon': icon,
        'link': lambda request: reverse(url_name) if _resolves() else '#',
        'permission': lambda request: _resolves() and (request.user.is_superuser or request.user.has_perm(perm)),
    }


# (proxy model name, short title, icon) per guild: the «ثبت مرکز» sidebar section.
# Mirrors business.models.guilds.GUILDS (settings cannot import models; a test checks they agree).
_GUILD_MENU = [
    ('medicallisting', 'پزشکی', 'stethoscope'),
    ('dentallisting', 'دندان‌پزشکی', 'medical_services'),
    ('healthlisting', 'سلامت', 'psychology'),
    ('beautylisting', 'زیبایی', 'content_cut'),
    ('professionallisting', 'حرفه‌ای', 'work'),
    ('educationlisting', 'آموزش', 'school'),
    ('tradeslisting', 'فنی', 'build'),
    ('otherlisting', 'سایر', 'category'),
]


def _changelist(app, model):
    return reverse_lazy(f'admin:{app}_{model}_changelist')


def _versioned_static(path):
    """static() URL with the source file's mtime as ?v=, so browsers fetch an
    edited stylesheet instead of serving a stale cached copy."""
    source = BASE_DIR / 'assets' / path
    try:
        version = int(source.stat().st_mtime)
    except OSError:
        return static(path)
    return f'{static(path)}?v={version}'


UNFOLD = {
    'SITE_TITLE': 'نوبت‌یار',
    'SITE_HEADER': 'نوبت‌یار',
    'SITE_SUBHEADER': 'پنل مدیریت',
    'SITE_URL': None,
    'SITE_SYMBOL': 'event_available',
    'SHOW_VIEW_ON_SITE': False,
    'SHOW_HISTORY': True,
    'STYLES': [lambda request: _versioned_static('admin_custom/css/unfold.css')],
    'COLORS': {
        # Brand violet (#8B5CF6 at 500), Tailwind's violet scale.
        'primary': {
            '50': 'oklch(96.9% 0.016 293.756)',
            '100': 'oklch(94.3% 0.029 294.588)',
            '200': 'oklch(89.4% 0.057 293.283)',
            '300': 'oklch(81.1% 0.111 293.571)',
            '400': 'oklch(70.2% 0.183 293.541)',
            '500': 'oklch(60.6% 0.25 292.717)',
            '600': 'oklch(54.1% 0.281 293.009)',
            '700': 'oklch(49.1% 0.27 292.581)',
            '800': 'oklch(43.2% 0.232 292.759)',
            '900': 'oklch(38% 0.189 293.745)',
            '950': 'oklch(28.3% 0.141 291.089)',
        },
    },
    'SIDEBAR': {
        'show_search': True,
        'show_all_applications': False,
        # Grouped by daily work, in the order staff do it. Titles are one or
        # two words (like the sidebar of the Nava template); what a page is for
        # belongs on the page, not in the menu.
        'navigation': [
            {
                'items': [
                    _item('داشبورد', 'dashboard', reverse_lazy('admin:index')),
                ],
            },
            {
                'title': 'بررسی',
                'separator': True,
                # Daily work: always open, one click to any page.
                'collapsible': False,
                'items': [
                    _item('صف بررسی', 'fact_check', reverse_lazy('admin:business_business_moderation_queue'), 'business.view_business'),
                    _item('کسب‌وکارها', 'storefront', _changelist('business', 'business'), 'business.view_business'),
                    _item('نمونه‌کارها', 'photo_library', _changelist('showcase', 'portfolioitem'), 'showcase.view_portfolioitem'),
                    _item('مدارک', 'workspace_premium', _changelist('showcase', 'certificate'), 'showcase.view_certificate'),
                    _item('تخلف‌ها', 'flag', _changelist('business', 'contentreport'), 'business.view_contentreport'),
                    _optional_item('نظرات', 'rate_review', 'admin:reviews_review_changelist', 'reviews.view_review'),
                    _item('سوابق بررسی', 'history', _changelist('business', 'businessmoderationlog'), 'business.view_businessmoderationlog'),
                    _item('کلمات ممنوع', 'block', _changelist('business', 'bannedkeyword'), 'business.view_bannedkeyword'),
                ],
            },
            {
                'title': 'ثبت مرکز',
                'separator': True,
                # One list and one add form per guild (business/admin/guilds.py).
                'collapsible': True,
                'items': [
                    _item(title, icon, _changelist('business', name), f'business.view_{name}')
                    for name, title, icon in _GUILD_MENU
                ],
            },
            {
                'title': 'کاربران',
                'separator': True,
                # Daily work: always open, one click to any page.
                'collapsible': False,
                'items': [
                    _item('حساب‌ها', 'person', _changelist('api', 'user'), 'api.view_user'),
                    _item('مشتریان', 'groups', _changelist('visitor', 'visitor'), 'visitor.view_visitor'),
                    _item('نوبت‌ها', 'event', _changelist('appointment', 'appointment'), 'appointment.view_appointment'),
                    _item('دستگاه‌ها', 'smartphone', _changelist('api', 'devicetoken'), 'api.view_devicetoken'),
                ],
            },
            {
                'title': 'رشد',
                'separator': True,
                'collapsible': True,
                'items': [
                    _item('تماس‌های امروز', 'call', lambda request: str(reverse_lazy('admin:marketing_marketinglead_changelist')) + '?today=1', 'marketing.view_marketinglead'),
                    _item('مراکز', 'contact_phone', _changelist('marketing', 'marketinglead'), 'marketing.view_marketinglead'),
                    _item('درخواست مالکیت', 'verified_user', _changelist('business', 'businessclaimrequest'), 'business.view_businessclaimrequest'),
                    _item('بنرها', 'image', _changelist('banners', 'banner'), 'banners.view_banner'),
                    _item('دسته‌های بنر', 'category', _changelist('banners', 'bannercategorygroup'), 'banners.view_bannercategorygroup'),
                    _item('لینک بنرها', 'link', _changelist('banners', 'sitesetting'), 'banners.view_sitesetting'),
                ],
            },
            {
                'title': 'پیام',
                'separator': True,
                'collapsible': True,
                'items': [
                    _item('گزارش پیامک', 'bar_chart', reverse_lazy('admin:visitor_smslog_report'), 'visitor.view_smslog'),
                    _item('پیامک‌ها', 'sms', _changelist('visitor', 'smslog'), 'visitor.view_smslog'),
                    _item('کمپین', 'campaign', reverse_lazy('admin:core_campaigns'), 'core.view_marketingpushlog'),
                    _item('گروه مخاطب', 'filter_alt', reverse_lazy('admin:core_segment_builder'), 'core.view_audiencesegment'),
                    _item('مخاطبان ذخیره', 'bookmarks', _changelist('core', 'audiencesegment'), 'core.view_audiencesegment'),
                    _item('خروجی مخاطبان', 'download', _changelist('core', 'audiencesegmentexport'), 'core.view_audiencesegmentexport'),
                    _item('پوش‌ها', 'notifications_active', _changelist('core', 'marketingpushlog'), 'core.view_marketingpushlog'),
                    _item('پیام اونرها', 'forum', _changelist('core', 'adminmessagelog'), 'core.view_adminmessagelog'),
                    _item('پیامک انقضا', 'mark_email_unread', _changelist('core', 'expirysmslog'), 'core.view_expirysmslog'),
                ],
            },
            {
                'title': 'مالی',
                'separator': True,
                'collapsible': True,
                'items': [
                    _item('گزارش مالی', 'monitoring', reverse_lazy('admin:accounting_financial_report'), 'accounting.view_transaction'),
                    _item('اشتراک‌ها', 'card_membership', _changelist('accounting', 'subscription'), 'accounting.view_subscription'),
                    _item('تراکنش‌ها', 'receipt_long', _changelist('accounting', 'transaction'), 'accounting.view_transaction'),
                    _item('خرید بسته', 'shopping_cart', _changelist('accounting', 'addonpurchase'), 'accounting.view_addonpurchase'),
                    _item('مایکت', 'store', _changelist('accounting', 'storepurchase'), 'accounting.view_storepurchase'),
                    _item('دفتر اعتبار', 'account_balance_wallet', _changelist('accounting', 'creditledger'), 'accounting.view_creditledger'),
                    _item('پاداش مشتری', 'person_add', _changelist('accounting', 'newclientreward'), 'accounting.view_newclientreward'),
                    _item('کیف‌پول تومانی', 'savings', _changelist('accounting', 'tomanwalletsettings'), 'accounting.view_tomanwalletsettings'),
                    _item('پلن‌ها', 'sell', _changelist('accounting', 'plan'), 'accounting.view_plan'),
                    _item('بسته‌ها', 'inventory_2', _changelist('accounting', 'addonpack'), 'accounting.view_addonpack'),
                    _item('یادآوری تمدید', 'schedule_send', _changelist('accounting', 'subscriptionremindersettings'), 'accounting.view_subscriptionremindersettings'),
                    _item('نمایش تماس', 'visibility_off', _changelist('accounting', 'contactvisibilitypolicy'), 'accounting.view_contactvisibilitypolicy'),
                ],
            },
            {
                'title': 'سیستم',
                'separator': True,
                'collapsible': True,
                'items': [
                    _item('نسخهٔ اپ', 'system_update', _changelist('versions', 'appversion'), 'versions.view_appversion'),
                    _item('ربات بله', 'smart_toy', _changelist('bale', 'balesettings'), 'bale.view_balesettings'),
                    _item('نقش‌ها', 'admin_panel_settings', _changelist('auth', 'group'), 'auth.view_group'),
                ],
            },
        ],
    },
}
