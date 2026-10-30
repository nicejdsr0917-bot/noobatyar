from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import AllowAny
from api.responses import APIResponse
from visitor.auth import VisitorTokenAuthentication, IsVisitorAuthenticated
from .models import Appointment
from .client_serializers import ClientAppointmentSerializer
from api.pagination import StandardPagination
from . import selectors
from .services.client_booking import (
    LOCK_EXPIRED_MESSAGE as _LOCK_EXPIRED_MESSAGE,
    ClientBookingError,
    client_book,
    travel_estimate,
    client_cancel,
    client_deposit_returned,
    client_submit_payment,
)
from .services.transitions import (
    appointment_expire_lock_if_due,
    appointment_store_gateway_track_id,
)
import logging

logger = logging.getLogger(__name__)

class ClientAppointmentListView(APIView):
    # request.user is a Visitor here (see visitor/auth.py), never a `User`.
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def get(self, request):
        # Paginate + serialize in one sync block: the serializer's
        # queue_position/estimated_turn_time fields run ORM .count() queries, so
        # this must not touch the async ORM. Pagination also bounds those
        # per-row queries to one page instead of the user's entire history.
        return await sync_to_async(self._list_paginated)(request)

    def _list_paginated(self, request):
        queryset = selectors.appointment_list_for_visitor(visitor=request.user)

        paginator = StandardPagination()
        page = paginator.paginate_queryset(queryset, request, view=self)
        serializer = ClientAppointmentSerializer(
            page,
            many=True,
            context={'request': request},
        )
        paginated = paginator.get_paginated_response(serializer.data)

        return APIResponse.success(
            data=paginated.data,
            message="لیست نوبت‌های شما با موفقیت دریافت شد",
        )

    async def post(self, request):
        from datetime import datetime, timezone
        from business.models import Business
        from .client_serializers import ClientAppointmentCreateSerializer

        serializer = ClientAppointmentCreateSerializer(data=request.data)
        if not serializer.is_valid():
            return APIResponse.error(message="اطلاعات نامعتبر است", code=400)

        business_id = serializer.validated_data['business_id']
        try:
            business = await Business.objects.aget(id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب‌وکار یافت نشد", code=404)

        # request.user is the Visitor resolved by VisitorTokenAuthentication —
        # already exists, no account/get_or_create needed here.
        try:
            appointment, requires_payment = await sync_to_async(client_book)(
                business=business,
                visitor=request.user,
                appointment_date=datetime.fromtimestamp(
                    serializer.validated_data['appointment_date'] / 1000.0, tz=timezone.utc
                ),
                duration=serializer.validated_data.get('service_duration'),
                description=serializer.validated_data.get('description', ''),
                raw_services=serializer.validated_data.get('selected_services'),
                source=serializer.validated_data.get('source', 'WEB'),
                travel=travel_estimate(request.data),
            )
        except ClientBookingError as exc:
            return APIResponse.error(message=exc.message, code=exc.code)

        if requires_payment:
            return APIResponse.success(
                data={'id': appointment.id, 'requires_payment': True},
                message="نوبت با موفقیت قفل شد. لطفا پرداخت را انجام دهید."
            )
        return APIResponse.success(
            data={'id': appointment.id, 'requires_payment': False},
            message="نوبت شما با موفقیت ثبت شد و در انتظار تایید کسب‌وکار است."
        )


class ClientAppointmentPaymentView(APIView):
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request, pk):
        try:
            appointment, message = await sync_to_async(client_submit_payment)(
                visitor=request.user,
                appointment_id=pk,
                method=request.data.get('method'),
                payment_reference=request.data.get('payment_reference'),
                payment_receipt=request.FILES.get('payment_receipt', None),
            )
        except ClientBookingError as exc:
            return APIResponse.error(message=exc.message, code=exc.code)

        return APIResponse.success(data={'id': appointment.id}, message=message)


class ClientAppointmentOnlinePaymentView(APIView):
    """Opens a Zibal payment for the deposit and hands back the gateway URL.

    The money goes to the business owner's own Zibal merchant, so this is only
    available when the owner both configured a merchant id and holds the
    online-gateway entitlement.
    """

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request, pk):
        from django.urls import reverse

        from accounting import entitlements
        from .payment.zibal_deposit import create_deposit_payment

        try:
            appointment = await sync_to_async(selectors.appointment_get_for_visitor)(
                pk=pk, visitor=request.user, select_related=('business',)
            )
        except Appointment.DoesNotExist:
            return APIResponse.error(message="نوبت یافت نشد", code=404)

        if appointment.status != 'LOCKED':
            return APIResponse.error(message="این نوبت قابل پرداخت نیست", code=400)

        # Same lazy lock expiry as the manual payment path: an abandoned hold is
        # released rather than sending the client to a gateway for a dead slot.
        if await sync_to_async(appointment_expire_lock_if_due)(appointment):
            return APIResponse.error(message=_LOCK_EXPIRED_MESSAGE, code=400)

        business = appointment.business
        if 'ONLINE' not in (business.accepted_payment_methods or []):
            return APIResponse.error(message="این کسب‌وکار پرداخت آنلاین ندارد", code=400)

        has_gateway = await sync_to_async(entitlements.has_feature)(
            business.user_id, entitlements.FEATURE_ONLINE_GATEWAY
        )
        if not has_gateway:
            return APIResponse.error(message="پرداخت آنلاین برای این کسب‌وکار فعال نیست", code=400)

        callback_url = request.build_absolute_uri(
            reverse('client-deposit-callback')
        )
        result = await sync_to_async(create_deposit_payment)(appointment, callback_url)
        if not result['success']:
            return APIResponse.error(message=result['error'], code=400)

        # The trackId is how the callback finds this appointment again; the query
        # string it arrives with is not trusted for identification.
        if not await sync_to_async(appointment_store_gateway_track_id)(
            appointment=appointment, track_id=result['track_id']
        ):
            return APIResponse.error(message="این نوبت قابل پرداخت نیست", code=400)

        return APIResponse.success(
            data={'payment_url': result['payment_url'], 'track_id': result['track_id']},
            message="در حال انتقال به درگاه پرداخت",
        )


class ClientDepositCallbackView(APIView):
    """Where Zibal returns the client's browser after a deposit payment.

    Unauthenticated on purpose — this is a browser redirect from the bank, with
    no Authorization header. Nothing here trusts the query string beyond the
    trackId lookup: whether the payment happened is decided by Zibal's verify
    endpoint, so a hand-crafted ``?success=1`` confirms nothing.
    """

    permission_classes = [AllowAny]

    async def get(self, request):
        from django.conf import settings
        from django.shortcuts import redirect

        client_web = getattr(settings, 'CLIENT_WEB_URL', '').rstrip('/')
        track_id = (request.GET.get('trackId') or '').strip()

        def _back(state: str, appointment_id=None):
            suffix = f"&id={appointment_id}" if appointment_id else ""
            return redirect(f"{client_web}/appointments?payment={state}{suffix}")

        if not track_id:
            return _back('failed')

        state, appointment = await sync_to_async(client_deposit_returned)(track_id)
        return _back(state, appointment.id if appointment else None)


class ClientAppointmentCancelView(APIView):
    """Lets a client cancel their own appointment before it starts."""

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request, pk):
        try:
            appointment = await sync_to_async(client_cancel)(
                visitor=request.user, appointment_id=pk
            )
        except ClientBookingError as exc:
            return APIResponse.error(message=exc.message, code=exc.code)

        return APIResponse.success(
            data={'id': appointment.id},
            message="نوبت شما لغو شد",
        )
