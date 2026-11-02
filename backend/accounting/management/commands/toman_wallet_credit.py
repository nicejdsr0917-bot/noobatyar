# accounting/management/commands/toman_wallet_credit.py
"""Credit (or with --debit, take from) an owner's toman wallet by phone number."""

from django.contrib.auth import get_user_model
from django.core.management.base import BaseCommand, CommandError

from accounting import toman_wallet


class Command(BaseCommand):
    help = "افزایش یا کسر دستی موجودی کیف‌پول تومانی یک کاربر (با شماره موبایل)"

    def add_arguments(self, parser):
        parser.add_argument("phone")
        parser.add_argument("amount", type=int, help="مبلغ به تومان")
        parser.add_argument("--debit", action="store_true", help="به‌جای افزایش، کسر کن")
        parser.add_argument("--reason", default="", help="کد دلیل (پیش‌فرض admin_credit / admin_debit)")

    def handle(self, phone, amount, debit, reason, **options):
        user = get_user_model().objects.filter(phone=phone).first()
        if user is None:
            raise CommandError("کاربری با این شماره پیدا نشد")
        if amount <= 0:
            raise CommandError("مبلغ باید بیشتر از صفر باشد")
        if debit:
            balance = toman_wallet.spend(user, amount, reason or toman_wallet.REASON_ADMIN_DEBIT, ref_type="admin")
            if balance is None:
                raise CommandError("موجودی کافی نیست")
        else:
            balance = toman_wallet.credit(user, amount, reason or toman_wallet.REASON_ADMIN_CREDIT, ref_type="admin")
        self.stdout.write(self.style.SUCCESS(f"موجودی جدید: {balance:,} تومان"))
