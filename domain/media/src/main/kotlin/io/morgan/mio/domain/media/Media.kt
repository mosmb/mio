package io.morgan.mio.domain.media

import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.UnitTotal

public enum class MediaFormat {
    TV,
    MOVIE,
    OVA,
    ONA,
    SPECIAL,
    MUSIC,
    MANGA,
    NOVEL,
    ONE_SHOT,
    OTHER,
}

public enum class MediaPublicationStatus {
    UPCOMING,
    RELEASING,
    FINISHED,
    CANCELLED,
    HIATUS,
    UNKNOWN,
}

public data class Artwork(
    public val poster: ImageReference?,
    public val banner: ImageReference?,
)

public data class MediaTotals(
    public val episodes: UnitTotal?,
    public val chapters: UnitTotal?,
    public val volumes: UnitTotal?,
)

public class Media(
    public val id: MediaId,
    public val type: MediaType,
    public val title: MediaTitle,
    public val format: MediaFormat?,
    public val publicationStatus: MediaPublicationStatus,
    public val synopsis: String?,
    public val artwork: Artwork,
    public val totals: MediaTotals,
    genres: List<String>,
    relatedMediaIds: List<MediaId>,
) {
    public val genres: List<String> = genres.toList()
    public val relatedMediaIds: List<MediaId> = relatedMediaIds.toList()

    init {
        require(synopsis == null || synopsis.isNotBlank())
        require(this.genres.all { it.isNotBlank() })
        require(this.genres.distinct().size == this.genres.size)
        require(this.relatedMediaIds.distinct().size == this.relatedMediaIds.size)
        require(id !in this.relatedMediaIds)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Media &&
                    id == other.id &&
                    type == other.type &&
                    title == other.title &&
                    format == other.format &&
                    publicationStatus == other.publicationStatus &&
                    synopsis == other.synopsis &&
                    artwork == other.artwork &&
                    totals == other.totals &&
                    genres == other.genres &&
                    relatedMediaIds == other.relatedMediaIds
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (format?.hashCode() ?: 0)
        result = 31 * result + publicationStatus.hashCode()
        result = 31 * result + (synopsis?.hashCode() ?: 0)
        result = 31 * result + artwork.hashCode()
        result = 31 * result + totals.hashCode()
        result = 31 * result + genres.hashCode()
        result = 31 * result + relatedMediaIds.hashCode()
        return result
    }

    override fun toString(): String =
        "Media(id=$id, type=$type, title=$title, format=$format, publicationStatus=$publicationStatus, " +
            "synopsis=$synopsis, artwork=$artwork, totals=$totals, genres=$genres, " +
            "relatedMediaIds=$relatedMediaIds)"
}
