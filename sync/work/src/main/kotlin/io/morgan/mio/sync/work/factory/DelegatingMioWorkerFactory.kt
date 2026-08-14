package io.morgan.mio.sync.work.factory

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

internal class DelegatingMioWorkerFactory(
    factories: Set<MioWorkerFactory>,
) : WorkerFactory() {
    private val orderedFactories =
        factories.sortedWith(
            compareBy(
                { factory -> factory::class.qualifiedName.orEmpty() },
                { factory -> factory::class.java.name },
            ),
        )

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val claimedWorkers =
            orderedFactories.mapNotNull { factory ->
                factory.createWorker(appContext, workerClassName, workerParameters)
            }
        check(claimedWorkers.size <= 1) {
            "Multiple MioWorkerFactory instances claimed a worker"
        }
        return claimedWorkers.singleOrNull()
    }
}
