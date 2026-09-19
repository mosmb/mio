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
    tableName = "collection_entry",
    foreignKeys = [
        ForeignKey(
            entity = SelectedAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
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
        Index(name = "index_collection_entry_account_id", value = ["account_id"]),
        Index(name = "index_collection_entry_media_id", value = ["media_id"]),
        Index(
            name = "index_collection_entry_account_id_media_id",
            value = ["account_id", "media_id"],
            unique = true,
        ),
    ],
)
public data class CollectionEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "collection_entry_id")
    public val collectionEntryId: String,
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "progress_unit")
    public val progressUnit: StoredProgressUnit,
    @ColumnInfo(name = "remote_status")
    public val remoteStatus: StoredCollectionStatus,
    @ColumnInfo(name = "remote_progress")
    public val remoteProgress: Int,
    @ColumnInfo(name = "remote_total")
    public val remoteTotal: Int?,
    @ColumnInfo(name = "remote_updated_at_utc_ms")
    public val remoteUpdatedAtUtcMs: Instant,
    @ColumnInfo(name = "remote_present", defaultValue = "1")
    public val remotePresent: Boolean = true,
    @ColumnInfo(name = "effective_status")
    public val effectiveStatus: StoredCollectionStatus,
    @ColumnInfo(name = "effective_progress")
    public val effectiveProgress: Int,
    @ColumnInfo(name = "effective_total")
    public val effectiveTotal: Int?,
    @ColumnInfo(name = "effective_updated_at_utc_ms")
    public val effectiveUpdatedAtUtcMs: Instant,
    @ColumnInfo(name = "effective_present", defaultValue = "1")
    public val effectivePresent: Boolean = true,
    @ColumnInfo(name = "local_progress_revision", defaultValue = "0")
    public val localProgressRevision: Long = 0L,
) {
    init {
        require(collectionEntryId.isNotBlank())
        require(accountId.isNotBlank())
        require(mediaId.isNotBlank())
        require(remoteProgress >= 0)
        require(remoteTotal == null || remoteTotal > 0)
        require(effectiveProgress >= 0)
        require(effectiveTotal == null || effectiveTotal > 0)
        require(localProgressRevision >= 0)
    }
}
