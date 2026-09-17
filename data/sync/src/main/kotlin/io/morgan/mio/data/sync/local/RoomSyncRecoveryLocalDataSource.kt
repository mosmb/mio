package io.morgan.mio.data.sync.local

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.SyncAccountIssueEntity
import io.morgan.mio.core.database.entity.SyncIssueEntity
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.data.sync.mapper.toDomain
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.SyncAccountIssue
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncIssue
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
            .observeAccountIssue(accountId.value)
            .combine(database.syncDao().observeIssuesForAccount(accountId.value)) { accountIssue, entryIssues ->
                io.morgan.mio.domain.sync.SyncRecoveryState(
                    accountIssue = accountIssue?.toDomain(),
                    entryIssues = entryIssues.map(SyncIssueEntity::toDomain),
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
            database.syncDao().getIssue(accountId.value, "${operationId.value}:sync")?.toDomain()
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
            if (current.toDomain() != issue) return@recoveryTransaction false
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
