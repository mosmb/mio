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

class LibraryEntryOrderTest {
    @Test
    fun ordersByEffectiveUpdateThenStableMediaAndEntryIdsWithoutMutatingInput() {
        val tiedAt = "2026-09-18T10:00:00Z"
        val input =
            listOf(
                entry("z", "z-entry", tiedAt),
                entry("older", "older-entry", "2026-09-18T09:00:00Z"),
                entry("a", "z-entry", tiedAt),
                entry("newer", "newer-entry", "2026-09-18T11:00:00Z"),
                entry("a", "b-entry", tiedAt),
            )
        val original = input.toList()

        val ordered = orderLibraryEntries(input)

        assertEquals(
            listOf("newer:newer-entry", "a:b-entry", "a:z-entry", "z:z-entry", "older:older-entry"),
            ordered.map { "${it.mediaId.value}:${it.id.value}" },
        )
        assertEquals(original, input)
    }

    private fun entry(
        mediaId: String,
        entryId: String,
        updatedAt: String,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId(entryId),
            accountId = AccountId("account-1"),
            mediaId = MediaId(mediaId),
            status = CollectionStatus.CURRENT,
            progress = ProgressValue(1),
            progressUnit = ProgressUnit.EPISODE,
            total = UnitTotal(12),
            updatedAt = Instant.parse(updatedAt),
        )
}
