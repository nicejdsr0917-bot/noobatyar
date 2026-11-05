"""Response-shape contracts (keys + JSON types) for the public directory and the
visitor's own appointment list. Old app versions in the field parse these
payloads: a refactor of the serializers/selectors must leave them untouched.
"""
from datetime import timedelta

from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from appointment.models import Appointment
from business.models import Business
from business.tests_query_counts import DirectoryFixtureMixin
from visitor.auth import sign_visitor_token
from visitor.models import Visitor


def jtype(v):
    if v is None:
        return 'null'
    if isinstance(v, bool):
        return 'bool'
    if isinstance(v, int):
        return 'int'
    if isinstance(v, float):
        return 'float'
    if isinstance(v, str):
        return 'str'
    if isinstance(v, list):
        return 'list'
    return 'dict'


def shape(d):
    out = {k: jtype(v) for k, v in d.items()}
    # Legitimately either: epoch ms, or null when nothing is free / bookable.
    if out.get('first_free_slot') == 'null':
        out['first_free_slot'] = 'int'
    return out


ENVELOPE = {'status', 'code', 'message', 'data'}
PAGE_KEYS = {'count': 'int', 'total_pages': 'int', 'current_page': 'int',
             'next': 'null', 'previous': 'null', 'results': 'list'}
INSURANCE_KEYS = {'id': 'int', 'name': 'str', 'slug': 'str', 'insurance_type': 'str', 'logo': 'null'}

PUBLIC_BUSINESS_SHAPE = {
    'id': 'int',
    'rating': 'dict',
    'unique_code': 'str',
    'category': 'str',
    'category_display': 'str',
    'title': 'str',
    'logo': 'null',
    'theme_color': 'str',
    'address': 'str',
    'latitude': 'float',
    'longitude': 'float',
    'province': 'str',
    'city': 'str',
    'city_code': 'null',
    'neighborhood': 'str',
    'is_claimed': 'bool',
    'online_booking_requests_count': 'int',
    'followers_count': 'int',
    'insurances': 'list',
    'distance_km': 'null',
    'phone': 'str',
    'extra_phones': 'list',
    'social_links': 'dict',
    'work_start_hour': 'int',
    'work_end_hour': 'int',
    'work_start_minute': 'int',
    'work_end_minute': 'int',
    'default_service_duration': 'int',
    'closed_weekdays': 'list',
    'payment_method': 'str',
    'accepted_payment_methods': 'list',
    'deposit_mode': 'str',
    'deposit_amount': 'int',
    'first_visit_discount': 'int',
    'payment_link': 'str',
    'bio': 'null',
    'booking_enabled': 'bool',
    'notice_enabled': 'bool',
    'notice_message': 'str',
    'allow_anonymous_view': 'bool',
    'services': 'list',
    'allow_client_add_service': 'bool',
    'has_location': 'bool',
    'is_expired': 'bool',
    # Area-only businesses (public_policy.apply_location_privacy).
    'location_approximate': 'bool',
    'location_radius_m': 'null',
    # Added by the view on top of the (cached) serializer payload; epoch ms,
    # or null when not bookable / nothing free. Fixtures here are bookable.
    'first_free_slot': 'int',
}

CLIENT_BUSINESS_SHAPE = {
    'id': 'int',
    'title': 'str',
    'category': 'str',
    'category_display': 'str',
    'unique_code': 'str',
    'phone': 'str',
    'extra_phones': 'list',
    'social_links': 'dict',
    'address': 'str',
    'latitude': 'float',
    'longitude': 'float',
    'province': 'str',
    'city': 'str',
    'neighborhood': 'str',
    'city_code': 'null',
    'is_claimed': 'bool',
    'online_booking_requests_count': 'int',
    'followers_count': 'int',
    'insurances': 'list',
    'distance_km': 'null',
    'logo': 'null',
    'theme_color': 'str',
    'default_service_duration': 'int',
    'work_start_hour': 'int',
    'work_end_hour': 'int',
    'work_start_minute': 'int',
    'work_end_minute': 'int',
    'allow_anonymous_view': 'bool',
    'notice_message': 'str',
    'notice_enabled': 'bool',
    'booking_enabled': 'bool',
    'payment_method': 'str',
    'accepted_payment_methods': 'list',
    'deposit_mode': 'str',
    'deposit_amount': 'int',
    'first_visit_discount': 'int',
    'payment_link': 'str',
    'online_gateway_enabled': 'bool',
    'services': 'list',
    'allow_client_add_service': 'bool',
    'has_location': 'bool',
    'is_expired': 'bool',
    # Area-only businesses (public_policy.apply_location_privacy).
    'location_approximate': 'bool',
    'location_radius_m': 'null',
}

APPOINTMENT_SHAPE = {
    'id': 'int',
    'business': 'dict',
    'appointment_date': 'int',
    'status': 'str',
    'queue_position': 'int',
    'estimated_turn_time': 'int',
    'reminder_sms_sent': 'bool',
    'reminder_push_sent': 'bool',
    'card_number': 'null',
    'card_owner_name': 'null',
    # The visitor's own booking slip (added for the app's appointment page).
    'description': 'null',
    'selected_services': 'list',
    'payment_reference': 'null',
    'payment_receipt': 'null',
    'deposit_payment_method': 'str',
}


class PublicBusinessShapeTests(DirectoryFixtureMixin, TestCase):
    LIST_URLS = ('/api/client/business/', '/api/client/businesses/')

    def setUp(self):
        super().setUp()
        self.biz = self.make_businesses(3)[0]

    def test_list_envelope_and_row_shape(self):
        for url in self.LIST_URLS:
            body = self.client.get(url, {'page_size': 2}).json()
            self.assertEqual(set(body), ENVELOPE, url)
            self.assertEqual(shape(body['data']), {**PAGE_KEYS, 'next': 'int'}, url)
            self.assertEqual(body['data']['count'], 3)
            row = body['data']['results'][0]
            self.assertEqual(shape(row), PUBLIC_BUSINESS_SHAPE, url)
            self.assertEqual(shape(row['insurances'][0]), INSURANCE_KEYS)

    def test_search_and_geo_rows_have_same_shape(self):
        for params in ({'search': 'biz'}, {'lat': 35.7, 'lng': 51.4, 'radius_km': 20},
                       {'category': 'DENTIST', 'only_claimed': 'true'}, {'city': 'تهران'}):
            row = self.client.get(self.LIST_URLS[0], params).json()['data']['results'][0]
            expected = dict(PUBLIC_BUSINESS_SHAPE)
            if 'lat' in params:
                expected['distance_km'] = 'float'
            self.assertEqual(shape(row), expected, params)

    def test_empty_page(self):
        data = self.client.get(self.LIST_URLS[0], {'search': 'zzzzzz'}).json()['data']
        self.assertEqual(data, {'count': 0, 'total_pages': 1, 'current_page': 1,
                                'next': None, 'previous': None, 'results': []})

    def test_detail_shape_both_prefixes(self):
        for prefix in self.LIST_URLS:
            body = self.client.get(f'{prefix}{self.biz.id}/').json()
            self.assertEqual(set(body), ENVELOPE)
            self.assertEqual(shape(body['data']), PUBLIC_BUSINESS_SHAPE, prefix)

    def test_detail_with_coordinates_has_distance(self):
        data = self.client.get(f'{self.LIST_URLS[0]}{self.biz.id}/',
                               {'lat': 35.7, 'lng': 51.4}).json()['data']
        self.assertEqual(jtype(data['distance_km']), 'float')

    def test_detail_404_shape(self):
        r = self.client.get(f'{self.LIST_URLS[0]}999999/')
        self.assertEqual(r.status_code, 404)
        self.assertEqual(set(r.json()), ENVELOPE)
        self.assertEqual(r.json()['code'], 404)


class ClientAppointmentShapeTests(DirectoryFixtureMixin, TestCase):
    def setUp(self):
        super().setUp()
        self.biz = self.make_businesses(1)[0]
        self.visitor = Visitor.objects.create(full_name='v', phone_number='09351110000')
        self.auth = {'HTTP_AUTHORIZATION': f'Visitor {sign_visitor_token(self.visitor.id)}'}

    def test_list_shape(self):
        Appointment.objects.create(
            business=self.biz, visitor=self.visitor, status='WAITING',
            appointment_date=timezone.now() + timedelta(hours=2))
        body = self.client.get(reverse('client-appointment-list'), **self.auth).json()
        self.assertEqual(set(body), ENVELOPE)
        self.assertEqual(shape(body['data']), {**PAGE_KEYS})
        row = body['data']['results'][0]
        self.assertEqual(shape(row), APPOINTMENT_SHAPE)
        self.assertEqual(shape(row['business']), CLIENT_BUSINESS_SHAPE)
        self.assertEqual(shape(row['business']['insurances'][0]), INSURANCE_KEYS)

    def test_list_empty(self):
        data = self.client.get(reverse('client-appointment-list'), **self.auth).json()['data']
        self.assertEqual(data['results'], [])
