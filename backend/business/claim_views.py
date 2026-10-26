"""Owner-facing endpoints for finding and claiming an imported directory listing.

Flow (see business/claims.py for the transfer itself):

    GET  /api/business/claimable/?search=&city=   find your listing
    POST /api/business/<id>/claim/                 start: OTP to the listing's
                                                   mobile, or a manual request
    POST /api/business/<id>/claim/verify/          finish an OTP claim
"""

import logging

from adrf.views import APIView
from asgiref.sync import sync_to_async
from django.core.paginator import Paginator
from django.db.models import Q
from rest_framework.permissions import IsAuthenticated
from rest_framework.throttling import UserRateThrottle

from api.phone import is_iran_mobile
from api.responses import APIResponse
from api.services.otp import send_otp as otp_send, verify_otp as otp_verify

from .claims import ClaimError, apply_claim, check_claim_quota, mask_phone
from .models import Business, BusinessClaimRequest
from .serializers import PublicBusinessSerializer

logger = logging.getLogger(__name__)

class ClaimRateThrottle(UserRateThrottle):
    """Per-user cap on claim start/verify. The OTP service's own per-phone
    cooldown still applies; this stops one account cycling through listings
    to fire SMS at many different numbers."""
    scope = 'claim'


NOT_CLAIMABLE = "این مرکز قابل ثبت مالکیت نیست یا قبلاً ثبت شده است"


def _claimable_filter():
    return Business.public_filter() & Q(is_claimed=False)


class ClaimableBusinessSearchView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request):
        search = request.query_params.get('search', '').strip()
        city = request.query_params.get('city', '').strip()
        try:
            page = max(1, int(request.query_params.get('page', 1)))
        except (ValueError, TypeError):
            page = 1

        if len(search) < 2:
            return APIResponse.error(message="حداقل دو حرف از نام مرکز را وارد کنید", code=400)

        filters = _claimable_filter() & (
            Q(title__icontains=search) | Q(phone__icontains=search) | Q(address__icontains=search)
        )
        if city:
            filters &= Q(city__icontains=city)

        def _fetch():
            qs = Business.objects.filter(filters).prefetch_related('insurances').order_by('title')
            paginator = Paginator(qs, 20)
            page_obj = paginator.get_page(page)
            items = list(page_obj.object_list)
            for b in items:
                b.distance_km = None
            data = PublicBusinessSerializer(items, many=True, context={'request': request}).data
            return paginator.count, page_obj.has_next(), data

        count, has_next, results = await sync_to_async(_fetch)()
        return APIResponse.success(
            data={'count': count, 'next': page + 1 if has_next else None, 'results': results},
            message="نتایج جستجو",
        )


class ClaimStartView(APIView):
    permission_classes = [IsAuthenticated]
    throttle_classes = [ClaimRateThrottle]

    async def post(self, request, business_id):
        user = request.user
        note = str(request.data.get('note') or '').strip()[:1000]

        def _start():
            business = Business.objects.filter(_claimable_filter(), id=business_id).first()
            if business is None:
                return None, NOT_CLAIMABLE

            quota_error = check_claim_quota(user)
            if quota_error:
                return None, quota_error

            method = (
                BusinessClaimRequest.METHOD_OTP if is_iran_mobile(business.phone)
                else BusinessClaimRequest.METHOD_MANUAL
            )
            claim, _created = BusinessClaimRequest.objects.update_or_create(
                business=business, user=user, status=BusinessClaimRequest.STATUS_PENDING,
                defaults={'method': method, 'note': note},
            )
            return (business, claim), None

        result, error = await sync_to_async(_start)()
        if error:
            return APIResponse.error(message=error, code=400)
        business, claim = result

        if claim.method == BusinessClaimRequest.METHOD_OTP:
            sent = await sync_to_async(otp_send)(business.phone)
            if not sent['success']:
                return APIResponse.error(message=sent.get('error', 'خطا در ارسال کد'), code=429)
            return APIResponse.success(
                data={
                    'claim_id': claim.id,
                    'method': claim.method,
                    'masked_phone': mask_phone(business.phone),
                    'expires_in': 180,
                },
                message=f"کد تأیید به شماره {mask_phone(business.phone)} ارسال شد",
            )

        logger.info("Manual claim request %s for business %s by user %s", claim.id, business.id, user.id)
        return APIResponse.success(
            data={'claim_id': claim.id, 'method': claim.method, 'masked_phone': ''},
            message="درخواست شما ثبت شد؛ پس از بررسی پشتیبانی، مرکز به حساب شما منتقل می‌شود",
        )


class ClaimVerifyView(APIView):
    permission_classes = [IsAuthenticated]
    throttle_classes = [ClaimRateThrottle]

    async def post(self, request, business_id):
        user = request.user
        code = str(request.data.get('code') or '').strip()
        if not code:
            return APIResponse.error(message="کد تأیید را وارد کنید", code=400)

        claim = await BusinessClaimRequest.objects.select_related('business', 'user').filter(
            business_id=business_id, user=user,
            status=BusinessClaimRequest.STATUS_PENDING,
            method=BusinessClaimRequest.METHOD_OTP,
        ).afirst()
        if claim is None:
            return APIResponse.error(message="درخواست فعالی برای این مرکز پیدا نشد", code=404)

        verified = await sync_to_async(otp_verify)(claim.business.phone, code)
        if not verified['success']:
            return APIResponse.error(message=verified.get('error', 'کد نامعتبر است'), code=400)

        try:
            business = await sync_to_async(apply_claim)(claim, resolution_note='تأیید پیامکی')
        except ClaimError as exc:
            return APIResponse.error(message=str(exc), code=409)

        return APIResponse.success(
            data={'business_id': business.id},
            message="مرکز با موفقیت به حساب شما منتقل شد",
        )
