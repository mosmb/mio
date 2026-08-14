package io.morgan.mio.sync.work.factory

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

public fun interface MioWorkerFactory {
    public fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker?
}
