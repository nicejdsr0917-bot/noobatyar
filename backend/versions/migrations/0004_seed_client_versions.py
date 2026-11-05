"""The client app's released versions, so its version check has rows to
compare with. Without any CLIENT row the check answered 404, which the client
app shows as a network error. Adds only rows that are missing; never touches
OWNER rows or an existing CLIENT row."""

from django.db import migrations

CLIENT_RELEASES = [
    ('1.1.0', 1789813665),
    ('1.2.0', 1789813666),
]


def seed(apps, schema_editor):
    AppVersion = apps.get_model('versions', 'AppVersion')
    for name, code in CLIENT_RELEASES:
        AppVersion.objects.get_or_create(
            app='CLIENT', version_code=code,
            defaults={'version_name': name, 'force_update': False, 'is_active': True},
        )


class Migration(migrations.Migration):

    dependencies = [
        ('versions', '0003_appversion_app'),
    ]

    operations = [
        migrations.RunPython(seed, migrations.RunPython.noop),
    ]
