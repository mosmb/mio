package io.morgan.mio.domain.sync

public enum class PendingOperationState {
    ENQUEUED,
    IN_FLIGHT,
    RETRYABLE_FAILURE,
    DELIVERED,
    BLOCKED,
    TERMINAL_FAILURE,
}
