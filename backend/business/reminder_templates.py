"""
business/reminder_templates.py

The fixed wordings a PANEL reminder SMS can use.

A PANEL reminder leaves Noobatyar's *advertising* line and is billed to the
owner's plan, so its text is not free-form: the owner picks one of these by
key (``Business.reminder_template``) and cannot edit it. That keeps every
message the line sends within its rules — no full stops (the provider's
filter reads one as a link), no emoji (each costs two UCS-2 characters and
tips a message into a second billed part) and a length that stays inside two
parts even with a long business name — and ``api.sms.signed`` still appends
the footer and the mandatory لغو11 after it.

MANUAL reminders are unaffected: those leave the owner's own SIM from inside
the app, where the owner is free to write whatever they like.

The owner app carries a copy of these strings (keyed identically) to render a
preview; the server is the one that actually sends, so this file is the source
of truth. Keep the two in sync when editing a wording.
"""

from api.jalali import format_date, _localize

DEFAULT_TEMPLATE = 'classic'

# key -> (label shown in admin/choices, wording)
TEMPLATES = {
    'classic': (
        'رسمی',
        '{visitor} عزیز، نوبت شما در {business} ساعت {time} تاریخ {date} است\n'
        'لطفاً چند دقیقه زودتر تشریف بیاورید',
    ),
    'friendly': (
        'دوستانه',
        'سلام {visitor} جان\n'
        '{minutes} دقیقه دیگر نوبت شما در {business} است (ساعت {time})\n'
        'منتظرتان هستیم',
    ),
    'short': (
        'کوتاه',
        'یادآوری نوبت {business}: امروز ساعت {time}\n'
        '{visitor} عزیز منتظرتان هستیم',
    ),
    'confirm': (
        'با درخواست تماس',
        '{visitor} عزیز، نوبت شما در {business} ساعت {time} تاریخ {date} است\n'
        'اگر امکان حضور ندارید لطفاً با ما تماس بگیرید',
    ),
}

CHOICES = [(key, label) for key, (label, _) in TEMPLATES.items()]


def render(template_key, *, visitor_name, business_title, appointment_date, minutes_before):
    """The reminder body (unsigned) for one appointment.

    An unknown key — a template retired after a business chose it — falls
    back to the default rather than sending nothing.
    """
    _, text = TEMPLATES.get(template_key) or TEMPLATES[DEFAULT_TEMPLATE]
    local = _localize(appointment_date)
    return (
        text
        .replace('{visitor}', visitor_name or '')
        .replace('{business}', business_title or '')
        .replace('{date}', format_date(appointment_date))
        .replace('{time}', f'{local:%H:%M}')
        .replace('{minutes}', str(minutes_before or 0))
    )
