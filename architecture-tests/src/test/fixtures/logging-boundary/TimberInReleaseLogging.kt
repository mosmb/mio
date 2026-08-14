package io.morgan.mio.core.logging

import timber.log.Timber

object TimberInReleaseLogging {
    fun log() {
        Timber.d(MioLogEvent.NETWORK_REQUEST_STARTED.name)
    }
}
