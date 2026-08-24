package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.UnitNumber
import kotlin.math.min
import kotlin.time.Instant

internal object ReadyCalculator {
    fun calculate(
        candidate: TodayQueueCandidate,
        now: Instant,
    ): ReadyTitle? {
        val entry = candidate.entry
        if (entry.status != CollectionStatus.CURRENT) return null
        if (entry.progress.value == Int.MAX_VALUE) return null

        val knownTotal = entry.total
        if (knownTotal != null && knownTotal.value <= entry.progress.value) return null

        val releasedThrough =
            candidate.releases
                .asSequence()
                .filter { release -> release.unit == entry.progressUnit }
                .filter { release -> release.number.value > entry.progress.value }
                .filter { release ->
                    val time = release.time
                    time is ReleaseTime.Trusted && time.instant <= now
                }.map { release -> release.number.value }
                .maxOrNull()

        val finishedKnownTotal =
            if (candidate.media.publicationStatus == MediaPublicationStatus.FINISHED) {
                knownTotal?.value
            } else {
                null
            }

        val releasedUnit = releasedThrough ?: finishedKnownTotal ?: return null
        val availableThrough = min(releasedUnit, knownTotal?.value ?: releasedUnit)
        val availableUnitCount = availableThrough - entry.progress.value
        if (availableUnitCount <= 0) return null

        return ReadyTitle(
            candidate = candidate,
            nextUnit = UnitNumber(entry.progress.value + 1),
            availableUnitCount = availableUnitCount,
        )
    }
}
