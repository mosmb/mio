package io.morgan.mio.feature.you.impl

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

@Immutable
internal sealed interface YouActivityUiState {
    data object Loading : YouActivityUiState

    data class Unavailable(
        val fromDate: LocalDate,
        val throughDate: LocalDate,
    ) : YouActivityUiState

    data class Content(
        val fromDate: LocalDate,
        val throughDate: LocalDate,
        val weeks: List<YouActivityWeekUiModel>,
    ) : YouActivityUiState {
        val totalActions: Long get() = weeks.sumOf { it.actionCount }
        val totalIncreases: Long get() = weeks.sumOf { it.increaseCount }
        val totalDecreases: Long get() = weeks.sumOf { it.decreaseCount }
        val totalCorrections: Long get() = weeks.sumOf { it.correctionCount }
    }
}

@Immutable
internal data class YouActivityWeekUiModel(
    val weekStart: LocalDate,
    val actionCount: Long,
    val increaseCount: Long,
    val decreaseCount: Long,
    val correctionCount: Long,
)
