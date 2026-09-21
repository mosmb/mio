package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "media_search_result",
    primaryKeys = ["account_id", "media_filter", "media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaSearchQueryEntity::class,
            parentColumns = ["account_id", "media_filter"],
            childColumns = ["account_id", "media_filter"],
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
            name = "index_media_search_result_account_id_media_filter_position",
            value = ["account_id", "media_filter", "position"],
            unique = true,
        ),
        Index(name = "index_media_search_result_media_id", value = ["media_id"]),
    ],
)
public data class MediaSearchResultEntity(
    @ColumnInfo(name = "account_id")
    public val accountId: String,
    @ColumnInfo(name = "media_filter")
    public val filter: String,
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "position")
    public val position: Int,
) {
    init {
        require(accountId.isNotBlank())
        require(filter in setOf("ALL", "ANIME", "MANGA") || filter.matches(Regex("BROWSE:(ANIME|MANGA):[A-Z_]+")))
        require(mediaId.isNotBlank())
        require(position >= 0)
    }
}
