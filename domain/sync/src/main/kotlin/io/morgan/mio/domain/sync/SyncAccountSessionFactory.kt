package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId

public fun interface SyncAccountSessionFactory {
    public suspend fun open(accountId: AccountId): SyncAccountSession?
}
