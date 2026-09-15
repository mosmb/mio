package io.morgan.mio.domain.sync

import kotlin.time.Duration

public sealed interface SyncRemoteResult {
    public data class Success(
        public val entry: SyncEntryState,
    ) : SyncRemoteResult

    public data class Transient(
        public val retryAfter: Duration? = null,
    ) : SyncRemoteResult {
        init {
            require(retryAfter == null || retryAfter.isFinite() && !retryAfter.isNegative())
        }
    }

    public data object Authentication : SyncRemoteResult

    public data class Rejected(
        public val remote: SyncEntryState? = null,
    ) : SyncRemoteResult

    public data class Conflict(
        public val remote: SyncEntryState? = null,
    ) : SyncRemoteResult

    public data object InvalidData : SyncRemoteResult
}
