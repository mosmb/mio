package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import kotlin.time.Instant

@Entity(
    tableName = "sync_lease",
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
public data class SyncLeaseEntity(
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "owner_token")
    public val ownerToken: String,
    @ColumnInfo(name = "expires_at_utc_ms")
    public val expiresAtUtcMs: Instant,
) {
    init {
        require(accountId.isNotBlank())
        require(ownerToken.isNotBlank())
    }
}
