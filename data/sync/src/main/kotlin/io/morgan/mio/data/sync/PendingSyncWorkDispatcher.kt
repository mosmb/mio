package io.morgan.mio.data.sync

import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.SyncScheduleEntity
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncWorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

public class PendingSyncWorkDispatcher(
    private val database: MioDatabase,
    private val scheduler: SyncWorkScheduler,
) {
    public fun start(scope: CoroutineScope): Job =
        scope.launch {
            database
                .syncDao()
                .observeDispatchRequests()
                .map { requests ->
                    requests.filter { request ->
                        request.requestedGeneration >
                            request.dispatchedGeneration
                    }
                }.onEach { requests -> requests.forEach { request -> dispatch(request) } }
                .collect()
        }

    private suspend fun dispatch(request: SyncScheduleEntity) {
        while (true) {
            try {
                scheduler.schedule(AccountId(request.accountId))
                database.inTransaction {
                    database.syncDao().acknowledgeDispatch(request.accountId, request.requestedGeneration)
                }
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Leave the generation unacknowledged. The bounded retry also covers a WorkManager
                // initialization race; process-start observation remains the durable recovery path.
                delay(DISPATCH_RETRY_DELAY_MILLIS)
            }
        }
    }

    private companion object {
        const val DISPATCH_RETRY_DELAY_MILLIS: Long = 1_000L
    }
}
