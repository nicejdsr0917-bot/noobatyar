from business.models import Business

from .models import Review


def public_reviews(business_id):
    return (Review.objects.filter(business_id=business_id, is_hidden=False)
            .select_related('visitor').order_by('-created_at', '-id'))


def owner_reviews(business_id):
    """Every review of the business, hidden ones included but flagged."""
    return (Review.objects.filter(business_id=business_id)
            .select_related('visitor').order_by('-created_at', '-id'))


def rating_summary(business):
    hist = business.rating_histogram or {}
    return {
        'avg': round(business.rating_avg or 0, 2),
        'count': business.rating_count or 0,
        'histogram': {str(i): int(hist.get(str(i), 0)) for i in range(1, 6)},
    }


def public_business_or_none(business_id):
    return Business.objects.filter(Business.public_filter(), id=business_id).first()
