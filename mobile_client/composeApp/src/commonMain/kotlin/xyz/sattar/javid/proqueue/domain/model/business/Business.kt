package xyz.sattar.javid.proqueue.domain.model.business

data class Business(
    val id: Long = 0,
    val title: String,
    /** Wire code, e.g. "DENTIST". Deliberately the raw string rather than
     *  [BusinessCategory]: the server's vocabulary grows, and an enum would
     *  silently coerce any category added after this build to OTHER — which
     *  would then be written back on the next save. */
    val category: String,
    /** Persian label as the *server* described it (`category_display`), so a
     *  category this build predates still renders correctly. Falls back to the
     *  compiled-in enum when absent (an older server, or a Room row written
     *  before this field existed). */
    val categoryLabel: String,
    val uniqueCode: String? = null,
    val phone: String,
    /** Numbers beyond the primary [phone] — same normalization, same
     *  ۰۹۱۲۱۲۳۴۵۶۷ shape, just more than one. */
    val extraPhones: List<String> = emptyList(),
    /** {kind: handle}; see backend/business/social_links.py. Withheld with the phone. */
    val socialLinks: Map<String, String> = emptyMap(),
    val address: String,
    val logoPath: String,
    val defaultServiceDuration: Int,
    val workStartHour: Int,
    val workEndHour: Int,
    /** Minute component alongside [workStartHour]/[workEndHour] — e.g. 19:30
     *  is workEndHour=19, workEndMinute=30. Defaults to 0 so every business
     *  that predates this field reads exactly as it did before. */
    val workStartMinute: Int = 0,
    val workEndMinute: Int = 0,
    /**
     * Weekly recurring days off, as Kotlin's `LocalDate.dayOfWeek.ordinal`
     * ints (Monday=0..Sunday=6) — matching Python's `date.weekday()` on the
     * server, so no translation table is needed on either side. An empty list
     * means open every day, which is also the default before this was ever
     * set; there is deliberately no separate "no day off" flag for the same
     * value, since that would just be a second way to say the same thing.
     *
     * Only affects future availability (AvailableSlotsView on the server) — an
     * appointment already booked on a day later marked closed is left alone.
     */
    val closedWeekdays: List<Int> = emptyList(),
    val notificationEnabled: Boolean,
    val notificationTypes: String,
    val notificationMinutesBefore: Int = DEFAULT_REMINDER_MINUTES,
    val createdAt: Long = 0,
    val allowAnonymousView: Boolean = false,
    val notifyOwnerBySms: Boolean = true,
    val paymentMethod: String? = null,
    val acceptedPaymentMethods: List<String>? = null,
    val maxAppointmentsPerHour: Int? = null,
    val depositMode: String? = null,
    val depositAmount: Int? = null,
    val merchantId: String = "",
    val paymentLink: String = "",
    val onlineGatewayEnabled: Boolean = false,
    val cardNumber: String = "",
    val cardOwnerName: String = "",
    val bio: String = "",
    // Moderation. Null status = the backend didn't report one (older server),
    // in which case the UI stays silent about moderation altogether.
    val moderationStatus: ModerationStatus? = null,
    val moderationStatusDisplay: String = "",
    val moderationNote: String = "",
    val moderationSubmittedAt: Long = 0,
    // Billing lock (subscription/plan limits) — independent of moderationStatus.
    // See ModerationStatus's kdoc: the two answer different questions and a
    // visibility check that only looks at one of them is wrong.
    val isLocked: Boolean = false,
    val logoBytes: ByteArray? = null,
    val themeColor: String = "",
    val bookingEnabled: Boolean = true,
    /** Emergency notice for the public booking page — hidden while disabled. */
    val noticeEnabled: Boolean = false,
    val noticeMessage: String = "",
    /**
     * Whether the client gets an appointment reminder at all. Off means no
     * reminder is sent on any channel; [reminderDelivery] only decides *how* an
     * enabled reminder travels.
     */
    val enableReminderSms: Boolean = true,
    /** [ReminderDelivery] value; PANEL needs the auto_reminder_sms entitlement. */
    val reminderDelivery: String = ReminderDelivery.MANUAL.value,
    /**
     * The services this business offers, defined once in the business screen.
     * Drives the chip picker both here (recording what a visitor received) and
     * on the client's public booking page (saying what they're coming for).
     */
    val services: List<String> = emptyList(),
    /**
     * Whether a client may type a service that isn't on [services]. Off by
     * default — an owner who leaves it off gets answers they can plan slot
     * lengths around instead of free text.
     */
    val allowClientAddService: Boolean = false,

    // Client discovery fields
    val latitude: Double? = null,
    val longitude: Double? = null,
    val province: String = "",
    val city: String = "",
    val neighborhood: String = "",
    val isClaimed: Boolean = true,
    val distanceKm: Double? = null,
    val onlineBookingRequestsCount: Int = 0,
    val followersCount: Int = 0,
    /** Null when the server did not say (guest, or a list row). */
    val isFollowing: Boolean? = null,
    val insurances: List<Insurance> = emptyList(),
    /** Start (epoch ms) of the earliest bookable slot within 7 days, or null. */
    val firstFreeSlot: Long? = null,
    /** Percent off a client's first visit there, paid at the counter; 0 = none. */
    val firstVisitDiscount: Int = 0,
) {
    /** True only when both the content review and the plan/billing state allow it. */
    val isPubliclyVisible: Boolean
        get() = moderationStatus?.isPubliclyVisible == true && !isLocked
}
