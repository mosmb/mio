package io.morgan.mio.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncAccountSessionFactory
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncRunResult

internal class PendingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val sessionFactory: SyncAccountSessionFactory,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val rawAccountId = inputData.getString(ACCOUNT_ID_KEY) ?: return Result.failure()
        val accountId =
            try {
                AccountId(rawAccountId)
            } catch (_: IllegalArgumentException) {
                return Result.failure()
            }
        val session =
            try {
                sessionFactory.open(accountId)
            } catch (configuration: SyncInfrastructureException) {
                if (configuration.kind == SyncInfrastructureException.Kind.CONFIGURATION) {
                    return Result.failure()
                }
                throw configuration
            } ?: return Result.success()
        session.use {
            return when (session.synchronize()) {
                SyncRunResult.Complete,
                SyncRunResult.AccountUnavailable,
                -> Result.success()
                is SyncRunResult.Retry -> Result.retry()
            }
        }
    }

    internal companion object {
        const val ACCOUNT_ID_KEY: String = "account_id"
    }
}
