package io.morgan.mio.core.database.model

public enum class StoredProgressUnit(
    public val serializedValue: String,
) {
    EPISODE("episode"),
    CHAPTER("chapter"),
    VOLUME("volume"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredProgressUnit =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown progress unit value: $value")
    }
}
