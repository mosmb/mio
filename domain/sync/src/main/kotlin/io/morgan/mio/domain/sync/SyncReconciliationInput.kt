package io.morgan.mio.domain.sync

public data class SyncReconciliationInput(
    public val candidate: SyncCandidate,
    public val remote: SyncEntryState,
)
