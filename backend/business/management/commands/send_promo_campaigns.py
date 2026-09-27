"""
Resume owner promo-SMS campaigns whose background sender died:

    python manage.py send_promo_campaigns

Campaigns normally finish on their own thread moments after the owner hits
send (business/promo_sms.py). This only matters when that thread was killed
mid-list — a deploy or container restart — and is safe to run at any time or
on any schedule: a campaign whose sender is still alive is left alone.
"""

from django.core.management.base import BaseCommand

from business.promo_sms import resume_stalled


class Command(BaseCommand):
    help = "ادامه‌ی ارسال کمپین‌های پیامک تبلیغاتی نیمه‌کاره"

    def handle(self, *args, **options):
        resumed = resume_stalled()
        self.stdout.write(self.style.SUCCESS(f"کمپین‌های ادامه‌یافته: {resumed}"))
