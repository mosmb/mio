package io.morgan.mio.domain.collection

public sealed interface CollectionWriteResult {
    public data class Committed(
        public val action: CollectionAction,
    ) : CollectionWriteResult

    public data class AlreadyHandled(
        public val action: CollectionAction,
    ) : CollectionWriteResult

    public data class Rejected(
        public val reason: Rejection,
    ) : CollectionWriteResult

    public data object Stale : CollectionWriteResult

    public enum class Rejection {
        INVALID_REQUEST,
        MISSING_ENTRY,
        UNSUPPORTED_STATUS,
        ACTION_ID_REUSED,
        UNDO_UNAVAILABLE,
    }
}
