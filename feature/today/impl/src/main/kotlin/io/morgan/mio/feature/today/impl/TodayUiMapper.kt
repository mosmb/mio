package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.queue.CaughtUpSummary
import io.morgan.mio.domain.queue.NewRecommendation
import io.morgan.mio.domain.queue.NextReleaseRecommendation
import io.morgan.mio.domain.queue.NowRecommendation
import io.morgan.mio.domain.queue.TodayQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal object TodayUiMapper {
    fun map(queue: TodayQueue): TodayUiState =
        queue.now?.let { recommendation ->
            TodayUiState.Populated(
                now = recommendation.toUiModel(),
                newRecommendations = queue.newRecommendations.map { item -> item.toUiModel() },
                remainingReadyCount = queue.remainingReadyCount,
            )
        } ?: TodayUiState.CaughtUp(requireNotNull(queue.caughtUp).toUiModel())

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

    private fun NewRecommendation.toUiModel(): TodayNewUiModel =
        TodayNewUiModel(
            mediaId = mediaId.value,
            mediaType = mediaType.toUiType(),
            title = title.value,
            posterReference = poster?.value,
            progress = progress.value,
            progressUnit = progressUnit.toUiUnit(),
            total = total?.value,
            nextUnit = nextUnit.value,
            availableUnitCount = availableUnitCount,
            releaseAge = releaseAge.toUiTime(isFuture = false),
        )

    private fun CaughtUpSummary.toUiModel(): TodayCaughtUpUiModel =
        TodayCaughtUpUiModel(
            artwork =
                artwork.map { item ->
                    TodayCaughtUpArtworkUiModel(
                        mediaId = item.mediaId.value,
                        title = item.title.value,
                        reference = item.reference?.value,
                    )
                },
            nextRelease = nextRelease?.toUiModel(),
        )

    private fun NextReleaseRecommendation.toUiModel(): TodayNextReleaseUiModel =
        TodayNextReleaseUiModel(
            mediaId = mediaId.value,
            mediaType = mediaType.toUiType(),
            title = title.value,
            posterReference = poster?.value,
            progressUnit = progressUnit.toUiUnit(),
            unitNumber = unitNumber.value,
            waitUntilRelease = waitUntilRelease.toUiTime(isFuture = true),
        )

    private fun Duration.toUiTime(isFuture: Boolean): TodayRelativeTime {
        require(isFinite())
        if (isFuture) {
            require(this > Duration.ZERO)
        } else {
            require(this >= Duration.ZERO)
        }
        return when {
            this < 1.hours -> TodayRelativeTime(0, TodayRelativeTimeUnit.UNDER_HOUR)
            this < 24.hours -> TodayRelativeTime(inWholeHours.toInt(), TodayRelativeTimeUnit.HOURS)
            else ->
                TodayRelativeTime(
                    amount = inWholeDays.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    unit = TodayRelativeTimeUnit.DAYS,
                )
        }
    }

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
