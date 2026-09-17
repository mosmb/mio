package io.morgan.mio.domain.sync

public interface RefreshRepository {
    public suspend fun isAuthenticationBlocked(): Boolean

    public suspend fun refresh(lease: SyncLease): SyncRefreshResult

    public suspend fun recordFailure(
        lease: SyncLease,
        kind: SyncAccountIssueKind,
    ): SyncCommitResult
}
