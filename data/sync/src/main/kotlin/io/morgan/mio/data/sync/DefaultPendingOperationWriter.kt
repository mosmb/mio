package io.morgan.mio.data.sync

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.sync.local.RoomPendingOperationLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.PendingOperationGroup

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultPendingOperationWriter(
    private val accountId: AccountId,
    private val localDataSource: RoomPendingOperationLocalDataSource,
) : PendingOperationWriter {
    override suspend fun enqueue(group: PendingOperationGroup) {
        require(group.accountId == accountId)
        localDataSource.enqueue(group)
    }
}
