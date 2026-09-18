package io.morgan.mio.feature.you.impl

import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.RecordedProgressActivity
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.previousOrSame
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal data class YouActivityWindow(
    val fromInclusive: Instant,
    val untilExclusive: Instant,
    val fromDate: LocalDate,
    val throughDate: LocalDate,
    val weekStarts: List<LocalDate>,
    val timeZone: TimeZone,
) {
    fun summarize(
        activities: List<RecordedProgressActivity>,
        mediaType: MediaType,
    ): List<YouActivityWeekUiModel> {
        val counts = weekStarts.map { MutableWeekCount() }
        val indexByWeek = weekStarts.withIndex().associate { (index, date) -> date to index }
        activities.forEach { activity ->
            if (activity.mediaType != mediaType ||
                activity.createdAt < fromInclusive ||
                activity.createdAt >= untilExclusive
            ) {
                return@forEach
            }
            val localDate = activity.createdAt.toLocalDateTime(timeZone).date
            val weekStart = localDate.previousOrSame(DayOfWeek.MONDAY)
            val index = indexByWeek[weekStart] ?: return@forEach
            val count = counts[index]
            count.actions += 1L
            when (activity.direction) {
                ProgressDirection.INCREASE -> count.increases += 1L
                ProgressDirection.DECREASE -> count.decreases += 1L
                ProgressDirection.CORRECTION -> count.corrections += 1L
            }
        }
        return weekStarts.mapIndexed { index, weekStart ->
            val count = counts[index]
            YouActivityWeekUiModel(
                weekStart = weekStart,
                actionCount = count.actions,
                increaseCount = count.increases,
                decreaseCount = count.decreases,
                correctionCount = count.corrections,
            )
        }
    }

    companion object {
        private const val WEEK_COUNT = 8

        fun current(
            now: Instant,
            timeZone: TimeZone,
        ): YouActivityWindow {
            val today = now.toLocalDateTime(timeZone).date
            val currentWeekStart = today.previousOrSame(DayOfWeek.MONDAY)
            val firstWeekStart = currentWeekStart - DatePeriod(days = (WEEK_COUNT - 1) * DAYS_PER_WEEK)
            val firstInstant = firstWeekStart.atStartOfDayIn(timeZone)
            val weeks = (0 until WEEK_COUNT).map { index -> firstWeekStart + DatePeriod(days = index * DAYS_PER_WEEK) }
            return YouActivityWindow(
                fromInclusive = firstInstant,
                // Include new Room activity while this screen remains visible.
                untilExclusive = (today + DatePeriod(days = 1)).atStartOfDayIn(timeZone),
                fromDate = firstWeekStart,
                throughDate = today,
                weekStarts = weeks,
                timeZone = timeZone,
            )
        }

        private const val DAYS_PER_WEEK = 7
    }

    private data class MutableWeekCount(
        var actions: Long = 0L,
        var increases: Long = 0L,
        var decreases: Long = 0L,
        var corrections: Long = 0L,
    )
}
