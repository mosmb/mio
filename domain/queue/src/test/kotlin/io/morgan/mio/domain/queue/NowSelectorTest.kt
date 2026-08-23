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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class NowSelectorTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val accountId = AccountId("account-1")

    @Test
    fun onlyCurrentEntriesWithTrustworthyReleasedUnitsAreEligible() {
        val statuses =
            listOf(
                CollectionStatus.PLANNED,
                CollectionStatus.PAUSED,
                CollectionStatus.COMPLETED,
                CollectionStatus.DROPPED,
                CollectionStatus.REPEATING,
            )

        statuses.forEach { status ->
            assertNull(
                select(
                    entry = entry(status = status),
                    releases = listOf(release(number = 2, time = ReleaseTime.Trusted(now))),
                ),
                "status $status must not be eligible",
            )
        }

        val selected =
            select(
                entry = entry(progress = 1),
                releases = listOf(release(number = 2, time = ReleaseTime.Trusted(now))),
            )

        assertEquals(2, selected?.nextUnit?.value)
        assertEquals(1, selected?.availableUnitCount)
    }

    @Test
    fun unknownAndUntrustedOrFutureReleasesDoNotClaimReadiness() {
        listOf(
            ReleaseTime.Unknown,
            ReleaseTime.Untrusted(now),
            ReleaseTime.Trusted(Instant.parse("2026-08-23T12:00:01Z")),
        ).forEach { time ->
            assertNull(select(entry = entry(), releases = listOf(release(time = time))))
        }
        assertNull(
            select(
                entry = entry(progressUnit = ProgressUnit.CHAPTER),
                releases = listOf(release(unit = ProgressUnit.EPISODE)),
            ),
        )
        assertNull(select(entry = entry(progress = 2), releases = listOf(release(number = 2))))
        assertNull(select(entry = entry(progress = 2), releases = listOf(release(number = 1))))
    }

    @Test
    fun everyProviderNormalizedProgressUnitUsesTheMatchingReleaseUnit() {
        ProgressUnit.entries.forEach { unit ->
            val recommendation =
                select(
                    entry = entry(progressUnit = unit),
                    releases = listOf(release(unit = unit)),
                )

            assertEquals(unit, recommendation?.progressUnit)
            assertEquals(2, recommendation?.nextUnit?.value)
        }
    }

    @Test
    fun nextUnitIsProgressPlusOneAndReleaseGapsCountTheNumberedSequence() {
        val selected =
            select(
                entry = entry(progress = 7, total = UnitTotal(12)),
                releases = listOf(release(number = 10)),
            )

        assertEquals(8, selected?.nextUnit?.value)
        assertEquals(3, selected?.availableUnitCount)
        assertEquals(UnitTotal(12), selected?.total)
    }

    @Test
    fun finishedKnownTotalProvidesFallbackButOtherStatusesNeedARelease() {
        val finished =
            select(
                entry = entry(progress = 7, total = UnitTotal(12)),
                media = media(publicationStatus = MediaPublicationStatus.FINISHED),
            )
        assertEquals(5, finished?.availableUnitCount)

        MediaPublicationStatus.entries
            .filterNot { status -> status == MediaPublicationStatus.FINISHED }
            .forEach { status ->
                assertNull(
                    select(
                        entry = entry(progress = 7, total = UnitTotal(12)),
                        media = media(publicationStatus = status),
                    ),
                    "publication status $status must not use the total fallback",
                )
            }
    }

    @Test
    fun knownTotalsCapReleaseEvidenceAndStaleTotalsAreRejected() {
        val capped =
            select(
                entry = entry(progress = 7, total = UnitTotal(9)),
                releases = listOf(release(number = 12)),
            )
        assertEquals(2, capped?.availableUnitCount)

        assertNull(
            select(
                entry = entry(progress = 7, total = UnitTotal(7)),
                releases = listOf(release(number = 8)),
            ),
        )
        assertNull(
            select(
                entry = entry(progress = Int.MAX_VALUE),
                releases = listOf(release(number = Int.MAX_VALUE)),
            ),
        )
    }

    @Test
    fun unknownTotalUsesReleaseEvidenceWithoutAnInventedTotal() {
        val selected =
            select(
                entry = entry(progress = 41, total = null, progressUnit = ProgressUnit.CHAPTER),
                releases = listOf(release(number = 44, unit = ProgressUnit.CHAPTER)),
            )

        assertEquals(42, selected?.nextUnit?.value)
        assertEquals(3, selected?.availableUnitCount)
        assertNull(selected?.total)
    }

    @Test
    fun mostRecentlyUpdatedWinsAndEqualTimestampsUseStableAscendingIds() {
        val older = entry(id = "entry-a", mediaId = "media-a", updatedAt = Instant.parse("2026-08-23T10:00:00Z"))
        val newer = entry(id = "entry-b", mediaId = "media-b", updatedAt = Instant.parse("2026-08-23T11:00:00Z"))
        val selectedNewer =
            NowSelector.select(
                candidates = listOf(candidate(older), candidate(newer)),
                now = now,
            )
        assertEquals(MediaId("media-b"), selectedNewer?.mediaId)

        val tieA = entry(id = "entry-z", mediaId = "media-a")
        val tieB = entry(id = "entry-a", mediaId = "media-b")
        val forward = NowSelector.select(listOf(candidate(tieA), candidate(tieB)), now)
        val reversed = NowSelector.select(listOf(candidate(tieB), candidate(tieA)), now)
        assertEquals(MediaId("media-a"), forward?.mediaId)
        assertEquals(forward, reversed)

        val sameMediaLaterId = entry(id = "entry-z", mediaId = "same-media", progress = 0)
        val sameMediaEarlierId = entry(id = "entry-a", mediaId = "same-media", progress = 1)
        val sameMediaSelection =
            NowSelector.select(
                candidates =
                    listOf(
                        candidate(sameMediaLaterId, releases = listOf(release("same-media", number = 2))),
                        candidate(sameMediaEarlierId, releases = listOf(release("same-media", number = 2))),
                    ),
                now = now,
            )
        assertEquals(ProgressValue(1), sameMediaSelection?.progress)
    }

    @Test
    fun duplicateReleasesMissingArtworkAndInputPermutationsDoNotChangeSelection() {
        val candidates =
            listOf(
                candidate(
                    entry(id = "entry-c", mediaId = "media-c"),
                    releases =
                        listOf(
                            release(mediaId = "media-c", number = 2),
                            release(mediaId = "media-c", number = 2),
                        ),
                    media = media(id = "media-c", artwork = Artwork(null, null)),
                ),
                candidate(entry(id = "entry-a", mediaId = "media-a")),
                candidate(entry(id = "entry-b", mediaId = "media-b")),
            )
        val expected = NowSelector.select(candidates, now)

        listOf(
            candidates,
            candidates.reversed(),
            listOf(candidates[1], candidates[2], candidates[0]),
            listOf(candidates[2], candidates[0], candidates[1]),
        ).forEach { order -> assertEquals(expected, NowSelector.select(order, now)) }
        assertEquals(MediaId("media-a"), expected?.mediaId)
    }

    @Test
    fun candidateCopiesReleasesAndRejectsForeignMediaIds() {
        val releases = mutableListOf(release())
        val candidate = candidate(entry(), releases = releases)
        releases.clear()
        assertTrue(candidate.releases.isNotEmpty())

        assertThrows(IllegalArgumentException::class.java) {
            NowCandidate(
                entry = entry(mediaId = "media-1"),
                media = media(id = "media-2"),
                releases = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NowCandidate(
                entry = entry(mediaId = "media-1"),
                media = media(id = "media-1"),
                releases = listOf(release(mediaId = "media-2")),
            )
        }
    }

    @Test
    fun recommendationInvariantsRejectInvalidProgressAndCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            NowRecommendation(
                mediaId = MediaId("media-1"),
                mediaType = MediaType.ANIME,
                title = MediaTitle("Title"),
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
                mediaId = MediaId("media-1"),
                mediaType = MediaType.ANIME,
                title = MediaTitle("Title"),
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

    private fun select(
        entry: CollectionEntry,
        media: Media = media(entry.mediaId.value),
        releases: List<Release> = emptyList(),
    ): NowRecommendation? = NowSelector.select(listOf(NowCandidate(entry, media, releases)), now)

    private fun candidate(
        entry: CollectionEntry,
        releases: List<Release> = listOf(release(mediaId = entry.mediaId.value)),
        media: Media = media(entry.mediaId.value),
    ): NowCandidate = NowCandidate(entry, media, releases)

    private fun entry(
        id: String = "entry-1",
        mediaId: String = "media-1",
        status: CollectionStatus = CollectionStatus.CURRENT,
        progress: Int = 1,
        progressUnit: ProgressUnit = ProgressUnit.EPISODE,
        total: UnitTotal? = UnitTotal(12),
        updatedAt: Instant = Instant.parse("2026-08-23T11:00:00Z"),
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId(id),
            accountId = accountId,
            mediaId = MediaId(mediaId),
            status = status,
            progress = ProgressValue(progress),
            progressUnit = progressUnit,
            total = total,
            updatedAt = updatedAt,
        )

    private fun media(
        id: String = "media-1",
        publicationStatus: MediaPublicationStatus = MediaPublicationStatus.RELEASING,
        artwork: Artwork = Artwork(ImageReference("poster://$id"), null),
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
        time: ReleaseTime = ReleaseTime.Trusted(now),
    ): Release = Release(MediaId(mediaId), unit, UnitNumber(number), time)
}
