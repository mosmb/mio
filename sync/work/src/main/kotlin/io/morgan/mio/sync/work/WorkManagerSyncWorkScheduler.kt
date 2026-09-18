package io.morgan.mio.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncWorkScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
public class WorkManagerSyncWorkScheduler(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SyncWorkScheduler {
    override suspend fun schedule(accountId: AccountId) {
        val request =
            OneTimeWorkRequestBuilder<PendingSyncWorker>()
                .setInputData(
                    androidx.work.Data
                        .Builder()
                        .putString(PendingSyncWorker.ACCOUNT_ID_KEY, accountId.value)
                        .build(),
                ).setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(accountId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    override suspend fun cancel(accountId: AccountId) {
        withContext(ioDispatcher) {
            WorkManager
                .getInstance(context)
                .cancelUniqueWork(uniqueWorkName(accountId))
                .result
                .get()
        }
    }

    public companion object {
        public const val UNIQUE_WORK_PREFIX: String = "mio.pending."

        public fun uniqueWorkName(accountId: AccountId): String = UNIQUE_WORK_PREFIX + accountId.value
    }
}
