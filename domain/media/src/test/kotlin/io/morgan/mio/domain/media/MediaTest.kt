package io.morgan.mio.domain.media

import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MediaTest {
    @Test
    fun mediaAcceptsMissingOptionalMetadata() {
        val media = media()

        assertEquals(null, media.format)
        assertEquals(null, media.synopsis)
        assertEquals(Artwork(null, null), media.artwork)
        assertEquals(MediaTotals(null, null, null), media.totals)
    }

    @Test
    fun mediaRejectsBlankSynopsisAndGenres() {
        assertThrows<IllegalArgumentException> { media(synopsis = " ") }
        assertThrows<IllegalArgumentException> { media(genres = listOf("Action", "\t")) }
    }

    @Test
    fun mediaRejectsDuplicateGenresAndRelatedIds() {
        assertThrows<IllegalArgumentException> { media(genres = listOf("Action", "Action")) }
        assertThrows<IllegalArgumentException> {
            media(relatedMediaIds = listOf(MediaId("related"), MediaId("related")))
        }
    }

    @Test
    fun mediaCannotRelateToItself() {
        assertThrows<IllegalArgumentException> {
            media(relatedMediaIds = listOf(MediaId("media-1")))
        }
    }

    @Test
    fun mediaSnapshotsCollectionInputs() {
        val genres = mutableListOf("Action")
        val relatedMediaIds = mutableListOf(MediaId("related"))
        val media = media(genres = genres, relatedMediaIds = relatedMediaIds)

        genres.clear()
        relatedMediaIds.clear()

        assertEquals(listOf("Action"), media.genres)
        assertEquals(listOf(MediaId("related")), media.relatedMediaIds)
        assertEquals(media, media(genres = listOf("Action"), relatedMediaIds = listOf(MediaId("related"))))
    }

    private fun media(
        synopsis: String? = null,
        genres: List<String> = emptyList(),
        relatedMediaIds: List<MediaId> = emptyList(),
    ): Media =
        Media(
            id = MediaId("media-1"),
            type = MediaType.ANIME,
            title = MediaTitle("Title"),
            format = null,
            publicationStatus = MediaPublicationStatus.UNKNOWN,
            synopsis = synopsis,
            artwork = Artwork(null, null),
            totals = MediaTotals(null, null, null),
            genres = genres,
            relatedMediaIds = relatedMediaIds,
        )
}
