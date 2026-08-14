package io.morgan.mio.core.network

import io.morgan.mio.core.logging.MioLogEvent
import io.morgan.mio.core.logging.MioLogLevel
import io.morgan.mio.core.logging.MioLogger

internal class LoggingNetworkEventSink(
    private val logger: MioLogger,
) : NetworkEventSink {
    override fun record(event: NetworkEvent) {
        when (event) {
            is NetworkEvent.Request -> logger.log(MioLogLevel.DEBUG, MioLogEvent.NETWORK_REQUEST_STARTED)
            is NetworkEvent.Response -> logger.log(MioLogLevel.DEBUG, MioLogEvent.NETWORK_REQUEST_SUCCEEDED)
            is NetworkEvent.Failure -> logger.log(MioLogLevel.WARNING, MioLogEvent.NETWORK_REQUEST_FAILED)
        }
    }
}
