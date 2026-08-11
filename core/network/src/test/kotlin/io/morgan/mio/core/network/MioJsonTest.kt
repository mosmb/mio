package io.morgan.mio.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MioJsonTest {
    @Test
    fun configurationIsStrictExceptForUnknownKeys() {
        assertTrue(MioJson.configuration.ignoreUnknownKeys)
        assertFalse(MioJson.configuration.explicitNulls)
        assertFalse(MioJson.configuration.encodeDefaults)
        assertFalse(MioJson.configuration.isLenient)
        assertFalse(MioJson.configuration.coerceInputValues)
        assertFalse(MioJson.configuration.allowStructuredMapKeys)
        assertFalse(MioJson.configuration.prettyPrint)
    }

    @Test
    fun unknownKeysAreIgnoredButMalformedRequiredAndUnknownEnumValuesFail() {
        val decoded = MioJson.decodeFromString<TestDto>("""{"required":"value","unknown":"ignored"}""")
        assertEquals("value", decoded.required)

        val sentinel = "fixture-sensitive-value"
        val malformed =
            assertThrows<SerializationException> {
                MioJson.decodeFromString<TestDto>("""{"required":"$sentinel","broken":""")
            }
        assertFalse(malformed.message.orEmpty().contains(sentinel))
        assertThrows<SerializationException> { MioJson.decodeFromString<TestDto>("{}") }
        assertThrows<SerializationException> {
            MioJson.decodeFromString<TestEnumDto>("""{"kind":"NOT_KNOWN"}""")
        }
    }

    @Test
    fun suppliedJsonSettingsDoNotRelaxTheSharedJson() {
        val lenientJson = Json { isLenient = true }

        assertTrue(lenientJson.configuration.isLenient)
        assertFalse(MioJson.configuration.isLenient)
    }

    @Serializable
    private data class TestDto(
        val required: String,
    )

    @Serializable
    private enum class TestKind {
        KNOWN,
    }

    @Serializable
    private data class TestEnumDto(
        val kind: TestKind,
    )

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (throwable: Throwable) {
            assertTrue(throwable is T)
            @Suppress("UNCHECKED_CAST")
            return throwable as T
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
}
