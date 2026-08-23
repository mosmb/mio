package io.morgan.mio.feature.today.impl

internal sealed interface TodayUiState {
    data object Loading : TodayUiState

    data object Empty : TodayUiState

    data class Content(
        val now: TodayNowUiModel,
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

internal enum class TodayMediaType {
    ANIME,
    MANGA,
}

internal enum class TodayProgressUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}
