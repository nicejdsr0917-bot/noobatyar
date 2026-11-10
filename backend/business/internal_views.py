"""Machine-to-machine endpoints for the n8n container (not part of the public API)."""
import hmac
import json

from django.conf import settings
from django.http import Http404, JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST

from .directory_sync import MAX_ROWS, sync_listings

SOURCE_MAX_LENGTH = 50


def _authorised(request):
    token = settings.DIRECTORY_SYNC_TOKEN
    header = request.META.get('HTTP_AUTHORIZATION', '')
    if not token or not header.startswith('Bearer '):
        return False
    return hmac.compare_digest(header[len('Bearer '):].encode(), token.encode())


@csrf_exempt
@require_POST
def directory_sync(request):
    """Body: {"source": "n8n-dana", "dry_run": true, "rows": [{title, city, phone, insurances, ...}]}."""
    # Unset token or a wrong one both look like "no such page".
    if not _authorised(request):
        raise Http404
    try:
        body = json.loads(request.body or b'{}')
    except ValueError:
        return JsonResponse({'detail': 'invalid JSON'}, status=400)
    rows = body.get('rows') if isinstance(body, dict) else None
    if not isinstance(rows, list) or not all(isinstance(r, dict) for r in rows):
        return JsonResponse({'detail': 'rows must be a list of objects'}, status=400)
    if len(rows) > MAX_ROWS:
        return JsonResponse({'detail': f'at most {MAX_ROWS} rows per call'}, status=413)
    source = str(body.get('source') or 'n8n')[:SOURCE_MAX_LENGTH]
    report = sync_listings(rows, source, dry_run=bool(body.get('dry_run')))
    return JsonResponse(report.as_dict())
