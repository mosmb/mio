package io.morgan.mio.core.logging

import io.morgan.mio.core.logging.MioLogEvent
import timber.log.Timber

object ValidLogging {
    fun debug() {
        Timber.d(MioLogEvent.NETWORK_REQUEST_STARTED.name)
    }
}
