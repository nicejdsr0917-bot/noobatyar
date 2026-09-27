"""
business/promo_views.py

Owner promo-SMS campaigns (see business/promo_sms.py):

    GET  /api/business/<id>/promo-sms/            screen data + history
    POST /api/business/<id>/promo-sms/preview/    {audience, message} -> cost
    POST /api/business/<id>/promo-sms/            {audience, message} -> send
    GET  /api/business/<id>/promo-sms/<cid>/      one campaign + recipients

Plain JSON like the SMS report endpoints next door (the app reads these with
``toDirectApiResponse``); refusals are ``{"message", "code"}`` with a 4xx so
the app shows the Persian reason as-is.
"""

from rest_framework.exceptions import NotFound
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from accounting import entitlements
from api.sms import SEND_WINDOW_END_HOUR, SEND_WINDOW_START_HOUR, within_send_window

from . import promo_sms
from .models import PromoCampaign
from .sms_views import _owned_business


def _campaign_json(campaign, with_recipients=False):
    data = {
        'id': campaign.id,
        'audience': campaign.audience,
        'audience_label': campaign.get_audience_display(),
        'message': campaign.message,
        'status': campaign.status,
        'recipient_count': campaign.recipient_count,
        'total_parts': campaign.total_parts,
        'sent_count': campaign.sent_count,
        'failed_count': campaign.failed_count,
        'skipped_count': campaign.skipped_count,
        'created_at': campaign.created_at.isoformat(),
        'finished_at': campaign.finished_at.isoformat() if campaign.finished_at else None,
    }
    if with_recipients:
        data['recipients'] = [
            {
                'name': r.name,
                'phone': r.phone,
                'status': r.status,
                'error_detail': r.error_detail,
            }
            for r in campaign.recipients.all()
        ]
    return data


def _refusal(exc: promo_sms.CampaignError):
    status = 403 if exc.code == 'feature' else 400
    return Response({'message': exc.message, 'code': exc.code}, status=status)


class PromoSmsView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, business_id):
        business = _owned_business(request, business_id)
        counts = promo_sms.audience_counts(business)
        campaigns = PromoCampaign.objects.filter(business=business)[:20]
        return Response({
            'entitled': entitlements.has_feature(request.user, entitlements.FEATURE_PROMOTIONAL_SMS),
            'within_send_window': within_send_window(),
            'send_window_start': SEND_WINDOW_START_HOUR,
            'send_window_end': SEND_WINDOW_END_HOUR,
            'max_message_chars': promo_sms.MAX_MESSAGE_CHARS,
            'available_credit': promo_sms.available_credit(request.user),
            'audiences': [
                {'key': key, 'label': label, 'count': counts.get(key, 0)}
                for key, label in PromoCampaign.AUDIENCE_CHOICES
            ],
            'ready_texts': promo_sms.READY_TEXTS,
            'campaigns': [_campaign_json(c) for c in campaigns],
        })

    def post(self, request, business_id):
        business = _owned_business(request, business_id)
        try:
            campaign = promo_sms.create_campaign(
                business,
                audience=request.data.get('audience'),
                message=request.data.get('message'),
                actor=request.user,
            )
        except promo_sms.CampaignError as exc:
            return _refusal(exc)
        return Response(_campaign_json(campaign), status=201)


class PromoSmsPreviewView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, business_id):
        business = _owned_business(request, business_id)
        try:
            data = promo_sms.preview(
                business,
                audience=request.data.get('audience'),
                message=request.data.get('message'),
            )
        except promo_sms.CampaignError as exc:
            return _refusal(exc)
        return Response(data)


class PromoSmsDetailView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, business_id, campaign_id):
        business = _owned_business(request, business_id)
        try:
            campaign = PromoCampaign.objects.get(id=campaign_id, business=business)
        except PromoCampaign.DoesNotExist:
            raise NotFound("کمپین مورد نظر یافت نشد")
        return Response(_campaign_json(campaign, with_recipients=True))
