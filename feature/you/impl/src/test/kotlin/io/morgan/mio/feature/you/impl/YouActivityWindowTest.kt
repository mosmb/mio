package io.morgan.mio.feature.you.impl

import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.RecordedProgressActivity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class YouActivityWindowTest {
    @Test
    fun windowCoversEightLocalCalendarWeeksFromMondayThroughToday() {
        val now = Instant.parse("2026-09-18T21:45:00Z")
        val window = YouActivityWindow.current(now, TimeZone.of("Europe/Paris"))

        assertEquals(LocalDate(2026, 7, 27), window.fromDate)
        assertEquals(LocalDate(2026, 9, 18), window.throughDate)
        assertEquals(8, window.weekStarts.size)
        assertEquals(Instant.parse("2026-07-26T22:00:00Z"), window.fromInclusive)
        assertEquals(Instant.parse("2026-09-18T22:00:00Z"), window.untilExclusive)
    }

    @Test
    fun weeklySummaryUsesLocalBoundariesAndKeepsCorrectionsOutOfIncreases() {
        val window =
            YouActivityWindow.current(
                Instant.parse("2026-09-18T21:45:00Z"),
                TimeZone.of("Europe/Paris"),
            )
        val activities =
            listOf(
                activity(window.fromInclusive, MediaType.ANIME, ProgressDirection.INCREASE),
                activity(Instant.parse("2026-08-02T21:59:59Z"), MediaType.ANIME, ProgressDirection.CORRECTION),
                activity(Instant.parse("2026-08-02T22:00:00Z"), MediaType.ANIME, ProgressDirection.DECREASE),
                activity(Instant.parse("2026-08-02T21:00:00Z"), MediaType.MANGA, ProgressDirection.INCREASE),
                activity(window.fromInclusive.minus(1.nanoseconds), MediaType.ANIME, ProgressDirection.INCREASE),
                activity(window.untilExclusive, MediaType.ANIME, ProgressDirection.INCREASE),
            )

        val summary = window.summarize(activities, MediaType.ANIME)

        assertEquals(8, summary.size)
        assertEquals(2L, summary[0].actionCount)
        assertEquals(1L, summary[0].increaseCount)
        assertEquals(1L, summary[0].correctionCount)
        assertEquals(1L, summary[1].actionCount)
        assertEquals(1L, summary[1].decreaseCount)
        assertEquals(0L, summary.drop(2).sumOf { it.actionCount })
    }

    @Test
    fun windowIncludesActionsRecordedAfterScreenOpenedAcrossDaylightSavingChange() {
        val window =
            YouActivityWindow.current(
                Instant.parse("2026-03-29T00:15:00Z"),
                TimeZone.of("Europe/Paris"),
            )

        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), window.untilExclusive)
        val summary =
            window.summarize(
                listOf(
                    activity(Instant.parse("2026-03-29T21:59:59Z"), MediaType.ANIME, ProgressDirection.INCREASE),
                    activity(window.untilExclusive, MediaType.ANIME, ProgressDirection.INCREASE),
                ),
                MediaType.ANIME,
            )

        assertEquals(1L, summary.last().actionCount)
    }

    private fun activity(
        createdAt: Instant,
        mediaType: MediaType,
        direction: ProgressDirection,
    ) = RecordedProgressActivity(createdAt, mediaType, direction)
}
