package io.morgan.mio.feature.today.impl

internal sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Populated(
        val now: TodayNowUiModel,
        val newRecommendations: List<TodayNewUiModel>,
        val remainingReadyCount: Int,
    ) : TodayUiState {
        init {
            require(remainingReadyCount >= 0)
            require(newRecommendations.size <= 3)
            require(
                newRecommendations.distinctBy { recommendation -> recommendation.mediaId }.size ==
                    newRecommendations.size,
            )
            require(newRecommendations.none { recommendation -> recommendation.mediaId == now.mediaId })
        }
    }

    data class CaughtUp(
        val receipt: TodayCaughtUpUiModel,
    ) : TodayUiState
}

internal data class TodayNowUiModel(
    val mediaId: String,
    val mediaType: TodayMediaType,
    val title: String,
    val posterReference: String?,
    val bannerReference: String?,
    val progress: Int,
    val progressUnit: TodayProgressUnit,
    val total: Int?,
    val nextUnit: Int,
    val availableUnitCount: Int,
)

internal data class TodayNewUiModel(
    val mediaId: String,
    val mediaType: TodayMediaType,
    val title: String,
    val posterReference: String?,
    val progress: Int,
    val progressUnit: TodayProgressUnit,
    val total: Int?,
    val nextUnit: Int,
    val availableUnitCount: Int,
    val releaseAge: TodayRelativeTime,
)

internal data class TodayCaughtUpUiModel(
    val artwork: List<TodayCaughtUpArtworkUiModel>,
    val nextRelease: TodayNextReleaseUiModel?,
)

internal data class TodayCaughtUpArtworkUiModel(
    val mediaId: String,
    val title: String,
    val reference: String?,
)

internal data class TodayNextReleaseUiModel(
    val mediaId: String,
    val mediaType: TodayMediaType,
    val title: String,
    val posterReference: String?,
    val progressUnit: TodayProgressUnit,
    val unitNumber: Int,
    val waitUntilRelease: TodayRelativeTime,
)

internal data class TodayRelativeTime(
    val amount: Int,
    val unit: TodayRelativeTimeUnit,
)

internal enum class TodayRelativeTimeUnit {
    UNDER_HOUR,
    HOURS,
    DAYS,
}

internal enum class TodayMediaType {
    ANIME,
    MANGA,
}

internal enum class TodayProgressUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}
