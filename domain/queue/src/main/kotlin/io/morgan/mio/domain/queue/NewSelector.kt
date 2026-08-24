package io.morgan.mio.domain.queue

import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.MediaId
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal object NewSelector {
    fun select(
        readyTitles: List<ReadyTitle>,
        excludedMediaId: MediaId,
        now: Instant,
    ): List<NewRecommendation> =
        readyTitles
            .mapNotNull { ready ->
                if (ready.candidate.media.id == excludedMediaId) return@mapNotNull null
                val releaseInstant = ready.earliestTrustedNextUnitInstant() ?: return@mapNotNull null
                if (releaseInstant < now - 7.days || releaseInstant > now) return@mapNotNull null
                NewCandidate(ready = ready, releaseInstant = releaseInstant)
            }.sortedWith(
                compareByDescending<NewCandidate> { candidate -> candidate.releaseInstant }
                    .thenBy { candidate -> candidate.ready.candidate.media.id.value }
                    .thenBy { candidate -> candidate.ready.candidate.entry.id.value },
            ).take(MAX_NEW_RECOMMENDATIONS)
            .map { candidate ->
                val ready = candidate.ready
                val entry = ready.candidate.entry
                val media = ready.candidate.media
                NewRecommendation(
                    mediaId = media.id,
                    mediaType = media.type,
                    title = media.title,
                    poster = media.artwork.poster,
                    progress = entry.progress,
                    progressUnit = entry.progressUnit,
                    total = entry.total,
                    nextUnit = ready.nextUnit,
                    availableUnitCount = ready.availableUnitCount,
                    releaseAge = now - candidate.releaseInstant,
                )
            }

    private fun ReadyTitle.earliestTrustedNextUnitInstant(): Instant? =
        candidate.releases
            .asSequence()
            .filter { release -> release.unit == candidate.entry.progressUnit }
            .filter { release -> release.number == nextUnit }
            .mapNotNull { release ->
                (release.time as? ReleaseTime.Trusted)?.instant
            }.minOrNull()

    private data class NewCandidate(
        val ready: ReadyTitle,
        val releaseInstant: Instant,
    )

    private const val MAX_NEW_RECOMMENDATIONS: Int = 3
}
