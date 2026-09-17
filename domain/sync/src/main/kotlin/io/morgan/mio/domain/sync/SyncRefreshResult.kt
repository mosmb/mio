package io.morgan.mio.domain.sync

/** Result of a complete content refresh, without exposing provider or presentation details. */
public sealed interface SyncRefreshResult {
    public data object Success : SyncRefreshResult

    public data object Transient : SyncRefreshResult

    public data object Authentication : SyncRefreshResult

    public data object InvalidData : SyncRefreshResult

    public data object AccountUnavailable : SyncRefreshResult
}
