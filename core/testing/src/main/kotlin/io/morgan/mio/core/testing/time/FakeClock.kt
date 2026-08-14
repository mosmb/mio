package io.morgan.mio.core.testing.time

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Instant

public class FakeClock(
    initialInstant: Instant,
) : Clock {
    private val lock = Any()
    private var currentInstant: Instant = initialInstant

    override fun now(): Instant = synchronized(lock) { currentInstant }

    public fun set(instant: Instant) {
        synchronized(lock) {
            currentInstant = instant
        }
    }

    public fun advanceBy(duration: Duration): Instant {
        require(duration.isFinite()) { "Fake clock duration must be finite" }
        return synchronized(lock) {
            val next =
                try {
                    currentInstant + duration
                } catch (_: RuntimeException) {
                    throw IllegalArgumentException("Fake clock advance overflow")
                }
            require(
                duration == ZERO ||
                    (duration > ZERO && next > currentInstant) ||
                    (duration < ZERO && next < currentInstant),
            ) { "Fake clock advance overflow" }
            currentInstant = next
            next
        }
    }

    override fun toString(): String = "FakeClock([REDACTED])"
}
