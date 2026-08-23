package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.queue.NowRecommendation
import io.morgan.mio.domain.queue.TodayQueue

internal object TodayUiMapper {
    fun map(queue: TodayQueue): TodayUiState =
        queue.now?.let { recommendation ->
            TodayUiState.Content(recommendation.toUiModel())
        } ?: TodayUiState.Empty

    private fun NowRecommendation.toUiModel(): TodayNowUiModel =
        TodayNowUiModel(
            mediaId = mediaId.value,
            mediaType = mediaType.toUiType(),
            title = title.value,
            posterReference = poster?.value,
            bannerReference = banner?.value,
            progress = progress.value,
            progressUnit = progressUnit.toUiUnit(),
            total = total?.value,
            nextUnit = nextUnit.value,
            availableUnitCount = availableUnitCount,
        )

    private fun MediaType.toUiType(): TodayMediaType =
        when (this) {
            MediaType.ANIME -> TodayMediaType.ANIME
            MediaType.MANGA -> TodayMediaType.MANGA
        }

    private fun ProgressUnit.toUiUnit(): TodayProgressUnit =
        when (this) {
            ProgressUnit.EPISODE -> TodayProgressUnit.EPISODE
            ProgressUnit.CHAPTER -> TodayProgressUnit.CHAPTER
            ProgressUnit.VOLUME -> TodayProgressUnit.VOLUME
        }
}
