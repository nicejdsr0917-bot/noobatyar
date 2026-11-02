from django.shortcuts import render

# Create your views here.
from adrf.views import APIView
from rest_framework.permissions import IsAuthenticated
from rest_framework import status
from asgiref.sync import sync_to_async
from django.core.exceptions import ValidationError
from django.db.models import Exists, OuterRef, Q
import logging

from .models import Visitor, SmsLog, VisitorArchive, VisitorActivity, VisitorContact
from .serializers import VisitorSerializer, VisitorAttachSerializer, SmsLogSerializer
from business.models import Business
from . import services
from .activity import record_activity
from common.pagination import page_dict
from common.params import page_params, positive_int
from common.responses import APIResponse

logger = logging.getLogger(__name__)


_positive_int = positive_int  # kept: old name (see common.params)


async def get_readable_visitor(visitor_id, user):
    """Return a visitor the given user is allowed to READ.

    An owner can read a visitor they created directly, or a visitor who booked
    online (and is therefore linked to their own user account, not the owner's)
    but has at least one appointment at a business the owner owns. Read-only:
    editing/deleting still requires direct ownership.
    """
    try:
        return await sync_to_async(Visitor.readable_by(user).get)(id=visitor_id)
    except Visitor.DoesNotExist:
        return None


async def get_owned_visitor(visitor_id, user):
    """Return a visitor the given user is allowed to MODIFY, else None.

    Deliberately stricter than get_readable_visitor(): only a contact this owner
    created themselves. A Visitor row is global — phone_number is unique
    platform-wide, so one row is shared by every business the person books at —
    which means the read rule ("has an appointment at one of my businesses")
    would let any owner rename or re-number someone who is really another
    business's client, for the whole platform.
    """
    try:
        return await sync_to_async(Visitor.objects.get)(id=visitor_id, user=user)
    except Visitor.DoesNotExist:
        return None


class VisitorView(APIView):
    permission_classes = [IsAuthenticated]

    async def _get_visitor_or_404(self, visitor_id: int, user) -> Visitor:
        """Fetch a visitor this owner may act on at all (read-level access).

        An owner curated this contact directly, or the visitor has an
        appointment at one of the owner's businesses. Visitor.user is optional
        (most self-booked visitors never set it), so restricting this to
        `user=owner` would hide almost every online booking from the owner it
        belongs to. Writes that change shared data use get_owned_visitor().
        """
        return await get_readable_visitor(visitor_id, user)

    async def get(self, request, visitor_id=None):
        """List all visitors or retrieve single visitor"""
        try:
            if visitor_id:
                # Retrieve single visitor (readable if owned OR linked to one of
                # the owner's businesses through an appointment).
                visitor = await get_readable_visitor(visitor_id, request.user)
                if not visitor:
                    return APIResponse.error(
                        message="مشتری یافت نشد",
                        code=404
                    )
                data = await sync_to_async(services.serialize_for_owner)(
                    request.user, visitor, many=False)
                return APIResponse.success(
                    data=data,
                    message="اطلاعات مشتری با موفقیت دریافت شد"
                )

            # List all visitors with pagination
            user = request.user

            # Get pagination parameters
            page, page_size, error = page_params(request)  # page_size capped at 100
            if error:
                return error

            # Same readability rule as get_readable_visitor(): include visitors
            # who booked online under their own account but have an appointment
            # at one of this owner's businesses, not just visitors the owner
            # created directly. Visitors this owner archived are hidden from
            # their list only — the row itself is untouched and still visible to
            # every other business the person books at.
            visitors = (
                Visitor.readable_by(user)
                .filter(~Exists(VisitorArchive.objects.filter(
                    visitor=OuterRef('pk'), owner=user)))
                .order_by('-created_at', '-id')
            )

            # Paginate in the database (COUNT + LIMIT/OFFSET); only the page's
            # rows are ever loaded.
            def _paginate():
                return page_dict(
                    visitors, page, page_size,
                    lambda objs: services.serialize_for_owner(user, objs),
                )

            data = await sync_to_async(_paginate)()

            return APIResponse.success(
                data=data,
                message="لیست مشتریان با موفقیت دریافت شد"
            )

        except Exception as e:
            logger.error(f"Error retrieving visitor(s): {str(e)}")
            return APIResponse.error(
                message="خطا در دریافت اطلاعات مشتری",
                code=500
            )

    async def post(self, request):
        """Create new visitor, or attach to an existing one by phone number.

        phone_number is globally unique on Visitor (it's one real person across
        the whole platform, not a per-owner record), so a blind create would
        500 on a visitor who already exists — whether from their own online
        booking or another owner having added them first.

        Attaching also writes a `VisitorContact` row. Without it, a customer
        who already existed under a *different* owner stayed invisible to this
        one: `readable_by` matched neither `user` (taken by whoever added them
        first) nor an appointment at this owner's business — which is precisely
        what the owner was trying to create.
        """
        try:
            serializer = VisitorAttachSerializer(data=request.data)

            if await sync_to_async(serializer.is_valid)():
                phone = serializer.validated_data['phone_number']
                full_name = serializer.validated_data['full_name']

                visitor, created, display_name = await sync_to_async(services.visitor_attach)(
                    request.user, phone, full_name)
                data = VisitorSerializer(visitor).data
                data['full_name'] = display_name

                return APIResponse.success(
                    data=data,
                    message="مشتری با موفقیت ایجاد شد" if created else "این مشتری از قبل ثبت شده بود و به لیست شما اضافه شد",
                    status=201 if created else 200,
                )

            return APIResponse.error(
                message="اطلاعات وارد شده معتبر نیست",
                code=400,
                data=serializer.errors  # ارسال جزئیات خطا برای دیباگ
            )

        except ValidationError as e:
            return APIResponse.error(
                message=str(e),
                code=400
            )
        except Exception as e:
            logger.error(f"Error creating visitor: {str(e)}")
            return APIResponse.error(
                message="خطا در ایجاد مشتری",
                code=500
            )

    async def put(self, request, visitor_id):
        """Update a visitor this owner created.

        Editing is restricted to contacts the owner added themselves, because the
        Visitor row is shared platform-wide — see get_owned_visitor().
        """
        try:
            try:
                updated_visitor = await sync_to_async(services.visitor_update_by_owner)(
                    request.user, visitor_id, request.data)
            except services.VisitorEditForbidden:
                # Distinguish "not yours to edit" from "does not exist", so the
                # owner is not left guessing why their own list rejects an edit.
                return APIResponse.error(
                    message="این مراجع توسط شما ثبت نشده و اطلاعاتش بین همه کسب‌وکارها مشترک است؛ ویرایش آن مجاز نیست",
                    code=403
                )
            except services.PhoneLocked:
                return APIResponse.error(
                    message="این مراجع از برنامهٔ مشتریان یا در کسب‌وکارهای دیگر استفاده می‌کند و شمارهٔ موبایل او قابل تغییر نیست؛ فقط نام قابل ویرایش است",
                    code=403
                )
            except services.VisitorNotFound:
                return APIResponse.error(message="مشتری یافت نشد", code=404)
            except services.InvalidVisitorData as e:
                return APIResponse.error(
                    message="اطلاعات وارد شده معتبر نیست",
                    code=400,
                    data=e.errors
                )

            return APIResponse.success(
                data=VisitorSerializer(updated_visitor).data,
                message="مشتری با موفقیت بروزرسانی شد"
            )

        except ValidationError as e:
            return APIResponse.error(
                message=str(e),
                code=400
            )
        except Exception as e:
            logger.error(f"Error updating visitor: {str(e)}")
            return APIResponse.error(
                message="خطا در به‌روزرسانی مشتری",
                code=500
            )

    async def delete(self, request, visitor_id):
        """Archive a visitor from this owner's list — never delete the row.

        The method and URL are unchanged so existing clients keep working, but
        the semantics are now "hide from my list". Deleting the row would take
        the person's appointments at *other* businesses with it and lock them out
        of their own account permanently — see VisitorArchive's docstring.
        """
        try:
            try:
                vid, visitor_name, created = await sync_to_async(services.visitor_archive)(
                    request.user, visitor_id)
            except services.VisitorNotFound:
                return APIResponse.error(
                    message="مشتری یافت نشد",
                    code=404
                )
            if created:
                logger.info(f"Visitor {vid} archived by user {request.user.id}")

            return APIResponse.success(
                message=f"مشتری '{visitor_name}' از لیست شما بایگانی شد",
                status=204,
                data=None
            )

        except Exception as e:
            logger.error(f"Error archiving visitor: {str(e)}")
            return APIResponse.error(
                message="خطا در بایگانی مشتری",
                code=500
            )


class VisitorRestoreView(APIView):
    """Undo an archive, putting the visitor back in this owner's list."""

    permission_classes = [IsAuthenticated]

    async def post(self, request, visitor_id):
        try:
            visitor = await get_readable_visitor(visitor_id, request.user)
            if not visitor:
                return APIResponse.error(message="مشتری یافت نشد", code=404)

            deleted, _ = await sync_to_async(
                VisitorArchive.objects.filter(owner=request.user, visitor=visitor).delete
            )()

            if deleted:
                await sync_to_async(record_activity)(
                    visitor,
                    'RESTORED_BY_OWNER',
                    actor_type=VisitorActivity.ACTOR_OWNER,
                    actor_user=request.user,
                )
                logger.info(f"Visitor {visitor_id} restored by user {request.user.id}")
                message = "مشتری به لیست شما بازگردانده شد"
            else:
                message = "این مشتری در بایگانی شما نبود"

            return APIResponse.success(
                data=await sync_to_async(services.serialize_for_owner)(
                    request.user, visitor, many=False),
                message=message,
            )

        except Exception as e:
            logger.error(f"Error restoring visitor: {str(e)}")
            return APIResponse.error(message="خطا در بازگردانی مشتری", code=500)


class VisitorMessageHistoryView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request, visitor_id):
        visitor = await get_readable_visitor(visitor_id, request.user)
        if not visitor:
            return APIResponse.error("مشتری یافت نشد", code=404)

        # Paginate and serialize in one sync block. SmsLogSerializer exposes
        # visitor_name/visitor_phone, so building `.data` walks the visitor
        # relation — a DB hit that raises SynchronousOnlyOperation if it happens
        # on the async side. Same pattern as ClientAppointmentListView.
        return await sync_to_async(self._list_messages)(request, visitor)

    def _list_messages(self, request, visitor):
        page = _positive_int(request.query_params.get('page'), default=1)
        page_size = min(_positive_int(request.query_params.get('page_size'), default=10), 100)

        # Visitor is global — shared by every business the person ever booked
        # at — so business__user alone still merges every business the
        # requesting owner happens to run, not just the one they're looking
        # at. business_id pins the feed down to that single business.
        logs = SmsLog.objects.filter(visitor=visitor, business__user=request.user)
        business_id = request.query_params.get('business_id')
        if business_id:
            # Only the caller's own businesses. Anything else (another owner's,
            # nonexistent, malformed, or the 0 an app sends before its business
            # has loaded) yields an empty page rather than a 404: the owner app
            # shows any error as a failure, and old versions are in the field.
            try:
                logs = logs.filter(business_id=int(business_id))
            except (TypeError, ValueError):
                logs = logs.none()

        # Kept as a queryset (not a list) so Paginator slices at the DB instead
        # of pulling the visitor's entire SMS history into memory.
        logs = logs.select_related('business', 'visitor').order_by('-sent_at')

        data = page_dict(
            logs, page, page_size,
            lambda objs: SmsLogSerializer(objs, many=True).data,
        )

        return APIResponse.success(
            data=data,
            message="تاریخچه پیام‌ها با موفقیت دریافت شد"
        )