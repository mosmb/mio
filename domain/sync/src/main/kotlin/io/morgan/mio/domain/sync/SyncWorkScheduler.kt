package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId

public fun interface SyncWorkScheduler {
    public suspend fun schedule(accountId: AccountId)

    public suspend fun cancel(accountId: AccountId): Unit = Unit
}
