# integrations/fcm.py
"""FCM HTTP v1 transport: the pooled session and the one POST.

Credentials, payload building and result interpretation stay in
``api.services.push``; this module only moves bytes.
"""
import requests

ENDPOINT = "https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
TIMEOUT = 10

# One pooled HTTP session for every FCM call: a bulk campaign or the outbox
# worker sends thousands of messages, and a fresh TLS handshake per message is
# most of the latency. Requests are always sent with an explicit timeout.
session = requests.Session()
session.mount("https://", requests.adapters.HTTPAdapter(pool_connections=4, pool_maxsize=16))


def endpoint(project_id: str) -> str:
    return ENDPOINT.format(project_id=project_id)


def post_message(url: str, access_token: str, message: dict) -> requests.Response:
    """POST one message. Looks ``session.post`` up at call time (patchable)."""
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json; UTF-8",
    }
    return session.post(url, headers=headers, json=message, timeout=TIMEOUT)
