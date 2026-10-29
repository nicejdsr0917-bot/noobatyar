from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import AllowAny
from .models import AppVersion
from .serializers import AppVersionSerializer


def _requested_app(raw):
    """Missing/blank -> OWNER (old clients). Unknown value -> None."""
    if raw in (None, ''):
        return AppVersion.App.OWNER
    value = str(raw).strip().upper()
    return value if value in AppVersion.App.values else None


class CheckVersionView(APIView):
    """
    POST /api/version/check/
    body: {"version_code": 100, "app": "OWNER"|"CLIENT"}  (app optional, default OWNER)
    """
    permission_classes = [AllowAny] 

    def post(self, request):
        client_version_code = request.data.get('version_code')

        if not client_version_code:
            return Response({
                "status": "error",
                "code": 400,
                "message": "version_code الزامی است",
                "data": None
            }, status=status.HTTP_400_BAD_REQUEST)

        app = _requested_app(request.data.get('app'))
        if app is None:
            return Response({
                "status": "error", "code": 400,
                "message": "app نامعتبر است", "data": None
            }, status=status.HTTP_400_BAD_REQUEST)

        # آخرین نسخه فعال
        latest = AppVersion.objects.filter(is_active=True, app=app).first()

        if not latest:
            return Response({
                "status": "error",
                "code": 404,
                "message": "نسخه‌ای یافت نشد",
                "data": None
            }, status=status.HTTP_404_NOT_FOUND)

        try:
            client_code = int(client_version_code)
        except (ValueError, TypeError):
            return Response({
                "status": "error",
                "code": 400,
                "message": "version_code باید عدد باشد",
                "data": None
            }, status=status.HTTP_400_BAD_REQUEST)

        is_outdated = client_code < latest.version_code

        return Response({
            "status": "success",
            "code": 200,
            "message": "بررسی نسخه موفق",
            "data": {
                "is_outdated": is_outdated,
                "force_update": latest.force_update if is_outdated else False,
                "latest_version": AppVersionSerializer(latest).data
            }
        })


class ChangelogListView(APIView):
    """
    GET /api/version/changelog/
    """
    permission_classes = [AllowAny] 

    def get(self, request):
        app = _requested_app(request.query_params.get('app'))
        if app is None:
            app = AppVersion.App.OWNER
        versions = AppVersion.objects.filter(is_active=True, app=app)

        changelog = [
            {
                "version_name": v.version_name,
                "version_code": v.version_code,
                "changes": v.changelog.changes if hasattr(v, 'changelog') else []
            }
            for v in versions
        ]

        return Response({
            "status": "success",
            "code": 200,
            "message": "لیست تغییرات",
            "data": changelog
        })
