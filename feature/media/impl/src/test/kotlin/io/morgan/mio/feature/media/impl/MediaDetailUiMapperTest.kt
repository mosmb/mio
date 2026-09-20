package io.morgan.mio.feature.media.impl

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaScore
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class MediaDetailUiMapperTest {
    @Test
    fun mapsHtmlOptionalsTotalsCollectionAndOrderedRelatedModels() {
        val root = media("root", synopsis = "  A <b>bright</b> &amp; quiet story.  ", releaseYear = 2026, score = 87)
        val related = listOf(media("related-1", title = "Related title"), media("related-2", title = "Second title"))
        val collection =
            CollectionEntry(
                id = CollectionEntryId("entry-1"),
                accountId = AccountId("account-1"),
                mediaId = root.id,
                status = CollectionStatus.CURRENT,
                progress = ProgressValue(4),
                progressUnit = ProgressUnit.EPISODE,
                total = UnitTotal(12),
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )

        val state = MediaDetailUiMapper.mapContent(root, related, collection)

        assertEquals("A bright & quiet story.", state.media.synopsis)
        assertEquals(2026, state.media.releaseYear)
        assertEquals(87, state.media.scorePercent)
        assertEquals(listOf("related-1", "related-2"), state.relatedTitles.map { title -> title.id })
        assertEquals("Related title", state.relatedTitles.first().title)
        assertEquals(MediaDetailCollectionStatus.CURRENT, state.collection?.status)
        assertEquals(4, state.collection?.progress)
        assertEquals(12, state.collection?.total)
    }

    @Test
    fun relatedTitlesPreserveSeasonNamesAndMetadataForDisambiguation() {
        val titles =
            listOf(
                media("season-2", title = "My Hero Academia Season 2", releaseYear = 2017),
                media("season-3", title = "My Hero Academia Season 3", releaseYear = 2018),
                media("manga", title = "My Hero Academia", type = MediaType.MANGA, format = MediaFormat.MANGA),
            )
        val result = MediaDetailUiMapper.mapContent(media("root"), titles, null).relatedTitles
        assertEquals(
            listOf("My Hero Academia Season 2", "My Hero Academia Season 3", "My Hero Academia"),
            result.map {
                it.title
            },
        )
        assertEquals(listOf(2017, 2018, null), result.map { it.releaseYear })
        assertEquals(MediaDetailFormat.TV, result.first().format)
        assertEquals(12, result.first().episodes)
        assertEquals(MediaDetailMediaType.MANGA, result.last().type)
    }

    @Test
    fun absentOrBlankSynopsisAndOptionalArtworkRemainAbsent() {
        assertNull(MediaDetailUiMapper.mapSynopsis(null))
        assertNull(MediaDetailUiMapper.mapSynopsis("  "))

        val state =
            MediaDetailUiMapper.mapContent(
                media("root", synopsis = null, poster = null, banner = null),
                emptyList(),
                null,
            )

        assertNull(state.media.synopsis)
        assertNull(state.media.posterReference)
        assertNull(state.media.bannerReference)
        assertNull(state.collection)
    }

    @Test
    fun nonCurrentCollectionKeepsStatusButOmitsFastControls() {
        val collection =
            CollectionEntry(
                id = CollectionEntryId("entry-completed"),
                accountId = AccountId("account-1"),
                mediaId = MediaId("root"),
                status = CollectionStatus.COMPLETED,
                progress = ProgressValue(12),
                progressUnit = ProgressUnit.VOLUME,
                total = UnitTotal(12),
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )

        val state = MediaDetailUiMapper.mapContent(media("root"), emptyList(), collection)

        assertEquals(MediaDetailCollectionStatus.COMPLETED, state.collection?.status)
        assertEquals(MediaDetailProgressUnit.VOLUME, state.collection?.progressUnit)
        assertNull(state.collection?.controls)
    }

    private fun media(
        id: String,
        title: String = "Title $id",
        synopsis: String? = "Synopsis",
        poster: String? = "https://images.invalid/$id.jpg",
        banner: String? = "https://images.invalid/$id-banner.jpg",
        releaseYear: Int? = null,
        score: Int? = null,
        type: MediaType = MediaType.ANIME,
        format: MediaFormat = MediaFormat.TV,
    ): Media =
        Media(
            id = MediaId(id),
            type = type,
            title = MediaTitle(title),
            format = format,
            publicationStatus = MediaPublicationStatus.RELEASING,
            synopsis = synopsis,
            artwork = Artwork(poster?.let(::ImageReference), banner?.let(::ImageReference)),
            totals = MediaTotals(UnitTotal(12), null, null),
            genres = listOf("Adventure", "Drama"),
            relatedMediaIds = emptyList(),
            releaseYear = releaseYear,
            score = score?.let(::MediaScore),
        )
}
