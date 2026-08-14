package io.morgan.mio.core.testing.coroutines

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher

public class MioTestDispatchers(
    public val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) {
    public val default: TestDispatcher = StandardTestDispatcher(scheduler)
    public val io: TestDispatcher = StandardTestDispatcher(scheduler)
    public val main: TestDispatcher = StandardTestDispatcher(scheduler)
}
