# accounting/management/commands/migrate_credits_to_db.py
"""
Pre-warm ``CreditBalance`` (the DB source of truth for paid credit and monthly
quotas, see ``accounting/usage.py``) from what Redis holds today.

DEPLOY ORDER
  1. ``manage.py migrate`` (creates accounting_creditbalance; additive).
  2. Deploy the new code. No data step is required for correctness: the first
     time any bucket is touched without a row, usage.py seeds it from the
     legacy Redis value (or, when Redis has none, the latest CreditLedger
     row), so users never see a 0 balance.
  3. Run ``migrate_credits_to_db --dry-run`` and read the report, then run it
     for real. This only pre-creates the rows (default: create-if-missing,
     never touching an existing row, so it is safe to re-run at any time —
     including after the new code has taken traffic — and never rolls a DB
     balance back to a stale Redis number).
  4. Verify: re-run with ``--dry-run``; it should report 0 to create.
     ``--overwrite`` forces DB := Redis/ledger value for existing rows; only
     use it before the new code serves traffic (it would erase newer DB
     activity).

For each user and each bucket (sms_wallet, appointment_wallet, and this month's
sms_monthly / appointment_monthly) the value is: Redis if the key exists, else
the latest CreditLedger row, else 0. Zero buckets get no row (nothing to keep).
"""

from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand
from django.db import transaction

from accounting import usage
from accounting.models import CreditBalance, CreditLedger

User = get_user_model()

_BUCKETS = (
    (CreditLedger.METRIC_SMS_WALLET, False),
    (CreditLedger.METRIC_APPOINTMENT_WALLET, False),
    (CreditLedger.METRIC_SMS_MONTHLY, True),
    (CreditLedger.METRIC_APPOINTMENT_MONTHLY, True),
)


class Command(BaseCommand):
    help = "کپی موجودی کیف‌پول و سهمیه‌ی ماهانه از Redis (و در نبود آن از CreditLedger) به CreditBalance."

    def add_arguments(self, parser):
        parser.add_argument("--dry-run", action="store_true", help="فقط گزارش بده، چیزی نوشته نشود")
        parser.add_argument(
            "--overwrite", action="store_true",
            help="ردیف‌های موجود را هم با مقدار Redis/ledger جایگزین کن (فقط پیش از استقرار کد جدید)",
        )
        parser.add_argument("--user-id", type=int, default=None)

    def handle(self, *args, **options):
        dry_run, overwrite = options["dry_run"], options["overwrite"]
        users = User.objects.all().order_by("id")
        if options["user_id"] is not None:
            users = users.filter(pk=options["user_id"])

        period_now = usage._period()
        created = updated = skipped = 0
        for uid in users.values_list("id", flat=True).iterator():
            for metric, monthly in _BUCKETS:
                period = period_now if monthly else ""
                existing = CreditBalance.objects.filter(
                    user_id=uid, metric=metric, period=period).first()
                if existing is not None and not overwrite:
                    skipped += 1
                    continue
                value = usage._legacy_value(uid, metric, period)
                if existing is None and value == 0:
                    skipped += 1
                    continue
                if existing is not None and existing.balance == value:
                    skipped += 1
                    continue
                verb = "update" if existing is not None else "create"
                self.stdout.write(
                    f"user={uid} {metric} {period or '-'}: {verb} -> {value}"
                    f"{' (dry-run)' if dry_run else ''}")
                if existing is None:
                    created += 1
                else:
                    updated += 1
                if not dry_run:
                    with transaction.atomic():
                        CreditBalance.objects.update_or_create(
                            user_id=uid, metric=metric, period=period,
                            defaults={"balance": value})
        self.stdout.write(self.style.SUCCESS(
            f"{'(dry-run) ' if dry_run else ''}{created} ایجاد، {updated} به‌روزرسانی، {skipped} بدون تغییر"))
