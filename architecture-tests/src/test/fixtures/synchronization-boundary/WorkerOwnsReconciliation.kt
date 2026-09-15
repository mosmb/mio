package io.morgan.mio.sync.work

private data class Remote(val progress: Int)

class WorkerOwnsReconciliationWorker {
    fun shouldWrite(remote: Remote): Boolean = remote.progress < 5
}
