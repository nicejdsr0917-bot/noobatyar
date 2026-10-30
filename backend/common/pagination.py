from django.core.paginator import Paginator
from rest_framework.pagination import PageNumberPagination
from rest_framework.response import Response

class StandardPagination(PageNumberPagination):
    page_size = 10
    page_size_query_param = 'page_size'
    max_page_size = 100

    def get_paginated_response(self, data):
        return Response({
            'count': self.page.paginator.count,
            'total_pages': self.page.paginator.num_pages,
            'current_page': self.page.number,
            'next': self.get_next_link(),
            'previous': self.get_previous_link(),
            'results': data
        })


def page_dict(qs, page, page_size, serialize):
    """One page of ``qs`` in the owner-app list envelope.

    ``serialize`` receives the page's object list and returns the ``results``
    value. Synchronous: call it inside the sync hop that also serialises (the
    serializer may touch the DB). Returns
    ``{count, total_pages, current_page, next, previous, results}`` with next /
    previous as page *numbers* (or None) -- not URLs, unlike StandardPagination.
    """
    paginator = Paginator(qs, page_size)
    page_obj = paginator.get_page(page)
    return {
        'count': paginator.count,
        'total_pages': paginator.num_pages,
        'current_page': page_obj.number,
        'next': page_obj.next_page_number() if page_obj.has_next() else None,
        'previous': page_obj.previous_page_number() if page_obj.has_previous() else None,
        'results': serialize(page_obj.object_list),
    }
