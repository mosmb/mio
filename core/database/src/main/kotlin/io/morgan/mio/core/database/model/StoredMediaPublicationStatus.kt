package io.morgan.mio.core.database.model

public enum class StoredMediaPublicationStatus(
    public val serializedValue: String,
) {
    UPCOMING("upcoming"),
    RELEASING("releasing"),
    FINISHED("finished"),
    CANCELLED("cancelled"),
    HIATUS("hiatus"),
    UNKNOWN("unknown"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredMediaPublicationStatus =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown publication status value: $value")
    }
}
