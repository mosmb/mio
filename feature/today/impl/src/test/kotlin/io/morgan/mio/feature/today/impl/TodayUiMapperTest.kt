package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.queue.CaughtUpSummary
import io.morgan.mio.domain.queue.NewRecommendation
import io.morgan.mio.domain.queue.NextReleaseRecommendation
import io.morgan.mio.domain.queue.NowRecommendation
import io.morgan.mio.domain.queue.TodayQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TodayUiMapperTest {
    @Test
    fun populatedMapsNowNewAndRemainingWithPresentationOnlyValues() {
        val state =
            TodayUiMapper.map(
                TodayQueue(
                    now = nowRecommendation("now", MediaType.ANIME, ProgressUnit.EPISODE),
                    newRecommendations = listOf(newRecommendation("new", 3.hours)),
                    remainingReadyCount = 4,
                    caughtUp = null,
                ),
            ) as TodayUiState.Populated

        assertEquals("now", state.now.mediaId)
        assertEquals(TodayMediaType.ANIME, state.now.mediaType)
        assertEquals("Title now", state.now.title)
        assertEquals("poster://now", state.now.posterReference)
        assertEquals("banner://now", state.now.bannerReference)
        assertEquals(41, state.now.progress)
        assertEquals(TodayProgressUnit.EPISODE, state.now.progressUnit)
        assertEquals(50, state.now.total)
        assertEquals(42, state.now.nextUnit)
        assertEquals(3, state.now.availableUnitCount)
        assertEquals(1, state.newRecommendations.size)
        val newRecommendation = state.newRecommendations.single()
        assertEquals("new", newRecommendation.mediaId)
        assertEquals(TodayMediaType.ANIME, newRecommendation.mediaType)
        assertEquals("New new", newRecommendation.title)
        assertNull(newRecommendation.posterReference)
        assertEquals(1, newRecommendation.progress)
        assertEquals(TodayProgressUnit.EPISODE, newRecommendation.progressUnit)
        assertEquals(12, newRecommendation.total)
        assertEquals(2, newRecommendation.nextUnit)
        assertEquals(2, newRecommendation.availableUnitCount)
        assertEquals(TodayRelativeTime(3, TodayRelativeTimeUnit.HOURS), newRecommendation.releaseAge)
        assertEquals(4, state.remainingReadyCount)
    }

    @Test
    fun caughtUpMapsArtworkAndFutureReleaseWithoutDomainTimeTypes() {
        val state =
            TodayUiMapper.map(
                TodayQueue(
                    now = null,
                    newRecommendations = emptyList(),
                    remainingReadyCount = 0,
                    caughtUp =
                        CaughtUpSummary(
                            artwork =
                                listOf(
                                    CaughtUpSummary.Artwork(
                                        mediaId = MediaId("artwork"),
                                        title = MediaTitle("Artwork"),
                                        reference = ImageReference("poster://artwork"),
                                    ),
                                ),
                            nextRelease =
                                NextReleaseRecommendation(
                                    mediaId = MediaId("future"),
                                    mediaType = MediaType.MANGA,
                                    title = MediaTitle("Future"),
                                    poster = null,
                                    progressUnit = ProgressUnit.CHAPTER,
                                    unitNumber = UnitNumber(42),
                                    waitUntilRelease = 24.hours,
                                ),
                        ),
                ),
            ) as TodayUiState.CaughtUp

        assertEquals(
            "artwork",
            state.receipt.artwork
                .single()
                .mediaId,
        )
        assertEquals(
            "poster://artwork",
            state.receipt.artwork
                .single()
                .reference,
        )
        assertEquals("future", state.receipt.nextRelease?.mediaId)
        assertEquals(TodayMediaType.MANGA, state.receipt.nextRelease?.mediaType)
        assertEquals("Future", state.receipt.nextRelease?.title)
        assertNull(state.receipt.nextRelease?.posterReference)
        assertEquals(TodayProgressUnit.CHAPTER, state.receipt.nextRelease?.progressUnit)
        assertEquals(42, state.receipt.nextRelease?.unitNumber)
        assertEquals(
            1,
            state.receipt.nextRelease
                ?.waitUntilRelease
                ?.amount,
        )
        assertEquals(
            TodayRelativeTimeUnit.DAYS,
            state.receipt.nextRelease
                ?.waitUntilRelease
                ?.unit,
        )
    }

    @Test
    fun relativeTimeOwnsZeroHourDayAndSevenDayBoundaries() {
        val ages =
            listOf(
                Duration.ZERO to TodayRelativeTime(0, TodayRelativeTimeUnit.UNDER_HOUR),
                1.hours to TodayRelativeTime(1, TodayRelativeTimeUnit.HOURS),
                24.hours to TodayRelativeTime(1, TodayRelativeTimeUnit.DAYS),
                7.days to TodayRelativeTime(7, TodayRelativeTimeUnit.DAYS),
            )

        ages.forEachIndexed { index, (age, expected) ->
            val state =
                TodayUiMapper.map(
                    TodayQueue(
                        now = nowRecommendation("now-$index", MediaType.ANIME, ProgressUnit.EPISODE),
                        newRecommendations = listOf(newRecommendation("new-$index", age)),
                        remainingReadyCount = 0,
                        caughtUp = null,
                    ),
                ) as TodayUiState.Populated
            assertEquals(expected, state.newRecommendations.single().releaseAge)
        }

        val waits =
            listOf(
                30.minutes to TodayRelativeTime(0, TodayRelativeTimeUnit.UNDER_HOUR),
                1.hours to TodayRelativeTime(1, TodayRelativeTimeUnit.HOURS),
                24.hours to TodayRelativeTime(1, TodayRelativeTimeUnit.DAYS),
                7.days to TodayRelativeTime(7, TodayRelativeTimeUnit.DAYS),
            )
        waits.forEachIndexed { index, (wait, expected) ->
            val state =
                TodayUiMapper.map(
                    TodayQueue(
                        now = null,
                        newRecommendations = emptyList(),
                        remainingReadyCount = 0,
                        caughtUp =
                            CaughtUpSummary(
                                artwork = emptyList(),
                                nextRelease =
                                    NextReleaseRecommendation(
                                        mediaId = MediaId("future-$index"),
                                        mediaType = MediaType.MANGA,
                                        title = MediaTitle("Future $index"),
                                        poster = null,
                                        progressUnit = ProgressUnit.VOLUME,
                                        unitNumber = UnitNumber(2),
                                        waitUntilRelease = wait,
                                    ),
                            ),
                    ),
                ) as TodayUiState.CaughtUp
            assertEquals(expected, state.receipt.nextRelease?.waitUntilRelease)
            assertEquals(TodayProgressUnit.VOLUME, state.receipt.nextRelease?.progressUnit)
        }
    }

    @Test
    fun unknownTotalsAndMissingArtworkRemainPresentationPrimitives() {
        val state =
            TodayUiMapper.map(
                TodayQueue(
                    now =
                        nowRecommendation("media", MediaType.MANGA, ProgressUnit.CHAPTER).copy(
                            poster = null,
                            banner = null,
                            total = null,
                        ),
                    newRecommendations = emptyList(),
                    remainingReadyCount = 0,
                    caughtUp = null,
                ),
            ) as TodayUiState.Populated

        assertNull(state.now.posterReference)
        assertNull(state.now.bannerReference)
        assertNull(state.now.total)
        assertEquals(41, state.now.progress)
        assertEquals(42, state.now.nextUnit)
    }

    private fun nowRecommendation(
        mediaId: String,
        mediaType: MediaType,
        progressUnit: ProgressUnit,
    ): NowRecommendation =
        NowRecommendation(
            mediaId = MediaId(mediaId),
            mediaType = mediaType,
            title = MediaTitle("Title $mediaId"),
            poster = ImageReference("poster://$mediaId"),
            banner = ImageReference("banner://$mediaId"),
            progress = ProgressValue(41),
            progressUnit = progressUnit,
            total = UnitTotal(50),
            nextUnit = UnitNumber(42),
            availableUnitCount = 3,
        )

    private fun newRecommendation(
        mediaId: String,
        releaseAge: Duration,
    ): NewRecommendation =
        NewRecommendation(
            mediaId = MediaId(mediaId),
            mediaType = MediaType.ANIME,
            title = MediaTitle("New $mediaId"),
            poster = null,
            progress = ProgressValue(1),
            progressUnit = ProgressUnit.EPISODE,
            total = UnitTotal(12),
            nextUnit = UnitNumber(2),
            availableUnitCount = 2,
            releaseAge = releaseAge,
        )
}
