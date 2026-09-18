package io.morgan.mio.data.sync

import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.SyncScheduleEntity
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncWorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

public class PendingSyncWorkDispatcher(
    private val database: MioDatabase,
    private val scheduler: SyncWorkScheduler,
) {
    private val cancelledAccountIds = ConcurrentHashMap.newKeySet<String>()
    private val accountMutexes = ConcurrentHashMap<String, Mutex>()
    private val dispatchJobs = ConcurrentHashMap<String, Job>()

    public fun start(scope: CoroutineScope): Job =
        scope.launch {
            val dispatchScope = this
            combine(
                database.selectedAccountDao().observeSelectedAccount(),
                database.syncDao().observeDispatchRequests(),
            ) { selectedAccount, requests ->
                selectedAccount?.accountId to requests
            }.collect { (selectedAccountId, requests) ->
                cancelledAccountIds.removeIf { accountId -> accountId != selectedAccountId }
                requests
                    .filter { request ->
                        request.accountId == selectedAccountId &&
                            request.requestedGeneration > request.dispatchedGeneration
                    }.forEach { request -> startDispatch(dispatchScope, request) }
            }
        }

    public suspend fun cancel(accountId: AccountId) {
        cancelledAccountIds += accountId.value
        dispatchJobs.remove(accountId.value)?.cancelAndJoin()
        accountMutex(accountId.value).withLock { scheduler.cancel(accountId) }
    }

    private fun startDispatch(
        scope: CoroutineScope,
        request: SyncScheduleEntity,
    ) {
        if (request.accountId in cancelledAccountIds) return
        dispatchJobs.compute(request.accountId) { accountId, existing ->
            if (existing?.isActive == true) {
                existing
            } else {
                lateinit var job: Job
                job =
                    scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            dispatch(request)
                        } finally {
                            dispatchJobs.remove(accountId, job)
                        }
                    }
                job.also { it.start() }
            }
        }
    }

    private suspend fun dispatch(request: SyncScheduleEntity) {
        while (true) {
            if (request.accountId in cancelledAccountIds) return
            try {
                accountMutex(request.accountId).withLock {
                    if (request.accountId !in cancelledAccountIds) {
                        scheduler.schedule(AccountId(request.accountId))
                        database.inTransaction {
                            database.syncDao().acknowledgeDispatch(request.accountId, request.requestedGeneration)
                        }
                    }
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

    private fun accountMutex(accountId: String): Mutex = accountMutexes.computeIfAbsent(accountId) { Mutex() }

    private companion object {
        const val DISPATCH_RETRY_DELAY_MILLIS: Long = 1_000L
    }
}
