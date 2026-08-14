package io.morgan.mio.core.logging

import timber.log.Timber

object RuntimeStringLogger {
    fun log(message: String) {
        Timber.d(message)
    }
}
