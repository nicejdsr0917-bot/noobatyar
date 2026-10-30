"""
Appointment reminder logic. The ``send_appointment_reminders`` command (run every
few minutes via cron) is a thin loop over :func:`run`.

``Business.enable_reminder_sms`` and ``Business.notification_minutes_before``
existed as settings for a long time but nothing ever acted on them, so clients
never received a reminder. This command closes that gap: for every upcoming
appointment whose reminder window has opened, it sends one SMS and stamps
``reminder_sent_at`` so the same appointment is never reminded twice.

Three channels, deliberately independent:

* **The client's SMS** — only for businesses on ``reminder_delivery='PANEL'``,
  the paid automatic channel gated behind ``auto_reminder_sms``. ``MANUAL``
  businesses (the default) send their reminders from the owner's own SIM inside
  the owner app; this job must send nothing and charge nothing for them.
  Stamped with ``reminder_sent_at``.
* **The owner's push** — free, so it goes out for *every* business with
  notifications on, whichever way the client is reminded. Stamped with
  ``reminder_push_sent_at``. A business on MANUAL depends on this push: it is
  the prompt to go and text the client.
* **The client's push** — also free, but a plan-tier perk: only businesses
  whose plan already includes ``auto_reminder_sms`` (the same entitlement
  that unlocks PANEL client SMS) get it, independent of which
  ``reminder_delivery`` they actually chose — same "free, so it goes out
  regardless of the SMS channel" reasoning as the owner's push above.
  Stamped with ``reminder_visitor_push_sent_at``.
"""

from datetime import timedelta

from django.db.models import Max, Q
from django.utils import timezone

from accounting import sms_billing
from api.jalali import format_datetime
from appointment.models import Appointment
from business import reminder_templates
from business.models import Business

# Statuses worth reminding about: the appointment is live and still expected.
REMINDABLE_STATUSES = ('WAITING', 'CONFIRMED')

# Ignore appointments whose reminder window opened long ago (e.g. after an
# outage) so a restarted job does not blast out a backlog of stale reminders.
MAX_LATENESS = timedelta(hours=2)


def _warn(stderr, message):
    if stderr is not None:
        stderr.write(message)


def claim(appointment, field, now):
    """
    Atomically claim one reminder channel for this appointment.

    A conditional UPDATE (stamp only while still NULL) decides which of
    two overlapping cron runs owns the send; the loser sees 0 rows and
    sends nothing. Stamped *before* sending on purpose — a provider that
    accepted the message but errored must not be retried every run.
    """
    claimed = Appointment.objects.filter(
        pk=appointment.pk, status__in=REMINDABLE_STATUSES, **{f'{field}__isnull': True}
    ).update(**{field: now, 'updated_at': now})
    if claimed:
        setattr(appointment, field, now)
    return bool(claimed)

def release(appointment, field, now):
    """Give a claim back when nothing was actually sent (e.g. no SMS credit)."""
    Appointment.objects.filter(pk=appointment.pk, **{field: now}).update(
        **{field: None, 'updated_at': timezone.now()}
    )
    setattr(appointment, field, None)

def sms_due(appointment):
    """Whether this appointment still owes the *client* an SMS.

    MANUAL businesses never do — their reminder leaves the owner's own SIM
    from inside the app, so sending here would both duplicate the message
    and spend quota the owner deliberately chose not to spend.
    """
    business = appointment.business
    return (
        appointment.reminder_sent_at is None
        and business.enable_reminder_sms
        and business.reminder_delivery == 'PANEL'
    )

def visitor_push_due(appointment):
    """
    Whether this appointment still owes the *client* a push reminder.

    Gated on the business's plan already including auto_reminder_sms
    (پرو/پرو پلاس and up) — the same entitlement that unlocks PANEL SMS —
    rather than on reminder_delivery itself: push is free, so a MANUAL
    business on that plan tier still gets it, same reasoning as the
    owner's own push.
    """
    from accounting.entitlements import FEATURE_AUTO_REMINDER_SMS, has_feature

    business = appointment.business
    return (
        appointment.reminder_visitor_push_sent_at is None
        and business.notification_enabled
        and has_feature(business.user_id, FEATURE_AUTO_REMINDER_SMS)
    )

def due_appointments(now):
    """
    Appointments whose reminder window has opened but that have not been
    reminded yet — on either channel.

    The window opens at ``appointment_date - notification_minutes_before``,
    which varies per business. Rather than express that subtraction in SQL
    (duration arithmetic on mixed types behaves differently on SQLite and
    PostgreSQL, the two backends this project uses), the query narrows to
    the widest possible window and the exact per-business cutoff is applied
    in Python. The candidate set is only "appointments starting soon", so
    this stays small.

    Unlike the first version of this job the filter is no longer restricted
    to PANEL businesses: a MANUAL one still needs the owner's push, which is
    free. Whether the *client* also gets an SMS is decided per appointment
    by :func:`sms_due`.
    """
    widest = (
        Business.objects
        .filter(notification_enabled=True)
        .aggregate(m=Max('notification_minutes_before'))['m']
    )
    if not widest:
        return []

    candidates = (
        Appointment.objects
        .filter(
            status__in=REMINDABLE_STATUSES,
            appointment_date__gt=now,
            appointment_date__lte=now + timedelta(minutes=widest),
            business__notification_enabled=True,
        )
        .filter(
            Q(reminder_sent_at__isnull=True)
            | Q(reminder_push_sent_at__isnull=True)
            | Q(reminder_visitor_push_sent_at__isnull=True)
        )
        .select_related('business', 'visitor')
        .order_by('appointment_date')
    )

    window_floor = now - MAX_LATENESS
    due = []
    for appointment in candidates:
        lead = timedelta(minutes=appointment.business.notification_minutes_before or 0)
        reminder_due = appointment.appointment_date - lead
        if window_floor <= reminder_due <= now:
            due.append(appointment)

    return due

def push_owner(appointment, now, stderr=None):
    """
    Notify the owner's phone through FCM. Returns 1 if any device took it.

    Stamped even when nothing was delivered — an owner with no registered
    device (push not set up, or app not installed) must not make this job
    re-check the same appointment every few minutes until it starts.
    """
    from accounting.entitlements import FEATURE_PUSH_NOTIFICATIONS, has_feature
    from api.services import push

    if not claim(appointment, 'reminder_push_sent_at', now):
        return 0

    business = appointment.business
    delivered = 0
    if push.is_configured() and has_feature(business.user_id, FEATURE_PUSH_NOTIFICATIONS):
        body = (
            f"{appointment.visitor.full_name} — "
            f"{format_datetime(appointment.appointment_date)}"
        )
        if business.reminder_delivery != 'PANEL':
            body += "\nیادآوری را برای مشتری بفرستید"
        try:
            delivered = push.send_to_user(
                business.user_id,
                title=f"یادآوری نوبت · {business.title}",
                body=body,
                data={
                    'type': 'APPOINTMENT_REMINDER',
                    'appointment_id': appointment.id,
                    'business_id': business.id,
                    'visitor_id': appointment.visitor_id,
                },
            )
        except Exception as exc:
            # Push is the convenience channel; the SMS below is what
            # actually carries the message. Never let it break the run.
            _warn(stderr, f"#{appointment.id}: ارسال اعلان ناموفق: {exc}")

    return 1 if delivered else 0

def push_visitor(appointment, now, stderr=None):
    """
    Notify the client's own device through FCM. Returns 1 if any device
    took it. Stamped even when nothing was delivered, and always logged
    to PushLog (unlike the owner's push, which only stamps a timestamp) —
    this is the record a future report screen reads to show "was this
    reminder delivered by push".
    """
    from api.services import push
    from visitor.models import PushLog

    if not claim(appointment, 'reminder_visitor_push_sent_at', now):
        return 0

    business = appointment.business
    visitor = appointment.visitor
    delivered = 0
    title = f"یادآوری نوبت · {business.title}"
    body = f"سلام {visitor.full_name}، نوبت شما در {business.title} نزدیک است."

    if push.is_configured():
        try:
            delivered = push.send_to_visitor(
                visitor.id,
                title=title,
                body=body,
                data={
                    'type': 'APPOINTMENT_REMINDER',
                    'appointment_id': appointment.id,
                    'business_id': business.id,
                },
            )
            PushLog.objects.create(
                business=business,
                visitor=visitor,
                appointment=appointment,
                title=title,
                body=body,
                status='SENT' if delivered else 'FAILED',
                error_detail='' if delivered else 'دستگاه فعالی برای این مراجع ثبت نشده است',
            )
        except Exception as exc:
            # Push is a convenience channel; the SMS above (if any) is
            # what actually carries the message. Never let it break the run.
            _warn(stderr, f"#{appointment.id}: ارسال اعلان به مشتری ناموفق: {exc}")
            PushLog.objects.create(
                business=business,
                visitor=visitor,
                appointment=appointment,
                title=title,
                body=body,
                status='FAILED',
                error_detail=str(exc),
            )

    return 1 if delivered else 0


def send_sms_reminder(appointment, now, stderr=None):
    """Send the client's SMS reminder. Returns 'sent', 'skipped' or 'failed'."""
    from api.sms import signed

    phone = appointment.visitor.phone_number
    if not phone:
        return 'skipped'

    business = appointment.business
    message = signed(reminder_templates.render(
        business.reminder_template,
        visitor_name=appointment.visitor.full_name,
        business_title=business.title,
        appointment_date=appointment.appointment_date,
        minutes_before=business.notification_minutes_before,
    ))

    # Claim before spending credit or sending: overlapping runs must not
    # both pass the (stale) reminder_sent_at-is-None check in sms_due.
    if not claim(appointment, 'reminder_sent_at', now):
        return 'skipped'

    status = sms_billing.send_metered_sms(
        business.user_id, appointment.business_id, appointment.visitor_id, phone, message,
    )
    if status == sms_billing.SKIPPED_QUOTA:
        # Nothing was sent: free the claim so a later run (after a top-up)
        # can still remind, as before.
        release(appointment, 'reminder_sent_at', now)
        _warn(stderr, f"#{appointment.id}: اعتبار پیامک کسب‌وکار {appointment.business_id} تمام شده است")
        return 'skipped'

    # The claim above stays even on failure: the provider may still have
    # delivered it, and retrying every few minutes would be worse than
    # missing one reminder.
    return 'sent' if status == sms_billing.SENT else 'failed'


def run(now, dry_run=False, stdout=None, stderr=None):
    """Process every due appointment; returns the counters the command prints."""
    due = due_appointments(now)

    counts = dict(sent=0, skipped=0, failed=0, pushed=0, visitor_pushed=0)
    for appointment in due:
        if dry_run:
            if stdout is not None:
                stdout.write(
                    f"[dry-run] #{appointment.id} → {appointment.visitor.phone_number} "
                    f"({appointment.appointment_date.isoformat()}) "
                    f"sms={'yes' if sms_due(appointment) else 'no'} "
                    f"push={'yes' if appointment.reminder_push_sent_at is None else 'no'} "
                    f"visitor_push={'yes' if visitor_push_due(appointment) else 'no'}"
                )
            # Count what would *actually* happen, not one-per-appointment:
            # the summary used to report an SMS for a MANUAL business that
            # the real run correctly refuses to send.
            if sms_due(appointment):
                counts['sent'] += 1
            if appointment.reminder_push_sent_at is None:
                counts['pushed'] += 1
            if visitor_push_due(appointment):
                counts['visitor_pushed'] += 1
            continue

        if appointment.reminder_push_sent_at is None:
            counts['pushed'] += push_owner(appointment, now, stderr)

        if visitor_push_due(appointment):
            counts['visitor_pushed'] += push_visitor(appointment, now, stderr)

        if not sms_due(appointment):
            continue

        result = send_sms_reminder(appointment, now, stderr)
        if result == 'sent':
            counts['sent'] += 1
        elif result == 'skipped':
            counts['skipped'] += 1
        else:
            counts['failed'] += 1
    return counts
