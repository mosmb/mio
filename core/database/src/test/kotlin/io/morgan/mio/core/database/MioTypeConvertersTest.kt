package io.morgan.mio.core.database

import io.morgan.mio.core.database.model.MioTypeConverters
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Instant

class MioTypeConvertersTest {
    private val converters = MioTypeConverters()

    @Test
    fun everyStorageEnumRoundTripsItsStableSerializedValue() {
        StoredConnectedProvider.entries.forEach { value ->
            assertEquals(value, StoredConnectedProvider.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedConnectedProviderToString(value))
            assertEquals(value, converters.stringToStoredConnectedProvider(value.serializedValue))
        }
        StoredMediaType.entries.forEach { value ->
            assertEquals(value, StoredMediaType.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedMediaTypeToString(value))
            assertEquals(value, converters.stringToStoredMediaType(value.serializedValue))
        }
        StoredMediaFormat.entries.forEach { value ->
            assertEquals(value, StoredMediaFormat.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedMediaFormatToString(value))
            assertEquals(value, converters.stringToStoredMediaFormat(value.serializedValue))
        }
        StoredMediaPublicationStatus.entries.forEach { value ->
            assertEquals(value, StoredMediaPublicationStatus.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedMediaPublicationStatusToString(value))
            assertEquals(value, converters.stringToStoredMediaPublicationStatus(value.serializedValue))
        }
        StoredProgressUnit.entries.forEach { value ->
            assertEquals(value, StoredProgressUnit.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedProgressUnitToString(value))
            assertEquals(value, converters.stringToStoredProgressUnit(value.serializedValue))
        }
        StoredCollectionStatus.entries.forEach { value ->
            assertEquals(value, StoredCollectionStatus.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedCollectionStatusToString(value))
            assertEquals(value, converters.stringToStoredCollectionStatus(value.serializedValue))
        }
        StoredReleaseTimeKind.entries.forEach { value ->
            assertEquals(value, StoredReleaseTimeKind.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedReleaseTimeKindToString(value))
            assertEquals(value, converters.stringToStoredReleaseTimeKind(value.serializedValue))
        }
        StoredPendingOperationKind.entries.forEach { value ->
            assertEquals(value, StoredPendingOperationKind.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedPendingOperationKindToString(value))
            assertEquals(value, converters.stringToStoredPendingOperationKind(value.serializedValue))
        }
        StoredPendingOperationState.entries.forEach { value ->
            assertEquals(value, StoredPendingOperationState.fromSerializedValue(value.serializedValue))
            assertEquals(value.serializedValue, converters.storedPendingOperationStateToString(value))
            assertEquals(value, converters.stringToStoredPendingOperationState(value.serializedValue))
        }
    }

    @Test
    fun repeatingCollectionStatusHasTheExactSerializedValueAndTheEnumHasSixConstants() {
        assertEquals("repeating", StoredCollectionStatus.REPEATING.serializedValue)
        assertEquals(6, StoredCollectionStatus.entries.size)
    }

    @Test
    fun unknownEnumValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StoredConnectedProvider.fromSerializedValue("unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredMediaType("unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredMediaFormat("unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredMediaPublicationStatus("unknown-value")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredProgressUnit("unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredCollectionStatus("unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredReleaseTimeKind("never")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredPendingOperationKind("unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToStoredPendingOperationState("unknown")
        }
    }

    @Test
    fun nullValuesRemainNull() {
        assertNull(converters.storedConnectedProviderToString(null))
        assertNull(converters.stringToStoredConnectedProvider(null))
        assertNull(converters.storedMediaFormatToString(null))
        assertNull(converters.stringToStoredMediaFormat(null))
        assertNull(converters.storedPendingOperationKindToString(null))
        assertNull(converters.stringToStoredPendingOperationKind(null))
        assertNull(converters.storedPendingOperationStateToString(null))
        assertNull(converters.stringToStoredPendingOperationState(null))
        assertNull(converters.instantToEpochMilliseconds(null))
        assertNull(converters.epochMillisecondsToInstant(null))
    }

    @Test
    fun instantsRoundTripAsUtcEpochMilliseconds() {
        val values =
            listOf(
                Instant.fromEpochMilliseconds(0),
                Instant.fromEpochMilliseconds(-1234),
                Instant.parse("2026-01-02T03:04:05.678Z"),
            )

        values.forEach { value ->
            assertEquals(value, converters.epochMillisecondsToInstant(converters.instantToEpochMilliseconds(value)))
        }
        assertEquals(0L, converters.instantToEpochMilliseconds(Instant.fromEpochMilliseconds(0)))
        assertEquals(-1234L, converters.instantToEpochMilliseconds(Instant.fromEpochMilliseconds(-1234)))
    }
}
