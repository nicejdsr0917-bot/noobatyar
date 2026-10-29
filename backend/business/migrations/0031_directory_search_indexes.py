"""Postgres-only indexes for the public directory (SQLite skips them).

* pg_trgm GIN indexes serve the ``icontains`` (ILIKE '%q%') search over
  title / address / city / neighborhood, which otherwise seq-scans.
* UPPER(unique_code) serves ``unique_code__iexact``.
* A partial index matching Business.public_filter() serves the default listing
  order (-is_claimed, -created_at).

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


def _statements():
    yield "CREATE EXTENSION IF NOT EXISTS pg_trgm"
    for col in TRGM_COLUMNS:
        yield (
            f"CREATE INDEX IF NOT EXISTS biz_{col}_trgm_idx "
            f"ON business USING gin ({col} gin_trgm_ops)"
        )
    yield "CREATE INDEX IF NOT EXISTS biz_unique_code_upper_idx ON business (UPPER(unique_code))"
    yield (
        "CREATE INDEX IF NOT EXISTS biz_public_list_idx "
        f"ON business (is_claimed DESC, created_at DESC) WHERE {PUBLIC_WHERE}"
    )


def _drops():
    yield "DROP INDEX IF EXISTS biz_public_list_idx"
    yield "DROP INDEX IF EXISTS biz_unique_code_upper_idx"
    for col in TRGM_COLUMNS:
        yield f"DROP INDEX IF EXISTS biz_{col}_trgm_idx"
    # The extension is left installed: other objects may come to rely on it.


def create(apps, schema_editor):
    if schema_editor.connection.vendor != 'postgresql':
        return
    for sql in _statements():
        schema_editor.execute(sql)


def drop(apps, schema_editor):
    if schema_editor.connection.vendor != 'postgresql':
        return
    for sql in _drops():
        schema_editor.execute(sql)


class Migration(migrations.Migration):

    dependencies = [
        ('business', '0030_business_city_code'),
    ]

    operations = [
        migrations.RunPython(create, drop),
    ]
