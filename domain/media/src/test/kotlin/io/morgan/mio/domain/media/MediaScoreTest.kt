package io.morgan.mio.domain.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MediaScoreTest {
    @Test
    fun acceptsOneThroughOneHundred() {
        assertEquals(1, MediaScore(1).percent)
        assertEquals(100, MediaScore(100).percent)
    }

    @Test
    fun rejectsZeroAndValuesAboveOneHundred() {
        assertThrows<IllegalArgumentException> { MediaScore(0) }
        assertThrows<IllegalArgumentException> { MediaScore(101) }
    }
}
