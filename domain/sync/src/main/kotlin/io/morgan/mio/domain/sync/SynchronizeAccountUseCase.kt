package io.morgan.mio.domain.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

public class SynchronizeAccountUseCase(
    private val repository: SyncRepository,
    private val clock: Clock,
    private val reconcile: ReconcilePendingOperationUseCase,
    private val refreshRepository: RefreshRepository,
    private val newLeaseOwner: () -> String,
) {
    public suspend operator fun invoke(refreshContent: Boolean = false): SyncRunResult {
        val startedAt = clock.now()
        val lease =
            repository.acquireLease(newLeaseOwner(), startedAt, startedAt + LEASE_TTL)
                ?: return SyncRunResult.Retry(
                    notBefore = startedAt + LEASE_TTL,
                    reason = SyncRunResult.RetryReason.LEASE_BUSY,
                )
        val leaseLost = AtomicBoolean(false)
        val accountGone = AtomicBoolean(false)
        val activeLease =
            java.util.concurrent.atomic
                .AtomicReference(lease)
        var earliestRetryAt: Instant? = null
        return try {
            coroutineScope {
                val heartbeat =
                    launch {
                        while (isActive && !leaseLost.get()) {
                            delay(HEARTBEAT)
                            val now = clock.now()
                            val renewed = repository.renewLease(activeLease.get(), now, now + LEASE_TTL)
                            if (renewed == null) {
                                leaseLost.set(true)
                                break
                            }
                            activeLease.set(renewed)
                        }
                    }
                try {
                    var authenticationBlocked = refreshRepository.isAuthenticationBlocked()
                    if (!authenticationBlocked && refreshContent && isActive) {
                        when (
                            val result =
                                withLeaseTimeout(activeLease.get()) { refreshRepository.refresh(activeLease.get()) }
                        ) {
                            SyncRefreshResult.Success,
                            SyncRefreshResult.Transient,
                            -> Unit
                            SyncRefreshResult.Authentication ->
                                refreshRepository
                                    .recordFailure(
                                        activeLease.get(),
                                        SyncAccountIssueKind.AUTHENTICATION,
                                    ).also { commit ->
                                        handleCommitResult(commit, leaseLost, accountGone)
                                        authenticationBlocked = true
                                    }
                            SyncRefreshResult.InvalidData ->
                                handleCommitResult(
                                    refreshRepository.recordFailure(
                                        activeLease.get(),
                                        SyncAccountIssueKind.INVALID_REMOTE_DATA,
                                    ),
                                    leaseLost,
                                    accountGone,
                                )
                            SyncRefreshResult.AccountUnavailable -> accountGone.set(true)
                            null -> Unit
                        }
                    }
                    while (isActive && !leaseLost.get() && !accountGone.get() && !authenticationBlocked) {
                        val candidate = repository.claimNext(activeLease.get(), clock.now()) ?: break
                        if (candidate.attemptBudgetExhausted) {
                            handleCommitResult(
                                repository.recordFailure(
                                    activeLease.get(),
                                    candidate,
                                    SyncIssueKind.RETRY_EXHAUSTED,
                                    null,
                                    null,
                                ),
                                leaseLost,
                                accountGone,
                            )
                            continue
                        }

                        val remote =
                            withLeaseTimeout(activeLease.get()) { repository.readRemote(candidate) }
                                ?: SyncRemoteResult.Transient()
                        when (remote) {
                            is SyncRemoteResult.Success -> {
                                when (val decision = reconcile(SyncReconciliationInput(candidate, remote.entry))) {
                                    SyncReconciliationDecision.Satisfied ->
                                        handleCommitResult(
                                            repository.commitSnapshot(
                                                activeLease.get(),
                                                listOf(remote.entry),
                                                setOf(candidate.operation.id),
                                            ),
                                            leaseLost,
                                            accountGone,
                                        )
                                    is SyncReconciliationDecision.Write -> {
                                        if (leaseLost.get() || accountGone.get()) break
                                        val written =
                                            withLeaseTimeout(activeLease.get()) {
                                                repository.writeRemote(candidate, decision.value)
                                            } ?: SyncRemoteResult.Transient()
                                        handleRemoteResult(
                                            lease = activeLease.get(),
                                            candidate = candidate,
                                            result = written,
                                            retryAt = { value -> earliestRetryAt = earlierOf(earliestRetryAt, value) },
                                            leaseLost = leaseLost,
                                            accountGone = accountGone,
                                        )
                                    }
                                    SyncReconciliationDecision.Conflict ->
                                        handleCommitResult(
                                            repository.recordFailure(
                                                activeLease.get(),
                                                candidate,
                                                SyncIssueKind.CONFLICT,
                                                null,
                                                remote.entry,
                                            ),
                                            leaseLost,
                                            accountGone,
                                        )
                                    SyncReconciliationDecision.Rejected ->
                                        handleCommitResult(
                                            repository.recordFailure(
                                                activeLease.get(),
                                                candidate,
                                                SyncIssueKind.REJECTED,
                                                null,
                                                remote.entry,
                                            ),
                                            leaseLost,
                                            accountGone,
                                        )
                                }
                            }
                            else ->
                                handleRemoteResult(
                                    lease = activeLease.get(),
                                    candidate = candidate,
                                    result = remote,
                                    retryAt = { value -> earliestRetryAt = earlierOf(earliestRetryAt, value) },
                                    leaseLost = leaseLost,
                                    accountGone = accountGone,
                                )
                        }
                    }
                    if (!leaseLost.get() && !accountGone.get()) repository.purgeSettled(activeLease.get(), clock.now())
                } finally {
                    heartbeat.cancel()
                    heartbeat.join()
                }
            }
            when {
                accountGone.get() -> SyncRunResult.AccountUnavailable
                leaseLost.get() -> SyncRunResult.Complete
                else -> {
                    val wakeAt = repository.nextWakeAt(activeLease.get(), clock.now())
                    val retryAt = earlierOf(earliestRetryAt, wakeAt)
                    if (retryAt == null) SyncRunResult.Complete else SyncRunResult.Retry(retryAt)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            withContext(NonCancellable) {
                try {
                    withTimeout(5.seconds) { repository.releaseLease(activeLease.get()) }
                } catch (_: Exception) {
                    Unit
                }
            }
        }
    }

    private suspend fun handleRemoteResult(
        lease: SyncLease,
        candidate: SyncCandidate,
        result: SyncRemoteResult,
        retryAt: (Instant) -> Unit,
        leaseLost: AtomicBoolean,
        accountGone: AtomicBoolean,
    ) {
        val commitResult =
            when (result) {
                is SyncRemoteResult.Success ->
                    if (reconcile(SyncReconciliationInput(candidate, result.entry)) ==
                        SyncReconciliationDecision.Satisfied
                    ) {
                        repository.commitSnapshot(lease, listOf(result.entry), setOf(candidate.operation.id))
                    } else {
                        repository.recordFailure(lease, candidate, SyncIssueKind.CONFLICT, null, result.entry)
                    }
                is SyncRemoteResult.Transient -> {
                    val retry = retryDecision(candidate, result.retryAfter)
                    if (retry.exhausted) {
                        repository.recordFailure(lease, candidate, SyncIssueKind.RETRY_EXHAUSTED, null, null)
                    } else {
                        val retryAtValue = checkNotNull(retry.retryAt)
                        retryAt(retryAtValue)
                        repository.recordFailure(lease, candidate, null, retryAtValue, null)
                    }
                }
                SyncRemoteResult.Authentication ->
                    repository.recordFailure(lease, candidate, SyncIssueKind.AUTHENTICATION, null, null)
                is SyncRemoteResult.Rejected ->
                    repository.recordFailure(lease, candidate, SyncIssueKind.REJECTED, null, result.remote)
                is SyncRemoteResult.Conflict ->
                    repository.recordFailure(lease, candidate, SyncIssueKind.CONFLICT, null, result.remote)
                SyncRemoteResult.InvalidData ->
                    repository.recordFailure(lease, candidate, SyncIssueKind.INVALID_REMOTE_DATA, null, null)
            }
        handleCommitResult(commitResult, leaseLost, accountGone)
    }

    private fun handleCommitResult(
        result: SyncCommitResult,
        leaseLost: AtomicBoolean,
        accountGone: AtomicBoolean,
    ) {
        when (result) {
            SyncCommitResult.Applied -> Unit
            SyncCommitResult.LeaseLost -> leaseLost.set(true)
            SyncCommitResult.AccountGone -> accountGone.set(true)
        }
    }

    private suspend fun <T> withLeaseTimeout(
        lease: SyncLease,
        block: suspend () -> T,
    ): T? {
        val remaining = lease.expiresAt - clock.now()
        val timeout = minOf(PROVIDER_CALL_CAP, remaining - 1.seconds)
        if (timeout <= Duration.ZERO) return null
        return withTimeoutOrNull(timeout.inWholeMilliseconds.coerceAtLeast(1L)) { block() }
    }

    private fun retryDecision(
        candidate: SyncCandidate,
        providerRetryAfter: Duration?,
    ): RetryDecision {
        val attempt = candidate.operation.attemptCount
        val exponential = BASE_RETRY_DELAY * (1 shl (attempt - 1).coerceIn(0, 4))
        val requested = maxOf(exponential.coerceAtMost(MAX_RETRY_DELAY), providerRetryAfter ?: Duration.ZERO)
        return if (attempt >= MAX_ATTEMPTS || requested > MAX_RETRY_AFTER) {
            RetryDecision(exhausted = true, retryAt = null)
        } else {
            RetryDecision(exhausted = false, retryAt = clock.now() + requested)
        }
    }

    private fun earlierOf(
        first: Instant?,
        second: Instant?,
    ): Instant? = listOfNotNull(first, second).minOrNull()

    private data class RetryDecision(
        val exhausted: Boolean,
        val retryAt: Instant?,
    )

    private companion object {
        const val MAX_ATTEMPTS: Int = 5
        val LEASE_TTL: Duration = 120.seconds
        val HEARTBEAT: Duration = 30.seconds
        val PROVIDER_CALL_CAP: Duration = 60.seconds
        val BASE_RETRY_DELAY: Duration = 30.seconds
        val MAX_RETRY_DELAY: Duration = 15.minutes
        val MAX_RETRY_AFTER: Duration = 24.hours
    }
}
