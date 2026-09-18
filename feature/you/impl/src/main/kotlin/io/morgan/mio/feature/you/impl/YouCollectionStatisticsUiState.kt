package io.morgan.mio.feature.you.impl

import androidx.compose.runtime.Immutable

@Immutable
internal sealed interface YouCollectionStatisticsUiState {
    data object Loading : YouCollectionStatisticsUiState

    data object Unavailable : YouCollectionStatisticsUiState

    data class Content(
        val completedTitleCount: Long,
        val episodes: YouRecordedProgressTotalUiModel,
        val chapters: YouRecordedProgressTotalUiModel,
        val anime: YouMediaStatisticsUiModel,
        val manga: YouMediaStatisticsUiModel,
        val artworkCandidates: List<YouArtworkUiModel>,
    ) : YouCollectionStatisticsUiState
}

@Immutable
internal data class YouRecordedProgressTotalUiModel(
    val value: Long?,
    val trackedTitleCount: Long,
    val includedTitleCount: Long,
)

@Immutable
internal data class YouMediaStatisticsUiModel(
    val trackedTitleCount: Long,
    val completedTitleCount: Long,
    val genreMetadataTitleCount: Long,
    val topGenres: List<YouGenreUiModel>,
)

@Immutable
internal data class YouGenreUiModel(
    val name: String,
    val titleCount: Long,
)

@Immutable
internal data class YouArtworkUiModel(
    val mediaId: String,
    val title: String,
    val posterReference: String?,
)
