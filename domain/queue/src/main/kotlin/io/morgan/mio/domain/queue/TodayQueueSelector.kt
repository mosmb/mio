package io.morgan.mio.domain.queue

import kotlin.time.Instant

internal object TodayQueueSelector {
    fun select(
        candidates: List<TodayQueueCandidate>,
        now: Instant,
    ): TodayQueue {
        val distinctCandidates = candidates.deduplicateByMediaId()
        val readyTitles = distinctCandidates.mapNotNull { candidate -> ReadyCalculator.calculate(candidate, now) }
        val nowRecommendation = NowSelector.select(readyTitles)
        if (nowRecommendation == null) {
            return TodayQueue(
                now = null,
                newRecommendations = emptyList(),
                remainingReadyCount = 0,
                caughtUp = CaughtUpSelector.select(distinctCandidates, now),
            )
        }

        val newRecommendations = NewSelector.select(readyTitles, nowRecommendation.mediaId, now)
        return TodayQueue(
            now = nowRecommendation,
            newRecommendations = newRecommendations,
            remainingReadyCount = (readyTitles.size - 1 - newRecommendations.size).coerceAtLeast(0),
            caughtUp = null,
        )
    }

    private fun List<TodayQueueCandidate>.deduplicateByMediaId(): List<TodayQueueCandidate> =
        groupBy { candidate -> candidate.media.id }
            .values
            .map { candidatesForMedia ->
                candidatesForMedia
                    .sortedWith(
                        compareByDescending<TodayQueueCandidate> { candidate -> candidate.entry.updatedAt }
                            .thenBy { candidate -> candidate.entry.id.value },
                    ).first()
            }.sortedWith(compareBy<TodayQueueCandidate> { candidate -> candidate.media.id.value })
}
