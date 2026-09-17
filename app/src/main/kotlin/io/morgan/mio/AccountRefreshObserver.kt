package io.morgan.mio

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.core.network.NetworkAvailability
import io.morgan.mio.core.network.NetworkMonitor
import io.morgan.mio.di.AccountGraphLifecycle
import io.morgan.mio.domain.account.AccountSessionRepository
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncRunResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Clock

@SingleIn(AppScope::class)
@Inject
internal class AccountRefreshObserver(
    private val accountSessionRepository: AccountSessionRepository,
    private val accountGraphLifecycle: AccountGraphLifecycle,
    private val networkMonitor: NetworkMonitor,
    private val clock: Clock,
    @param:IoDispatcher private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) {
    private var observerJob: Job? = null
    private var refreshJob: Job? = null
    private var ownerScope: CoroutineScope? = null
    private var activeAccount: AccountId? = null
    private var trailingRequested = false
    private var foreground = false
    private var lastAvailability: NetworkAvailability? = null

    internal fun start(scope: CoroutineScope) {
        synchronized(this) {
            if (observerJob?.isActive == true) return
            foreground = true
            ownerScope = scope
            lastAvailability = null
            observerJob =
                scope.launch(dispatcher) {
                    launch {
                        accountSessionRepository.state
                            .map { state -> (state as? AccountSessionState.Authenticated)?.accountId }
                            .distinctUntilChanged()
                            .collect { accountId -> onAccountChanged(accountId) }
                    }
                    launch {
                        networkMonitor.availability
                            .distinctUntilChanged()
                            .collect { availability -> onAvailabilityChanged(availability) }
                    }
                }
        }
    }

    internal fun stop() {
        val jobs =
            synchronized(this) {
                foreground = false
                trailingRequested = false
                activeAccount = null
                ownerScope = null
                lastAvailability = null
                val observer = observerJob
                val refresh = refreshJob
                observerJob = null
                refreshJob = null
                observer to refresh
            }
        jobs.first?.cancel()
        jobs.second?.cancel()
    }

    internal fun requestRefresh() {
        synchronized(this) {
            val accountId = activeAccount ?: return
            if (!foreground) return
            if (lastAvailability != NetworkAvailability.AVAILABLE) return
            if (refreshJob?.isActive == true) {
                trailingRequested = true
                return
            }
            val scope = ownerScope ?: return
            launchRefreshLocked(scope, accountId)
        }
    }

    private fun launchRefreshLocked(
        scope: CoroutineScope,
        accountId: AccountId,
    ) {
        val job = scope.launch(dispatcher) { runRefreshLoop(accountId) }
        refreshJob = job
        job.invokeOnCompletion {
            synchronized(this) {
                if (refreshJob !== job) return@synchronized
                refreshJob = null
                if (
                    foreground &&
                    activeAccount == accountId &&
                    lastAvailability == NetworkAvailability.AVAILABLE &&
                    trailingRequested
                ) {
                    trailingRequested = false
                    ownerScope?.let { nextScope -> launchRefreshLocked(nextScope, accountId) }
                }
            }
        }
    }

    private fun onAccountChanged(accountId: AccountId?) {
        synchronized(this) {
            if (activeAccount == accountId) return
            activeAccount = accountId
            trailingRequested = false
            refreshJob?.cancel()
            refreshJob = null
        }
        if (accountId != null) requestRefresh()
    }

    private fun onAvailabilityChanged(availability: NetworkAvailability) {
        val shouldRequestWhenAvailable =
            synchronized(this) {
                val previous = lastAvailability
                lastAvailability = availability
                previous == null || previous == NetworkAvailability.UNAVAILABLE
            }
        if (availability == NetworkAvailability.UNAVAILABLE) {
            synchronized(this) {
                trailingRequested = false
                refreshJob?.cancel()
            }
        } else if (shouldRequestWhenAvailable) {
            requestRefresh()
        }
    }

    private suspend fun runRefreshLoop(accountId: AccountId) {
        var leaseBusyRetryUsed = false
        while (true) {
            synchronized(this) {
                if (
                    !foreground ||
                    activeAccount != accountId ||
                    lastAvailability != NetworkAvailability.AVAILABLE
                ) {
                    return
                }
                trailingRequested = false
            }
            val synchronize =
                accountGraphLifecycle.withActiveGraph { graph ->
                    graph.synchronizeAccountUseCase.takeIf { graph.accountId == accountId }
                }
            val result =
                try {
                    synchronize?.invoke(refreshContent = true)
                } catch (_: SyncInfrastructureException) {
                    // Keep cached rendering and the observer alive; the next lifecycle/network signal retries.
                    return
                }
            if (result == null) {
                synchronized(this) { trailingRequested = false }
                return
            }
            var waitForLeaseRetry = false
            val runAgain =
                synchronized(this) {
                    if (!foreground ||
                        activeAccount != accountId ||
                        lastAvailability != NetworkAvailability.AVAILABLE
                    ) {
                        trailingRequested = false
                        false
                    } else {
                        val requestedWhileRunning = trailingRequested
                        trailingRequested = false
                        val busyLeaseRetry =
                            result is SyncRunResult.Retry &&
                                result.reason == SyncRunResult.RetryReason.LEASE_BUSY &&
                                !leaseBusyRetryUsed
                        if (busyLeaseRetry) {
                            leaseBusyRetryUsed = true
                            waitForLeaseRetry = true
                        }
                        requestedWhileRunning || busyLeaseRetry
                    }
                }
            if (!runAgain) return
            if (result is SyncRunResult.Retry && waitForLeaseRetry) {
                val waitMillis = max(0L, (result.notBefore - clock.now()).inWholeMilliseconds)
                if (waitMillis > 0L) delay(waitMillis)
            }
        }
    }
}
