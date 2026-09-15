package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import kotlin.time.Instant

@Entity(
    tableName = "pending_operation_group",
    foreignKeys = [
        ForeignKey(
            entity = SelectedAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            name = "index_pending_operation_group_operation_group_id",
            value = ["operation_group_id"],
            unique = true,
        ),
        Index(name = "index_pending_operation_group_account_id", value = ["account_id"]),
        Index(name = "index_pending_operation_group_compensates_group_id", value = ["compensates_group_id"]),
    ],
)
public data class PendingOperationGroupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "group_order")
    public val groupOrder: Long = 0L,
    @ColumnInfo(name = "operation_group_id")
    public val operationGroupId: String,
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "created_at_utc_ms")
    public val createdAtUtcMs: Instant,
    @ColumnInfo(name = "compensates_group_id")
    public val compensatesGroupId: String?,
) {
    init {
        require(groupOrder >= 0)
        require(operationGroupId.isNotBlank())
        require(accountId.isNotBlank())
        require(compensatesGroupId == null || compensatesGroupId.isNotBlank())
        require(compensatesGroupId != operationGroupId)
    }
}

@Entity(
    tableName = "pending_operation",
    foreignKeys = [
        ForeignKey(
            entity = PendingOperationGroupEntity::class,
            parentColumns = ["operation_group_id"],
            childColumns = ["operation_group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SelectedAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CollectionEntryEntity::class,
            parentColumns = ["collection_entry_id"],
            childColumns = ["collection_entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_pending_operation_operation_group_id", value = ["operation_group_id"]),
        Index(name = "index_pending_operation_account_id", value = ["account_id"]),
        Index(name = "index_pending_operation_collection_entry_id", value = ["collection_entry_id"]),
        Index(name = "index_pending_operation_lifecycle_state", value = ["lifecycle_state"]),
        Index(
            name = "index_pending_operation_operation_group_id_position_in_group",
            value = ["operation_group_id", "position_in_group"],
            unique = true,
        ),
    ],
)
public data class PendingOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    public val operationId: String,
    @ColumnInfo(name = "operation_group_id")
    public val operationGroupId: String,
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "collection_entry_id")
    public val collectionEntryId: String,
    @ColumnInfo(name = "position_in_group")
    public val positionInGroup: Int,
    @ColumnInfo(name = "kind")
    public val kind: StoredPendingOperationKind,
    @ColumnInfo(name = "base_progress")
    public val baseProgress: Int?,
    @ColumnInfo(name = "target_progress")
    public val targetProgress: Int?,
    @ColumnInfo(name = "base_status")
    public val baseStatus: StoredCollectionStatus?,
    @ColumnInfo(name = "target_status")
    public val targetStatus: StoredCollectionStatus?,
    @ColumnInfo(name = "lifecycle_state")
    public val lifecycleState: StoredPendingOperationState,
    @ColumnInfo(name = "attempt_count")
    public val attemptCount: Int,
    @ColumnInfo(name = "last_attempt_at_utc_ms")
    public val lastAttemptAtUtcMs: Instant?,
    @ColumnInfo(name = "retry_not_before_utc_ms")
    public val retryNotBeforeUtcMs: Instant?,
) {
    init {
        require(operationId.isNotBlank())
        require(operationGroupId.isNotBlank())
        require(accountId.isNotBlank())
        require(collectionEntryId.isNotBlank())
        require(positionInGroup >= 0)
        require(attemptCount >= 0)
        require(baseProgress == null || baseProgress >= 0)
        require(targetProgress == null || targetProgress >= 0)

        when (kind) {
            StoredPendingOperationKind.SET_PROGRESS_FORWARD -> {
                require(baseProgress != null && targetProgress != null)
                require(baseStatus == null && targetStatus == null)
                require(targetProgress > baseProgress)
            }

            StoredPendingOperationKind.SET_PROGRESS_CORRECTION -> {
                require(baseProgress != null && targetProgress != null)
                require(baseStatus == null && targetStatus == null)
                require(targetProgress != baseProgress)
            }

            StoredPendingOperationKind.SET_STATUS -> {
                require(baseProgress == null && targetProgress == null)
                require(baseStatus != null && targetStatus != null)
                require(targetStatus != baseStatus)
            }
        }

        when (lifecycleState) {
            StoredPendingOperationState.ENQUEUED -> {
                require(attemptCount == 0)
                require(lastAttemptAtUtcMs == null)
                require(retryNotBeforeUtcMs == null)
            }

            StoredPendingOperationState.IN_FLIGHT,
            StoredPendingOperationState.DELIVERED,
            -> {
                require(attemptCount > 0)
                require(lastAttemptAtUtcMs != null)
            }

            StoredPendingOperationState.RETRYABLE_FAILURE -> {
                require(attemptCount > 0)
                require(lastAttemptAtUtcMs != null)
                require(retryNotBeforeUtcMs != null)
            }

            StoredPendingOperationState.BLOCKED,
            StoredPendingOperationState.TERMINAL_FAILURE,
            -> {
                require(attemptCount > 0)
                require(lastAttemptAtUtcMs != null)
            }

            StoredPendingOperationState.CANCELLED -> {
                require(retryNotBeforeUtcMs == null)
                if (attemptCount == 0) {
                    require(lastAttemptAtUtcMs == null)
                } else {
                    require(lastAttemptAtUtcMs != null)
                }
            }
        }
    }
}

@Entity(
    tableName = "pending_operation_dependency",
    primaryKeys = ["operation_id", "depends_on_operation_id"],
    foreignKeys = [
        ForeignKey(
            entity = PendingOperationEntity::class,
            parentColumns = ["operation_id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PendingOperationEntity::class,
            parentColumns = ["operation_id"],
            childColumns = ["depends_on_operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            name = "index_pending_operation_dependency_depends_on_operation_id",
            value = ["depends_on_operation_id"],
        ),
    ],
)
public data class PendingOperationDependencyEntity(
    @ColumnInfo(name = "operation_id")
    public val operationId: String,
    @ColumnInfo(name = "depends_on_operation_id")
    public val dependsOnOperationId: String,
) {
    init {
        require(operationId.isNotBlank())
        require(dependsOnOperationId.isNotBlank())
        require(operationId != dependsOnOperationId)
    }
}
