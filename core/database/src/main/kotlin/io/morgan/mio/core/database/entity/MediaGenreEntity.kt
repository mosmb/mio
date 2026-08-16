package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "media_genre",
    primaryKeys = ["media_id", "genre"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_media_genre_media_id", value = ["media_id"]),
        Index(
            name = "index_media_genre_media_id_position",
            value = ["media_id", "position"],
            unique = true,
        ),
    ],
)
public data class MediaGenreEntity(
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "genre")
    public val genre: String,
    @ColumnInfo(name = "position")
    public val position: Int,
) {
    init {
        require(mediaId.isNotBlank())
        require(genre.isNotBlank())
        require(position >= 0)
    }
}
