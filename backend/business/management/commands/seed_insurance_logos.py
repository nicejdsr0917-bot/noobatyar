from pathlib import Path

from django.core.files import File
from django.core.management.base import BaseCommand

from business.cache import bump_all
from business.models import Insurance

# business/insurance_logos/<slug>.png, 256px. Supplementary insurers from
# bimeh.com's vector set; تامین اجتماعی from Wikimedia Commons; سلامت from the
# owner-supplied Corel preview. آتیه‌سازان and نیروهای مسلح have none yet.
LOGO_DIR = Path(__file__).resolve().parents[2] / 'insurance_logos'


class Command(BaseCommand):
    help = 'Attaches the bundled insurer logos to the catalog (skips ones that already have a logo).'

    def add_arguments(self, parser):
        parser.add_argument('--force', action='store_true', help='Replace logos uploaded from the admin too.')

    def handle(self, *args, force=False, **options):
        attached = 0
        for path in sorted(LOGO_DIR.glob('*.png')):
            insurance = Insurance.objects.filter(slug=path.stem).first()
            if insurance is None or (insurance.logo and not force):
                continue
            with path.open('rb') as f:
                insurance.logo.save(path.name, File(f), save=True)
            attached += 1
        if attached:
            bump_all()
        self.stdout.write(self.style.SUCCESS(f'{attached} logos attached.'))
