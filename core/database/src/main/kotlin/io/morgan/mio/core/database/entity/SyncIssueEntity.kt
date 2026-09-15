package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredProgressUnit
import kotlin.time.Instant

@Entity(
    tableName = "sync_issue",
    foreignKeys = [
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
        Index(name = "index_sync_issue_account_id", value = ["account_id"]),
        Index(name = "index_sync_issue_collection_entry_id", value = ["collection_entry_id"]),
        Index(name = "index_sync_issue_account_id_operation_id", value = ["account_id", "operation_id"], unique = true),
    ],
)
public data class SyncIssueEntity(
    @PrimaryKey
    @ColumnInfo(name = "issue_id")
    public val issueId: String,
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "collection_entry_id")
    public val collectionEntryId: String,
    @ColumnInfo(name = "operation_group_id")
    public val operationGroupId: String,
    @ColumnInfo(name = "operation_id")
    public val operationId: String,
    @ColumnInfo(name = "kind")
    public val kind: String,
    @ColumnInfo(name = "base_progress")
    public val baseProgress: Int?,
    @ColumnInfo(name = "base_status")
    public val baseStatus: StoredCollectionStatus?,
    @ColumnInfo(name = "target_progress")
    public val targetProgress: Int?,
    @ColumnInfo(name = "target_status")
    public val targetStatus: StoredCollectionStatus?,
    @ColumnInfo(name = "progress_unit")
    public val progressUnit: StoredProgressUnit,
    @ColumnInfo(name = "local_progress")
    public val localProgress: Int,
    @ColumnInfo(name = "local_status")
    public val localStatus: StoredCollectionStatus,
    @ColumnInfo(name = "local_total")
    public val localTotal: Int?,
    @ColumnInfo(name = "local_updated_at_utc_ms")
    public val localUpdatedAtUtcMs: Instant,
    @ColumnInfo(name = "local_revision")
    public val localRevision: Long,
    @ColumnInfo(name = "remote_progress")
    public val remoteProgress: Int?,
    @ColumnInfo(name = "remote_status")
    public val remoteStatus: StoredCollectionStatus?,
    @ColumnInfo(name = "remote_total")
    public val remoteTotal: Int?,
    @ColumnInfo(name = "remote_updated_at_utc_ms")
    public val remoteUpdatedAtUtcMs: Instant?,
    @ColumnInfo(name = "detected_at_utc_ms")
    public val detectedAtUtcMs: Instant,
) {
    init {
        require(issueId.isNotBlank())
        require(accountId.isNotBlank())
        require(collectionEntryId.isNotBlank())
        require(operationGroupId.isNotBlank())
        require(operationId.isNotBlank())
        require(kind.isNotBlank())
        require(baseProgress == null || baseProgress >= 0)
        require(targetProgress == null || targetProgress >= 0)
        require(localProgress >= 0)
        require(localTotal == null || localTotal > 0)
        require(localTotal == null || localProgress <= localTotal)
        require(localRevision >= 0L)
        require(remoteProgress == null || remoteProgress >= 0)
        require(remoteTotal == null || remoteTotal > 0)
        require(remoteTotal == null || remoteProgress == null || remoteProgress <= remoteTotal)
        require((baseProgress == null) == (targetProgress == null))
        require((baseStatus == null) == (targetStatus == null))
        require((baseProgress == null) xor (baseStatus == null))
        require((remoteProgress == null) == (remoteStatus == null))
        require((remoteProgress == null) == (remoteUpdatedAtUtcMs == null))
    }
}
