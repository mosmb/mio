package io.morgan.mio.sync.work

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncAccountSessionFactory
import io.morgan.mio.domain.sync.SyncRunResult

class ValidThinSyncWorker(
    private val sessionFactory: SyncAccountSessionFactory,
) {
    suspend fun run(accountId: AccountId): SyncRunResult {
        val session = sessionFactory.open(accountId) ?: return SyncRunResult.AccountUnavailable
        session.use { return it.synchronize() }
    }
}
