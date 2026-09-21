package io.morgan.mio.feature.discover.impl

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class DiscoverBrowseSeasonTest {
    @Test fun labelsFollowUtcSeasonBoundariesAndYearRollover() {
        assertEquals(DiscoverBrowseSeason(2026, 2), DiscoverBrowseSeason.at(Instant.parse("2026-09-30T23:59:59Z")))
        assertEquals(DiscoverBrowseSeason(2026, 3), DiscoverBrowseSeason.at(Instant.parse("2026-10-01T00:00:00Z")))
        assertEquals(DiscoverBrowseSeason(2027, 0), DiscoverBrowseSeason(2026, 3).next())
        assertEquals(DiscoverBrowseSeason(2026, 1), DiscoverBrowseSeason(2026, 0).next())
    }
}
