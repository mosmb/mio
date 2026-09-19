package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.entity.SyncAccountIssueEntity
import io.morgan.mio.core.database.entity.SyncIssueEntity
import io.morgan.mio.core.database.entity.SyncLeaseEntity
import io.morgan.mio.core.database.entity.SyncScheduleEntity
import io.morgan.mio.core.database.model.StoredPendingOperationState
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
public abstract class SyncDao {
    @Query(
        """
        INSERT INTO sync_lease(account_id, owner_token, expires_at_utc_ms)
        SELECT :accountId, :ownerToken, :expiresAtUtcMs
        WHERE EXISTS (SELECT 1 FROM selected_account WHERE account_id = :accountId)
        ON CONFLICT(account_id) DO UPDATE SET
            owner_token = excluded.owner_token,
            expires_at_utc_ms = excluded.expires_at_utc_ms
        WHERE sync_lease.expires_at_utc_ms <= :nowUtcMs
           OR sync_lease.owner_token = :ownerToken
        """,
    )
    public abstract suspend fun tryAcquireLease(
        accountId: String,
        ownerToken: String,
        nowUtcMs: Instant,
        expiresAtUtcMs: Instant,
    ): Long

    @Query(
        "UPDATE sync_lease SET owner_token = :ownerToken, expires_at_utc_ms = :expiresAtUtcMs " +
            "WHERE account_id = :accountId AND owner_token = :ownerToken AND expires_at_utc_ms > :nowUtcMs",
    )
    public abstract suspend fun renewLease(
        accountId: String,
        ownerToken: String,
        nowUtcMs: Instant,
        expiresAtUtcMs: Instant,
    ): Int

    @Query(
        "DELETE FROM sync_lease WHERE account_id = :accountId AND owner_token = :ownerToken " +
            "AND expires_at_utc_ms > :nowUtcMs",
    )
    public abstract suspend fun releaseLease(
        accountId: String,
        ownerToken: String,
        nowUtcMs: Instant,
    ): Int

    @Query("SELECT * FROM sync_lease WHERE account_id = :accountId")
    public abstract suspend fun getLease(accountId: String): SyncLeaseEntity?

    @Query(
        """
        SELECT pending_operation.* FROM pending_operation
        INNER JOIN pending_operation_group
          ON pending_operation.operation_group_id = pending_operation_group.operation_group_id
        WHERE pending_operation.account_id = :accountId
          AND pending_operation_group.account_id = :accountId
          AND (
              pending_operation.attempt_count < 5
              OR (pending_operation.lifecycle_state = 'in_flight'
                  AND pending_operation.last_attempt_at_utc_ms <= :abandonedBeforeUtcMs)
          )
          AND NOT EXISTS (
              SELECT 1 FROM sync_issue account_issue
              WHERE account_issue.account_id = :accountId
                AND account_issue.kind = 'authentication'
          )
          AND NOT EXISTS (
              SELECT 1 FROM sync_account_issue account_issue
              WHERE account_issue.account_id = :accountId
                AND account_issue.kind = 'authentication'
          )
          AND (
             pending_operation.lifecycle_state = 'enqueued'
             OR (pending_operation.lifecycle_state = 'retryable_failure'
                 AND (pending_operation.retry_not_before_utc_ms IS NULL
                    OR pending_operation.retry_not_before_utc_ms <= :nowUtcMs))
             OR (pending_operation.lifecycle_state = 'in_flight'
                 AND pending_operation.last_attempt_at_utc_ms <= :abandonedBeforeUtcMs)
          )
          AND NOT EXISTS (
              SELECT 1 FROM pending_operation_dependency dependency
              INNER JOIN pending_operation prerequisite
                ON prerequisite.operation_id = dependency.depends_on_operation_id
              WHERE dependency.operation_id = pending_operation.operation_id
                AND prerequisite.lifecycle_state NOT IN ('delivered', 'cancelled', 'terminal_failure')
          )
          AND NOT EXISTS (
              SELECT 1 FROM pending_operation earlier
              INNER JOIN pending_operation_group earlier_group
                ON earlier.operation_group_id = earlier_group.operation_group_id
              WHERE earlier.account_id = pending_operation.account_id
                AND earlier.collection_entry_id = pending_operation.collection_entry_id
                AND earlier.lifecycle_state NOT IN ('delivered', 'cancelled')
                AND NOT (
                    earlier.lifecycle_state = 'terminal_failure'
                    AND COALESCE(pending_operation_group.compensates_group_id = earlier.operation_group_id, 0)
                )
                AND (
                    earlier_group.group_order < pending_operation_group.group_order
                    OR (earlier_group.group_order = pending_operation_group.group_order
                        AND earlier.position_in_group < pending_operation.position_in_group)
                )
          )
        ORDER BY pending_operation_group.group_order, pending_operation.position_in_group
        LIMIT 1
        """,
    )
    public abstract suspend fun getNextCandidate(
        accountId: String,
        nowUtcMs: Instant,
        abandonedBeforeUtcMs: Instant,
    ): PendingOperationEntity?

    @Query(
        "UPDATE pending_operation SET lifecycle_state = 'in_flight', " +
            "attempt_count = CASE WHEN attempt_count < 5 THEN attempt_count + 1 ELSE attempt_count END, " +
            "last_attempt_at_utc_ms = :nowUtcMs, retry_not_before_utc_ms = NULL " +
            "WHERE operation_id = :operationId AND account_id = :accountId " +
            "AND (attempt_count < 5 OR lifecycle_state = 'in_flight') " +
            "AND ((lifecycle_state IN ('enqueued', 'retryable_failure') " +
            "AND (retry_not_before_utc_ms IS NULL OR retry_not_before_utc_ms <= :nowUtcMs)) " +
            "OR (lifecycle_state = 'in_flight' AND last_attempt_at_utc_ms <= :abandonedBeforeUtcMs))",
    )
    public abstract suspend fun claimOperation(
        accountId: String,
        operationId: String,
        nowUtcMs: Instant,
        abandonedBeforeUtcMs: Instant,
    ): Int

    @Query(
        "UPDATE pending_operation SET lifecycle_state = :state, retry_not_before_utc_ms = :retryNotBeforeUtcMs " +
            "WHERE operation_id = :operationId AND account_id = :accountId",
    )
    public abstract suspend fun setOperationState(
        accountId: String,
        operationId: String,
        state: StoredPendingOperationState,
        retryNotBeforeUtcMs: Instant?,
    ): Int

    @Query(
        "SELECT pending_operation.* FROM pending_operation " +
            "INNER JOIN pending_operation_group ON pending_operation.operation_group_id = " +
            "pending_operation_group.operation_group_id " +
            "WHERE pending_operation.account_id = :accountId " +
            "ORDER BY pending_operation_group.group_order, pending_operation.position_in_group",
    )
    public abstract suspend fun getOrderedOperations(accountId: String): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operation WHERE operation_id = :operationId")
    public abstract suspend fun getOperation(operationId: String): PendingOperationEntity?

    @Query("SELECT * FROM pending_operation_group WHERE operation_group_id = :groupId")
    public abstract suspend fun getGroup(
        groupId: String,
    ): io.morgan.mio.core.database.entity.PendingOperationGroupEntity?

    @Query("SELECT * FROM pending_operation_group WHERE account_id = :accountId ORDER BY group_order")
    public abstract suspend fun getGroups(
        accountId: String,
    ): List<io.morgan.mio.core.database.entity.PendingOperationGroupEntity>

    @Query(
        "SELECT * FROM pending_operation_group " +
            "WHERE account_id = :accountId AND compensates_group_id = :groupId",
    )
    public abstract suspend fun getCompensatingGroups(
        accountId: String,
        groupId: String,
    ): List<io.morgan.mio.core.database.entity.PendingOperationGroupEntity>

    @Query("SELECT * FROM pending_operation WHERE operation_group_id = :groupId ORDER BY position_in_group")
    public abstract suspend fun getOperationsForGroup(groupId: String): List<PendingOperationEntity>

    @Query(
        "SELECT * FROM pending_operation_dependency WHERE operation_id = :operationId " +
            "ORDER BY depends_on_operation_id",
    )
    public abstract suspend fun getDependenciesForOperation(
        operationId: String,
    ): List<io.morgan.mio.core.database.entity.PendingOperationDependencyEntity>

    @Query("SELECT * FROM collection_entry WHERE collection_entry_id = :entryId")
    public abstract suspend fun getEntry(entryId: String): CollectionEntryEntity?

    @Query("SELECT * FROM collection_entry WHERE account_id = :accountId ORDER BY collection_entry_id")
    public abstract fun observeEntriesForAccount(accountId: String): Flow<List<CollectionEntryEntity>>

    @Query("SELECT * FROM selected_account WHERE selection_key = 'selected'")
    public abstract suspend fun getSelectedAccount(): SelectedAccountEntity?

    @Query("SELECT * FROM account_provider_id WHERE account_id = :accountId")
    public abstract suspend fun getAccountProviderId(accountId: String): AccountProviderIdEntity?

    @Query(
        "SELECT * FROM collection_entry_provider_id WHERE provider = :provider AND collection_entry_id = :entryId",
    )
    public abstract suspend fun getEntryProviderId(
        entryId: String,
        provider: io.morgan.mio.core.database.model.StoredConnectedProvider,
    ): CollectionEntryProviderIdEntity?

    @Query(
        "SELECT * FROM collection_entry_provider_id " +
            "WHERE provider = :provider AND remote_collection_entry_id = :remoteEntryId LIMIT 1",
    )
    public abstract suspend fun findEntryProviderIdByRemote(
        provider: io.morgan.mio.core.database.model.StoredConnectedProvider,
        remoteEntryId: String,
    ): CollectionEntryProviderIdEntity?

    @Query("SELECT * FROM collection_entry_provider_id WHERE collection_entry_id = :entryId LIMIT 1")
    public abstract suspend fun getEntryProviderId(entryId: String): CollectionEntryProviderIdEntity?

    @Query(
        "SELECT * FROM media_provider_id WHERE media_id = :mediaId AND provider = :provider LIMIT 1",
    )
    public abstract suspend fun getMediaProviderId(
        mediaId: String,
        provider: io.morgan.mio.core.database.model.StoredConnectedProvider,
    ): MediaProviderIdEntity?

    @Query(
        "SELECT * FROM media_provider_id " +
            "WHERE provider = :provider AND remote_media_id = :remoteMediaId LIMIT 1",
    )
    public abstract suspend fun findMediaProviderIdByRemote(
        provider: io.morgan.mio.core.database.model.StoredConnectedProvider,
        remoteMediaId: String,
    ): MediaProviderIdEntity?

    @Query("SELECT * FROM sync_schedule ORDER BY account_id")
    public abstract fun observeDispatchRequests(): Flow<List<SyncScheduleEntity>>

    @Query("SELECT * FROM sync_schedule WHERE account_id = :accountId")
    public abstract suspend fun getDispatchRequest(accountId: String): SyncScheduleEntity?

    @Query(
        "INSERT INTO sync_schedule(account_id, requested_generation, dispatched_generation) " +
            "VALUES (:accountId, 1, 0) " +
            "ON CONFLICT(account_id) DO UPDATE SET requested_generation = requested_generation + 1 " +
            "WHERE requested_generation < 9223372036854775807",
    )
    public abstract suspend fun requestDispatch(accountId: String): Long

    @Query(
        "UPDATE pending_operation SET lifecycle_state = 'cancelled', retry_not_before_utc_ms = NULL " +
            "WHERE operation_id = :operationId AND account_id = :accountId " +
            "AND lifecycle_state IN ('enqueued', 'retryable_failure', 'blocked', 'terminal_failure')",
    )
    public abstract suspend fun cancelOperation(
        accountId: String,
        operationId: String,
    ): Int

    @Query(
        "UPDATE sync_schedule SET dispatched_generation = :generation " +
            "WHERE account_id = :accountId AND requested_generation = :generation " +
            "AND dispatched_generation < :generation",
    )
    public abstract suspend fun acknowledgeDispatch(
        accountId: String,
        generation: Long,
    ): Int

    @Query("SELECT * FROM sync_schedule WHERE account_id = :accountId")
    public abstract suspend fun getSchedule(accountId: String): SyncScheduleEntity?

    @Query("SELECT * FROM collection_entry WHERE account_id = :accountId ORDER BY collection_entry_id")
    public abstract suspend fun getEntries(accountId: String): List<CollectionEntryEntity>

    @Query(
        "SELECT * FROM local_progress_activity " +
            "WHERE account_id = :accountId AND operation_group_id = :groupId",
    )
    public abstract suspend fun getActivity(
        accountId: String,
        groupId: String,
    ): LocalProgressActivityEntity?

    @Query(
        "SELECT * FROM local_collection_activity " +
            "WHERE account_id = :accountId AND operation_group_id = :groupId",
    )
    public abstract suspend fun getCollectionActivity(
        accountId: String,
        groupId: String,
    ): io.morgan.mio.core.database.entity.LocalCollectionActivityEntity?

    @Query(
        "SELECT MIN(retry_not_before_utc_ms) FROM pending_operation " +
            "WHERE account_id = :accountId AND lifecycle_state = 'retryable_failure'",
    )
    public abstract suspend fun getNextRetryAt(accountId: String): Instant?

    @Query(
        "SELECT MIN(undo_until_utc_ms) FROM (" +
            "SELECT undo_until_utc_ms FROM local_progress_activity " +
            "WHERE account_id = :accountId AND undone_at_utc_ms IS NULL " +
            "AND undo_until_utc_ms > :now AND EXISTS (SELECT 1 FROM pending_operation_group g " +
            "WHERE g.operation_group_id = local_progress_activity.operation_group_id) " +
            "UNION ALL " +
            "SELECT undo_until_utc_ms FROM local_collection_activity " +
            "WHERE account_id = :accountId AND undone_at_utc_ms IS NULL " +
            "AND undo_until_utc_ms > :now AND EXISTS (SELECT 1 FROM pending_operation_group g " +
            "WHERE g.operation_group_id = local_collection_activity.operation_group_id))",
    )
    public abstract suspend fun getNextUndoDeadline(
        accountId: String,
        now: Instant,
    ): Instant?

    @Query(
        "SELECT MIN(last_attempt_at_utc_ms) FROM pending_operation WHERE account_id = :accountId AND lifecycle_state = 'in_flight'",
    )
    public abstract suspend fun getOldestInFlightAttempt(accountId: String): Instant?

    @Query("SELECT * FROM sync_issue WHERE account_id = :accountId ORDER BY detected_at_utc_ms, issue_id")
    public abstract fun observeIssuesForAccount(accountId: String): Flow<List<SyncIssueEntity>>

    @Query("SELECT * FROM sync_issue WHERE account_id = :accountId ORDER BY detected_at_utc_ms, issue_id")
    public abstract suspend fun getIssues(accountId: String): List<SyncIssueEntity>

    @Query(
        "SELECT * FROM sync_issue " +
            "WHERE account_id = :accountId AND operation_group_id = :groupId",
    )
    public abstract suspend fun getIssuesForGroup(
        accountId: String,
        groupId: String,
    ): List<SyncIssueEntity>

    @Query(
        "SELECT * FROM sync_issue WHERE account_id = :accountId AND collection_entry_id = :entryId",
    )
    public abstract suspend fun getIssuesForEntry(
        accountId: String,
        entryId: String,
    ): List<SyncIssueEntity>

    @Query("SELECT * FROM sync_issue WHERE account_id = :accountId AND issue_id = :issueId")
    public abstract suspend fun getIssue(
        accountId: String,
        issueId: String,
    ): SyncIssueEntity?

    @Query("SELECT * FROM sync_issue WHERE account_id = :accountId AND issue_id IN (:issueIds)")
    public abstract suspend fun getIssuesByIds(
        accountId: String,
        issueIds: Set<String>,
    ): List<SyncIssueEntity>

    @Upsert
    public abstract suspend fun upsertIssue(issue: SyncIssueEntity)

    @Query("DELETE FROM sync_issue WHERE account_id = :accountId AND issue_id = :issueId")
    public abstract suspend fun deleteIssue(
        accountId: String,
        issueId: String,
    ): Int

    @Query("SELECT * FROM sync_account_issue WHERE account_id = :accountId")
    public abstract fun observeAccountIssue(accountId: String): Flow<SyncAccountIssueEntity?>

    @Query("SELECT * FROM sync_account_issue WHERE account_id = :accountId")
    public abstract suspend fun getAccountIssue(accountId: String): SyncAccountIssueEntity?

    @Upsert
    public abstract suspend fun upsertAccountIssue(issue: SyncAccountIssueEntity)

    @Query("DELETE FROM sync_account_issue WHERE account_id = :accountId AND issue_id = :issueId")
    public abstract suspend fun deleteAccountIssue(
        accountId: String,
        issueId: String,
    ): Int

    @Query(
        "DELETE FROM sync_account_issue " +
            "WHERE account_id = :accountId AND kind = :kind",
    )
    public abstract suspend fun deleteAccountIssueIfKind(
        accountId: String,
        kind: String,
    ): Int

    @Query(
        "UPDATE pending_operation SET lifecycle_state = 'enqueued', attempt_count = 0, " +
            "last_attempt_at_utc_ms = NULL, retry_not_before_utc_ms = NULL " +
            "WHERE operation_id = :operationId AND account_id = :accountId " +
            "AND lifecycle_state IN ('retryable_failure', 'terminal_failure', 'blocked')",
    )
    public abstract suspend fun rearmOperation(
        accountId: String,
        operationId: String,
    ): Int

    @Query(
        "UPDATE collection_entry SET remote_status = :remoteStatus, remote_progress = :remoteProgress, " +
            "remote_total = :remoteTotal, remote_updated_at_utc_ms = :remoteUpdatedAtUtcMs, " +
            "remote_present = 1, effective_present = :effectivePresent, " +
            "effective_status = :effectiveStatus, effective_progress = :effectiveProgress, " +
            "effective_total = :effectiveTotal, effective_updated_at_utc_ms = :effectiveUpdatedAtUtcMs, " +
            "local_progress_revision = :localProgressRevision " +
            "WHERE collection_entry_id = :entryId AND account_id = :accountId AND media_id = :mediaId",
    )
    public abstract suspend fun updateEntrySnapshot(
        entryId: String,
        accountId: String,
        mediaId: String,
        remoteStatus: io.morgan.mio.core.database.model.StoredCollectionStatus,
        remoteProgress: Int,
        remoteTotal: Int?,
        remoteUpdatedAtUtcMs: Instant,
        effectiveStatus: io.morgan.mio.core.database.model.StoredCollectionStatus,
        effectiveProgress: Int,
        effectiveTotal: Int?,
        effectiveUpdatedAtUtcMs: Instant,
        localProgressRevision: Long,
        effectivePresent: Boolean = true,
    ): Int

    @Query("SELECT * FROM sync_lease WHERE account_id = :accountId")
    public abstract fun observeLease(accountId: String): Flow<SyncLeaseEntity?>

    @Query(
        "DELETE FROM pending_operation_group WHERE account_id = :accountId AND operation_group_id = :groupId " +
            "AND NOT EXISTS (SELECT 1 FROM pending_operation WHERE operation_group_id = :groupId " +
            "AND lifecycle_state NOT IN ('delivered', 'cancelled', 'terminal_failure'))",
    )
    public abstract suspend fun deleteSettledGroup(
        accountId: String,
        groupId: String,
    ): Int
}
