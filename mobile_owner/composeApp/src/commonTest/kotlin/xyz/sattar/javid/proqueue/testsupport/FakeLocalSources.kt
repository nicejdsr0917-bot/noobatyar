package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

/** In-memory appointment store with the same replaceWindow contract as the Room one. */
class FakeAppointmentLocalSource : StubAppointmentLocalSource() {
    val rows = linkedMapOf<Long, Appointment>()
    var clearCalls = 0
    var replaceCalls = 0
    var lastWindow: Pair<Long, Long>? = null

    override suspend fun upsertAppointments(appointments: List<Appointment>) {
        appointments.forEach { rows[it.id] = it }
    }

    override suspend fun clearAll() {
        clearCalls++
        rows.clear()
    }

    override suspend fun replaceWindow(
        businessId: Long,
        fromInclusive: Long,
        toExclusive: Long,
        appointments: List<Appointment>
    ) {
        replaceCalls++
        lastWindow = fromInclusive to toExclusive
        val keep = appointments.map { it.id }.toSet()
        rows.values.removeAll {
            it.businessId == businessId && it.appointmentDate >= fromInclusive &&
                it.appointmentDate < toExclusive && it.id !in keep
        }
        upsertAppointments(appointments)
    }
}

class FakeVisitorLocalSource : StubVisitorLocalSource() {
    val rows = linkedMapOf<Long, Visitor>()
    var clearCalls = 0
    override suspend fun upsertVisitors(visitors: List<Visitor>) {
        visitors.forEach { rows[it.id] = it }
    }
    override suspend fun clearAllVisitors() {
        clearCalls++
        rows.clear()
    }
}

open class FakeMessageLocalSource : StubMessageLocalSource() {
    val rows = linkedMapOf<Long, Message>()
    var clearCalls = 0
    override suspend fun clearAll() {
        clearCalls++
        rows.clear()
    }
}

class FakeBusinessLocalSource : StubBusinessLocalSource() {
    val rows = linkedMapOf<Long, Business>()
    var clearCalls = 0
    override suspend fun clearAllBusinesses() {
        clearCalls++
        rows.clear()
    }
}

class FakeUserLocalSource : StubUserLocalSource() {
    var userCleared = 0
    var subscriptionCleared = 0
    override suspend fun clearUser() { userCleared++ }
    override suspend fun clearSubscription() { subscriptionCleared++ }
}
