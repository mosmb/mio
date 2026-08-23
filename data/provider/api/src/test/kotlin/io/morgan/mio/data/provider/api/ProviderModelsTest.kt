package io.morgan.mio.data.provider.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Instant

class ProviderModelsTest {
    private val instant = Instant.parse("2026-01-01T00:00:00Z")
    private val mediaId = ProviderMediaId("media")

    @Test
    fun totalsAndReleaseNumbersMustBePositive() {
        assertThrows<IllegalArgumentException> { ProviderMediaTotals(0, null, null) }
        assertThrows<IllegalArgumentException> { ProviderMediaTotals(null, -1, null) }
        assertThrows<IllegalArgumentException> {
            ProviderRelease(mediaId, ProviderProgressUnit.EPISODE, 0, ProviderReleaseTime.Unknown)
        }
    }

    @Test
    fun mediaRejectsBlankMetadataDuplicatesSelfRelationsAndWrongReleaseOwner() {
        assertThrows<IllegalArgumentException> { media(title = " ") }
        assertThrows<IllegalArgumentException> { media(synopsis = " ") }
        assertThrows<IllegalArgumentException> { media(genres = listOf("Drama", "Drama")) }
        assertThrows<IllegalArgumentException> {
            media(relatedMediaIds = listOf(ProviderMediaId("related"), ProviderMediaId("related")))
        }
        assertThrows<IllegalArgumentException> { media(releaseYear = 0) }
        assertThrows<IllegalArgumentException> { media(scorePercent = 0) }
        assertThrows<IllegalArgumentException> { media(scorePercent = 101) }
        assertThrows<IllegalArgumentException> { media(relatedMediaIds = listOf(mediaId)) }
        assertThrows<IllegalArgumentException> {
            media(
                releases =
                    listOf(
                        ProviderRelease(mediaId, ProviderProgressUnit.EPISODE, 1, ProviderReleaseTime.Unknown),
                        ProviderRelease(mediaId, ProviderProgressUnit.EPISODE, 1, ProviderReleaseTime.Unknown),
                    ),
            )
        }
        assertThrows<IllegalArgumentException> {
            media(
                releases =
                    listOf(
                        ProviderRelease(
                            ProviderMediaId("other"),
                            ProviderProgressUnit.EPISODE,
                            1,
                            ProviderReleaseTime.Unknown,
                        ),
                    ),
            )
        }
    }

    @Test
    fun listInputsAreDefensivelyCopied() {
        val genres = mutableListOf("Drama")
        val related = mutableListOf(ProviderMediaId("related"))
        val releases = mutableListOf<ProviderRelease>()
        val media = media(genres = genres, relatedMediaIds = related, releases = releases)

        genres += "Action"
        related.clear()
        releases += ProviderRelease(mediaId, ProviderProgressUnit.EPISODE, 2, ProviderReleaseTime.Unknown)

        assertEquals(listOf("Drama"), media.genres)
        assertEquals(listOf(ProviderMediaId("related")), media.relatedMediaIds)
        assertEquals(emptyList<ProviderRelease>(), media.releases)
    }

    @Test
    fun collectionEntriesRejectInvalidProgressAndTotals() {
        assertThrows<IllegalArgumentException> { entry(progress = -1) }
        assertThrows<IllegalArgumentException> { entry(total = 0) }
        assertThrows<IllegalArgumentException> { entry(progress = 3, total = 2) }
    }

    @Test
    fun collectionStatusHasExactlySixConstants() {
        assertEquals(
            listOf(
                ProviderCollectionStatus.CURRENT,
                ProviderCollectionStatus.PLANNED,
                ProviderCollectionStatus.PAUSED,
                ProviderCollectionStatus.COMPLETED,
                ProviderCollectionStatus.DROPPED,
                ProviderCollectionStatus.REPEATING,
            ),
            ProviderCollectionStatus.entries,
        )
    }

    @Test
    fun releaseTimeTrustStatesRemainDistinct() {
        val trusted = ProviderReleaseTime.Trusted(instant)
        val untrusted = ProviderReleaseTime.Untrusted(instant)

        assertEquals(ProviderReleaseTime.Unknown, ProviderReleaseTime.Unknown)
        assertEquals(instant, trusted.instant)
        assertEquals(instant, untrusted.instant)
        assertEquals(false, trusted == untrusted)
    }

    private fun media(
        title: String = "Title",
        synopsis: String? = null,
        genres: List<String> = emptyList(),
        relatedMediaIds: List<ProviderMediaId> = emptyList(),
        releases: List<ProviderRelease> = emptyList(),
        releaseYear: Int? = null,
        scorePercent: Int? = null,
    ): ProviderMedia =
        ProviderMedia(
            id = mediaId,
            type = ProviderMediaType.ANIME,
            title = title,
            format = ProviderMediaFormat.TV,
            publicationStatus = ProviderMediaPublicationStatus.UNKNOWN,
            synopsis = synopsis,
            poster = null,
            banner = null,
            totals = ProviderMediaTotals(null, null, null),
            genres = genres,
            relatedMediaIds = relatedMediaIds,
            releases = releases,
            releaseYear = releaseYear,
            scorePercent = scorePercent,
        )

    private fun entry(
        progress: Int = 0,
        total: Int? = null,
    ): ProviderCollectionEntry =
        ProviderCollectionEntry(
            id = ProviderCollectionEntryId("entry"),
            accountId = ProviderAccountId("account"),
            mediaId = mediaId,
            status = ProviderCollectionStatus.CURRENT,
            progress = progress,
            progressUnit = ProviderProgressUnit.EPISODE,
            total = total,
            updatedAt = instant,
        )
}
