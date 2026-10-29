"""
Appointment reminder job — intended to run every few minutes via cron:

    python manage.py send_appointment_reminders

All logic lives in ``appointment.services.reminders`` (channels, claims and
billing are documented there); this command is just the loop and the summary.

Options:
    --dry-run   report what would be sent without sending or stamping anything.
"""

from django.core.management.base import BaseCommand
from django.utils import timezone

from appointment.services import reminders


class Command(BaseCommand):
    help = "ارسال پیامک یادآوری برای نوبت‌های نزدیک"

    def add_arguments(self, parser):
        parser.add_argument(
            '--dry-run',
            action='store_true',
            help="فقط گزارش بده، پیامکی ارسال نکن",
        )

    def handle(self, *args, **options):
        counts = reminders.run(
            timezone.now(), dry_run=options['dry_run'], stdout=self.stdout, stderr=self.stderr,
        )
        summary = (
            f"یادآوری‌ها: {counts['sent']} پیامک ارسال، {counts['skipped']} رد شده، "
            f"{counts['failed']} ناموفق، {counts['pushed']} اعلان به اونر، "
            f"{counts['visitor_pushed']} اعلان به مشتری"
        )
        self.stdout.write(self.style.SUCCESS(summary))
