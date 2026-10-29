from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import AllowAny
from rest_framework.exceptions import ValidationError as DRFValidationError
from api.responses import APIResponse
from business.serializers import normalize_service_names
from visitor.auth import VisitorTokenAuthentication, IsVisitorAuthenticated
from visitor.activity import record_activity
from visitor.models import VisitorActivity
from .models import Appointment
from .client_serializers import ClientAppointmentSerializer
from .cache_utils import invalidate_slots_cache
from api.pagination import StandardPagination
from . import selectors
from .services.booking import BookingFailure, appointment_create_for_client
from .services.deposits import deposit_settle
from .services.notifications import (
    notify_booking_cancelled_by_client,
    notify_booking_created,
    notify_deposit_paid,
    send_booking_sms,
)
from .services.transitions import (
    CLIENT_CANCELLABLE_STATUSES,
    appointment_cancel_by_client,
    appointment_expire_lock_if_due,
    appointment_store_gateway_track_id,
    appointment_submit_payment,
)
from django.utils import timezone
import logging

logger = logging.getLogger(__name__)

# The logic behind these names lives in services/. The old names stay because
# the views below call through them, and tests / the outbox handler registry /
# mock.patch targets reach them here.
_fire_booking_sms = notify_booking_created
_fire_cancellation_sms = notify_booking_cancelled_by_client
_fire_deposit_paid_sms = notify_deposit_paid
_send_booking_sms = send_booking_sms
_expire_lock_if_due = appointment_expire_lock_if_due
_cancel_transition = appointment_cancel_by_client
_settle_deposit = deposit_settle

_LOCK_EXPIRED_MESSAGE = "مهلت پرداخت این نوبت به پایان رسیده است. لطفاً دوباره نوبت بگیرید"


def _resolve_selected_services(business, raw):
    """
    What the client says they're coming for, as the comma-joined value stored
    on the appointment. Returns ``(value, error_message)``.

    Anything off-menu is rejected unless the owner opted into it — otherwise the
    switch in the business settings would be decoration, since the client
    controls the payload.
    """
    try:
        selected_services = normalize_service_names(raw)
    except DRFValidationError as exc:
        return None, str(exc.detail[0])

    if not business.allow_client_add_service:
        menu = set(normalize_service_names(business.services))
        unknown = [name for name in selected_services if name not in menu]
        if unknown:
            return None, "خدمت انتخاب‌شده در لیست خدمات این کسب‌وکار نیست"

    # Appointment.selected_services is a CharField(500); on Postgres an
    # over-long value is an error, not a truncation. Trimmed *after* the
    # menu check so dropping the overflow can never hide an off-menu name
    # that should have been rejected.
    while selected_services and len(','.join(selected_services)) > 500:
        selected_services.pop()
    return ','.join(selected_services), None


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

        # A business that is not publicly visible does not accept new bookings.
        # Two unrelated causes end up here — the owner's plan lapsed
        # (`is_locked`) and a moderator rejected/suspended the listing — and they
        # deliberately share one neutral message: which one applies is the
        # owner's business, not something to disclose to whoever followed a stale
        # link. 403 rather than 404 because the client reached this from a real
        # link and deserves to be told the booking was refused, not that the page
        # never existed.
        if not business.is_publicly_visible:
            return APIResponse.error(
                message="این کسب‌وکار در حال حاضر نوبت جدید نمی‌پذیرد",
                code=403,
            )

        # request.user is the Visitor resolved by VisitorTokenAuthentication —
        # already exists, no account/get_or_create needed here.
        visitor = request.user

        app_date = datetime.fromtimestamp(serializer.validated_data['appointment_date'] / 1000.0, tz=timezone.utc)
        duration = serializer.validated_data.get('service_duration') or business.default_service_duration

        # A payment step only applies when the business actually collects money
        # at booking time — a deposit, or a card/online method. Those are all
        # premium capabilities, so they are re-checked against the owner's
        # *current* plan: a downgraded owner whose business row still carries a
        # stale CARD/deposit config must not push clients into a payment step.
        requires_payment = await sync_to_async(selectors.business_requires_payment)(business)

        selected_services, error = _resolve_selected_services(
            business, serializer.validated_data.get('selected_services')
        )
        if error:
            return APIResponse.error(message=error, code=400)

        # Quota, slot conflict, row creation and quota consumption all happen
        # in ONE transaction that holds the business row lock, so two clients
        # racing for the last credit / the same time cannot both win.
        result = await sync_to_async(appointment_create_for_client)(
            business=business,
            visitor=visitor,
            appointment_date=app_date,
            duration=duration,
            description=serializer.validated_data.get('description', ''),
            selected_services=selected_services,
            requires_payment=requires_payment,
        )
        if result.failure is BookingFailure.QUOTA:
            return APIResponse.error(
                message="ظرفیت نوبت‌های این کسب‌وکار برای این ماه تکمیل شده است",
                code=409,
            )
        if result.failure is not None:
            return APIResponse.error(
                message="این زمان به‌تازگی رزرو شد. لطفاً زمان دیگری انتخاب کنید",
                code=409,
            )
        appointment = result.appointment

        # Slot occupancy changed → drop cached slot views for this business/date.
        await sync_to_async(invalidate_slots_cache)(business_id, app_date)

        await sync_to_async(record_activity)(
            visitor,
            'APPOINTMENT_BOOKED',
            actor_type=VisitorActivity.ACTOR_VISITOR,
            business=business,
            appointment=appointment,
            status=appointment.status,
        )

        if requires_payment:
            # SMS is deferred until payment is completed (ClientAppointmentPaymentView).
            return APIResponse.success(
                data={'id': appointment.id, 'requires_payment': True},
                message="نوبت با موفقیت قفل شد. لطفا پرداخت را انجام دهید."
            )

        # No payment needed → confirm the booking and notify client + owner now.
        appointment.business = business
        appointment.visitor = visitor
        await sync_to_async(_fire_booking_sms)(appointment, visitor.phone_number)
        return APIResponse.success(
            data={'id': appointment.id, 'requires_payment': False},
            message="نوبت شما با موفقیت ثبت شد و در انتظار تایید کسب‌وکار است."
        )


class ClientAppointmentPaymentView(APIView):
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request, pk):
        try:
            appointment = await sync_to_async(selectors.appointment_get_for_visitor)(
                pk=pk, visitor=request.user
            )
        except Appointment.DoesNotExist:
            return APIResponse.error(message="نوبت یافت نشد", code=404)

        if appointment.status != 'LOCKED':
            return APIResponse.error(message="این نوبت قابل پرداخت نیست", code=400)

        # Lazy lock expiry — no cleanup job needed.
        if await sync_to_async(_expire_lock_if_due)(appointment):
            return APIResponse.error(message=_LOCK_EXPIRED_MESSAGE, code=400)

        payment_reference = (request.data.get('payment_reference') or '').strip()
        payment_receipt = request.FILES.get('payment_receipt', None)
        # The client states how they are paying. Older clients that predate this
        # field submitted a receipt/reference, so default to CARD for them.
        method = (request.data.get('method') or 'CARD').upper()

        if method not in ('CARD', 'ONLINE', 'CASH'):
            return APIResponse.error(message="روش پرداخت نامعتبر است", code=400)

        business = appointment.business

        if method == 'CASH':
            # Paying in person: nothing to verify, so the booking goes straight
            # to the owner's approval queue instead of the receipt queue.
            if business.deposit_mode == 'MANDATORY':
                return APIResponse.error(
                    message="پرداخت بیعانه برای این کسب‌وکار الزامی است",
                    code=400,
                )
            changes = {'status': 'PENDING_APPROVAL', 'deposit_payment_method': 'NONE'}
            success_message = "نوبت شما ثبت شد و در انتظار تایید کسب‌وکار است"
        else:
            # Card / online transfers must come with proof, otherwise the owner
            # sees a "verify the receipt" item with no receipt to verify.
            if not payment_receipt and not payment_reference:
                return APIResponse.error(
                    message="لطفاً شماره پیگیری را وارد کنید یا تصویر فیش را آپلود نمایید",
                    code=400,
                )
            changes = {
                'status': 'PENDING_VERIFICATION',
                'payment_reference': payment_reference,
                'deposit_payment_method': 'CARD',
            }
            if payment_receipt:
                # Store the file now; only the resulting name goes in the update.
                await sync_to_async(appointment.payment_receipt.save)(
                    payment_receipt.name, payment_receipt, save=False
                )
                changes['payment_receipt'] = appointment.payment_receipt.name
            success_message = "پرداخت با موفقیت ثبت شد و نوبت در انتظار تایید است"

        if not await sync_to_async(appointment_submit_payment)(
            appointment=appointment, changes=changes
        ):
            return APIResponse.error(message="این نوبت قابل پرداخت نیست", code=400)

        # Slot occupancy changed → drop cached slot views for this business/date.
        await sync_to_async(invalidate_slots_cache)(
            appointment.business_id, appointment.appointment_date
        )

        # ── SMS Notifications via Melipayamak ─────────────────────────
        await sync_to_async(_fire_booking_sms)(appointment, request.user.phone_number)
        # ─────────────────────────────────────────────────────────────

        return APIResponse.success(
            data={'id': appointment.id},
            message=success_message
        )


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
        if await sync_to_async(_expire_lock_if_due)(appointment):
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

        state, appointment = await sync_to_async(_settle_deposit)(track_id)
        if appointment is not None and state == 'success_new':
            await sync_to_async(invalidate_slots_cache)(
                appointment.business_id, appointment.appointment_date
            )
            await sync_to_async(_fire_deposit_paid_sms)(appointment)
            state = 'success'
        return _back(state, appointment.id if appointment else None)


class ClientAppointmentCancelView(APIView):
    """Lets a client cancel their own appointment before it starts."""

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request, pk):
        try:
            appointment = await sync_to_async(selectors.appointment_get_for_visitor)(
                pk=pk, visitor=request.user
            )
        except Appointment.DoesNotExist:
            return APIResponse.error(message="نوبت یافت نشد", code=404)

        if appointment.status not in CLIENT_CANCELLABLE_STATUSES:
            return APIResponse.error(message="این نوبت قابل لغو نیست", code=400)

        if appointment.appointment_date <= timezone.now():
            return APIResponse.error(
                message="زمان این نوبت گذشته است و قابل لغو نیست",
                code=400,
            )

        previous_status = appointment.status
        # Conditional transition: if a callback/owner action moved the row since
        # we read it, nothing is cancelled and no credit is refunded.
        if not await sync_to_async(_cancel_transition)(appointment, previous_status):
            return APIResponse.error(message="این نوبت قابل لغو نیست", code=400)

        # Freeing the slot changes availability for everyone.
        await sync_to_async(invalidate_slots_cache)(
            appointment.business_id, appointment.appointment_date
        )

        await sync_to_async(record_activity)(
            appointment.visitor,
            'APPOINTMENT_CANCELLED',
            actor_type=VisitorActivity.ACTOR_VISITOR,
            business=appointment.business,
            appointment=appointment,
            old=previous_status,
            new='CANCELLED',
        )

        await sync_to_async(_fire_cancellation_sms)(appointment)

        return APIResponse.success(
            data={'id': appointment.id},
            message="نوبت شما لغو شد",
        )
