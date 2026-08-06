package io.morgan.mio.feature.today.impl

import timber.log.Timber

object TimberOutsideLogging {
    fun log(message: String) {
        Timber.d(message)
    }
}
