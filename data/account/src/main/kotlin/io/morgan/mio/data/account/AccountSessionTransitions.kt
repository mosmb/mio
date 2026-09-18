package io.morgan.mio.data.account

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import kotlinx.coroutines.sync.Mutex

/** Serializes credential changes across connection, reconnection, and disconnection. */
@SingleIn(AppScope::class)
@Inject
internal class AccountSessionTransitions {
    val mutex = Mutex()
    var generation: Long = 0
}
