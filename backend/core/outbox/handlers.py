"""Outbox handler registry: kind -> function(payload).

Handlers re-load everything by id from the payload and call the existing
synchronous send functions, so message texts and credit consumption are exactly
what they were when these ran on daemon threads / inside the request. Imports
are lazy so importing this module (done by the worker) stays cheap.
"""

from .service import register


@register('appointment.booking_sms')
def booking_sms(payload):
    from appointment.services.notifications import send_booking_sms
    send_booking_sms(**payload)


@register('appointment.client_sms')
def client_sms(payload):
    from appointment.services.notifications import send_client_sms
    send_client_sms(**payload)


@register('bale.review_card')
def bale_review_card(payload):
    from bale.notify import _send
    _send(payload['business_id'], payload['kind'], payload.get('changed') or [])


@register('bale.owner_moderation_notify')
def owner_moderation_notify(payload):
    from bale.notify import notify_owner_of_decision
    notify_owner_of_decision(payload['business_id'], payload['to_status'])


@register('promo.campaign')
def promo_campaign(payload):
    from business.promo_sms import process_campaign
    process_campaign(payload['campaign_id'])


@register('core.admin_message')
def admin_message(payload):
    from core.messaging import deliver_admin_message
    deliver_admin_message(payload)


@register('core.push_campaign_chunk')
def push_campaign_chunk(payload):
    from core.messaging import deliver_push_chunk
    deliver_push_chunk(payload)


@register('core.expiry_sms_chunk')
def expiry_sms_chunk(payload):
    from core.messaging import deliver_expiry_chunk
    deliver_expiry_chunk(payload)
