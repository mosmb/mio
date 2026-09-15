package io.morgan.mio.domain.sync

import kotlin.time.Instant

public sealed interface SyncRunResult {
    public data object Complete : SyncRunResult

    public data class Retry(
        public val notBefore: Instant,
    ) : SyncRunResult

    public data object AccountUnavailable : SyncRunResult
}
