package io.morgan.mio.domain.media

import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.UnitNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ReleaseTest {
    @Test
    fun releaseTimingRepresentsUnknownTrustedAndUntrustedStates() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        val unknown = ReleaseTime.Unknown
        val trusted = ReleaseTime.Trusted(instant)
        val untrusted = ReleaseTime.Untrusted(instant)

        assertEquals(ReleaseTime.Unknown, unknown)
        assertEquals(instant, trusted.instant)
        assertEquals(instant, untrusted.instant)
        assertEquals(
            Release(
                mediaId = MediaId("media-1"),
                unit = ProgressUnit.EPISODE,
                number = UnitNumber(1),
                time = trusted,
            ).time,
            trusted,
        )
    }
}
