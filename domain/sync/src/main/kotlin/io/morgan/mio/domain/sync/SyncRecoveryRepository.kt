package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import kotlinx.coroutines.flow.Flow

public interface SyncRecoveryRepository {
    public fun observeState(): Flow<SyncRecoveryState>

    public suspend fun readIssue(operationId: OperationId): SyncIssue?

    public suspend fun rearm(issue: SyncIssue): Boolean

    public suspend fun resolveConflict(
        issue: SyncIssue,
        resolution: PendingOperationGroup,
    ): Boolean

    public suspend fun recoverAuthentication(
        accountId: AccountId,
        issueIds: Set<String>,
    ): Boolean
}
