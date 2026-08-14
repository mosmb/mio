package io.morgan.mio.core.network

import io.morgan.mio.core.logging.MioLogEvent
import io.morgan.mio.core.logging.MioLogLevel
import io.morgan.mio.core.logging.MioLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class LoggingNetworkEventSinkTest {
    @Test
    fun mapsNetworkEventsToClosedCoarseLogEventsOnly() {
        val events = mutableListOf<Pair<MioLogLevel, MioLogEvent>>()
        val sink = LoggingNetworkEventSink(MioLogger { level, event -> events += level to event })

        sink.record(NetworkEvent.Request("POST", "provider.invalid"))
        sink.record(NetworkEvent.Response("POST", "provider.invalid", 200))
        sink.record(NetworkEvent.Failure("POST", "provider.invalid", NetworkFailureCategory.TIMEOUT))

        assertEquals(
            listOf(
                MioLogLevel.DEBUG to MioLogEvent.NETWORK_REQUEST_STARTED,
                MioLogLevel.DEBUG to MioLogEvent.NETWORK_REQUEST_SUCCEEDED,
                MioLogLevel.WARNING to MioLogEvent.NETWORK_REQUEST_FAILED,
            ),
            events,
        )
    }
}
