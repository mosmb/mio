package io.morgan.mio.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProgressTest {
    @Test
    fun progressValueAllowsZeroAndRejectsNegativeValues() {
        assertEquals(0, ProgressValue(0).value)
        assertThrows<IllegalArgumentException> { ProgressValue(-1) }
    }

    @Test
    fun totalsAndNumbersRequirePositiveValues() {
        assertEquals(1, UnitTotal(1).value)
        assertEquals(1, UnitNumber(1).value)
        assertThrows<IllegalArgumentException> { UnitTotal(0) }
        assertThrows<IllegalArgumentException> { UnitTotal(-1) }
        assertThrows<IllegalArgumentException> { UnitNumber(0) }
        assertThrows<IllegalArgumentException> { UnitNumber(-1) }
    }

    @Test
    fun textValuesRejectBlankInput() {
        assertThrows<IllegalArgumentException> { MediaTitle(" ") }
        assertThrows<IllegalArgumentException> { ImageReference("\t") }
    }
}
