package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.components.ToastyType
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.domain.usecase.BusinessUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase
import xyz.sattar.javid.proqueue.feature.messages.MessagesIntent
import xyz.sattar.javid.proqueue.feature.messages.MessagesViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeAppPreferences
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessStore
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.StubBusinessApi
import xyz.sattar.javid.proqueue.testsupport.StubBusinessLocalSource
import xyz.sattar.javid.proqueue.testsupport.StubUserRepository
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The business filter: templates are per business and must never leak across a switch. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private val shopA = testBusiness(1, "A")
    private val shopB = testBusiness(2, "B")
    private val prefs = FakeAppPreferences()

    private val users = object : StubUserRepository() {
        override suspend fun getMyEntitlements() = ApiResponse.Error("none", 404)
    }

    private fun viewModel(store: FakeBusinessStore) = MessagesViewModel(
        generateReminderMessageUseCase = GenerateReminderMessageUseCase(prefs),
        getMyEntitlementsUseCase = GetMyEntitlementsUseCase(users),
        businessUpsertUseCase = BusinessUpsertUseCase(
            BusinessRepositoryImpl(StubBusinessLocalSource(), StubBusinessApi())
        ),
        businessStore = store,
        preferences = prefs,
    )

    @Test
    fun loadsTheSelectedBusinessTemplateNotAnotherOnes() = runTest {
        prefs.setMessageTemplate(1, "template-A {visitor}")
        prefs.setMessageTemplate(2, "template-B {visitor}")
        val vm = viewModel(FakeBusinessStore(shopB))

        vm.sendIntent(MessagesIntent.Load); advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.businessId)
        assertEquals("template-B {visitor}", vm.uiState.value.template)
    }

    @Test
    fun switchingBusinessAndReloadingReplacesTheTemplate() = runTest {
        prefs.setMessageTemplate(1, "template-A")
        prefs.setMessageTemplate(2, "template-B")
        val store = FakeBusinessStore(shopA)
        val vm = viewModel(store)
        vm.sendIntent(MessagesIntent.Load); advanceUntilIdle()
        assertEquals("template-A", vm.uiState.value.template)

        store.selectBusiness(shopB)
        vm.sendIntent(MessagesIntent.Load); advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.businessId)
        assertEquals("B", vm.uiState.value.businessTitle)
        assertEquals("template-B", vm.uiState.value.template)
    }

    @Test
    fun aBusinessWithoutATemplateGetsTheDefaultNotTheOtherBusinessOnes() = runTest {
        prefs.setMessageTemplate(1, "template-A")
        val vm = viewModel(FakeBusinessStore(shopB))

        vm.sendIntent(MessagesIntent.Load); advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(state.readyTemplates.first(), state.template)
    }

    @Test
    fun savingWritesOnlyUnderTheSelectedBusiness() = runTest {
        prefs.setMessageTemplate(1, "template-A")
        val vm = viewModel(FakeBusinessStore(shopB))
        vm.sendIntent(MessagesIntent.Load); advanceUntilIdle()

        vm.sendIntent(MessagesIntent.UpdateTemplate("new B text")); advanceUntilIdle()
        vm.sendIntent(MessagesIntent.Save); advanceUntilIdle()

        assertEquals("new B text", prefs.getMessageTemplate(2))
        assertEquals("template-A", prefs.getMessageTemplate(1), "A is untouched")
        assertEquals(ToastyType.Success, vm.uiState.value.message?.type)
    }

    @Test
    fun withoutABusinessLoadWarnsAndSaveWritesNothing() = runTest {
        val vm = viewModel(FakeBusinessStore(null))

        vm.sendIntent(MessagesIntent.Load); advanceUntilIdle()
        assertEquals(Str.no_business_selected, vm.uiState.value.message?.text)
        assertNull(vm.uiState.value.businessId)

        vm.sendIntent(MessagesIntent.Save); advanceUntilIdle()
        assertEquals(emptyMap(), prefs.templates.filterValues { it.value != null })
    }
}
