"""Postgres-only indexes for the public directory (SQLite skips them).

* pg_trgm GIN indexes serve the ``icontains`` search over title / address /
  city / neighborhood, which otherwise seq-scans. Django 4.2 renders
  ``icontains`` on Postgres as ``UPPER("col"::text) LIKE UPPER('%q%')``, so the
  indexes are on that exact expression — a plain ``col gin_trgm_ops`` index
  would never be chosen.
* UPPER(unique_code) serves ``unique_code__iexact``.
* A partial index matching Business.public_filter() serves the default listing
  order (-is_claimed, -created_at).

Every index is built/dropped CONCURRENTLY (no write lock on a live table), so
the migration is non-atomic and each statement runs on its own.

They are created with raw SQL rather than model ``Meta.indexes`` because they
are Postgres-specific operator-class / expression / partial indexes that the
SQLite test database cannot build, and keeping them out of the model state means
``makemigrations`` stays clean on both backends.
"""
from django.db import migrations

TRGM_COLUMNS = ('title', 'address', 'city', 'neighborhood')

PUBLIC_WHERE = (
    "NOT is_locked AND (moderation_status = 'APPROVED' "
    "OR (moderation_status = 'PENDING' AND first_approved_at IS NOT NULL))"
)


class PostgresOnlySQL(migrations.RunSQL):
    """RunSQL that is a no-op on any backend other than PostgreSQL."""

    def database_forwards(self, app_label, schema_editor, from_state, to_state):
        if schema_editor.connection.vendor == 'postgresql':
            super().database_forwards(app_label, schema_editor, from_state, to_state)

    def database_backwards(self, app_label, schema_editor, from_state, to_state):
        if schema_editor.connection.vendor == 'postgresql':
            super().database_backwards(app_label, schema_editor, from_state, to_state)


def _index(name, create_sql):
    return PostgresOnlySQL(
        sql=f"CREATE INDEX CONCURRENTLY IF NOT EXISTS {name} {create_sql}",
        reverse_sql=f"DROP INDEX CONCURRENTLY IF EXISTS {name}",
    )


class Migration(migrations.Migration):
    # CREATE INDEX CONCURRENTLY cannot run inside a transaction block.
    atomic = False

    dependencies = [
        ('business', '0030_business_city_code'),
    ]

    operations = [
        # The extension is left installed on reverse: other objects may rely on it.
        PostgresOnlySQL(sql="CREATE EXTENSION IF NOT EXISTS pg_trgm", reverse_sql=migrations.RunSQL.noop),
        *[
            _index(
                f"biz_{col}_trgm_idx",
                f"ON business USING gin (UPPER({col}::text) gin_trgm_ops)",
            )
            for col in TRGM_COLUMNS
        ],
        _index("biz_unique_code_upper_idx", "ON business (UPPER(unique_code))"),
        _index(
            "biz_public_list_idx",
            f"ON business (is_claimed DESC, created_at DESC) WHERE {PUBLIC_WHERE}",
        ),
    ]
