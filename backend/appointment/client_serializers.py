from rest_framework import serializers
from .models import Appointment
from business.selectors import attach_owner_state
from business.serializers import ClientBusinessSerializer

from .selectors_stats import split_selected_services


class ClientAppointmentListSerializer(serializers.ListSerializer):
    """Resolves the owners' subscription / booking-quota state for the whole
    page in a constant number of queries (see business.selectors) before the
    rows are serialised, instead of once per row."""

    def to_representation(self, data):
        items = data.all() if hasattr(data, 'all') else data
        items = list(items)
        businesses = {}
        for a in items:
            b = a.business
            if not hasattr(b, 'has_active_subscription'):
                businesses.setdefault(b.id, []).append(b)
        # One state lookup per distinct business, attached to every row's copy.
        if businesses:
            firsts = [group[0] for group in businesses.values()]
            attach_owner_state(firsts)
            for group in businesses.values():
                for b in group[1:]:
                    b.has_active_subscription = group[0].has_active_subscription
                    b.can_book = group[0].can_book
        return super().to_representation(items)


class ClientAppointmentSerializer(serializers.ModelSerializer):
    business = ClientBusinessSerializer(read_only=True)
    appointment_date = serializers.SerializerMethodField()
    queue_position = serializers.SerializerMethodField()
    estimated_turn_time = serializers.SerializerMethodField()
    # Plain booleans, not the timestamps themselves — the visitor's own
    # appointment view only needs "was I reminded", not exactly when. See
    # Appointment.reminder_sent_at / reminder_visitor_push_sent_at for the
    # underlying fields (owner's own push reminder, reminder_push_sent_at, is
    # deliberately not exposed here — it's about the owner, not this visitor).
    reminder_sms_sent = serializers.SerializerMethodField()
    reminder_push_sent = serializers.SerializerMethodField()
    # Where to send the card-to-card deposit. Deliberately NOT part of the
    # business payload (that is public to anyone browsing): it appears only on
    # the signed-in visitor's own appointment while it is still LOCKED, i.e.
    # while they are on the checkout step. Null otherwise.
    card_number = serializers.SerializerMethodField()
    card_owner_name = serializers.SerializerMethodField()
    # The visitor's own booking slip: what they asked for and the proof they
    # sent. This serializer only ever serves the signed-in visitor's own rows.
    selected_services = serializers.SerializerMethodField()
    payment_receipt = serializers.ImageField(read_only=True)

    class Meta:
        model = Appointment
        list_serializer_class = ClientAppointmentListSerializer
        fields = [
            'id',
            'business',
            'appointment_date',
            'status',
            'queue_position',
            'estimated_turn_time',
            'reminder_sms_sent',
            'reminder_push_sent',
            'card_number',
            'card_owner_name',
            'description',
            'selected_services',
            'payment_reference',
            'payment_receipt',
            'deposit_payment_method',
        ]

    def _card_visible(self, obj):
        if obj.status != 'LOCKED':
            return False
        request = self.context.get('request')
        user = getattr(request, 'user', None)
        if user is None or getattr(user, 'pk', None) is None or obj.visitor_id != user.pk:
            return False
        business = obj.business
        accepted = business.accepted_payment_methods or []
        takes_card = 'CARD' in accepted or business.payment_method == 'CARD'
        return takes_card and bool((business.card_number or '').strip())

    def get_card_number(self, obj):
        return obj.business.card_number if self._card_visible(obj) else None

    def get_card_owner_name(self, obj):
        return (obj.business.card_owner_name or '') if self._card_visible(obj) else None

    def get_selected_services(self, obj):
        return split_selected_services(obj.selected_services or '')

    def get_reminder_sms_sent(self, obj):
        return obj.reminder_sent_at is not None

    def get_reminder_push_sent(self, obj):
        return obj.reminder_visitor_push_sent_at is not None

    def get_appointment_date(self, obj):
        if obj.appointment_date:
            return int(obj.appointment_date.timestamp() * 1000)
        return None

    def get_queue_position(self, obj):
        """
        Calculates how many appointments are WAITING or IN_PROGRESS 
        for the same business on the same day, scheduled before this appointment.
        """
        if obj.status not in ['WAITING', 'IN_PROGRESS']:
            return 0
        # Computed once per row: estimated_turn_time asks for it again.
        cached = getattr(obj, '_queue_position_cache', None)
        if cached is not None:
            return cached
        # List endpoint: counted in SQL together with the page rows.
        annotated = getattr(obj, 'queue_ahead', None)
        if annotated is not None:
            obj._queue_position_cache = annotated
            return annotated
        
        # Count appointments on the same day with an earlier time
        start_of_day = obj.appointment_date.replace(hour=0, minute=0, second=0, microsecond=0)
        end_of_day = obj.appointment_date.replace(hour=23, minute=59, second=59, microsecond=999999)
        
        ahead = Appointment.objects.filter(
            business=obj.business,
            appointment_date__range=(start_of_day, end_of_day),
            appointment_date__lt=obj.appointment_date,
            status__in=['WAITING', 'IN_PROGRESS']
        ).count()
        obj._queue_position_cache = ahead
        return ahead

    def get_estimated_turn_time(self, obj):
        """
        Estimates the time the user's turn will arrive.
        Wait time = queue_position * 15 minutes.
        """
        queue_pos = self.get_queue_position(obj)
        if queue_pos == 0 and obj.status == 'WAITING':
            # Their turn is next or very soon
            return self.get_appointment_date(obj)
            
        from datetime import timedelta
        # Add 15 minutes for each person ahead
        estimated_time = obj.appointment_date + timedelta(minutes=15 * queue_pos)
        return int(estimated_time.timestamp() * 1000)

class ClientAppointmentCreateSerializer(serializers.ModelSerializer):
    business_id = serializers.IntegerField(write_only=True)
    appointment_date = serializers.IntegerField(write_only=True) # Unix timestamp in ms
    service_duration = serializers.IntegerField(write_only=True, required=False)
    description = serializers.CharField(write_only=True, required=False, allow_blank=True)
    # What the client says they're coming for, picked from the business's own
    # menu (business.services). Accepted as a list here and stored on the
    # appointment as the same comma-separated string the owner app writes, so
    # both booking paths produce one comparable value.
    # Whether a name that is NOT on the menu is allowed depends on the
    # business's allow_client_add_service switch — enforced in the view, which
    # is the only place that has the Business loaded.
    selected_services = serializers.ListField(
        child=serializers.CharField(allow_blank=True),
        write_only=True,
        required=False,
    )

    # Only WEB / APP can be claimed by a client; anything else is rejected.
    source = serializers.ChoiceField(
        choices=[Appointment.SOURCE_WEB, Appointment.SOURCE_APP],
        write_only=True, required=False, default=Appointment.SOURCE_WEB,
    )
    # Slug of the supplementary insurance the client will use; checked against
    # the business's own list in client_book, an unknown one is dropped.
    insurance = serializers.CharField(write_only=True, required=False, allow_blank=True, allow_null=True, max_length=50)

    class Meta:
        model = Appointment
        fields = ['business_id', 'appointment_date', 'service_duration', 'description', 'selected_services', 'source', 'insurance']
