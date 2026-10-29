"""Thin clients for external services (Zibal, Melipayamak, FCM, Bale).

Plain package, no models. Each module owns its pooled HTTP client and returns
typed results; business rules stay with the callers.
"""
