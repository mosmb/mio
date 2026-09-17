package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(
    tableName = "sync_account_issue",
    foreignKeys = [
        ForeignKey(
            entity = SelectedAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_sync_account_issue_account_id", value = ["account_id"], unique = true),
    ],
)
public data class SyncAccountIssueEntity(
    @PrimaryKey
    @ColumnInfo(name = "issue_id")
    public val issueId: String,
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "kind")
    public val kind: String,
    @ColumnInfo(name = "detected_at_utc_ms")
    public val detectedAtUtcMs: Instant,
) {
    init {
        require(issueId.isNotBlank())
        require(accountId.isNotBlank())
        require(kind.isNotBlank())
    }
}
