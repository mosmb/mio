package io.morgan.mio.feature.media.impl

internal sealed interface MediaDetailUiState {
    data object Loading : MediaDetailUiState

    data object Error : MediaDetailUiState

    data class Content(
        val media: MediaDetailUiModel,
        val relatedTitles: List<MediaDetailRelatedTitleUiModel>,
        val collection: MediaDetailCollectionUiModel?,
    ) : MediaDetailUiState
}

internal data class MediaDetailUiModel(
    val id: String,
    val title: String,
    val type: MediaDetailMediaType,
    val format: MediaDetailFormat?,
    val publicationStatus: MediaDetailPublicationStatus,
    val synopsis: String?,
    val posterReference: String?,
    val bannerReference: String?,
    val totals: MediaDetailTotalsUiModel,
    val genres: List<String>,
    val releaseYear: Int?,
    val scorePercent: Int?,
)

internal data class MediaDetailRelatedTitleUiModel(
    val id: String,
    val title: String,
    val type: MediaDetailMediaType,
    val posterReference: String?,
)

internal data class MediaDetailTotalsUiModel(
    val episodes: Int?,
    val chapters: Int?,
    val volumes: Int?,
)

internal data class MediaDetailCollectionUiModel(
    val status: MediaDetailCollectionStatus,
    val progress: Int,
    val progressUnit: MediaDetailProgressUnit,
    val total: Int?,
)

internal enum class MediaDetailMediaType {
    ANIME,
    MANGA,
}

internal enum class MediaDetailFormat {
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

internal enum class MediaDetailPublicationStatus {
    UPCOMING,
    RELEASING,
    FINISHED,
    CANCELLED,
    HIATUS,
    UNKNOWN,
}

internal enum class MediaDetailCollectionStatus {
    CURRENT,
    PLANNED,
    PAUSED,
    COMPLETED,
    DROPPED,
    REPEATING,
}

internal enum class MediaDetailProgressUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}
