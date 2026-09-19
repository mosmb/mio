package io.morgan.mio.data.sync.local

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.SyncAccountIssueEntity
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.data.sync.mapper.toDomain
import io.morgan.mio.data.sync.mapper.toPendingOperationEntity
import io.morgan.mio.data.sync.mapper.toPendingOperationGroupEntity
import io.morgan.mio.data.sync.mapper.toRemoteSyncEntryState
import io.morgan.mio.data.sync.mapper.toSyncEntryState
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationKind
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.SyncAccountIssue
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncIssueKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

@Inject
public class RoomSyncRecoveryLocalDataSource(
    private val database: MioDatabase,
) {
    public fun observeState(accountId: AccountId): Flow<io.morgan.mio.domain.sync.SyncRecoveryState> =
        database
            .syncDao()
            .observeIssuesForAccount(accountId.value)
            .combine(database.syncDao().observeEntriesForAccount(accountId.value)) { entryIssues, entries ->
                val entriesById = entries.associateBy { entry -> entry.collectionEntryId }
                entryIssues.map { issue ->
                    val entry = entriesById[issue.collectionEntryId]
                    val current = entry?.toSyncEntryState()
                    val latestRemote = if (issue.remoteProgress != null) entry?.toRemoteSyncEntryState() else null
                    issue.toDomain(current = current, latestRemote = latestRemote)
                }
            }.combine(database.syncDao().observeAccountIssue(accountId.value)) { entryIssues, accountIssue ->
                io.morgan.mio.domain.sync.SyncRecoveryState(
                    accountIssue = accountIssue?.toDomain(),
                    entryIssues = entryIssues,
                )
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                if (throwable is SQLiteException) {
                    throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
                }
                throw throwable
            }

    public suspend fun readIssue(
        accountId: AccountId,
        operationId: OperationId,
    ): SyncIssue? =
        storage {
            if (database.syncDao().getSelectedAccount()?.accountId != accountId.value) return@storage null
            val issue = database.syncDao().getIssue(accountId.value, "${operationId.value}:sync") ?: return@storage null
            val entry = database.syncDao().getEntry(issue.collectionEntryId)
            issue.toDomain(
                current = entry?.takeIf { it.accountId == accountId.value }?.toSyncEntryState(),
                latestRemote =
                    entry
                        ?.takeIf { it.accountId == accountId.value && issue.remoteProgress != null }
                        ?.toRemoteSyncEntryState(),
            )
        }

    public suspend fun rearm(
        accountId: AccountId,
        issue: SyncIssue,
    ): Boolean =
        recoveryTransaction {
            if (issue.accountId != accountId) return@recoveryTransaction false
            if (database.syncDao().getSelectedAccount()?.accountId != accountId.value) {
                return@recoveryTransaction false
            }
            val current =
                database.syncDao().getIssue(accountId.value, "${issue.operationId.value}:sync")
                    ?: return@recoveryTransaction false
            if (current.toDomain().copy(local = issue.local, remote = issue.remote) != issue) {
                return@recoveryTransaction false
            }
            val operation =
                database.syncDao().getOperation(issue.operationId.value)
                    ?: return@recoveryTransaction false
            if (
                operation.accountId != accountId.value ||
                operation.collectionEntryId != issue.entryId.value ||
                operation.lifecycleState !in
                setOf(
                    StoredPendingOperationState.RETRYABLE_FAILURE,
                    StoredPendingOperationState.TERMINAL_FAILURE,
                )
            ) {
                return@recoveryTransaction false
            }
            check(database.syncDao().rearmOperation(accountId.value, operation.operationId) == 1)
            check(database.syncDao().deleteIssue(accountId.value, current.issueId) == 1)
            database.syncDao().requestDispatch(accountId.value)
            true
        }

    public suspend fun resolveConflict(
        accountId: AccountId,
        issue: SyncIssue,
        resolution: PendingOperationGroup,
    ): Boolean =
        recoveryTransaction {
            if (issue.accountId != accountId || resolution.accountId != accountId) return@recoveryTransaction false
            if (issue.kind != SyncIssueKind.CONFLICT || issue.remote == null) return@recoveryTransaction false
            if (database.syncDao().getSelectedAccount()?.accountId != accountId.value) {
                return@recoveryTransaction false
            }

            val currentEntity =
                database.syncDao().getIssue(accountId.value, "${issue.operationId.value}:sync")
                    ?: return@recoveryTransaction false
            val issueRemote = issue.remote ?: return@recoveryTransaction false
            val entry = database.syncDao().getEntry(issue.entryId.value) ?: return@recoveryTransaction false
            if (entry.accountId != accountId.value) return@recoveryTransaction false
            val currentIssue =
                currentEntity.toDomain(
                    current = entry.toSyncEntryState(),
                    latestRemote = entry.toRemoteSyncEntryState(),
                )
            if (currentIssue != issue) return@recoveryTransaction false
            if (entry.toSyncEntryState() != issue.local) {
                return@recoveryTransaction false
            }
            val isStatus = issue.base is OperationValue.Status && issue.target is OperationValue.Status
            if (!isStatus && (issue.base !is OperationValue.Progress || issue.target !is OperationValue.Progress)) {
                return@recoveryTransaction false
            }

            val original = database.syncDao().getOperation(issue.operationId.value) ?: return@recoveryTransaction false
            if (original.accountId != accountId.value ||
                original.collectionEntryId != issue.entryId.value ||
                original.operationGroupId != issue.groupId.value ||
                original.kind !in (
                    if (isStatus) {
                        setOf(StoredPendingOperationKind.SET_STATUS, StoredPendingOperationKind.SET_STATUS_CORRECTION)
                    } else {
                        setOf(StoredPendingOperationKind.SET_PROGRESS_CORRECTION)
                    }
                ) ||
                original.lifecycleState != StoredPendingOperationState.TERMINAL_FAILURE
            ) {
                return@recoveryTransaction false
            }

            val operation = resolution.operations.firstOrNull() ?: return@recoveryTransaction false
            val localValue =
                if (isStatus) {
                    OperationValue.Status(
                        issue.local.status,
                    )
                } else {
                    OperationValue.Progress(issue.local.progress)
                }
            val remoteValue =
                if (isStatus) {
                    OperationValue.Status(
                        issueRemote.status,
                    )
                } else {
                    OperationValue.Progress(issueRemote.progress)
                }
            val converged =
                localValue == remoteValue &&
                    operation.target == localValue &&
                    operation.base == (if (issue.base != localValue) issue.base else issue.target)
            val keepDevice =
                (operation.base == remoteValue && operation.target == localValue) ||
                    (converged && resolution.operations.size == 2)
            val useProvider =
                (converged && !keepDevice) || (operation.base == localValue && operation.target == remoteValue)
            if (!keepDevice && !useProvider) return@recoveryTransaction false
            val expectedKind =
                if (isStatus) {
                    PendingOperationKind.SET_STATUS_CORRECTION
                } else {
                    PendingOperationKind.SET_PROGRESS_CORRECTION
                }
            val suffix = if (isStatus) "status" else "progress"
            val followUpStatus = !isStatus && keepDevice && issue.local.status != issueRemote.status
            if (resolution.compensatesGroupId != null ||
                operation.id.value != "${resolution.id.value}:$suffix" ||
                operation.kind != expectedKind ||
                operation.positionInGroup != 0 ||
                operation.dependsOn.isNotEmpty() ||
                resolution.operations.size != (if (followUpStatus) 2 else 1) ||
                database.syncDao().getGroup(resolution.id.value) != null ||
                resolution.operations.any { pending ->
                    pending.groupId != resolution.id ||
                        pending.accountId != accountId ||
                        pending.collectionEntryId != issue.entryId ||
                        pending.state != io.morgan.mio.domain.sync.PendingOperationState.ENQUEUED ||
                        database.syncDao().getOperation(pending.id.value) != null
                }
            ) {
                return@recoveryTransaction false
            }
            if (followUpStatus) {
                val status = resolution.operations[1]
                if (status.id.value != "${resolution.id.value}:status" ||
                    status.kind != PendingOperationKind.SET_STATUS_CORRECTION ||
                    status.positionInGroup != 1 ||
                    status.base != OperationValue.Status(issueRemote.status) ||
                    status.target != OperationValue.Status(issue.local.status) ||
                    status.dependsOn != setOf(operation.id)
                ) {
                    return@recoveryTransaction false
                }
            }

            val entryOperations =
                database
                    .syncDao()
                    .getOrderedOperations(accountId.value)
                    .filter { pending -> pending.collectionEntryId == issue.entryId.value }
            val cancellableStates =
                setOf(
                    StoredPendingOperationState.ENQUEUED,
                    StoredPendingOperationState.RETRYABLE_FAILURE,
                    StoredPendingOperationState.BLOCKED,
                    StoredPendingOperationState.TERMINAL_FAILURE,
                )
            if (entryOperations.any { pending -> pending.lifecycleState == StoredPendingOperationState.IN_FLIGHT }) {
                return@recoveryTransaction false
            }
            val entryIssues = database.syncDao().getIssuesForEntry(accountId.value, issue.entryId.value)
            if (entryIssues.any { entity -> entity.issueId != currentEntity.issueId }) return@recoveryTransaction false

            entryOperations
                .filter { pending ->
                    pending.lifecycleState in cancellableStates &&
                        (
                            !isStatus ||
                                pending.kind in
                                setOf(
                                    StoredPendingOperationKind.SET_STATUS,
                                    StoredPendingOperationKind.SET_STATUS_CORRECTION,
                                )
                        )
                }.forEach { pending ->
                    check(database.syncDao().cancelOperation(accountId.value, pending.operationId) == 1)
                }
            check(database.syncDao().deleteIssue(accountId.value, currentEntity.issueId) == 1)

            val groupEntity = resolution.toPendingOperationGroupEntity()
            check(database.pendingOperationDao().insertGroup(groupEntity) > 0L)
            val operationEntities =
                resolution.operations.map { chosen ->
                    chosen.toPendingOperationEntity().let { pending ->
                        if (useProvider || (converged && chosen == operation)) {
                            // Accept the observed provider value locally; never write it back later.
                            pending.copy(
                                lifecycleState = StoredPendingOperationState.DELIVERED,
                                attemptCount = 1,
                                lastAttemptAtUtcMs = resolution.createdAt,
                            )
                        } else {
                            pending
                        }
                    }
                }
            database.pendingOperationDao().insertOperations(operationEntities)
            val dependencies =
                resolution.operations.flatMap { pending ->
                    pending.dependsOn.map { dependency ->
                        io.morgan.mio.core.database.entity.PendingOperationDependencyEntity(
                            pending.id.value,
                            dependency.value,
                        )
                    }
                }
            database.pendingOperationDao().insertDependencies(dependencies)
            val remaining =
                if (isStatus) {
                    database
                        .syncDao()
                        .getOrderedOperations(accountId.value)
                        .filter { pending ->
                            pending.collectionEntryId == issue.entryId.value &&
                                pending.operationGroupId != resolution.id.value
                        }.map { pending -> pending.toDomain(emptySet()) }
                } else {
                    emptyList()
                }
            val effective = ProjectSyncEntryUseCase()(issueRemote, issue.local, remaining + resolution.operations)
            check(
                database.syncDao().updateEntrySnapshot(
                    entryId = entry.collectionEntryId,
                    accountId = accountId.value,
                    mediaId = entry.mediaId,
                    remoteStatus =
                        io.morgan.mio.core.database.model.StoredCollectionStatus
                            .valueOf(issueRemote.status.name),
                    remoteProgress = issueRemote.progress.value,
                    remoteTotal = issueRemote.total?.value,
                    remoteUpdatedAtUtcMs = issueRemote.updatedAt,
                    effectiveStatus =
                        io.morgan.mio.core.database.model.StoredCollectionStatus.valueOf(
                            effective.status.name,
                        ),
                    effectiveProgress = effective.progress.value,
                    effectiveTotal = effective.total?.value,
                    effectiveUpdatedAtUtcMs = resolution.createdAt,
                    localProgressRevision = effective.revision,
                ) == 1,
            )
            database.syncDao().requestDispatch(accountId.value)
            true
        }

    public suspend fun recoverAuthentication(
        accountId: AccountId,
        issueIds: Set<String>,
    ): Boolean =
        recoveryTransaction {
            if (database.syncDao().getSelectedAccount()?.accountId != accountId.value) {
                return@recoveryTransaction false
            }
            var changed = false
            val accountIssue = database.syncDao().getAccountIssue(accountId.value)
            if (
                accountIssue != null &&
                accountIssue.issueId in issueIds &&
                accountIssue.kind == SyncAccountIssueKind.AUTHENTICATION.serializedValue()
            ) {
                check(database.syncDao().deleteAccountIssue(accountId.value, accountIssue.issueId) == 1)
                changed = true
            }
            if (issueIds.isNotEmpty()) {
                database.syncDao().getIssuesByIds(accountId.value, issueIds).forEach { issue ->
                    if (issue.kind != "authentication") return@forEach
                    val operation =
                        database.syncDao().getOperation(issue.operationId)
                            ?: return@forEach
                    if (
                        operation.accountId != accountId.value ||
                        operation.lifecycleState !in
                        setOf(
                            StoredPendingOperationState.RETRYABLE_FAILURE,
                            StoredPendingOperationState.TERMINAL_FAILURE,
                        )
                    ) {
                        return@forEach
                    }
                    check(database.syncDao().rearmOperation(accountId.value, operation.operationId) == 1)
                    check(database.syncDao().deleteIssue(accountId.value, issue.issueId) == 1)
                    changed = true
                }
            }
            if (changed) database.syncDao().requestDispatch(accountId.value)
            changed
        }

    private suspend fun <T> recoveryTransaction(block: suspend () -> T): T = storage { database.inTransaction(block) }

    private suspend fun <T> storage(block: suspend () -> T): T =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }
}

private fun SyncAccountIssueEntity.toDomain(): SyncAccountIssue =
    SyncAccountIssue(
        id = issueId,
        accountId = AccountId(accountId),
        kind = kind.toSyncAccountIssueKind(),
        detectedAt = detectedAtUtcMs,
    )

private fun SyncAccountIssueKind.serializedValue(): String =
    when (this) {
        SyncAccountIssueKind.AUTHENTICATION -> "authentication"
        SyncAccountIssueKind.INVALID_REMOTE_DATA -> "invalid_remote_data"
    }

private fun String.toSyncAccountIssueKind(): SyncAccountIssueKind =
    when (this) {
        "authentication" -> SyncAccountIssueKind.AUTHENTICATION
        "invalid_remote_data" -> SyncAccountIssueKind.INVALID_REMOTE_DATA
        else -> throw SyncInfrastructureException(SyncInfrastructureException.Kind.CONFIGURATION)
    }
