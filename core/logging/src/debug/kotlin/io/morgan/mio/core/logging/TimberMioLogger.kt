package io.morgan.mio.core.logging

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import timber.log.Timber

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
public class TimberMioLogger : MioLogger {
    init {
        ensureDebugTree()
    }

    override fun log(
        level: MioLogLevel,
        event: MioLogEvent,
    ) {
        when (level) {
            MioLogLevel.DEBUG -> Timber.d(event.name)
            MioLogLevel.INFO -> Timber.i(event.name)
            MioLogLevel.WARNING -> Timber.w(event.name)
            MioLogLevel.ERROR -> Timber.e(event.name)
        }
    }

    private fun ensureDebugTree() {
        synchronized(TimberMioLogger::class.java) {
            if (Timber.forest().none { tree -> tree is Timber.DebugTree }) {
                Timber.plant(Timber.DebugTree())
            }
        }
    }
}
