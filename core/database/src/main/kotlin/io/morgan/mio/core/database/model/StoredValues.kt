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

public enum class StoredReleaseTimeKind(
    public val serializedValue: String,
) {
    UNKNOWN("unknown"),
    TRUSTED("trusted"),
    UNTRUSTED("untrusted"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredReleaseTimeKind =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown release time kind value: $value")
    }
}

public enum class StoredPendingOperationKind(
    public val serializedValue: String,
) {
    SET_PROGRESS_FORWARD("set_progress_forward"),
    SET_PROGRESS_CORRECTION("set_progress_correction"),
    SET_STATUS("set_status"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredPendingOperationKind =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown pending operation kind value: $value")
    }
}

public enum class StoredPendingOperationState(
    public val serializedValue: String,
) {
    ENQUEUED("enqueued"),
    IN_FLIGHT("in_flight"),
    RETRYABLE_FAILURE("retryable_failure"),
    DELIVERED("delivered"),
    BLOCKED("blocked"),
    TERMINAL_FAILURE("terminal_failure"),
    ;

    public companion object {
        public fun fromSerializedValue(value: String): StoredPendingOperationState =
            entries.firstOrNull { it.serializedValue == value }
                ?: throw IllegalArgumentException("Unknown pending operation state value: $value")
    }
}
