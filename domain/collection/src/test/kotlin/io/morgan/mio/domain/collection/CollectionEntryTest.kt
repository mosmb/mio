package io.morgan.mio.domain.collection

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class CollectionEntryTest {
    @Test
    fun collectionAcceptsUnknownTotalsAndStaleProgressWithoutChangingStatus() {
        val unknownTotal = entry(total = null, progress = ProgressValue(7))
        val staleProgress = entry(total = UnitTotal(3), progress = ProgressValue(7))

        assertEquals(null, unknownTotal.total)
        assertEquals(7, staleProgress.progress.value)
        assertEquals(CollectionStatus.CURRENT, staleProgress.status)
    }

    private fun entry(
        total: UnitTotal?,
        progress: ProgressValue,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId("entry-1"),
            accountId = AccountId("account-1"),
            mediaId = MediaId("media-1"),
            status = CollectionStatus.CURRENT,
            progress = progress,
            progressUnit = ProgressUnit.EPISODE,
            total = total,
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
}
