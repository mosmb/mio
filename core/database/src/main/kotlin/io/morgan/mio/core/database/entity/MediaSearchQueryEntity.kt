package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.morgan.mio.core.database.model.StoredConnectedProvider

@Entity(
    tableName = "media_search_query",
    primaryKeys = ["account_id", "media_filter"],
    foreignKeys = [
        ForeignKey(
            entity = SelectedAccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "index_media_search_query_account_id", value = ["account_id"])],
)
public data class MediaSearchQueryEntity(
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "media_filter")
    public val filter: String,
    @ColumnInfo(name = "provider")
    public val provider: StoredConnectedProvider,
    @ColumnInfo(name = "normalized_query")
    public val normalizedQuery: String,
    @ColumnInfo(name = "anime_cursor")
    public val animeCursor: String?,
    @ColumnInfo(name = "manga_cursor")
    public val mangaCursor: String?,
) {
    init {
        require(accountId.isNotBlank())
        require(filter in setOf("ALL", "ANIME", "MANGA") || filter.matches(Regex("BROWSE:(ANIME|MANGA):[A-Z_]+")))
        require(normalizedQuery.isNotBlank() && normalizedQuery == normalizedQuery.trim())
    }
}
