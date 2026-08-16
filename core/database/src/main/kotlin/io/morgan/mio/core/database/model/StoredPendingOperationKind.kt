package io.morgan.mio.core.database.model

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
