package io.morgan.mio.core.database.model

public enum class StoredCollectionStatus(
    public val serializedValue: String,
) {
    CURRENT("current"),
    PLANNED("planned"),
    PAUSED("paused"),
    COMPLETED("completed"),
    DROPPED("dropped"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredCollectionStatus =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown collection status value: $value")
    }
}
