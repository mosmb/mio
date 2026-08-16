package io.morgan.mio.core.database.model

public enum class StoredConnectedProvider(
    public val serializedValue: String,
) {
    ANILIST("anilist"),
    MY_ANIME_LIST("my_anime_list"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredConnectedProvider =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown connected provider value: $value")
    }
}
