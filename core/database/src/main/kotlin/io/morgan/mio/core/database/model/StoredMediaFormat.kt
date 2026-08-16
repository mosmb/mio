package io.morgan.mio.core.database.model

public enum class StoredMediaFormat(
    public val serializedValue: String,
) {
    TV("tv"),
    MOVIE("movie"),
    OVA("ova"),
    ONA("ona"),
    SPECIAL("special"),
    MUSIC("music"),
    MANGA("manga"),
    NOVEL("novel"),
    ONE_SHOT("one_shot"),
    OTHER("other"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredMediaFormat =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown media format value: $value")
    }
}
