package io.morgan.mio.domain.sync

public data class SyncRecoveryState(
    public val accountIssue: SyncAccountIssue?,
    public val entryIssues: List<SyncIssue>,
)
