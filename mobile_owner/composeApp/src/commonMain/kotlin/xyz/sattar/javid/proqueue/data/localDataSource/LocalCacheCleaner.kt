package xyz.sattar.javid.proqueue.data.localDataSource

import kotlinx.coroutines.CancellationException
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorLocalSource

/**
 * Wipes every locally cached table. Called on logout so the next account that
 * signs in on this device never sees the previous owner's appointments,
 * visitors, messages or businesses.
 *
 * Works over the *LocalSource interfaces, so it covers both the Room stores
 * (Android/iOS) and the in-memory ones (web). Dependents go first (messages,
 * appointments) so a foreign-key store never rejects a delete; one failing
 * step does not stop the others, the first failure is rethrown at the end.
 */
class LocalCacheCleaner(
    private val appointments: AppointmentLocalSource,
    private val visitors: VisitorLocalSource,
    private val messages: MessageLocalSource,
    private val businesses: BusinessLocalSource,
    private val users: UserLocalSource,
) {
    suspend fun clearAll() {
        var failure: Throwable? = null
        val steps: List<suspend () -> Unit> = listOf(
            { messages.clearAll() },
            { appointments.clearAll() },
            { visitors.clearAllVisitors() },
            { businesses.clearAllBusinesses() },
            { users.clearSubscription() },
            { users.clearUser() },
        )
        for (step in steps) {
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (failure == null) failure = e
            }
        }
        failure?.let { throw it }
    }
}
