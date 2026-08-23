package io.morgan.mio.data.provider.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProviderMediaDetailsTest {
    @Test
    fun validatesRootRelationIdentityAndPreservesOrder() {
        val first = media("first")
        val second = media("second")
        val root = media("root", related = listOf("first", "second"))
        val details = ProviderMediaDetails(ProviderKey.ANILIST, root, listOf(first, second))

        assertEquals(listOf(first, second), details.related)
    }

    @Test
    fun rejectsDuplicateSelfAndOutOfAgreementRelations() {
        val first = media("first")
        val root = media("root", related = listOf("first"))
        assertThrows<IllegalArgumentException> {
            ProviderMediaDetails(ProviderKey.ANILIST, root, listOf(first, first))
        }
        assertThrows<IllegalArgumentException> {
            ProviderMediaDetails(ProviderKey.ANILIST, root, listOf(root))
        }
        assertThrows<IllegalArgumentException> {
            ProviderMediaDetails(ProviderKey.ANILIST, media("root", related = emptyList()), listOf(first))
        }
    }

    @Test
    fun defensivelyCopiesRelatedInput() {
        val related = mutableListOf(media("first"))
        val details = ProviderMediaDetails(ProviderKey.ANILIST, media("root", related = listOf("first")), related)

        related.clear()

        assertEquals(1, details.related.size)
    }

    private fun media(
        id: String,
        related: List<String> = emptyList(),
    ): ProviderMedia =
        ProviderMedia(
            id = ProviderMediaId(id),
            type = ProviderMediaType.ANIME,
            title = id,
            format = ProviderMediaFormat.TV,
            publicationStatus = ProviderMediaPublicationStatus.UNKNOWN,
            synopsis = null,
            poster = null,
            banner = null,
            totals = ProviderMediaTotals(null, null, null),
            genres = emptyList(),
            relatedMediaIds = related.map(::ProviderMediaId),
            releases = emptyList(),
        )
}
