package io.morgan.mio.data.sync

import dev.zacsweers.metro.Inject
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.sync.local.RoomRefreshLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.RefreshRepository
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncCommitResult
import io.morgan.mio.domain.sync.SyncLease
import io.morgan.mio.domain.sync.SyncRefreshResult

@Inject
public class DefaultRefreshRepository(
    private val accountId: AccountId,
    private val remoteDataSource: ProviderRefreshRemoteDataSource,
    private val localDataSource: RoomRefreshLocalDataSource,
) : RefreshRepository {
    override suspend fun isAuthenticationBlocked(): Boolean = localDataSource.isAuthenticationBlocked(accountId)

    override suspend fun refresh(lease: SyncLease): SyncRefreshResult =
        when (val result = remoteDataSource.read(lease.accountId)) {
            is ProviderResult.Success ->
                when (localDataSource.apply(lease, result.value)) {
                    SyncCommitResult.Applied -> SyncRefreshResult.Success
                    SyncCommitResult.LeaseLost -> SyncRefreshResult.Transient
                    SyncCommitResult.AccountGone -> SyncRefreshResult.AccountUnavailable
                }
            is ProviderResult.Failure -> result.failure.toRefreshResult()
        }

    override suspend fun recordFailure(
        lease: SyncLease,
        kind: SyncAccountIssueKind,
    ): SyncCommitResult = localDataSource.recordFailure(lease, kind)
}

private fun ProviderFailure.toRefreshResult(): SyncRefreshResult =
    when (this) {
        ProviderFailure.NetworkUnavailable,
        ProviderFailure.Timeout,
        ProviderFailure.ServiceUnavailable,
        is ProviderFailure.RateLimited,
        -> SyncRefreshResult.Transient
        ProviderFailure.AuthenticationRequired,
        ProviderFailure.AuthorizationDenied,
        ProviderFailure.InvalidAuthorizationCallback,
        -> SyncRefreshResult.Authentication
        ProviderFailure.MalformedResponse,
        ProviderFailure.UnsupportedMandatoryData,
        ProviderFailure.MutationRejected,
        ProviderFailure.MutationConflict,
        ProviderFailure.Unexpected,
        -> SyncRefreshResult.InvalidData
    }
