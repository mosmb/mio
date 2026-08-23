package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.UnitNumber
import kotlin.time.Instant

internal object NowSelector {
    fun select(
        candidates: List<NowCandidate>,
        now: Instant,
    ): NowRecommendation? =
        candidates
            .mapNotNull { candidate -> candidateRecommendation(candidate, now) }
            .sortedWith(
                compareByDescending<EligibleCandidate> { eligible -> eligible.candidate.entry.updatedAt }
                    .thenBy { eligible -> eligible.candidate.media.id.value }
                    .thenBy { eligible -> eligible.candidate.entry.id.value },
            ).firstOrNull()
            ?.recommendation

    private fun candidateRecommendation(
        candidate: NowCandidate,
        now: Instant,
    ): EligibleCandidate? {
        val entry = candidate.entry
        if (entry.status != CollectionStatus.CURRENT) return null
        if (entry.progress.value == Int.MAX_VALUE) return null

        val knownTotal = entry.total
        if (knownTotal != null && knownTotal.value <= entry.progress.value) return null

        val latestReleasedUnit =
            candidate.releases
                .asSequence()
                .filter { release -> release.unit == entry.progressUnit }
                .filter { release -> release.number.value > entry.progress.value }
                .filter { release ->
                    val time = release.time
                    time is ReleaseTime.Trusted && time.instant <= now
                }.map { release -> release.number.value }
                .maxOrNull()

        val finishedTotal =
            if (candidate.media.publicationStatus == MediaPublicationStatus.FINISHED) {
                knownTotal?.value
            } else {
                null
            }

        val releasedThrough = latestReleasedUnit ?: finishedTotal ?: return null
        val availableThrough = minOf(releasedThrough, knownTotal?.value ?: releasedThrough)
        val availableUnitCount = availableThrough - entry.progress.value
        if (availableUnitCount <= 0) return null

        return EligibleCandidate(
            candidate = candidate,
            recommendation =
                NowRecommendation(
                    mediaId = candidate.media.id,
                    mediaType = candidate.media.type,
                    title = candidate.media.title,
                    poster = candidate.media.artwork.poster,
                    banner = candidate.media.artwork.banner,
                    progress = entry.progress,
                    progressUnit = entry.progressUnit,
                    total = knownTotal,
                    nextUnit = UnitNumber(entry.progress.value + 1),
                    availableUnitCount = availableUnitCount,
                ),
        )
    }

    private data class EligibleCandidate(
        val candidate: NowCandidate,
        val recommendation: NowRecommendation,
    )
}
