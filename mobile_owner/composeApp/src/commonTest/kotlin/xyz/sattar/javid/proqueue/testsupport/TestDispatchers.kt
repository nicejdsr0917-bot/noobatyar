package xyz.sattar.javid.proqueue.testsupport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Installs a test dispatcher as Dispatchers.Main for the duration of a test
 * (viewModelScope runs on Main). Call [setUp] in @BeforeTest and [tearDown]
 * in @AfterTest, or use [withMainDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherSetup(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) {
    fun setUp() = Dispatchers.setMain(dispatcher)
    fun tearDown() = Dispatchers.resetMain()

    companion object {
        fun standard() = MainDispatcherSetup(StandardTestDispatcher())
    }
}
