package xyz.sattar.javid.proqueue.testsupport

import app.cash.turbine.test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HarnessSmokeTest {
    @Test
    fun turbineAndCoroutinesTestRun() = runTest {
        flowOf(1, 2).test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            awaitComplete()
        }
    }
}
