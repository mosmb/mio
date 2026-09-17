package io.morgan.mio.data.sync

import dev.zacsweers.metro.Inject
import io.morgan.mio.data.sync.local.RoomSyncRecoveryLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import io.morgan.mio.domain.sync.SyncRecoveryState
import kotlinx.coroutines.flow.Flow

@Inject
public class DefaultSyncRecoveryRepository(
    private val accountId: AccountId,
    private val localDataSource: RoomSyncRecoveryLocalDataSource,
) : SyncRecoveryRepository {
    override fun observeState(): Flow<SyncRecoveryState> = localDataSource.observeState(accountId)

    override suspend fun readIssue(operationId: OperationId): SyncIssue? =
        localDataSource.readIssue(accountId, operationId)

    override suspend fun rearm(issue: SyncIssue): Boolean = localDataSource.rearm(accountId, issue)

    override suspend fun recoverAuthentication(
        accountId: AccountId,
        issueIds: Set<String>,
    ): Boolean = if (accountId != this.accountId) false else localDataSource.recoverAuthentication(accountId, issueIds)
}
