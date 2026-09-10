package io.morgan.mio.domain.progress

public sealed interface ProgressWriteResult {
    public data class Committed(
        public val action: ProgressAction,
    ) : ProgressWriteResult

    public data class AlreadyHandled(
        public val action: ProgressAction,
    ) : ProgressWriteResult

    public data object Stale : ProgressWriteResult

    public data class Rejected(
        public val reason: Rejection,
    ) : ProgressWriteResult

    public enum class Rejection {
        MISSING_ENTRY,
        INELIGIBLE,
        AT_BOUNDARY,
        INVALID_REQUEST,
        ACTION_ID_REUSED,
        UNDO_UNAVAILABLE,
    }
}
