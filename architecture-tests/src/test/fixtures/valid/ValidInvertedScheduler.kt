package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.AccountId

fun interface ValidInvertedScheduler {
    suspend fun schedule(accountId: AccountId)
}
