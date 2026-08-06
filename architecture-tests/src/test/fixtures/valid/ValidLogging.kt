package io.morgan.mio.core.logging

import timber.log.Timber

object ValidLogging {
    fun debug(message: String) {
        Timber.d(message)
    }
}
