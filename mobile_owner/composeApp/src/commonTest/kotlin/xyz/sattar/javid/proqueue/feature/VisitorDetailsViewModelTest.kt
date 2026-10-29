package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.VisitorPaging
import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import xyz.sattar.javid.proqueue.feature.visitorDetails.VisitorDetailsIntent
import xyz.sattar.javid.proqueue.feature.visitorDetails.VisitorDetailsState
import xyz.sattar.javid.proqueue.feature.visitorDetails.VisitorDetailsViewModel
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.StubAppointmentRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VisitorDetailsViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() {
        BusinessStateHolder.clearBusiness()
        main.tearDown()
    }

    private val business = Business(
        id = 7, title = "Shop", category = "OTHER", categoryLabel = "x", phone = "0", address = "a",
        logoPath = "", defaultServiceDuration = 30, workStartHour = 9, workEndHour = 17,
        notificationEnabled = false, notificationTypes = ""
    )

    private fun sms(text: String) = Message(
        id = 0, appointmentId = 0, messageType = "SMS", content = text, sentAt = 1,
        businessTitle = "Shop", remote = true
    )

    private val gates = mutableMapOf<Long, ArrayDeque<CompletableDeferred<List<Message>>>>()
    private fun gate(visitor: Long) = CompletableDeferred<List<Message>>().also { gates.getOrPut(visitor) { ArrayDeque() }.addLast(it) }

    private val messages = object : MessageRepository {
        override suspend fun insertMessage(message: Message) = true
        override suspend fun getAppointmentMessages(appointmentId: Long) = emptyList<Message>()
        override suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long) = emptyList<Message>()
        override suspend fun deleteMessage(id: Long) = true
        override suspend fun deleteMessagesByVisitorId(visitorId: Long) = true
        override suspend fun getRemoteMessages(visitorId: Long, businessId: Long, businessTitle: String) =
            gates.getValue(visitorId).removeFirst().await()
    }

    private val visitors = object : VisitorRepository {
        override suspend fun getVisitors(page: Int, pageSize: Int, query: String?) = VisitorPaging(emptyList(), 0, false)
        override suspend fun getVisitorById(visitorId: Long) = Visitor(visitorId, "visitor-$visitorId", "09", 0)
        override suspend fun createVisitor(visitor: Visitor) = 0L
        override suspend fun updateVisitor(visitor: Visitor) = true
        override suspend fun deleteVisitor(visitorId: Long): ApiResponse<Unit> = ApiResponse.Success(Unit)
    }

    private val appointments = object : StubAppointmentRepository() {
        override suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long) =
            emptyList<AppointmentWithDetails>()
        override suspend fun syncAppointments(
            businessId: Long, visitorId: Long?, status: String?, date: Long?, dateFrom: Long?,
            dateTo: Long?, ordering: xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering?,
            page: Int, pageSize: Int, replaceWindow: Boolean,
        ) = true
    }

    private fun viewModel() = VisitorDetailsViewModel(
        visitorRepository = visitors,
        appointmentRepository = appointments,
        messageRepository = messages,
        sendMessageUseCase = SendMessageUseCase(messages),
        generateReminderMessageUseCase = GenerateReminderMessageUseCase(),
        syncAppointmentsUseCase = SyncAppointmentsUseCase(appointments),
    )

    @Test
    fun lateRemoteMessagesForVisitorANeverLeakIntoVisitorB() = runTest {
        BusinessStateHolder.selectBusiness(business)
        val gateA = gate(1)   // A: slow
        val gateB = gate(2)   // B: fast
        val vm = viewModel()

        vm.uiState.test {
            assertEquals(VisitorDetailsState(), awaitItem())

            vm.sendIntent(VisitorDetailsIntent.LoadVisitorDetails(1))
            advanceUntilIdle()
            vm.sendIntent(VisitorDetailsIntent.LoadVisitorDetails(2))
            advanceUntilIdle()

            gateB.complete(listOf(sms("for-B")))
            advanceUntilIdle()
            gateA.complete(listOf(sms("for-A")))   // arrives after B is on screen
            advanceUntilIdle()

            val seen = cancelAndConsumeRemainingEvents()
                .filterIsInstance<app.cash.turbine.Event.Item<VisitorDetailsState>>().map { it.value }
            val final = seen.last()
            assertEquals(2L, final.visitor?.id)
            assertEquals(listOf("for-B"), final.messages.map { it.content })
            // and A's stale load never produced a state that shows A's data
            assertEquals(emptyList(), seen.filter { it.visitor?.id == 1L })
        }
    }

    @Test
    fun aThenBThenAAgainDropsTheFirstALoad() = runTest {
        BusinessStateHolder.selectBusiness(business)
        val a1 = gate(1)
        val b = gate(2)
        val a2 = gate(1)
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.sendIntent(VisitorDetailsIntent.LoadVisitorDetails(1)); advanceUntilIdle()
            vm.sendIntent(VisitorDetailsIntent.LoadVisitorDetails(2)); advanceUntilIdle()
            vm.sendIntent(VisitorDetailsIntent.LoadVisitorDetails(1)); advanceUntilIdle()

            b.complete(listOf(sms("B")))
            a2.complete(listOf(sms("A-new"))); advanceUntilIdle()
            a1.complete(listOf(sms("A-old"))); advanceUntilIdle()

            val seen = cancelAndConsumeRemainingEvents()
                .filterIsInstance<app.cash.turbine.Event.Item<VisitorDetailsState>>().map { it.value }
            assertEquals(listOf("A-new"), seen.last().messages.map { it.content })
            assertEquals(emptyList(), seen.filter { s -> s.messages.any { it.content == "A-old" || it.content == "B" } })
        }
    }
}
