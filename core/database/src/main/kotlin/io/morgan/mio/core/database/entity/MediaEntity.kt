package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType

@Entity(tableName = "media")
public data class MediaEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "media_type")
    public val mediaType: StoredMediaType,
    @ColumnInfo(name = "title")
    public val title: String,
    @ColumnInfo(name = "format")
    public val format: StoredMediaFormat?,
    @ColumnInfo(name = "publication_status")
    public val publicationStatus: StoredMediaPublicationStatus,
    @ColumnInfo(name = "synopsis")
    public val synopsis: String?,
    @ColumnInfo(name = "poster_reference")
    public val posterReference: String?,
    @ColumnInfo(name = "banner_reference")
    public val bannerReference: String?,
    @ColumnInfo(name = "episode_total")
    public val episodeTotal: Int?,
    @ColumnInfo(name = "chapter_total")
    public val chapterTotal: Int?,
    @ColumnInfo(name = "volume_total")
    public val volumeTotal: Int?,
) {
    init {
        require(mediaId.isNotBlank())
        require(title.isNotBlank())
        require(synopsis == null || synopsis.isNotBlank())
        require(posterReference == null || posterReference.isNotBlank())
        require(bannerReference == null || bannerReference.isNotBlank())
        require(episodeTotal == null || episodeTotal > 0)
        require(chapterTotal == null || chapterTotal > 0)
        require(volumeTotal == null || volumeTotal > 0)
    }
}
