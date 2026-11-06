package xyz.sattar.javid.proqueue.feature

import kotlinx.serialization.json.Json
import xyz.sattar.javid.proqueue.core.ui.components.paymentTrackingCode
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentDto
import xyz.sattar.javid.proqueue.testsupport.testAppointment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppointmentInsuranceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun payload(extra: String) = """
        {"id":1,"visitor":{"id":2,"full_name":"V","phone_number":"0912","created_at":"2026-10-29T10:00:00Z"},
         "appointment_date":1000,"service_duration":30,"status":"WAITING","description":null,
         "created_at":"2026-10-29T10:00:00Z","updated_at":"2026-10-29T10:00:00Z"$extra}
    """.trimIndent()

    @Test
    fun parsesTheInsuranceNameAndToleratesAnOldServer() {
        assertEquals("دی", json.decodeFromString<AppointmentDto>(payload(""","insurance_name":"دی"""")).insuranceName)
        assertNull(json.decodeFromString<AppointmentDto>(payload(""","insurance_name":null""")).insuranceName)
        assertNull(json.decodeFromString<AppointmentDto>(payload("")).insuranceName)
    }

    @Test
    fun trackingCodeOnlyForAPaidDeposit() {
        val base = testAppointment(1, 1)
        assertEquals("123", base.copy(paymentReference = " 123 ", depositPaymentMethod = "CARD").paymentTrackingCode())
        assertEquals("tid", base.copy(paymentReference = "tid", depositPaymentMethod = "GATEWAY").paymentTrackingCode())
        // A trackId left on an abandoned gateway attempt is not a payment.
        assertNull(base.copy(paymentReference = "tid", depositPaymentMethod = "NONE").paymentTrackingCode())
        assertNull(base.copy(paymentReference = " ", depositPaymentMethod = "CARD").paymentTrackingCode())
        assertNull(base.paymentTrackingCode())
    }
}
