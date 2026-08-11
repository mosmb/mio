package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.morgan.mio.core.database.model.StoredConnectedProvider

public const val SELECTED_ACCOUNT_KEY: String = "selected"

@Entity(
    tableName = "selected_account",
    indices = [
        Index(
            name = "index_selected_account_account_id",
            value = ["account_id"],
            unique = true,
        ),
    ],
)
public data class SelectedAccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "selection_key")
    public val selectionKey: String,
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "connected_provider")
    public val connectedProvider: StoredConnectedProvider,
    @ColumnInfo(name = "display_name")
    public val displayName: String,
    @ColumnInfo(name = "avatar_reference")
    public val avatarReference: String?,
) {
    init {
        require(selectionKey == SELECTED_ACCOUNT_KEY)
        require(accountId.isNotBlank())
        require(displayName.isNotBlank())
        require(avatarReference == null || avatarReference.isNotBlank())
    }
}

@Entity(
    tableName = "account_provider_id",
    primaryKeys = ["account_id"],
    foreignKeys = [
        ForeignKey(
            entity = SelectedAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_account_provider_id_account_id", value = ["account_id"]),
        Index(
            name = "index_account_provider_id_provider_remote_account_id",
            value = ["provider", "remote_account_id"],
            unique = true,
        ),
    ],
)
public data class AccountProviderIdEntity(
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "provider")
    public val provider: StoredConnectedProvider,
    @ColumnInfo(name = "remote_account_id")
    public val remoteAccountId: String,
) {
    init {
        require(accountId.isNotBlank())
        require(remoteAccountId.isNotBlank())
    }
}
