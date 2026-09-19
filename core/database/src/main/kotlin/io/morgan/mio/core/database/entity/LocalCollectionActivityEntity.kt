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
    tableName = "local_collection_activity",
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
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            name = "index_local_collection_activity_operation_group_id",
            value = ["operation_group_id"],
            unique = true,
        ),
        Index(
            name = "index_local_collection_activity_account_id_activity_order",
            value = ["account_id", "activity_order"],
        ),
        Index(name = "index_local_collection_activity_collection_entry_id", value = ["collection_entry_id"]),
        Index(name = "index_local_collection_activity_media_id", value = ["media_id"]),
    ],
)
public data class LocalCollectionActivityEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "activity_order")
    public val activityOrder: Long = 0L,
    @ColumnInfo(name = "operation_group_id")
    public val operationGroupId: String,
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "collection_entry_id")
    public val collectionEntryId: String,
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "progress_unit")
    public val progressUnit: StoredProgressUnit,
    @ColumnInfo(name = "action_kind")
    public val actionKind: String,
    @ColumnInfo(name = "before_present")
    public val beforePresent: Boolean,
    @ColumnInfo(name = "before_progress")
    public val beforeProgress: Int?,
    @ColumnInfo(name = "before_status")
    public val beforeStatus: StoredCollectionStatus?,
    @ColumnInfo(name = "before_total")
    public val beforeTotal: Int?,
    @ColumnInfo(name = "before_updated_at_utc_ms")
    public val beforeUpdatedAtUtcMs: Instant?,
    @ColumnInfo(name = "before_revision")
    public val beforeRevision: Long?,
    @ColumnInfo(name = "after_progress")
    public val afterProgress: Int,
    @ColumnInfo(name = "after_status")
    public val afterStatus: StoredCollectionStatus,
    @ColumnInfo(name = "after_total")
    public val afterTotal: Int?,
    @ColumnInfo(name = "after_updated_at_utc_ms")
    public val afterUpdatedAtUtcMs: Instant,
    @ColumnInfo(name = "after_revision")
    public val afterRevision: Long,
    @ColumnInfo(name = "created_at_utc_ms")
    public val createdAtUtcMs: Instant,
    @ColumnInfo(name = "undo_until_utc_ms")
    public val undoUntilUtcMs: Instant,
    @ColumnInfo(name = "undone_at_utc_ms")
    public val undoneAtUtcMs: Instant?,
) {
    init {
        require(activityOrder >= 0L)
        require(operationGroupId.isNotBlank())
        require(accountId.isNotBlank())
        require(collectionEntryId.isNotBlank())
        require(mediaId.isNotBlank())
        require(actionKind == "add" || actionKind == "status")
        require(beforeProgress == null || beforeProgress >= 0)
        require(beforeTotal == null || beforeTotal > 0)
        require(beforeTotal == null || beforeProgress == null || beforeProgress <= beforeTotal)
        require(beforeRevision == null || beforeRevision >= 0L)
        require(afterProgress >= 0)
        require(afterTotal == null || afterTotal > 0)
        require(afterTotal == null || afterProgress <= afterTotal)
        require(afterRevision >= 0L)
        require(undoUntilUtcMs >= createdAtUtcMs)
        if (beforePresent) {
            require(beforeProgress != null)
            require(beforeStatus != null)
            require(beforeUpdatedAtUtcMs != null)
            require(beforeRevision != null)
        } else {
            require(beforeProgress == null)
            require(beforeStatus == null)
            require(beforeUpdatedAtUtcMs == null)
            require(beforeRevision == null)
        }
    }
}
