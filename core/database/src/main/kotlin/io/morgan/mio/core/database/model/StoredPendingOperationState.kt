package io.morgan.mio.core.database.model

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
