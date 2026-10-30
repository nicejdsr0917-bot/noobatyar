package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.SavedLocation
import xyz.sattar.javid.proqueue.core.prefs.decodeSavedLocation
import xyz.sattar.javid.proqueue.core.prefs.encode
import xyz.sattar.javid.proqueue.feature.businessDetail.BusinessMapViewModel
import xyz.sattar.javid.proqueue.feature.businessDetail.distanceKm
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessRepository
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessMapTest {
    private val main = MainDispatcherSetup()
    private val repo = FakeBusinessRepository()
    private val prefs = FakeClientPreferences()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    // Azadi Tower and Milad Tower, Tehran: about 6.0 km apart in a straight line.
    private val azadi = 35.6997 to 51.3380
    private val milad = 35.7448 to 51.3753

    private fun vm() = BusinessMapViewModel(repo, prefs, main.dispatcher, now = { 1234L })

    @Test
    fun savedLocationRoundTripsAndRejectsJunk() {
        val loc = SavedLocation(35.7, 51.4, 99L)
        assertEquals(loc, decodeSavedLocation(loc.encode()))
        assertNull(decodeSavedLocation(null))
        assertNull(decodeSavedLocation("1,2"))
        assertNull(decodeSavedLocation("95,10,1"), "latitude out of range")
        assertNull(decodeSavedLocation("a,b,c"))
    }

    @Test
    fun aFixShowsTheStraightLineDistanceAndIsSaved() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(businessDto(7).copy(latitude = milad.first, longitude = milad.second))
        val vm = vm()
        vm.load(7)
        assertNull(vm.state.value.distanceKm(), "no position yet")
        vm.onLocating()
        assertTrue(vm.state.value.locating)
        vm.onLocationResult(LocationResult.Success(azadi.first, azadi.second))
        advanceUntilIdle()
        val km = vm.state.value.distanceKm()!!
        assertTrue(abs(km - 6.0) < 0.3, "got $km")
        assertTrue(vm.state.value.fresh)
        assertEquals(SavedLocation(azadi.first, azadi.second, 1234L), prefs.location.value)
    }

    @Test
    fun theSavedLocationGivesADistanceBeforeGpsAnswers() = runTest(main.dispatcher) {
        prefs.location.value = SavedLocation(azadi.first, azadi.second, 1L)
        repo.details[7] = ApiResponse.Success(businessDto(7).copy(latitude = milad.first, longitude = milad.second))
        val vm = vm()
        vm.load(7)
        assertTrue(vm.state.value.distanceKm() != null)
        assertFalse(vm.state.value.fresh, "shown as the saved position")
    }

    @Test
    fun aRefusedPermissionIsReportedAndNothingIsSaved() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(businessDto(7))
        val vm = vm()
        vm.load(7)
        vm.onLocationResult(LocationResult.PermissionDenied)
        assertEquals(LocationResult.PermissionDenied, vm.state.value.locationProblem)
        assertFalse(vm.state.value.locating)
        assertNull(prefs.location.value)
    }
}
