from django.db import models

class AppVersion(models.Model):
    class App(models.TextChoices):
        OWNER = 'OWNER', 'اپ مالک'
        CLIENT = 'CLIENT', 'اپ مشتری'

    # Which mobile app this release row belongs to. Existing rows (and old
    # clients that send no `app`) are the owner app.
    app = models.CharField(max_length=10, choices=App.choices,
                           default=App.OWNER, db_index=True, verbose_name='اپ')
    version_name = models.CharField(max_length=20, verbose_name='نام نسخه')  # 1.0.0
    version_code = models.IntegerField( verbose_name='کد نسخه')  # 123456789
    force_update = models.BooleanField(default=False, verbose_name='آپدیت اجباری')
    is_active = models.BooleanField(default=True, verbose_name='فعال')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ایجاد')

    class Meta:
        verbose_name = 'نسخه‌ی اپ'
        verbose_name_plural = 'نسخه‌های اپ'
        ordering = ['-version_code']
        constraints = [
            models.UniqueConstraint(fields=['app', 'version_code'],
                                    name='uniq_appversion_app_code'),
        ]

    def __str__(self):
        return f"{self.version_name} ({self.version_code})"


class VersionChangelog(models.Model):
    class Meta:
        verbose_name = 'تغییرات نسخه'
        verbose_name_plural = 'تغییرات نسخه'

    version = models.OneToOneField(
        AppVersion,
        on_delete=models.CASCADE,
        related_name='changelog'
    )
    changes = models.JSONField(default=list)  # ["change 1", "change 2"]

    def __str__(self):
        return f"Changelog {self.version.version_name}"
