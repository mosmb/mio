package io.morgan.mio.core.testing.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FakeClockTest {
    @Test
    fun setAndAdvanceByExposeDeterministicInstants() {
        val clock = FakeClock(Instant.parse("2026-08-14T09:00:00Z"))

        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), clock.now())
        assertEquals(Instant.parse("2026-08-14T09:00:02Z"), clock.advanceBy(2.seconds))
        clock.set(Instant.parse("2026-08-15T12:00:00Z"))
        assertEquals(Instant.parse("2026-08-15T12:00:00Z"), clock.now())
    }

    @Test
    fun infiniteAdvancesAreRejectedAndDiagnosticsAreRedacted() {
        val clock = FakeClock(Instant.parse("2026-08-14T09:00:00Z"))

        assertThrows<IllegalArgumentException> { clock.advanceBy(kotlin.time.Duration.INFINITE) }
        assertTrue(!clock.toString().contains("2026-08-14"))
    }

    @Test
    fun finiteOverflowIsRejectedWithoutChangingTheClock() {
        val maximum = Instant.parse("+1000000000-12-31T23:59:59.999999999Z")
        val clock = FakeClock(maximum)

        assertThrows<IllegalArgumentException> { clock.advanceBy(1.nanoseconds) }

        assertEquals(maximum, clock.now())
    }
}
