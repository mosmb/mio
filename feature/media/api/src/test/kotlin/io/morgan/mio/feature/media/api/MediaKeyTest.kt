package io.morgan.mio.feature.media.api

import io.morgan.mio.domain.model.MediaId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaKeyTest {
    private val json = Json

    @Test
    fun keyIsFocusedAndSerializesToOneMioIdString() {
        val key = MediaKey(MediaId("mio-media-1"))

        assertEquals("mio-media-1", key.mediaId.value)
        assertEquals("{\"mediaId\":\"mio-media-1\"}", json.encodeToString(MediaKey.serializer(), key))
    }

    @Test
    fun keyRoundTripsAndRejectsBlankIds() {
        val key = MediaKey(MediaId("mio-media-1"))

        assertEquals(key, json.decodeFromString(MediaKey.serializer(), json.encodeToString(MediaKey.serializer(), key)))
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(MediaKey.serializer(), "{\"mediaId\":\" \"}")
        }
    }
}
