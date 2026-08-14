package io.morgan.mio.core.testing.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class MioTestDispatchersTest {
    @Test
    fun allDispatchersShareOneSchedulerAndVirtualTime() =
        runTest {
            val dispatchers = MioTestDispatchers(testScheduler)
            val calls = mutableListOf<String>()
            val scope = CoroutineScope(SupervisorJob())

            assertSame(testScheduler, dispatchers.default.scheduler)
            assertSame(testScheduler, dispatchers.io.scheduler)
            assertSame(testScheduler, dispatchers.main.scheduler)

            scope.launch(dispatchers.default) {
                delay(1.seconds)
                calls += "default"
            }
            scope.launch(dispatchers.io) {
                delay(1.seconds)
                calls += "io"
            }
            scope.launch(dispatchers.main) {
                calls += "main"
            }

            assertEquals(emptyList<String>(), calls)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("main", "default", "io"), calls)
            scope.cancel()
        }
}
