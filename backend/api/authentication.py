"""JWT authentication that also remembers which IPs an owner uses."""

import logging

from django.core.cache import cache
from django.utils import timezone
from rest_framework_simplejwt.authentication import JWTAuthentication

from .throttles import client_ip

logger = logging.getLogger(__name__)

# One write per user and IP per hour is plenty for spotting a shared IP.
SIGHTING_EVERY_SECONDS = 60 * 60


def record_ip_sighting(user_id, ip):
    if not ip:
        return
    try:
        if not cache.add(f"ipseen:{user_id}:{ip}", 1, SIGHTING_EVERY_SECONDS):
            return
        from .models import UserIpSighting

        UserIpSighting.objects.update_or_create(
            user_id=user_id, ip=ip, defaults={"last_seen": timezone.now()},
        )
    except Exception:
        # Never fail a request over bookkeeping.
        logger.exception("Could not record IP sighting for user %s", user_id)


class IpRecordingJWTAuthentication(JWTAuthentication):
    def authenticate(self, request):
        result = super().authenticate(request)
        if result is not None:
            record_ip_sighting(result[0].pk, client_ip(request))
        return result
