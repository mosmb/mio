package io.morgan.mio.data.provider.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Instant

class CompleteProviderImportTest {
    private val accountId = ProviderAccountId("account")
    private val mediaId = ProviderMediaId("media")
    private val instant = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun emptyCollectionIsAValidCompleteSnapshot() {
        val snapshot = CompleteProviderImport(ProviderKey.ANILIST, accountId, emptyList(), emptyList())

        assertEquals(emptyList<ProviderMedia>(), snapshot.media)
        assertEquals(emptyList<ProviderCollectionEntry>(), snapshot.entries)
    }

    @Test
    fun completeImportCopiesListsAndAllowsRelatedMediaWithoutEntry() {
        val relatedMediaId = ProviderMediaId("related")
        val owner = media(id = mediaId, relatedMediaIds = listOf(relatedMediaId))
        val related = media(id = relatedMediaId)
        val media = mutableListOf(owner, related)
        val entries = mutableListOf(entry(mediaId = mediaId))
        val snapshot = CompleteProviderImport(ProviderKey.ANILIST, accountId, media, entries)

        media.clear()
        entries.clear()

        assertEquals(listOf(owner, related), snapshot.media)
        assertEquals(listOf(ProviderCollectionEntryId("entry")), snapshot.entries.map { entry -> entry.id })
    }

    @Test
    fun rejectsDuplicateIdsDuplicateMediaEntriesWrongAccountAndMissingMedia() {
        assertThrows<IllegalArgumentException> {
            CompleteProviderImport(
                ProviderKey.ANILIST,
                accountId,
                listOf(media(), media()),
                listOf(entry()),
            )
        }
        assertThrows<IllegalArgumentException> {
            CompleteProviderImport(
                ProviderKey.ANILIST,
                accountId,
                listOf(media()),
                listOf(entry(), entry(id = "entry-2")),
            )
        }
        assertThrows<IllegalArgumentException> {
            CompleteProviderImport(
                ProviderKey.ANILIST,
                accountId,
                listOf(media()),
                listOf(entry(account = ProviderAccountId("other"))),
            )
        }
        assertThrows<IllegalArgumentException> {
            CompleteProviderImport(
                ProviderKey.ANILIST,
                accountId,
                listOf(media()),
                listOf(entry(mediaId = ProviderMediaId("missing"))),
            )
        }
        assertThrows<IllegalArgumentException> {
            CompleteProviderImport(
                ProviderKey.ANILIST,
                accountId,
                listOf(media(id = ProviderMediaId("unlinked"))),
                emptyList(),
            )
        }
    }

    private fun media(
        id: ProviderMediaId = mediaId,
        relatedMediaIds: List<ProviderMediaId> = emptyList(),
    ): ProviderMedia =
        ProviderMedia(
            id = id,
            type = ProviderMediaType.ANIME,
            title = "Title ${id.value}",
            format = null,
            publicationStatus = ProviderMediaPublicationStatus.UNKNOWN,
            synopsis = null,
            poster = null,
            banner = null,
            totals = ProviderMediaTotals(null, null, null),
            genres = emptyList(),
            relatedMediaIds = relatedMediaIds,
            releases = emptyList(),
        )

    private fun entry(
        id: String = "entry",
        account: ProviderAccountId = accountId,
        mediaId: ProviderMediaId = this.mediaId,
    ): ProviderCollectionEntry =
        ProviderCollectionEntry(
            id = ProviderCollectionEntryId(id),
            accountId = account,
            mediaId = mediaId,
            status = ProviderCollectionStatus.CURRENT,
            progress = 0,
            progressUnit = ProviderProgressUnit.EPISODE,
            total = null,
            updatedAt = instant,
        )
}
