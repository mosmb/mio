package io.morgan.mio.domain.collection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionStatusTest {
    @Test
    fun collectionStatusHasExactlySixConstantsInOrder() {
        assertEquals(
            listOf(
                CollectionStatus.CURRENT,
                CollectionStatus.PLANNED,
                CollectionStatus.PAUSED,
                CollectionStatus.COMPLETED,
                CollectionStatus.DROPPED,
                CollectionStatus.REPEATING,
            ),
            CollectionStatus.entries,
        )
    }
}
