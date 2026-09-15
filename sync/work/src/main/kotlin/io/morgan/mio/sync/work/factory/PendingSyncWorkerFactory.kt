package io.morgan.mio.sync.work.factory

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.domain.sync.SyncAccountSessionFactory
import io.morgan.mio.sync.work.PendingSyncWorker

@ContributesIntoSet(AppScope::class)
@Inject
internal class PendingSyncWorkerFactory(
    private val sessionFactory: SyncAccountSessionFactory,
) : MioWorkerFactory {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == PendingSyncWorker::class.java.name) {
            PendingSyncWorker(appContext, workerParameters, sessionFactory)
        } else {
            null
        }
}
