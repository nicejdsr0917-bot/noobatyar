"""
Subscription lifecycle job — intended to run periodically (e.g. hourly cron):

    python manage.py run_subscription_lifecycle

It performs three things:

  1. Renewal reminders — SMS to owners whose subscription ends within
     ``SubscriptionReminderSettings.days_before`` days and who haven't been
     reminded yet. Gated by ``SubscriptionReminderSettings.is_enabled`` —
     see the "تنظیمات یادآوری تمدید اشتراک" row in the Django admin.
  2. Grace-period expiry — subscriptions past ``ends_at + GRACE_DAYS`` are marked
     ``expired``.
  3. Graceful downgrade — for each just-expired user, excess businesses are
     locked (kept, not deleted) to match the new (baseline) quota.

Tunable via env: SUBSCRIPTION_GRACE_DAYS. The reminder toggle, lead time and
message text live in the admin (SubscriptionReminderSettings), not env vars —
see that model's docstring for why.
"""

import os
from datetime import timedelta

from django.core.management.base import BaseCommand
from django.utils import timezone

from accounting.models import Subscription, SubscriptionReminderSettings

GRACE_DAYS = int(os.getenv("SUBSCRIPTION_GRACE_DAYS", "3"))


class Command(BaseCommand):
    help = "ارسال یادآوری تمدید، انقضای اشتراک‌ها با مهلت، و قفل کسب‌وکارهای مازاد"

    def handle(self, *args, **options):
        now = timezone.now()
        self._send_renewal_reminders(now)
        self._expire_and_downgrade(now)
        self.stdout.write(self.style.SUCCESS("چرخه‌ی عمر اشتراک‌ها اجرا شد"))

    # ── 1. Renewal reminders ─────────────────────────────────────────────────
    def _send_renewal_reminders(self, now):
        from api.sms import send_sms, signed

        from core.models import ExpirySmsLog

        settings_row = SubscriptionReminderSettings.load()
        # Stamped on every run, switch on or off — the admin shows it so an
        # operator can see whether anything is actually scheduling this job.
        SubscriptionReminderSettings.objects.filter(pk=settings_row.pk).update(last_run_at=now)
        if not settings_row.is_enabled:
            self.stdout.write("یادآوری تمدید: خاموش است (SubscriptionReminderSettings.is_enabled=False)")
            return

        window_end = now + timedelta(days=settings_row.days_before)
        due = Subscription.objects.filter(
            status="active",
            reminder_sent=False,
            ends_at__gt=now,
            ends_at__lte=window_end,
        ).select_related("user", "plan")

        sent = failed = 0
        recipients = []
        for sub in due:
            name = sub.user.name or ""
            phone = getattr(sub.user, "phone", None)
            ok, err = False, "بدون شماره تلفن"
            if phone:
                days = max(1, sub.days_left())
                msg = signed(settings_row.render_message(
                    plan_name=sub.plan.name, days_left=days, owner_name=name,
                ))
                try:
                    # Platform-level notice to the owner about their own
                    # subscription, so it is not billed to their SMS quota.
                    ok, err = send_sms(phone, msg)
                    if not ok:
                        self.stderr.write(f"reminder SMS failed for {phone}: {err}")
                except Exception as exc:  # noqa: BLE001 — reminder must not crash the job
                    ok, err = False, str(exc)
                    self.stderr.write(f"reminder SMS failed for {phone}: {exc}")
            if ok:
                sent += 1
                recipients.append({"name": name, "phone": phone, "status": "SENT"})
            else:
                failed += 1
                recipients.append({"name": name, "phone": phone or "", "status": "FAILED", "error": err or ""})
            sub.reminder_sent = True
            sub.save(update_fields=["reminder_sent"])

        # In the same report as the admin's manual sends, so it shows
        # everything that actually went out — not only what staff sent.
        if recipients:
            ExpirySmsLog.objects.create(
                source=ExpirySmsLog.SOURCE_AUTO,
                message=settings_row.message_template,
                days_before=settings_row.days_before,
                filters={"plans": [], "skip_reminded": True},
                matched_count=len(recipients), sent_count=sent, failed_count=failed,
                recipients=recipients, sent_by=None,
            )

        self.stdout.write(f"یادآوری تمدید: {len(recipients)} مورد")

    # ── 2 & 3. Expiry + graceful downgrade ───────────────────────────────────
    def _expire_and_downgrade(self, now):
        from business.services import sync_locks

        cutoff = now - timedelta(days=GRACE_DAYS)
        expired_subs = Subscription.objects.filter(
            status="active",
            ends_at__lte=cutoff,
        ).select_related("user")

        affected_user_ids = set()
        count = 0
        for sub in expired_subs:
            sub.status = "expired"
            sub.save(update_fields=["status"])
            affected_user_ids.add(sub.user_id)
            count += 1

        # Re-sync business locks now that these users have dropped to baseline.
        for user in _users_by_ids(affected_user_ids):
            sync_locks(user)

        self.stdout.write(f"اشتراک منقضی‌شده: {count} مورد، قفل کسب‌وکار برای {len(affected_user_ids)} کاربر")


def _users_by_ids(user_ids):
    if not user_ids:
        return []
    from django.contrib.auth import get_user_model
    User = get_user_model()
    return list(User.objects.filter(id__in=user_ids))
