from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework import status
from django.utils import timezone
from datetime import datetime, timedelta, timezone as dt_timezone
from appointment import selectors
from appointment.models import Appointment
from appointment.serializers import AppointmentQuerySerializer
from common.responses import APIResponse



from api.pagination import StandardPagination

class AppointmentQueryView(StandardPagination):
    pass


def _utc_day_start(timestamp_ms):
    """Midnight (default timezone) of the UTC calendar day of an epoch-ms value."""
    day = datetime.fromtimestamp(int(timestamp_ms) / 1000, tz=dt_timezone.utc)
    return timezone.make_aware(datetime.combine(day.date(), datetime.min.time()))


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def appointment_list(request):
    """
    List appointments with flexible filtering.

    Query Parameters:
    - business_id (required): Filter by business ID
    - visitor_id (optional): Filter by visitor ID
    - date (optional): Filter by specific date (Unix timestamp in milliseconds)
    - date_from (optional): Filter from date (Unix timestamp in milliseconds)
    - date_to (optional): Filter to date (Unix timestamp in milliseconds)
    - status (optional): Filter by status (WAITING, COMPLETED, CANCELLED, NO_SHOW)
    - ordering (optional): Order by field (e.g., 'appointment_date', '-appointment_date')
    - page (optional): Page number
    - page_size (optional): Items per page (default: 20, max: 100)
    """

    # Validate required business_id parameter
    business_id = request.query_params.get('business_id')
    if not business_id:
        return APIResponse.error('پارامتر business_id الزامی است', code=status.HTTP_400_BAD_REQUEST)

    try:
        business_id = int(business_id)
    except ValueError:
        return APIResponse.error('شناسه کسب‌وکار (business_id) باید عدد صحیح معتبر باشد', code=status.HTTP_400_BAD_REQUEST)

    user = request.user

    # Scoped to the caller's own businesses (see the selector): an id the
    # caller doesn't own comes back empty and reveals nothing about existence.
    queryset = selectors.appointment_list_for_owner(user=user, business_id=business_id)

    # Filter by visitor_id
    visitor_id = request.query_params.get('visitor_id')
    if visitor_id:
        try:
            visitor_id = int(visitor_id)
            queryset = queryset.filter(visitor_id=visitor_id)
        except ValueError:
            return APIResponse.error('شناسه مشتری (visitor_id) باید عدد صحیح معتبر باشد', code=status.HTTP_400_BAD_REQUEST)

    # Filter by specific date
    date_param = request.query_params.get('date')
    if date_param:
        try:
            date_start = _utc_day_start(date_param)
            date_end = date_start + timedelta(days=1)
            queryset = queryset.filter(appointment_date__gte=date_start, appointment_date__lt=date_end)
        except (ValueError, OSError):
            return APIResponse.error('تاریخ (date) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد', code=status.HTTP_400_BAD_REQUEST)

    # Filter by date range
    date_from = request.query_params.get('date_from')
    date_to = request.query_params.get('date_to')

    if date_from:
        try:
            queryset = queryset.filter(appointment_date__gte=_utc_day_start(date_from))
        except (ValueError, OSError):
            return APIResponse.error('تاریخ شروع (date_from) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد', code=status.HTTP_400_BAD_REQUEST)

    if date_to:
        try:
            queryset = queryset.filter(appointment_date__lt=_utc_day_start(date_to) + timedelta(days=1))
        except (ValueError, OSError):
            return APIResponse.error('تاریخ پایان (date_to) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد', code=status.HTTP_400_BAD_REQUEST)

    # Filter by status
    status_param = request.query_params.get('status')
    if status_param:
        # Derive from the model so the filter never drifts out of sync with the
        # real status set (previously missing LOCKED and PENDING_VERIFICATION,
        # which made valid filters return 400).
        valid_statuses = [choice[0] for choice in Appointment.STATUS_CHOICES]
        if status_param.upper() not in valid_statuses:
            return APIResponse.error(f'وضعیت باید یکی از این مقادیر باشد: {", ".join(valid_statuses)}', code=status.HTTP_400_BAD_REQUEST)
        queryset = queryset.filter(status=status_param.upper())

    # Ordering
    ordering = request.query_params.get('ordering', '-appointment_date')
    valid_ordering_fields = ['appointment_date', '-appointment_date', 'created_at', '-created_at', 'status', '-status']
    if ordering in valid_ordering_fields:
        queryset = queryset.order_by(ordering)
    else:
        queryset = queryset.order_by('-appointment_date')

    # Paginate results
    paginator = AppointmentQueryView()
    paginated_queryset = paginator.paginate_queryset(queryset, request)

    # Serialize
    serializer = AppointmentQuerySerializer(paginated_queryset, many=True)

    paginated_response = paginator.get_paginated_response(serializer.data)
    return APIResponse.success(data=paginated_response.data)
