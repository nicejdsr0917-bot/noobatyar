package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.domain.model.business.acceptsInsurance
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.feature.more.MyInsuranceViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessRepository
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MyInsuranceTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    @Test
    fun aBusinessAcceptsTheInsuranceItListsAndNothingWhenNoneIsSet() {
        val business = businessDto(1).toDomain().copy(insurances = listOf(Insurance(1, "بیمه دانا", "dana")))
        assertTrue(business.acceptsInsurance("dana"))
        assertFalse(business.acceptsInsurance("asia"))
        assertFalse(business.acceptsInsurance(null))
    }

    @Test
    fun pickingAndRemovingMyInsuranceIsKept() = runTest(main.dispatcher) {
        val prefs = FakeClientPreferences()
        val vm = MyInsuranceViewModel(FakeBusinessRepository(), prefs, main.dispatcher)
        advanceUntilIdle()
        assertNull(vm.state.value.mySlug)

        vm.select("dana")
        advanceUntilIdle()
        assertEquals("dana", prefs.myInsurance.value)
        assertEquals("dana", vm.state.value.mySlug)

        vm.select(null)
        advanceUntilIdle()
        assertNull(vm.state.value.mySlug)
    }
}
