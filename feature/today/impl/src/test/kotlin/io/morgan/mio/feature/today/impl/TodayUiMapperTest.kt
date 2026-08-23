package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.queue.NowRecommendation
import io.morgan.mio.domain.queue.TodayQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayUiMapperTest {
    @Test
    fun emptyQueueMapsToNeutralEmptyState() {
        assertEquals(TodayUiState.Empty, TodayUiMapper.map(TodayQueue(now = null)))
    }

    @Test
    fun contentMapsAllFieldsToPresentationValuesAndPreservesArtworkReferences() {
        val recommendation =
            NowRecommendation(
                mediaId = MediaId("media-1"),
                mediaType = MediaType.MANGA,
                title = MediaTitle("A title"),
                poster = ImageReference("poster://one"),
                banner = ImageReference("banner://one"),
                progress = ProgressValue(41),
                progressUnit = ProgressUnit.CHAPTER,
                total = null,
                nextUnit = UnitNumber(42),
                availableUnitCount = 3,
            )

        val state = TodayUiMapper.map(TodayQueue(recommendation)) as TodayUiState.Content

        assertEquals(
            TodayNowUiModel(
                mediaId = "media-1",
                mediaType = TodayMediaType.MANGA,
                title = "A title",
                posterReference = "poster://one",
                bannerReference = "banner://one",
                progress = 41,
                progressUnit = TodayProgressUnit.CHAPTER,
                total = null,
                nextUnit = 42,
                availableUnitCount = 3,
            ),
            state.now,
        )
        assertTrue(state.now.posterReference != state.now.bannerReference)
    }

    @Test
    fun mappedStateContainsNoDomainAggregateOrArtworkObject() {
        val state =
            TodayUiMapper.map(
                TodayQueue(
                    NowRecommendation(
                        mediaId = MediaId("media-1"),
                        mediaType = MediaType.ANIME,
                        title = MediaTitle("Title"),
                        poster = null,
                        banner = null,
                        progress = ProgressValue(0),
                        progressUnit = ProgressUnit.EPISODE,
                        total = UnitTotal(12),
                        nextUnit = UnitNumber(1),
                        availableUnitCount = 1,
                    ),
                ),
            ) as TodayUiState.Content

        assertNull(state.now.posterReference)
        assertNull(state.now.bannerReference)
        assertEquals(0, state.now.progress)
        assertEquals(12, state.now.total)
    }
}
