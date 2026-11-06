package xyz.sattar.javid.proqueue.domain.model.appointment

data class Appointment(
    val id: Long,
    val businessId: Long,
    val visitorId: Long,
    val appointmentDate: Long,
    val serviceDuration: Int?,
    val status: String,
    val description: String?,
    /** Comma-separated service-catalog names picked as chips, e.g. "رنگ مو,کوتاهی". */
    val selectedServices: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val paymentReceipt: String? = null,
    val trackingCode: String? = null,
    val paymentReference: String? = null,
    val depositPaymentMethod: String? = null,
    /** WEB / APP / OWNER / UNKNOWN; null when the server predates the field. */
    val source: String? = null,
    /** Percent off for a new client's first visit (paid at the counter); 0 for none. */
    val firstVisitDiscount: Int = 0,
    /** Supplementary insurance the client picked at booking; null for none. */
    val insuranceName: String? = null,
)
