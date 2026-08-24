package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.UnitNumber
import kotlin.time.Instant

internal object CaughtUpSelector {
    fun select(
        candidates: List<TodayQueueCandidate>,
        now: Instant,
    ): CaughtUpSummary {
        val artwork =
            candidates
                .asSequence()
                .filter { candidate ->
                    candidate.entry.status == CollectionStatus.CURRENT ||
                        candidate.entry.status == CollectionStatus.COMPLETED
                }.sortedWith(candidateOrder)
                .take(MAX_ARTWORK)
                .map { candidate ->
                    CaughtUpSummary.Artwork(
                        mediaId = candidate.media.id,
                        title = candidate.media.title,
                        reference = candidate.media.artwork.poster ?: candidate.media.artwork.banner,
                    )
                }.toList()

        val nextRelease =
            candidates
                .asSequence()
                .filter { candidate ->
                    candidate.entry.status == CollectionStatus.CURRENT ||
                        candidate.entry.status == CollectionStatus.PLANNED ||
                        candidate.entry.status == CollectionStatus.PAUSED
                }.flatMap { candidate ->
                    candidate.releases.asSequence().mapNotNull { release ->
                        val trustedInstant = (release.time as? ReleaseTime.Trusted)?.instant ?: return@mapNotNull null
                        if (release.unit != candidate.entry.progressUnit) return@mapNotNull null
                        if (release.number.value <= candidate.entry.progress.value) return@mapNotNull null
                        if (candidate.entry.total?.let { total -> release.number.value > total.value } == true) {
                            return@mapNotNull null
                        }
                        if (trustedInstant <= now) return@mapNotNull null
                        FutureReleaseCandidate(candidate, release.number, trustedInstant)
                    }
                }.minWithOrNull(futureReleaseOrder)
                ?.let { future ->
                    val candidate = future.candidate
                    NextReleaseRecommendation(
                        mediaId = candidate.media.id,
                        mediaType = candidate.media.type,
                        title = candidate.media.title,
                        poster = candidate.media.artwork.poster,
                        progressUnit = candidate.entry.progressUnit,
                        unitNumber = future.unitNumber,
                        waitUntilRelease = future.releaseInstant - now,
                    )
                }

        return CaughtUpSummary(artwork = artwork, nextRelease = nextRelease)
    }

    private data class FutureReleaseCandidate(
        val candidate: TodayQueueCandidate,
        val unitNumber: UnitNumber,
        val releaseInstant: Instant,
    )

    private val candidateOrder =
        compareByDescending<TodayQueueCandidate> { candidate -> candidate.entry.updatedAt }
            .thenBy { candidate -> candidate.media.id.value }
            .thenBy { candidate -> candidate.entry.id.value }

    private val futureReleaseOrder =
        compareBy<FutureReleaseCandidate> { candidate -> candidate.releaseInstant }
            .thenBy { candidate -> candidate.candidate.media.id.value }
            .thenBy { candidate -> candidate.unitNumber.value }
            .thenBy { candidate -> candidate.candidate.entry.id.value }

    private const val MAX_ARTWORK: Int = 6
}
