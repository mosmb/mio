package io.morgan.mio.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IdentityTest {
    @Test
    fun idsRejectBlankValuesAndPreserveOpaqueValues() {
        listOf<() -> Any>(
            { AccountId("") },
            { MediaId("   ") },
            { CollectionEntryId("\t\n") },
            { OperationId(" ") },
        ).forEach { create ->
            assertThrows<IllegalArgumentException> { create() }
        }

        assertEquals(" provider / 42 ", AccountId(" provider / 42 ").value)
        assertEquals(AccountId("same"), AccountId("same"))
        assertNotEquals(AccountId("same"), MediaId("same"))
    }

    @Test
    fun everyIdTypeRejectsEmptyAndWhitespaceOnlyInput() {
        listOf("", "   ", "\t", "\n").forEach { blank ->
            assertThrows<IllegalArgumentException> { AccountId(blank) }
            assertThrows<IllegalArgumentException> { MediaId(blank) }
            assertThrows<IllegalArgumentException> { CollectionEntryId(blank) }
            assertThrows<IllegalArgumentException> { OperationId(blank) }
        }
    }
}
