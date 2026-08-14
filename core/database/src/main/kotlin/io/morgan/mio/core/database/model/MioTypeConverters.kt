package io.morgan.mio.core.database.model

import androidx.room.TypeConverter
import kotlin.time.Instant

public class MioTypeConverters {
    @TypeConverter
    public fun storedConnectedProviderToString(value: StoredConnectedProvider?): String? = value?.serializedValue

    @TypeConverter
    public fun stringToStoredConnectedProvider(value: String?): StoredConnectedProvider? =
        value?.let(StoredConnectedProvider::fromSerializedValue)

    @TypeConverter
    public fun storedMediaTypeToString(value: StoredMediaType?): String? = value?.serializedValue

    @TypeConverter
    public fun stringToStoredMediaType(value: String?): StoredMediaType? =
        value?.let(StoredMediaType::fromSerializedValue)

    @TypeConverter
    public fun storedMediaFormatToString(value: StoredMediaFormat?): String? = value?.serializedValue

    @TypeConverter
    public fun stringToStoredMediaFormat(value: String?): StoredMediaFormat? =
        value?.let(StoredMediaFormat::fromSerializedValue)

    @TypeConverter
    public fun storedMediaPublicationStatusToString(value: StoredMediaPublicationStatus?): String? =
        value?.serializedValue

    @TypeConverter
    public fun stringToStoredMediaPublicationStatus(value: String?): StoredMediaPublicationStatus? =
        value?.let(StoredMediaPublicationStatus::fromSerializedValue)

    @TypeConverter
    public fun storedProgressUnitToString(value: StoredProgressUnit?): String? = value?.serializedValue

    @TypeConverter
    public fun stringToStoredProgressUnit(value: String?): StoredProgressUnit? =
        value?.let(StoredProgressUnit::fromSerializedValue)

    @TypeConverter
    public fun storedCollectionStatusToString(value: StoredCollectionStatus?): String? = value?.serializedValue

    @TypeConverter
    public fun stringToStoredCollectionStatus(value: String?): StoredCollectionStatus? =
        value?.let(StoredCollectionStatus::fromSerializedValue)

    @TypeConverter
    public fun storedReleaseTimeKindToString(value: StoredReleaseTimeKind?): String? = value?.serializedValue

    @TypeConverter
    public fun stringToStoredReleaseTimeKind(value: String?): StoredReleaseTimeKind? =
        value?.let(StoredReleaseTimeKind::fromSerializedValue)

    @TypeConverter
    public fun storedPendingOperationKindToString(value: StoredPendingOperationKind?): String? = value?.serializedValue

    @TypeConverter
    public fun stringToStoredPendingOperationKind(value: String?): StoredPendingOperationKind? =
        value?.let(StoredPendingOperationKind::fromSerializedValue)

    @TypeConverter
    public fun storedPendingOperationStateToString(value: StoredPendingOperationState?): String? =
        value?.serializedValue

    @TypeConverter
    public fun stringToStoredPendingOperationState(value: String?): StoredPendingOperationState? =
        value?.let(StoredPendingOperationState::fromSerializedValue)

    @TypeConverter
    public fun instantToEpochMilliseconds(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    public fun epochMillisecondsToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)
}
