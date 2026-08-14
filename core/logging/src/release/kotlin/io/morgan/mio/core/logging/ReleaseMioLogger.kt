package io.morgan.mio.core.logging

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
public class ReleaseMioLogger : MioLogger {
    override fun log(
        level: MioLogLevel,
        event: MioLogEvent,
    ) {
        MioLogger.None.log(level, event)
    }
}
