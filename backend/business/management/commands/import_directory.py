# backend/business/management/commands/import_directory.py
"""
Bulk directory importer for unclaimed business listings from external sources
(e.g., Medical Council directory, syndicate datasets). The same import the
admin's «ورود از فایل» page runs; see business/directory_import.py for the
rules (column names, deduplication, what is a warning).

Usage:
    python manage.py import_directory --file <path> --source <name> [--batch-size 1000]
"""
import os

from django.core.management.base import BaseCommand, CommandError

from business.directory_import import read_rows, run_import


class Command(BaseCommand):
    help = "Bulk imports directory business listings from JSON, JSONL, or CSV files."

    def add_arguments(self, parser):
        parser.add_argument('--file', type=str, required=True, help="Path to the file to import (.json, .jsonl, .csv)")
        parser.add_argument(
            '--source', type=str, default='directory_import',
            help="Source dataset identifier for imported listings (e.g. tehran_medical_council)",
        )
        parser.add_argument('--batch-size', type=int, default=1000, help="Records to insert per batch (default: 1000)")
        parser.add_argument('--dry-run', action='store_true', help="Check the file and report, without saving")

    def handle(self, *args, **options):
        path = options['file']
        if not os.path.exists(path):
            raise CommandError(f"File not found: {path}")
        try:
            rows = read_rows(path)
            report = run_import(
                rows, options['source'],
                dry_run=options['dry_run'], require_city=False,
                batch_size=max(1, options['batch_size']), start_row=1,
            )
        except ValueError as exc:
            raise CommandError(str(exc))

        verb = 'Would import' if report.dry_run else 'Imported'
        self.stdout.write(self.style.SUCCESS(
            f"Directory import completed! Total rows read: {report.total}, "
            f"{verb}: {report.created}, Skipped/Duplicates: {report.skipped}."
        ))
