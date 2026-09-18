package io.morgan.mio.di

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.logging.MioLogEvent
import io.morgan.mio.core.logging.MioLogLevel
import io.morgan.mio.core.logging.MioLogger
import io.morgan.mio.data.account.AccountSessionDisconnectSupport
import io.morgan.mio.data.preferences.local.DataStorePreferencesLocalDataSource
import io.morgan.mio.data.sync.PendingSyncWorkDispatcher
import io.morgan.mio.domain.model.AccountId

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
internal class MetroAccountSessionDisconnectSupport(
    private val pendingSyncWorkDispatcher: PendingSyncWorkDispatcher,
    private val preferencesLocalDataSource: DataStorePreferencesLocalDataSource,
    private val logger: MioLogger,
) : AccountSessionDisconnectSupport {
    override suspend fun cancelAccountWork(accountId: AccountId) {
        pendingSyncWorkDispatcher.cancel(accountId)
    }

    override suspend fun clearAccountPreferences(accountId: AccountId) {
        preferencesLocalDataSource.clearAccountPreferences(accountId)
    }

    override fun reportCleanupFailure() {
        logger.log(MioLogLevel.WARNING, MioLogEvent.ACCOUNT_DISCONNECT_CLEANUP_FAILED)
    }
}
