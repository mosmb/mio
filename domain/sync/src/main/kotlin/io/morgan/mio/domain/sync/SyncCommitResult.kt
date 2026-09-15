package io.morgan.mio.domain.sync

public sealed interface SyncCommitResult {
    public data object Applied : SyncCommitResult

    public data object LeaseLost : SyncCommitResult

    public data object AccountGone : SyncCommitResult
}
