package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "related_media",
    primaryKeys = ["media_id", "related_media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["related_media_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [
        Index(name = "index_related_media_media_id", value = ["media_id"]),
        Index(name = "index_related_media_related_media_id", value = ["related_media_id"]),
        Index(
            name = "index_related_media_media_id_position",
            value = ["media_id", "position"],
            unique = true,
        ),
    ],
)
public data class RelatedMediaEntity(
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "related_media_id")
    public val relatedMediaId: String,
    @ColumnInfo(name = "position")
    public val position: Int,
) {
    init {
        require(mediaId.isNotBlank())
        require(relatedMediaId.isNotBlank())
        require(mediaId != relatedMediaId)
        require(position >= 0)
    }
}
