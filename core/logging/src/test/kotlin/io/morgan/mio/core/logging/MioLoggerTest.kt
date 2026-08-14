package io.morgan.mio.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MioLoggerTest {
    @Test
    fun apiIsClosedAndNoneIsSilent() {
        val events = mutableListOf<Pair<MioLogLevel, MioLogEvent>>()
        val recording = MioLogger { level, event -> events += level to event }

        recording.log(MioLogLevel.DEBUG, MioLogEvent.NETWORK_REQUEST_STARTED)
        MioLogger.None.log(MioLogLevel.ERROR, MioLogEvent.NETWORK_REQUEST_FAILED)

        assertEquals(
            listOf(
                MioLogLevel.DEBUG to MioLogEvent.NETWORK_REQUEST_STARTED,
            ),
            events,
        )
        assertEquals(
            listOf("DEBUG", "INFO", "WARNING", "ERROR"),
            MioLogLevel.entries.map(Enum<*>::name),
        )
        assertEquals(3, MioLogEvent.entries.size)
        assertFalse(events.toString().contains("fixture-sensitive-value"))
        assertTrue(MioLogger.None !== recording)
    }
}
