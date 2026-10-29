package xyz.sattar.javid.proqueue.data

import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.localDataSource.LocalCacheCleaner
import xyz.sattar.javid.proqueue.data.repository.user.UserRepositoryImpl
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor
import xyz.sattar.javid.proqueue.testsupport.FakeAppointmentLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeMessageLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeUserLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeVisitorLocalSource
import xyz.sattar.javid.proqueue.testsupport.StubUserApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LogoutWipesLocalDataTest {
    private val appointments = FakeAppointmentLocalSource()
    private val visitors = FakeVisitorLocalSource()
    private val messages = FakeMessageLocalSource()
    private val businesses = FakeBusinessLocalSource()
    private val users = FakeUserLocalSource()
    private val cleaner = LocalCacheCleaner(appointments, visitors, messages, businesses, users)

    private fun seed() {
        appointments.rows[1] = Appointment(1, 1, 1, 0, null, "WAITING", null, createdAt = 0, updatedAt = 0)
        visitors.rows[1] = Visitor(1, "A", "0912", 0)
        messages.rows[1] = Message(1, 1, "SMS", "hi", 0, "B")
    }

    private fun api(result: ApiResponse<Unit>) = object : StubUserApi() {
        override suspend fun logout(): ApiResponse<Unit> = result
    }

    @Test
    fun logoutWipesEveryLocalSource() = runTest {
        seed()
        val repo = UserRepositoryImpl(api(ApiResponse.Success(Unit)), users, cleaner)

        repo.logout()

        assertTrue(appointments.rows.isEmpty(), "appointments")
        assertTrue(visitors.rows.isEmpty(), "visitors")
        assertTrue(messages.rows.isEmpty(), "messages")
        assertEquals(1, businesses.clearCalls)
        assertEquals(1, users.userCleared)
        assertEquals(1, users.subscriptionCleared)
    }

    @Test
    fun logoutWipesEvenWhenTheServerCallFails() = runTest {
        seed()
        val repo = UserRepositoryImpl(api(ApiResponse.Error("down", 500)), users, cleaner)

        repo.logout()

        assertTrue(appointments.rows.isEmpty())
        assertTrue(visitors.rows.isEmpty())
        assertTrue(messages.rows.isEmpty())
    }

    @Test
    fun oneFailingStoreDoesNotStopTheOthersAndTheFailureSurfaces() = runTest {
        seed()
        val failing = object : FakeMessageLocalSource() {
            override suspend fun clearAll() = throw IllegalStateException("disk")
        }
        val c = LocalCacheCleaner(appointments, visitors, failing, businesses, users)

        assertFailsWith<IllegalStateException> { c.clearAll() }

        assertTrue(appointments.rows.isEmpty())
        assertTrue(visitors.rows.isEmpty())
        assertEquals(1, users.userCleared)
    }
}
