package xyz.sattar.javid.proqueue.feature

import kotlinx.serialization.json.Json
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentStatsDto
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppointmentSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun payload(extra: String) = """
        {"id":1,"visitor":{"id":2,"full_name":"V","phone_number":"0912","created_at":"2026-10-29T10:00:00Z"},
         "appointment_date":1000,"service_duration":30,"status":"WAITING","description":null,
         "created_at":"2026-10-29T10:00:00Z","updated_at":"2026-10-29T10:00:00Z"$extra}
    """.trimIndent()

    @Test
    fun parsesTheSourceWhenTheServerSendsIt() {
        assertEquals("APP", json.decodeFromString<AppointmentDto>(payload(""","source":"APP"""")).source)
    }

    @Test
    fun anOldServerWithoutTheKeyStillParses() {
        assertNull(json.decodeFromString<AppointmentDto>(payload("")).source)
    }

    @Test
    fun knownSourcesGetTheirPersianLabels() {
        assertEquals("اپ نوبت‌یار", AppointmentSource.labelFor("APP"))
        assertEquals("سایت", AppointmentSource.labelFor("WEB"))
        assertEquals("ثبت توسط شما", AppointmentSource.labelFor("OWNER"))
        assertEquals("سایت", AppointmentSource.labelFor(" web "))
    }

    @Test
    fun unknownNullAndFutureSourcesShowNoBadge() {
        assertNull(AppointmentSource.labelFor("UNKNOWN"))
        assertNull(AppointmentSource.labelFor(null))
        assertNull(AppointmentSource.labelFor(""))
        assertNull(AppointmentSource.labelFor("CARRIER_PIGEON"))
    }

    @Test
    fun statsCarryTheSourceCountsAndOldServersGetAnEmptyMap() {
        val withCounts = json.decodeFromString<AppointmentStatsDto>(
            """{"total_appointments":20,"source_counts":{"WEB":3,"APP":12,"OWNER":5,"UNKNOWN":0}}"""
        )
        assertEquals(12, withCounts.sourceCounts["APP"])
        assertEquals(20, withCounts.totalAppointments)
        assertEquals(emptyMap(), json.decodeFromString<AppointmentStatsDto>("""{"total_appointments":1}""").sourceCounts)
    }
}
