package io.morgan.mio.data.provider.api

import kotlin.time.Instant

public data class ProviderMediaTotals(
    public val episodes: Int?,
    public val chapters: Int?,
    public val volumes: Int?,
) {
    init {
        require(episodes == null || episodes > 0)
        require(chapters == null || chapters > 0)
        require(volumes == null || volumes > 0)
    }
}

public sealed interface ProviderReleaseTime {
    public data object Unknown : ProviderReleaseTime

    public data class Trusted(
        public val instant: Instant,
    ) : ProviderReleaseTime

    public data class Untrusted(
        public val instant: Instant,
    ) : ProviderReleaseTime
}

public data class ProviderRelease(
    public val mediaId: ProviderMediaId,
    public val unit: ProviderProgressUnit,
    public val number: Int,
    public val time: ProviderReleaseTime,
) {
    init {
        require(number > 0)
    }
}

public class ProviderMedia(
    public val id: ProviderMediaId,
    public val type: ProviderMediaType,
    public val title: String,
    public val format: ProviderMediaFormat?,
    public val publicationStatus: ProviderMediaPublicationStatus,
    public val synopsis: String?,
    public val poster: ProviderImageReference?,
    public val banner: ProviderImageReference?,
    public val totals: ProviderMediaTotals,
    genres: List<String>,
    relatedMediaIds: List<ProviderMediaId>,
    releases: List<ProviderRelease>,
) {
    public val genres: List<String> = genres.toList()
    public val relatedMediaIds: List<ProviderMediaId> = relatedMediaIds.toList()
    public val releases: List<ProviderRelease> = releases.toList()

    init {
        require(title.isNotBlank())
        require(synopsis == null || synopsis.isNotBlank())
        require(this.genres.all(String::isNotBlank))
        require(this.genres.size == this.genres.toSet().size)
        require(this.relatedMediaIds.size == this.relatedMediaIds.toSet().size)
        require(id !in this.relatedMediaIds)
        require(this.releases.all { release -> release.mediaId == id })
        require(
            this.releases
                .map { release -> release.unit to release.number }
                .toSet()
                .size == this.releases.size,
        )
    }
}
