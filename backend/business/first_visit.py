"""First-visit discount: a percentage the business gives a client on their
first visit, settled at the counter. Noobatyar shows it on the business's
card and page, tells the client at booking, and flags the appointment for
the owner; it never touches the deposit."""
from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import IsAuthenticated

from api.responses import APIResponse

from .models import Business


def is_first_visit(appointment):
    """No other finished visit by this client at this business."""
    from appointment.models import Appointment
    return not Appointment.objects.filter(
        business_id=appointment.business_id, visitor_id=appointment.visitor_id, status='COMPLETED',
    ).exclude(pk=appointment.pk).exists()


def discount_for(appointment, percent):
    """The discount this appointment carries: the business's percent on a first visit, else 0."""
    if not percent or appointment.status == 'CANCELLED' or not appointment.visitor_id:
        return 0
    return percent if is_first_visit(appointment) else 0


class OwnerFirstVisitDiscountView(APIView):
    """GET / PUT /api/business/<id>/first-visit-discount/ — {"percent": 0..50}."""
    permission_classes = [IsAuthenticated]

    async def get(self, request, business_id):
        business = await Business.objects.filter(pk=business_id, user=request.user).afirst()
        if business is None:
            return APIResponse.error('کسب و کار یافت نشد', code=404)
        return APIResponse.success(data={'percent': business.first_visit_discount})

    async def put(self, request, business_id):
        try:
            percent = int(request.data.get('percent'))
        except (TypeError, ValueError):
            return APIResponse.error('درصد تخفیف را وارد کنید', code=400)
        if not 0 <= percent <= Business.MAX_FIRST_VISIT_DISCOUNT:
            return APIResponse.error(f'درصد تخفیف باید بین ۰ تا {Business.MAX_FIRST_VISIT_DISCOUNT} باشد', code=400)

        def _save():
            from . import cache as public_cache
            updated = Business.objects.filter(pk=business_id, user=request.user).update(first_visit_discount=percent)
            if updated:
                public_cache.bump_business(business_id)
            return updated

        if not await sync_to_async(_save)():
            return APIResponse.error('کسب و کار یافت نشد', code=404)
        msg = 'تخفیف اولین مراجعه خاموش شد' if percent == 0 else 'تخفیف اولین مراجعه ذخیره شد'
        return APIResponse.success(data={'percent': percent}, message=msg)
