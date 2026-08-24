package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TodayQueueSelectorTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val accountId = AccountId("account-1")

    @Test
    fun readyCalculationPreservesTask17StatusAndTrustRules() {
        val excludedStatuses =
            listOf(
                CollectionStatus.PLANNED,
                CollectionStatus.PAUSED,
                CollectionStatus.COMPLETED,
                CollectionStatus.DROPPED,
                CollectionStatus.REPEATING,
            )
        excludedStatuses.forEach { status ->
            assertNull(
                ReadyCalculator.calculate(
                    candidate(
                        entry(status = status, mediaId = status.name),
                        mediaId = status.name,
                        releases = listOf(release(status.name)),
                    ),
                    now,
                ),
            )
        }

        val current =
            ReadyCalculator.calculate(
                candidate(entry(progress = 7), releases = listOf(release(number = 10))),
                now,
            )
        assertEquals(UnitNumber(8), current?.nextUnit)
        assertEquals(3, current?.availableUnitCount)

        assertNull(
            ReadyCalculator.calculate(
                candidate(
                    entry(progress = 1),
                    releases = listOf(release(time = ReleaseTime.Unknown)),
                ),
                now,
            ),
        )
        assertNull(
            ReadyCalculator.calculate(
                candidate(
                    entry(progress = 1),
                    releases = listOf(release(time = ReleaseTime.Untrusted(now))),
                ),
                now,
            ),
        )
        assertNull(
            ReadyCalculator.calculate(
                candidate(
                    entry(progress = 1),
                    releases = listOf(release(time = ReleaseTime.Trusted(now + 1.hours))),
                ),
                now,
            ),
        )
        assertNull(
            ReadyCalculator.calculate(
                candidate(
                    entry(progress = 1, progressUnit = ProgressUnit.CHAPTER),
                    releases = listOf(release(unit = ProgressUnit.EPISODE)),
                ),
                now,
            ),
        )
    }

    @Test
    fun finishedKnownTotalIsTheOnlyReleaseFreeFallback() {
        val finished =
            ReadyCalculator.calculate(
                candidate(
                    entry(progress = 7, total = UnitTotal(12)),
                    media = media(publicationStatus = MediaPublicationStatus.FINISHED),
                ),
                now,
            )
        assertEquals(5, finished?.availableUnitCount)

        MediaPublicationStatus.entries
            .filterNot { status -> status == MediaPublicationStatus.FINISHED }
            .forEach { status ->
                assertNull(
                    ReadyCalculator.calculate(
                        candidate(
                            entry(progress = 7, total = UnitTotal(12)),
                            media = media(publicationStatus = status),
                        ),
                        now,
                    ),
                )
            }
    }

    @Test
    fun knownTotalsCapEvidenceAndInvalidProgressIsRejected() {
        val capped =
            ReadyCalculator.calculate(
                candidate(
                    entry(progress = 7, total = UnitTotal(9)),
                    releases = listOf(release(number = 12)),
                ),
                now,
            )
        assertEquals(2, capped?.availableUnitCount)
        assertNull(
            ReadyCalculator.calculate(
                candidate(entry(progress = 7, total = UnitTotal(7)), releases = listOf(release(number = 8))),
                now,
            ),
        )
        assertNull(
            ReadyCalculator.calculate(
                candidate(
                    entry(progress = Int.MAX_VALUE, total = null),
                    releases = listOf(release(number = Int.MAX_VALUE)),
                ),
                now,
            ),
        )
    }

    @Test
    fun acceptedNowRulesRemainCoveredForEveryUnitBoundsUnknownTotalsAndMissingArtwork() {
        ProgressUnit.entries.forEach { unit ->
            val selected =
                NowSelector.select(
                    listOf(
                        ready(
                            candidate(
                                entry(mediaId = unit.name, progressUnit = unit),
                                mediaId = unit.name,
                                releases = listOf(release(mediaId = unit.name, unit = unit)),
                            ),
                        ),
                    ),
                )
            assertEquals(unit, selected?.progressUnit)
            assertEquals(UnitNumber(2), selected?.nextUnit)
        }

        listOf(1, 2).forEach { releasedNumber ->
            assertNull(
                ReadyCalculator.calculate(
                    candidate(
                        entry(progress = 2),
                        releases = listOf(release(number = releasedNumber)),
                    ),
                    now,
                ),
            )
        }

        val unknownTotal =
            NowSelector.select(
                listOf(
                    ready(
                        candidate(
                            entry(progress = 41, progressUnit = ProgressUnit.CHAPTER, total = null),
                            releases = listOf(release(unit = ProgressUnit.CHAPTER, number = 44)),
                        ),
                    ),
                ),
            )
        assertEquals(UnitNumber(42), unknownTotal?.nextUnit)
        assertEquals(3, unknownTotal?.availableUnitCount)
        assertNull(unknownTotal?.total)

        val missingArtwork =
            NowSelector.select(
                listOf(
                    ready(
                        candidate(
                            entry(mediaId = "missing-art"),
                            mediaId = "missing-art",
                            media = media(id = "missing-art", artwork = Artwork(null, null)),
                            releases =
                                listOf(
                                    release(mediaId = "missing-art"),
                                    release(mediaId = "missing-art"),
                                ),
                        ),
                    ),
                ),
            )
        assertNull(missingArtwork?.poster)
        assertNull(missingArtwork?.banner)
        assertEquals(1, missingArtwork?.availableUnitCount)
    }

    @Test
    fun acceptedNowTieBreaksAndRecommendationInvariantsRemainCovered() {
        val older =
            ready(
                candidate(
                    entry(id = "older", mediaId = "older", updatedAt = "2026-08-23T10:00:00Z"),
                    mediaId = "older",
                    releases = listOf(release(mediaId = "older")),
                ),
            )
        val newer =
            ready(
                candidate(
                    entry(id = "newer", mediaId = "newer", updatedAt = "2026-08-23T11:00:00Z"),
                    mediaId = "newer",
                    releases = listOf(release(mediaId = "newer")),
                ),
            )
        assertEquals(MediaId("newer"), NowSelector.select(listOf(older, newer))?.mediaId)

        val tieA =
            ready(
                candidate(
                    entry(id = "entry-z", mediaId = "media-a"),
                    mediaId = "media-a",
                    releases = listOf(release(mediaId = "media-a")),
                ),
            )
        val tieB =
            ready(
                candidate(
                    entry(id = "entry-a", mediaId = "media-b"),
                    mediaId = "media-b",
                    releases = listOf(release(mediaId = "media-b")),
                ),
            )
        assertEquals(NowSelector.select(listOf(tieA, tieB)), NowSelector.select(listOf(tieB, tieA)))
        assertEquals(MediaId("media-a"), NowSelector.select(listOf(tieA, tieB))?.mediaId)

        assertThrows(IllegalArgumentException::class.java) {
            NowRecommendation(
                mediaId = MediaId("invalid-next"),
                mediaType = MediaType.ANIME,
                title = MediaTitle("Invalid next"),
                poster = null,
                banner = null,
                progress = ProgressValue(3),
                progressUnit = ProgressUnit.EPISODE,
                total = UnitTotal(10),
                nextUnit = UnitNumber(5),
                availableUnitCount = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NowRecommendation(
                mediaId = MediaId("invalid-count"),
                mediaType = MediaType.ANIME,
                title = MediaTitle("Invalid count"),
                poster = null,
                banner = null,
                progress = ProgressValue(3),
                progressUnit = ProgressUnit.EPISODE,
                total = UnitTotal(10),
                nextUnit = UnitNumber(4),
                availableUnitCount = 0,
            )
        }
    }

    @Test
    fun exactNextUnitControlsNewAndSevenDayBoundaryIsInclusive() {
        val exactNow =
            ready(
                candidate(
                    entry(mediaId = "exact-now"),
                    mediaId = "exact-now",
                    releases = listOf(release(mediaId = "exact-now", number = 2, time = now)),
                ),
            )
        assertEquals(
            Duration.ZERO,
            NewSelector.select(listOf(exactNow), MediaId("other"), now).single().releaseAge,
        )

        val exactBoundary =
            ready(
                candidate(
                    entry(mediaId = "boundary"),
                    mediaId = "boundary",
                    releases = listOf(release(mediaId = "boundary", number = 2, time = now - 7.days)),
                ),
            )
        val recommendation =
            NewSelector
                .select(
                    readyTitles = listOf(exactBoundary),
                    excludedMediaId = MediaId("other"),
                    now = now,
                ).single()
        assertEquals(7.days, recommendation.releaseAge)

        val stale =
            ready(
                candidate(
                    entry(mediaId = "stale"),
                    mediaId = "stale",
                    releases = listOf(release(mediaId = "stale", number = 2, time = now - 7.days - 1.hours)),
                ),
            )
        assertTrue(
            NewSelector
                .select(listOf(stale), MediaId("other"), now)
                .isEmpty(),
        )

        val missingExactNext =
            ready(
                candidate(
                    entry(mediaId = "gap"),
                    mediaId = "gap",
                    releases = listOf(release(mediaId = "gap", number = 3, time = now)),
                ),
            )
        assertTrue(NewSelector.select(listOf(missingExactNext), MediaId("other"), now).isEmpty())

        val hostileExactTimes =
            listOf(
                "future" to ReleaseTime.Trusted(now + 1.hours),
                "unknown" to ReleaseTime.Unknown,
                "untrusted" to ReleaseTime.Untrusted(now),
            )
        hostileExactTimes.forEach { (id, exactTime) ->
            val candidate =
                ready(
                    candidate(
                        entry(mediaId = id),
                        mediaId = id,
                        releases =
                            listOf(
                                release(mediaId = id, number = 2, time = exactTime),
                                release(mediaId = id, number = 3, time = now),
                            ),
                    ),
                )
            assertTrue(NewSelector.select(listOf(candidate), MediaId("other"), now).isEmpty(), id)
        }

        val wrongUnitExact =
            ready(
                candidate(
                    entry(mediaId = "wrong-unit"),
                    mediaId = "wrong-unit",
                    releases =
                        listOf(
                            release(
                                mediaId = "wrong-unit",
                                unit = ProgressUnit.CHAPTER,
                                number = 2,
                                time = now,
                            ),
                            release(mediaId = "wrong-unit", number = 3, time = now),
                        ),
                ),
            )
        assertTrue(NewSelector.select(listOf(wrongUnitExact), MediaId("other"), now).isEmpty())

        val oldNextWithRecentLaterUnit =
            ready(
                candidate(
                    entry(mediaId = "old-next"),
                    mediaId = "old-next",
                    releases =
                        listOf(
                            release(mediaId = "old-next", number = 2, time = now - 8.days),
                            release(mediaId = "old-next", number = 3, time = now),
                        ),
                ),
            )
        assertTrue(NewSelector.select(listOf(oldNextWithRecentLaterUnit), MediaId("other"), now).isEmpty())

        val duplicateOld =
            ready(
                candidate(
                    entry(mediaId = "duplicate"),
                    mediaId = "duplicate",
                    releases =
                        listOf(
                            release(mediaId = "duplicate", number = 2, time = now - 8.days),
                            release(mediaId = "duplicate", number = 2, time = now - 1.hours),
                        ),
                ),
            )
        assertTrue(NewSelector.select(listOf(duplicateOld), MediaId("other"), now).isEmpty())
    }

    @Test
    fun newSortsRecencyExcludesNowCapsAtThreeAndCountsTheRest() {
        val nowCandidate =
            candidate(
                entry(id = "now-entry", mediaId = "now", updatedAt = "2026-08-23T11:59:00Z"),
                mediaId = "now",
                releases = listOf(release(mediaId = "now", number = 2, time = now)),
            )
        val newCandidates =
            (1..4).map { index ->
                val id = "new-$index"
                candidate(
                    entry(id = "entry-$id", mediaId = id, updatedAt = "2026-08-23T10:0$index:00Z"),
                    mediaId = id,
                    releases = listOf(release(mediaId = id, number = 2, time = now - index.hours)),
                )
            }
        val backlog =
            candidate(
                entry(id = "backlog-entry", mediaId = "backlog"),
                mediaId = "backlog",
                releases = listOf(release(mediaId = "backlog", number = 3, time = now)),
            )

        val queue = TodayQueueSelector.select(listOf(nowCandidate, *newCandidates.toTypedArray(), backlog), now)
        val reversedQueue =
            TodayQueueSelector.select((listOf(nowCandidate) + newCandidates + backlog).reversed(), now)

        assertEquals(queue, reversedQueue)
        assertEquals(MediaId("now"), queue.now?.mediaId)
        assertEquals(
            listOf(MediaId("new-1"), MediaId("new-2"), MediaId("new-3")),
            queue.newRecommendations.map { it.mediaId },
        )
        assertEquals(2, queue.remainingReadyCount)
        assertTrue(queue.newRecommendations.none { recommendation -> recommendation.mediaId == queue.now?.mediaId })
        assertTrue(queue.caughtUp == null)
    }

    @Test
    fun nowPriorityAndDuplicateMediaSelectionAreDeterministic() {
        val duplicateOlder =
            candidate(
                entry(id = "entry-z", mediaId = "same", updatedAt = "2026-08-23T10:00:00Z", progress = 0),
                mediaId = "same",
                releases = listOf(release(mediaId = "same", number = 2)),
            )
        val duplicateNewer =
            candidate(
                entry(id = "entry-a", mediaId = "same", updatedAt = "2026-08-23T11:00:00Z", progress = 1),
                mediaId = "same",
                releases = listOf(release(mediaId = "same", number = 2)),
            )
        val other =
            candidate(
                entry(id = "entry-b", mediaId = "other", updatedAt = "2026-08-23T11:00:00Z"),
                mediaId = "other",
                releases = listOf(release(mediaId = "other", number = 2)),
            )

        val first = TodayQueueSelector.select(listOf(duplicateOlder, duplicateNewer, other), now)
        val reversed = TodayQueueSelector.select(listOf(other, duplicateNewer, duplicateOlder), now)

        assertEquals(first, reversed)
        assertEquals(MediaId("other"), first.now?.mediaId)
        assertEquals(ProgressValue(1), first.newRecommendations.firstOrNull()?.progress ?: first.now?.progress)
        assertEquals(1, first.now?.availableUnitCount)
    }

    @Test
    fun caughtUpSelectsDistinctRecentArtworkAndEarliestTrustworthyFutureRelease() {
        val artworkCandidates =
            (1..8).map { index ->
                candidate(
                    entry(
                        id = "entry-art-$index",
                        mediaId = "art-$index",
                        status = if (index == 8) CollectionStatus.COMPLETED else CollectionStatus.CURRENT,
                        updatedAt = "2026-08-${(23 - index).toString().padStart(2, '0')}T12:00:00Z",
                    ),
                    mediaId = "art-$index",
                    media =
                        media(
                            id = "art-$index",
                            artwork =
                                if (index == 1) {
                                    Artwork(null, ImageReference("banner://art-1"))
                                } else {
                                    Artwork(ImageReference("poster://art-$index"), null)
                                },
                        ),
                    releases = emptyList(),
                )
            }
        val future =
            candidate(
                entry(id = "future-entry", mediaId = "future", status = CollectionStatus.PLANNED),
                mediaId = "future",
                releases = listOf(release(mediaId = "future", number = 3, time = now + 2.hours)),
            )
        val laterFuture =
            candidate(
                entry(id = "later-entry", mediaId = "later", status = CollectionStatus.PAUSED),
                mediaId = "later",
                releases = listOf(release(mediaId = "later", number = 2, time = now + 3.hours)),
            )
        val tiedFuture =
            candidate(
                entry(id = "tied-entry", mediaId = "aaa-future", status = CollectionStatus.PAUSED),
                mediaId = "aaa-future",
                releases = listOf(release(mediaId = "aaa-future", number = 4, time = now + 2.hours)),
            )
        val excluded =
            candidate(
                entry(id = "dropped-entry", mediaId = "dropped", status = CollectionStatus.DROPPED),
                mediaId = "dropped",
                releases = listOf(release(mediaId = "dropped", number = 2, time = now + 1.hours)),
            )

        val inputs = artworkCandidates + future + laterFuture + tiedFuture + excluded
        val queue = TodayQueueSelector.select(inputs, now)
        val reversedQueue = TodayQueueSelector.select(inputs.reversed(), now)
        val receipt = requireNotNull(queue.caughtUp)

        assertEquals(queue, reversedQueue)
        assertNull(queue.now)
        assertTrue(queue.newRecommendations.isEmpty())
        assertEquals(6, receipt.artwork.size)
        assertEquals(MediaId("art-1"), receipt.artwork.first().mediaId)
        assertEquals(ImageReference("banner://art-1"), receipt.artwork.first().reference)
        assertEquals(MediaId("aaa-future"), receipt.nextRelease?.mediaId)
        assertEquals(UnitNumber(4), receipt.nextRelease?.unitNumber)
        assertEquals(2.hours, receipt.nextRelease?.waitUntilRelease)
        assertFalse(receipt.artwork.any { artwork -> artwork.mediaId == MediaId("dropped") })
    }

    @Test
    fun futureReleaseRejectsWrongStatusUnitNumberTotalAndTrust() {
        val statuses =
            listOf(
                CollectionStatus.COMPLETED,
                CollectionStatus.DROPPED,
                CollectionStatus.REPEATING,
            )
        statuses.forEach { status ->
            val queue =
                TodayQueueSelector.select(
                    listOf(
                        candidate(
                            entry(mediaId = status.name, status = status),
                            mediaId = status.name,
                            releases = listOf(release(mediaId = status.name, number = 2, time = now + 1.hours)),
                        ),
                    ),
                    now,
                )
            assertNull(queue.caughtUp?.nextRelease)
        }

        val invalid =
            candidate(
                entry(mediaId = "invalid", total = UnitTotal(2), progressUnit = ProgressUnit.CHAPTER),
                mediaId = "invalid",
                releases =
                    listOf(
                        release(mediaId = "invalid", unit = ProgressUnit.EPISODE, number = 2, time = now + 1.hours),
                        release(mediaId = "invalid", unit = ProgressUnit.CHAPTER, number = 1, time = now + 1.hours),
                        release(mediaId = "invalid", unit = ProgressUnit.CHAPTER, number = 3, time = now + 1.hours),
                        release(
                            mediaId = "invalid",
                            unit = ProgressUnit.CHAPTER,
                            number = 2,
                            time = ReleaseTime.Unknown,
                        ),
                    ),
            )
        assertNull(TodayQueueSelector.select(listOf(invalid), now).caughtUp?.nextRelease)
    }

    @Test
    fun candidateCopiesReleasesAndPublicQueueCopiesListsAndRejectsConflicts() {
        val releases = mutableListOf(release())
        val candidate = candidate(entry(), releases = releases)
        releases.clear()
        assertTrue(candidate.releases.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            TodayQueueCandidate(entry(mediaId = "one"), media(id = "two"), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodayQueueCandidate(entry(mediaId = "one"), media(id = "one"), listOf(release(mediaId = "two")))
        }

        val nowRecommendation =
            NowRecommendation(
                mediaId = MediaId("now"),
                mediaType = MediaType.ANIME,
                title = MediaTitle("Now"),
                poster = null,
                banner = null,
                progress = ProgressValue(0),
                progressUnit = ProgressUnit.EPISODE,
                total = UnitTotal(2),
                nextUnit = UnitNumber(1),
                availableUnitCount = 1,
            )
        val newRecommendation =
            NewRecommendation(
                mediaId = MediaId("new"),
                mediaType = MediaType.MANGA,
                title = MediaTitle("New"),
                poster = null,
                progress = ProgressValue(0),
                progressUnit = ProgressUnit.CHAPTER,
                total = null,
                nextUnit = UnitNumber(1),
                availableUnitCount = 1,
                releaseAge = Duration.ZERO,
            )
        val mutable = mutableListOf(newRecommendation)
        val queue = TodayQueue(nowRecommendation, mutable, 0, null)
        mutable.clear()
        assertEquals(1, queue.newRecommendations.size)
        assertThrows(IllegalArgumentException::class.java) {
            TodayQueue(nowRecommendation, listOf(newRecommendation.copy(mediaId = MediaId("now"))), 0, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodayQueue(null, emptyList(), 0, null)
        }
        val cappedSummary =
            CaughtUpSummary(
                artwork =
                    (1..7).map { index ->
                        CaughtUpSummary.Artwork(MediaId("$index"), MediaTitle("$index"), null)
                    },
                nextRelease = null,
            )
        assertEquals(6, cappedSummary.artwork.size)

        val mutableArtwork =
            mutableListOf(
                CaughtUpSummary.Artwork(MediaId("artwork"), MediaTitle("Artwork"), null),
            )
        val copiedSummary = CaughtUpSummary(artwork = mutableArtwork, nextRelease = null)
        mutableArtwork.clear()
        assertEquals(1, copiedSummary.artwork.size)
    }

    private fun ready(candidate: TodayQueueCandidate): ReadyTitle =
        requireNotNull(ReadyCalculator.calculate(candidate, now))

    private fun candidate(
        entry: CollectionEntry,
        mediaId: String = entry.mediaId.value,
        media: Media = media(mediaId),
        releases: List<Release> = listOf(release(mediaId)),
    ): TodayQueueCandidate = TodayQueueCandidate(entry, media, releases)

    private fun entry(
        id: String = "entry-1",
        mediaId: String = "media-1",
        status: CollectionStatus = CollectionStatus.CURRENT,
        progress: Int = 1,
        progressUnit: ProgressUnit = ProgressUnit.EPISODE,
        total: UnitTotal? = UnitTotal(12),
        updatedAt: String = "2026-08-23T11:00:00Z",
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId(id),
            accountId = accountId,
            mediaId = MediaId(mediaId),
            status = status,
            progress = ProgressValue(progress),
            progressUnit = progressUnit,
            total = total,
            updatedAt = Instant.parse(updatedAt),
        )

    private fun media(
        id: String = "media-1",
        publicationStatus: MediaPublicationStatus = MediaPublicationStatus.RELEASING,
        artwork: Artwork = Artwork(ImageReference("poster://$id"), ImageReference("banner://$id")),
    ): Media =
        Media(
            id = MediaId(id),
            type = MediaType.ANIME,
            title = MediaTitle("Title $id"),
            format = MediaFormat.TV,
            publicationStatus = publicationStatus,
            synopsis = null,
            artwork = artwork,
            totals = MediaTotals(UnitTotal(12), null, null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
        )

    private fun release(
        mediaId: String = "media-1",
        unit: ProgressUnit = ProgressUnit.EPISODE,
        number: Int = 2,
        time: Instant,
    ): Release = release(mediaId, unit, number, ReleaseTime.Trusted(time))

    private fun release(
        mediaId: String = "media-1",
        unit: ProgressUnit = ProgressUnit.EPISODE,
        number: Int = 2,
        time: ReleaseTime = ReleaseTime.Trusted(now),
    ): Release = Release(MediaId(mediaId), unit, UnitNumber(number), time)
}
