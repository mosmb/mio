package io.morgan.mio.domain.collection

import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle

public data class CollectionStatisticsSnapshot(
    public val completedTitleCount: Long,
    public val recordedEpisodes: RecordedProgressTotal,
    public val recordedChapters: RecordedProgressTotal,
    public val anime: TypeStatistics,
    public val manga: TypeStatistics,
    public val artwork: List<Artwork>,
) {
    public data class RecordedProgressTotal(
        public val value: Long?,
        public val trackedTitleCount: Long,
        public val includedTitleCount: Long,
    )

    public data class TypeStatistics(
        public val trackedTitleCount: Long,
        public val completedTitleCount: Long,
        public val genreMetadataTitleCount: Long,
        public val topGenres: List<GenreCount>,
    )

    public data class GenreCount(
        public val name: String,
        public val titleCount: Long,
    )

    public data class Artwork(
        public val mediaId: MediaId,
        public val title: MediaTitle,
        public val poster: ImageReference?,
    )
}
