package io.morgan.mio.domain.sync

public enum class SyncIssueKind {
    AUTHENTICATION,
    REJECTED,
    CONFLICT,
    RETRY_EXHAUSTED,
    INVALID_REMOTE_DATA,
}
