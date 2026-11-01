"""A business's social pages: Instagram, Telegram, WhatsApp, Eitaa, Bale,
Rubika and a website.

Stored on ``Business.social_links`` as ``{kind: value}``. The value is the
bare handle (``noobatyar``), a mobile number for WhatsApp (``09121234567``),
or a full ``https://`` URL for the website - never a messenger URL, so the
clients build the link themselves and a stored value can never point anywhere
but the network its key names.

Owners paste whatever they have: ``@name``, ``instagram.com/name/``,
``https://t.me/name``, ``wa.me/98912...``. ``normalize_social_links`` accepts
all of those and reduces them to the stored form.
"""
import re
from urllib.parse import urlparse

from common.phone import is_iran_mobile, normalize_phone

# Order is the order clients show them in.
SOCIAL_KINDS = ('instagram', 'telegram', 'whatsapp', 'eitaa', 'bale', 'rubika', 'website')

LABELS = {
    'instagram': 'اینستاگرام',
    'telegram': 'تلگرام',
    'whatsapp': 'واتس‌اپ',
    'eitaa': 'ایتا',
    'bale': 'بله',
    'rubika': 'روبیکا',
    'website': 'وب‌سایت',
}

# Hosts a pasted link may come from, per kind; the path's first segment is
# the handle.
_HOSTS = {
    'instagram': ('instagram.com', 'instagr.am'),
    'telegram': ('t.me', 'telegram.me'),
    'eitaa': ('eitaa.com',),
    'bale': ('ble.ir', 'bale.ai'),
    'rubika': ('rubika.ir',),
}

_HANDLE = {
    'instagram': re.compile(r'^(?!.*\.\.)[A-Za-z0-9._]{1,30}$'),
    'telegram': re.compile(r'^[A-Za-z][A-Za-z0-9_]{4,31}$'),
    'eitaa': re.compile(r'^[A-Za-z][A-Za-z0-9_]{2,31}$'),
    'bale': re.compile(r'^[A-Za-z][A-Za-z0-9_]{2,31}$'),
    'rubika': re.compile(r'^[A-Za-z][A-Za-z0-9_.]{2,31}$'),
}

WEBSITE_MAX = 200


class SocialLinkError(ValueError):
    """``str(err)`` is a message for the owner."""


def _handle_from(kind, raw):
    text = raw.strip()
    lowered = text.lower()
    for host in _HOSTS[kind]:
        for prefix in (f'https://www.{host}/', f'http://www.{host}/', f'https://{host}/',
                       f'http://{host}/', f'www.{host}/', f'{host}/'):
            if lowered.startswith(prefix):
                text = text[len(prefix):].split('?')[0].split('#')[0].strip('/').split('/')[0]
                break
        else:
            continue
        break
    text = text.lstrip('@')
    if not _HANDLE[kind].match(text):
        raise SocialLinkError(f'شناسه {LABELS[kind]} درست نیست؛ فقط حروف انگلیسی، عدد و _ (مثل @noobatyar)')
    return text


def _whatsapp(raw):
    text = raw.strip()
    for prefix in ('https://wa.me/', 'http://wa.me/', 'wa.me/', 'https://api.whatsapp.com/send?phone='):
        if text.lower().startswith(prefix):
            text = text[len(prefix):].split('?')[0].split('&')[0]
            break
    phone = normalize_phone(text)
    if not is_iran_mobile(phone):
        raise SocialLinkError('شماره واتس‌اپ باید موبایل ۱۱ رقمی باشد (مثل ۰۹۱۲۱۲۳۴۵۶۷)')
    return phone


def _website(raw):
    text = raw.strip()
    if '://' not in text:
        text = 'https://' + text
    parsed = urlparse(text)
    if parsed.scheme not in ('http', 'https') or '.' not in (parsed.hostname or '') \
            or len(text) > WEBSITE_MAX or any(c.isspace() for c in text):
        raise SocialLinkError('آدرس وب‌سایت درست نیست (مثل example.ir)')
    return text


def normalize_social_links(value):
    """-> the stored dict; raises SocialLinkError. Blank values are dropped."""
    if value in (None, ''):
        return {}
    if not isinstance(value, dict):
        raise SocialLinkError('شبکه‌های اجتماعی باید به شکل {نوع: شناسه} باشد')
    out = {}
    for kind in SOCIAL_KINDS:
        raw = value.get(kind)
        if raw is None or not str(raw).strip():
            continue
        raw = str(raw)
        if kind == 'whatsapp':
            out[kind] = _whatsapp(raw)
        elif kind == 'website':
            out[kind] = _website(raw)
        else:
            out[kind] = _handle_from(kind, raw)
    unknown = set(value) - set(SOCIAL_KINDS)
    if unknown:
        raise SocialLinkError(f'نوع ناشناخته: {", ".join(sorted(unknown))}')
    return out
