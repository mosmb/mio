package io.morgan.mio.core.database.model

public enum class StoredMediaType(
    public val serializedValue: String,
) {
    ANIME("anime"),
    MANGA("manga"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredMediaType =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown media type value: $value")
    }
}
