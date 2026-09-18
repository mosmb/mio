package io.morgan.mio.feature.library.api

import io.morgan.mio.core.navigation.TopLevelSlot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryKeyTest {
    private val json = Json

    @Test
    fun ordinaryLibraryRootHasNoReadyRequest() {
        val key = LibraryKey()

        assertEquals(null, key.readyRequestId)
        assertEquals(TopLevelSlot.LIBRARY, key.targetSlot)
        assertEquals(false, key.replacesTargetStack)
        assertEquals("{}", json.encodeToString(LibraryKey.serializer(), key))
    }

    @Test
    fun explicitReadyRequestRoundTripsAndRejectsBlankIds() {
        val key = LibraryKey(readyRequestId = "request-1")

        assertEquals(
            key,
            json.decodeFromString(LibraryKey.serializer(), json.encodeToString(LibraryKey.serializer(), key)),
        )
        assertTrue(key.replacesTargetStack)
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(LibraryKey.serializer(), "{\"readyRequestId\":\" \"}")
        }
    }
}
