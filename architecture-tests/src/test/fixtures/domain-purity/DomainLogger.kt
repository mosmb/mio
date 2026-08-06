package io.morgan.mio.domain.model

import timber.log.Timber

object DomainLogger {
    fun log(message: String) {
        Timber.d(message)
    }
}
