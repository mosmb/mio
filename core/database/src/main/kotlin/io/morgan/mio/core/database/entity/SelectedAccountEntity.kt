package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
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
