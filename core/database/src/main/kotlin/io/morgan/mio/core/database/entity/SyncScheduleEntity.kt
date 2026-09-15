package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "sync_schedule",
    primaryKeys = ["account_id"],
    foreignKeys = [
        ForeignKey(
            entity = SelectedAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
public data class SyncScheduleEntity(
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "requested_generation")
    public val requestedGeneration: Long,
    @ColumnInfo(name = "dispatched_generation")
    public val dispatchedGeneration: Long,
) {
    init {
        require(accountId.isNotBlank())
        require(requestedGeneration >= 0L)
        require(dispatchedGeneration >= 0L)
        require(dispatchedGeneration <= requestedGeneration)
    }
}
