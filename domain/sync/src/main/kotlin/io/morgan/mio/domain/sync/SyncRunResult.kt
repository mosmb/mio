package io.morgan.mio.domain.sync

import kotlin.time.Instant

public sealed interface SyncRunResult {
    public data object Complete : SyncRunResult

    public data class Retry(
        public val notBefore: Instant,
        public val reason: RetryReason = RetryReason.SCHEDULED_WORK,
    ) : SyncRunResult

    public enum class RetryReason {
        LEASE_BUSY,
        SCHEDULED_WORK,
    }

    public data object AccountUnavailable : SyncRunResult
}
