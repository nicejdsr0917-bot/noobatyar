# appointments/views.py
import logging
import uuid
from datetime import datetime, timezone
from typing import Optional, Dict, Any, Tuple

from adrf.views import APIView
from asgiref.sync import sync_to_async
from django.core.exceptions import ValidationError
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response

from appointment import selectors
from appointment.models import Appointment, Business, Visitor
from appointment.serializers import AppointmentQuerySerializer
from appointment.services.booking import (
    BookingFailure,
    appointment_create_for_owner,
    appointment_delete_for_owner,
    appointment_update_details_for_owner,
)
from appointment.services.notifications import notify_client_of_decision, send_client_sms
from appointment.services.transitions import (
    StatusChangeFailure,
    appointment_change_status_by_owner,
    appointment_update_details_and_status_for_owner,
)
from api.responses import APIResponse
from api.views import _extract_error

logger = logging.getLogger(__name__)


def _parse_timestamp(value) -> datetime:
    """Parse Unix epoch milliseconds to timezone-aware UTC datetime."""
    try:
        return datetime.fromtimestamp(int(value) / 1000, tz=timezone.utc)
    except (ValueError, TypeError, OSError) as e:
        raise ValueError(f"Invalid timestamp: {value}") from e


def _serialized(appointment):
    """AppointmentQuerySerializer data, evaluated in the calling (sync) thread."""
    return AppointmentQuerySerializer(appointment).data


class AppointmentView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request: Request, appointment_id: int) -> Response:
        """Get appointment details by ID"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()

        try:
            # Get appointment with ownership check
            appointment, error_response = await self._get_appointment_optimized(
                appointment_id, request.user, request_id
            )
            if error_response:
                return error_response

            # Serialize appointment data
            appointment_data = await sync_to_async(_serialized)(appointment)

            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.info(
                f"[{request_id}] Appointment retrieved",
                extra={
                    'appointment_id': appointment_id,
                    'elapsed_ms': elapsed_ms
                }
            )

            return APIResponse.success(data=appointment_data, message='جزئیات نوبت با موفقیت دریافت شد')

        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Error retrieving appointment",
                extra={
                    'appointment_id': appointment_id,
                    'error': str(e),
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    async def post(self, request: Request) -> Response:
        """Create new appointment with conflict detection"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()
        user = request.user

        try:
            # Extract and validate required fields
            business_id = request.data.get('business_id')
            visitor_id = request.data.get('visitor_id')
            appointment_date = request.data.get('appointment_date')
            service_duration = request.data.get('service_duration', 30)
            description = request.data.get('description', '')
            selected_services = request.data.get('selected_services', '')

            if not all([business_id, visitor_id, appointment_date]):
                logger.warning(
                    f"[{request_id}] Missing required fields",
                    extra={
                        'user_id': request.user.id,
                        'provided_fields': list(request.data.keys())
                    }
                )
                return APIResponse.error('فیلدهای business_id، visitor_id و appointment_date الزامی هستند', code=status.HTTP_400_BAD_REQUEST)

            # Verify ownership and get related objects
            business, visitor, error_response = await self._verify_ownership(
                request.user, business_id, visitor_id, request_id
            )
            if error_response:
                return error_response

            # Parse appointment date from Unix epoch milliseconds
            try:
                appointment_datetime = _parse_timestamp(appointment_date)
            except ValueError as e:
                logger.warning(
                    f"[{request_id}] Invalid timestamp: {appointment_date}",
                    extra={'error': str(e)}
                )
                return APIResponse.error('فرمت تاریخ نامعتبر است', code=status.HTTP_400_BAD_REQUEST)

            # Monthly quota (owner's plan) and the hourly capacity are checked
            # by the service, the latter under the business lock. The owner may
            # deliberately overlap appointments (walk-ins, parallel chairs), so
            # there is no time-overlap check — only the capacity cap, when
            # configured.
            try:
                result = await sync_to_async(appointment_create_for_owner)(
                    business=business,
                    visitor=visitor,
                    appointment_date=appointment_datetime,
                    service_duration=service_duration,
                    description=description,
                    selected_services=selected_services,
                    user=user,
                )
            except ValidationError as e:
                logger.error(
                    f"[{request_id}] Validation error creating appointment",
                    extra={'errors': e.message_dict}
                )
                raise
            if result.failure is BookingFailure.QUOTA:
                return APIResponse.error(
                    'ظرفیت نوبت‌های این ماه بر اساس پلن شما تکمیل شده است',
                    code=status.HTTP_409_CONFLICT,
                )
            if result.failure is not None:
                return APIResponse.error('ظرفیت این ساعت تکمیل شده است', code=status.HTTP_409_CONFLICT)
            appointment = result.appointment

            # Serialize response
            response_data = await sync_to_async(_serialized)(appointment)

            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.info(
                f"[{request_id}] Appointment created successfully",
                extra={
                    'appointment_id': appointment.id,
                    'business_id': business.id,
                    'visitor_id': visitor.id,
                    'elapsed_ms': elapsed_ms
                }
            )

            return APIResponse.success(data=response_data, message='نوبت با موفقیت ایجاد شد', status=status.HTTP_201_CREATED)

        except ValidationError as e:
            error_message = _extract_error(e.message_dict) if hasattr(e, 'message_dict') else str(e)
            return APIResponse.error(error_message, code=status.HTTP_400_BAD_REQUEST)
        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Unexpected error creating appointment",
                extra={
                    'error': str(e),
                    'error_type': type(e).__name__,
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    async def patch(self, request: Request, appointment_id: int) -> Response:
        """Update appointment with state machine validation"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()

        try:
            # Get appointment with optimized query
            appointment, error_response = await self._get_appointment_optimized(
                appointment_id, request.user, request_id
            )
            if error_response:
                return error_response

            # Determine update type based on payload
            update_fields = request.data.keys()
            is_status_only = 'status' in update_fields and len(update_fields) == 1
            is_details_update = any(
                field in update_fields
                for field in ['appointment_date', 'service_duration', 'description', 'selected_services']
            )

            if 'status' in update_fields and is_details_update:
                return await self._update_details_and_status(
                    appointment, request.data, request_id, start_time
                )
            if is_status_only:
                return await self._update_status_with_validation(
                    appointment, request.data['status'], request_id, start_time
                )
            elif is_details_update:
                return await self._update_details_with_conflict_check(
                    appointment, request.data, request_id, start_time
                )
            else:
                logger.warning(
                    f"[{request_id}] Invalid update payload",
                    extra={'provided_fields': list(update_fields)}
                )
                return APIResponse.error('فیلدهای قابل به‌روزرسانی نامعتبر هستند', code=status.HTTP_400_BAD_REQUEST)

        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Unexpected error updating appointment",
                extra={
                    'appointment_id': appointment_id,
                    'error': str(e),
                    'error_type': type(e).__name__,
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    async def delete(self, request: Request, appointment_id: int) -> Response:
        """Delete appointment (hard delete; the owner's credit is refunded)"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()

        try:
            # Get appointment with ownership check
            appointment, error_response = await self._get_appointment_optimized(
                appointment_id, request.user, request_id
            )
            if error_response:
                return error_response

            await sync_to_async(appointment_delete_for_owner)(appointment=appointment)
            logger.debug(
                f"[{request_id}] Appointment deleted permanently",
                extra={'appointment_id': appointment_id}
            )

            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.info(
                f"[{request_id}] Appointment deleted",
                extra={
                    'appointment_id': appointment_id,
                    'elapsed_ms': elapsed_ms
                }
            )

            return APIResponse.success(message='نوبت با موفقیت حذف شد')

        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Error deleting appointment",
                extra={
                    'appointment_id': appointment_id,
                    'error': str(e),
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    # ==================== Helper Methods ====================

    @sync_to_async
    def _verify_ownership(
            self, user, business_id: int, visitor_id: int, request_id: str
    ) -> Tuple[Optional[Business], Optional[Visitor], Optional[Response]]:
        """Verify user owns both business and visitor with single query optimization"""
        try:
            business = Business.objects.select_related('user').get(
                id=business_id, user=user
            )
        except Business.DoesNotExist:
            logger.warning(
                f"[{request_id}] Business not found or unauthorized",
                extra={'business_id': business_id, 'user_id': user.id}
            )
            return None, None, APIResponse.error('کسب‌وکار یافت نشد', code=status.HTTP_404_NOT_FOUND)

        try:
            # readable_by(), not user=user: a customer who booked through the
            # client web app has no `user` link to this owner (usually none at
            # all), so the stricter filter refused to book a repeat appointment
            # for someone the owner could already see in their own visitor
            # list — "مراجعه‌کننده یافت نشد" for an existing customer.
            visitor = Visitor.readable_by(user).select_related('user').get(
                id=visitor_id
            )
        except Visitor.DoesNotExist:
            logger.warning(
                f"[{request_id}] Visitor not found or unauthorized",
                extra={'visitor_id': visitor_id, 'user_id': user.id}
            )
            return None, None, APIResponse.error('مراجعه‌کننده یافت نشد', code=status.HTTP_404_NOT_FOUND)

        return business, visitor, None

    @sync_to_async
    def _get_appointment_optimized(
            self, appointment_id: int, user, request_id: str
    ) -> Tuple[Optional[Appointment], Optional[Response]]:
        """Get appointment with optimized query and ownership check"""
        try:
            appointment = selectors.appointment_get_for_owner(
                appointment_id=appointment_id, user=user
            )
            return appointment, None
        except Appointment.DoesNotExist:
            logger.warning(
                f"[{request_id}] Appointment not found or unauthorized",
                extra={'appointment_id': appointment_id, 'user_id': user.id}
            )
            return None, APIResponse.error('نوبت یافت نشد', code=status.HTTP_404_NOT_FOUND)

    @staticmethod
    def _status_failure_response(result, appointment, new_status, request_id):
        """The 400 for a rejected status change, or None when it went through."""
        if result.failure is StatusChangeFailure.INVALID_STATUS:
            valid_statuses = [value for value, _ in Appointment.STATUS_CHOICES]
            logger.warning(
                f"[{request_id}] Invalid status value",
                extra={'provided_status': new_status, 'valid_statuses': valid_statuses}
            )
            return APIResponse.error(f'وضعیت نامعتبر است. مقادیر مجاز: {", ".join(valid_statuses)}', code=status.HTTP_400_BAD_REQUEST)

        if result.failure is StatusChangeFailure.INVALID_TRANSITION:
            logger.warning(
                f"[{request_id}] Invalid status transition",
                extra={
                    'appointment_id': appointment.id,
                    'current_status': result.old_status,
                    'requested_status': new_status,
                    'allowed_transitions': list(result.allowed)
                }
            )
            return APIResponse.error(
                f'تغییر وضعیت از {result.old_status} به {new_status} مجاز نیست. وضعیت‌های مجاز: {", ".join(result.allowed)}',
                code=status.HTTP_400_BAD_REQUEST
            )
        return None

    async def _update_status_with_validation(
            self, appointment: Appointment, new_status: str, request_id: str, start_time: datetime
    ) -> Response:
        """Update status with state machine validation"""
        try:
            result = await sync_to_async(appointment_change_status_by_owner)(
                appointment=appointment, new_status=new_status
            )
        except Appointment.DoesNotExist:
            return APIResponse.error('نوبت یافت نشد', code=status.HTTP_404_NOT_FOUND)

        failure_response = self._status_failure_response(result, appointment, new_status, request_id)
        if failure_response is not None:
            return failure_response

        elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
        logger.info(
            f"[{request_id}] Appointment status updated",
            extra={
                'appointment_id': appointment.id,
                'old_status': result.old_status,
                'new_status': new_status,
                'elapsed_ms': elapsed_ms
            }
        )

        return APIResponse.success(
            data=await sync_to_async(_serialized)(appointment),
            message='وضعیت نوبت با موفقیت بروزرسانی شد',
        )

    async def _update_details_with_conflict_check(
            self, appointment: Appointment, data: Dict[str, Any], request_id: str, start_time: datetime
    ) -> Response:
        """Update appointment details (the owner may overlap appointments)"""
        new_date_str = data.get('appointment_date')

        # Parse new date from Unix epoch milliseconds if provided
        new_date = None
        if new_date_str:
            try:
                new_date = _parse_timestamp(new_date_str)
            except ValueError as e:
                logger.warning(
                    f"[{request_id}] Invalid timestamp: {new_date_str}",
                    extra={'error': str(e)}
                )
                return APIResponse.error('فرمت تاریخ نامعتبر است', code=status.HTTP_400_BAD_REQUEST)

        result = await sync_to_async(appointment_update_details_for_owner)(
            appointment=appointment,
            status=data.get('status'),
            appointment_date=new_date,
            service_duration=data.get('service_duration'),
            description=data.get('description'),
            selected_services=data.get('selected_services'),
        )
        if result.validation_error is not None:
            e = result.validation_error
            logger.error(
                f"[{request_id}] Validation error updating appointment",
                extra={'errors': e.message_dict}
            )
            error_message = _extract_error(e.message_dict) if hasattr(e, 'message_dict') else str(e)
            return APIResponse.error(error_message, code=status.HTTP_400_BAD_REQUEST)

        elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
        logger.info(
            f"[{request_id}] Appointment details updated",
            extra={'appointment_id': appointment.id, 'elapsed_ms': elapsed_ms}
        )

        return APIResponse.success(
            data=await sync_to_async(_serialized)(appointment),
            message='جزئیات نوبت با موفقیت بروزرسانی شد',
        )

    async def _update_details_and_status(
            self, appointment: Appointment, data: Dict[str, Any], request_id: str, start_time: datetime
    ) -> Response:
        """PATCH with ``status`` AND detail fields: details + state-machine status, atomically."""
        new_date = None
        if data.get('appointment_date'):
            try:
                new_date = _parse_timestamp(data.get('appointment_date'))
            except ValueError as e:
                logger.warning(
                    f"[{request_id}] Invalid timestamp: {data.get('appointment_date')}",
                    extra={'error': str(e)}
                )
                return APIResponse.error('فرمت تاریخ نامعتبر است', code=status.HTTP_400_BAD_REQUEST)

        new_status = data['status']
        try:
            details, result = await sync_to_async(appointment_update_details_and_status_for_owner)(
                appointment=appointment,
                status=new_status,
                appointment_date=new_date,
                service_duration=data.get('service_duration'),
                description=data.get('description'),
                selected_services=data.get('selected_services'),
            )
        except Appointment.DoesNotExist:
            return APIResponse.error('نوبت یافت نشد', code=status.HTTP_404_NOT_FOUND)

        if details.validation_error is not None:
            e = details.validation_error
            logger.error(
                f"[{request_id}] Validation error updating appointment",
                extra={'errors': e.message_dict}
            )
            error_message = _extract_error(e.message_dict) if hasattr(e, 'message_dict') else str(e)
            return APIResponse.error(error_message, code=status.HTTP_400_BAD_REQUEST)

        failure_response = self._status_failure_response(result, appointment, new_status, request_id)
        if failure_response is not None:
            return failure_response

        elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
        logger.info(
            f"[{request_id}] Appointment details and status updated",
            extra={'appointment_id': appointment.id, 'elapsed_ms': elapsed_ms}
        )
        return APIResponse.success(
            data=await sync_to_async(_serialized)(appointment),
            message='جزئیات نوبت با موفقیت بروزرسانی شد',
        )
