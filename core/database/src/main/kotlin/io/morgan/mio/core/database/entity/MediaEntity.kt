package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import kotlin.time.Instant

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

@Entity(
    tableName = "release",
    primaryKeys = ["media_id", "progress_unit", "unit_number"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_release_media_id", value = ["media_id"]),
    ],
)
public data class ReleaseEntity(
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "progress_unit")
    public val progressUnit: StoredProgressUnit,
    @ColumnInfo(name = "unit_number")
    public val unitNumber: Int,
    @ColumnInfo(name = "time_kind")
    public val timeKind: StoredReleaseTimeKind,
    @ColumnInfo(name = "release_instant_utc_ms")
    public val releaseInstantUtcMs: Instant?,
) {
    init {
        require(mediaId.isNotBlank())
        require(unitNumber > 0)
        when (timeKind) {
            StoredReleaseTimeKind.UNKNOWN -> require(releaseInstantUtcMs == null)
            StoredReleaseTimeKind.TRUSTED,
            StoredReleaseTimeKind.UNTRUSTED,
            -> requireNotNull(releaseInstantUtcMs)
        }
    }
}

@Entity(
    tableName = "media_provider_id",
    primaryKeys = ["provider", "remote_media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_media_provider_id_media_id", value = ["media_id"]),
        Index(
            name = "index_media_provider_id_provider_media_id",
            value = ["provider", "media_id"],
            unique = true,
        ),
    ],
)
public data class MediaProviderIdEntity(
    @ColumnInfo(name = "provider")
    public val provider: StoredConnectedProvider,
    @ColumnInfo(name = "remote_media_id")
    public val remoteMediaId: String,
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
) {
    init {
        require(remoteMediaId.isNotBlank())
        require(mediaId.isNotBlank())
    }
}
