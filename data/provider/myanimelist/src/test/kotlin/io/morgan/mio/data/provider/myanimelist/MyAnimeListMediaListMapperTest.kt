package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class MyAnimeListMediaListMapperTest {
    private val accountId = ProviderAccountId("12345")
    private val updatedAt = "2026-04-25T15:59:05+09:00"

    private fun animeEntry(
        id: Int? = 1,
        title: String? = "Fixture Anime",
        mediaType: String? = "tv",
        status: String? = "finished_airing",
        numEpisodes: Int? = 12,
        synopsis: String? = "Fixture synopsis",
        picture: MyAnimeListPictureDto? =
            MyAnimeListPictureDto(
                medium = "https://images.invalid/medium.jpg",
                large = "https://images.invalid/large.jpg",
            ),
        genres: List<MyAnimeListGenreDto?>? = listOf(MyAnimeListGenreDto(1, "Action")),
        mean: Double? = null,
        startDate: String? = null,
        startSeason: MyAnimeListStartSeasonDto? = null,
        listStatus: String? = "watching",
        watched: Int? = 3,
        rewatching: Boolean? = false,
        updated: String? = updatedAt,
        node: MyAnimeListAnimeDto? =
            MyAnimeListAnimeDto(
                id = id,
                title = title,
                mainPicture = picture,
                synopsis = synopsis,
                mediaType = mediaType,
                status = status,
                numEpisodes = numEpisodes,
                mean = mean,
                startDate = startDate,
                startSeason = startSeason,
                genres = genres,
            ),
        listStatusDto: MyAnimeListAnimeListStatusDto? =
            MyAnimeListAnimeListStatusDto(
                status = listStatus,
                numEpisodesWatched = watched,
                isRewatching = rewatching,
                updatedAt = updated,
            ),
    ) = MyAnimeListAnimeListEntryDto(node = node, listStatus = listStatusDto)

    private fun mangaEntry(
        id: Int? = 1,
        title: String? = "Fixture Manga",
        mediaType: String? = "manga",
        status: String? = "finished",
        numChapters: Int? = 100,
        numVolumes: Int? = 10,
        mean: Double? = null,
        startDate: String? = null,
        startSeason: MyAnimeListStartSeasonDto? = null,
        listStatus: String? = "reading",
        chaptersRead: Int? = 5,
        volumesRead: Int? = 0,
        rereading: Boolean? = false,
        updated: String? = updatedAt,
    ) = MyAnimeListMangaListEntryDto(
        node =
            MyAnimeListMangaDto(
                id = id,
                title = title,
                mainPicture = null,
                synopsis = null,
                mediaType = mediaType,
                status = status,
                numChapters = numChapters,
                numVolumes = numVolumes,
                mean = mean,
                startDate = startDate,
                startSeason = startSeason,
                genres = null,
            ),
        listStatus =
            MyAnimeListMangaListStatusDto(
                status = listStatus,
                numChaptersRead = chaptersRead,
                numVolumesRead = volumesRead,
                isRereading = rereading,
                updatedAt = updated,
            ),
    )

    private fun mapAnime(vararg entries: MyAnimeListAnimeListEntryDto) =
        MyAnimeListMediaListMapper.toCompleteImport(accountId, entries.toList(), emptyList())

    private fun mapManga(vararg entries: MyAnimeListMangaListEntryDto) =
        MyAnimeListMediaListMapper.toCompleteImport(accountId, emptyList(), entries.toList())

    private fun failure(result: ProviderResult<*>) = (result as ProviderResult.Failure).failure

    @Test
    fun aRepresentativeAnimeEntryMapsToTheExpectedMediaAndEntry() {
        val result = mapAnime(animeEntry()) as ProviderResult.Success
        val media = result.value.media.single()
        val entry = result.value.entries.single()

        assertEquals("anime/1", media.id.value)
        assertEquals(ProviderMediaType.ANIME, media.type)
        assertEquals("Fixture Anime", media.title)
        assertEquals(ProviderMediaFormat.TV, media.format)
        assertEquals(ProviderMediaPublicationStatus.FINISHED, media.publicationStatus)
        assertEquals("Fixture synopsis", media.synopsis)
        assertEquals(ProviderImageReference("https://images.invalid/large.jpg"), media.poster)
        assertNull(media.banner)
        assertEquals(listOf("Action"), media.genres)
        assertEquals(12, media.totals.episodes)
        assertNull(media.totals.chapters)
        assertNull(media.totals.volumes)
        assertTrue(media.releases.isEmpty())
        assertTrue(media.relatedMediaIds.isEmpty())

        assertEquals("anime/1", entry.id.value)
        assertEquals("anime/1", entry.mediaId.value)
        assertEquals(accountId, entry.accountId)
        assertEquals(ProviderCollectionStatus.CURRENT, entry.status)
        assertEquals(ProviderProgressUnit.EPISODE, entry.progressUnit)
        assertEquals(3, entry.progress)
        assertEquals(12, entry.total)
        assertEquals(Instant.parse("2026-04-25T06:59:05Z"), entry.updatedAt)
    }

    @Test
    fun meanAndSeasonYearPopulateInitialMetadataAndMeanRoundsToNearestPercent() {
        val anime =
            mapAnime(
                animeEntry(
                    mean = 8.74,
                    startSeason = MyAnimeListStartSeasonDto(2026),
                ),
            ) as ProviderResult.Success
        assertEquals(
            87,
            anime.value.media
                .single()
                .scorePercent,
        )
        assertEquals(
            2026,
            anime.value.media
                .single()
                .releaseYear,
        )

        val fallback =
            mapAnime(animeEntry(mean = 0.05, startDate = "2024-03-01")) as ProviderResult.Success
        assertNull(
            fallback.value.media
                .single()
                .scorePercent,
        )
        assertEquals(
            2024,
            fallback.value.media
                .single()
                .releaseYear,
        )
    }

    @Test
    fun everyAnimeListStatusMapsToItsCanonicalStatus() {
        val expectations =
            mapOf(
                "watching" to ProviderCollectionStatus.CURRENT,
                "plan_to_watch" to ProviderCollectionStatus.PLANNED,
                "on_hold" to ProviderCollectionStatus.PAUSED,
                "completed" to ProviderCollectionStatus.COMPLETED,
                "dropped" to ProviderCollectionStatus.DROPPED,
            )

        expectations.forEach { (raw, expected) ->
            val result = mapAnime(animeEntry(listStatus = raw)) as ProviderResult.Success
            assertEquals(
                raw,
                expected,
                result.value.entries
                    .single()
                    .status,
            )
        }
    }

    @Test
    fun everyMangaListStatusMapsToItsCanonicalStatus() {
        val expectations =
            mapOf(
                "reading" to ProviderCollectionStatus.CURRENT,
                "plan_to_read" to ProviderCollectionStatus.PLANNED,
                "on_hold" to ProviderCollectionStatus.PAUSED,
                "completed" to ProviderCollectionStatus.COMPLETED,
                "dropped" to ProviderCollectionStatus.DROPPED,
            )

        expectations.forEach { (raw, expected) ->
            val result = mapManga(mangaEntry(listStatus = raw)) as ProviderResult.Success
            assertEquals(
                raw,
                expected,
                result.value.entries
                    .single()
                    .status,
            )
        }
    }

    @Test
    fun theRewatchingFlagOverridesTheStatusIncludingAnUnknownOne() {
        val watching = mapAnime(animeEntry(listStatus = "watching", rewatching = true)) as ProviderResult.Success
        assertEquals(
            ProviderCollectionStatus.REPEATING,
            watching.value.entries
                .single()
                .status,
        )

        val completed = mapAnime(animeEntry(listStatus = "completed", rewatching = true)) as ProviderResult.Success
        assertEquals(
            ProviderCollectionStatus.REPEATING,
            completed.value.entries
                .single()
                .status,
        )

        val unknown = mapAnime(animeEntry(listStatus = "who_knows", rewatching = true)) as ProviderResult.Success
        assertEquals(
            ProviderCollectionStatus.REPEATING,
            unknown.value.entries
                .single()
                .status,
        )
    }

    @Test
    fun theRereadingFlagOverridesTheMangaStatus() {
        val result = mapManga(mangaEntry(listStatus = "completed", rereading = true)) as ProviderResult.Success
        assertEquals(
            ProviderCollectionStatus.REPEATING,
            result.value.entries
                .single()
                .status,
        )
    }

    @Test
    fun anUnknownOrNullListStatusWithoutTheFlagIsUnsupportedMandatoryData() {
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(listStatus = "who_knows"))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(listStatus = null))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapManga(mangaEntry(listStatus = "who_knows"))))
    }

    @Test
    fun aNullNodeOrNullListStatusIsUnsupportedMandatoryData() {
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(node = null))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(listStatusDto = null))))
    }

    @Test
    fun aNullOrNonPositiveNodeIdIsUnsupportedMandatoryData() {
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(id = null))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(id = 0))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(id = -3))))
    }

    @Test
    fun anAbsentOrBlankTitleIsUnsupportedMandatoryData() {
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(title = null))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(title = "   "))))
    }

    @Test
    fun anAnimeAndAMangaSharingANumericIdBothSurviveAsDistinctMedia() {
        val result =
            MyAnimeListMediaListMapper.toCompleteImport(
                accountId,
                listOf(animeEntry(id = 1, title = "Shared Id Anime")),
                listOf(mangaEntry(id = 1, title = "Shared Id Manga")),
            ) as ProviderResult.Success

        assertEquals(listOf("anime/1", "manga/1"), result.value.media.map { media -> media.id.value })
        assertEquals(listOf("anime/1", "manga/1"), result.value.entries.map { entry -> entry.id.value })
        assertEquals(
            listOf(ProviderMediaType.ANIME, ProviderMediaType.MANGA),
            result.value.media.map { media -> media.type },
        )
    }

    @Test
    fun aRepeatedRemoteIdKeepsTheFirstOccurrenceRatherThanFailing() {
        val result =
            mapAnime(
                animeEntry(id = 7, title = "First"),
                animeEntry(id = 7, title = "Second"),
            ) as ProviderResult.Success

        assertEquals(listOf("anime/7"), result.value.media.map { media -> media.id.value })
        assertEquals(
            "First",
            result.value.media
                .single()
                .title,
        )
        assertEquals(1, result.value.entries.size)
    }

    @Test
    fun mangaUsesVolumesOnlyWhenVolumesAreReadAndNoChaptersAre() {
        val volume =
            mapManga(mangaEntry(chaptersRead = 0, volumesRead = 4)) as ProviderResult.Success
        assertEquals(
            ProviderProgressUnit.VOLUME,
            volume.value.entries
                .single()
                .progressUnit,
        )
        assertEquals(
            4,
            volume.value.entries
                .single()
                .progress,
        )
        assertEquals(
            10,
            volume.value.entries
                .single()
                .total,
        )

        val chapter =
            mapManga(mangaEntry(chaptersRead = 9, volumesRead = 4)) as ProviderResult.Success
        assertEquals(
            ProviderProgressUnit.CHAPTER,
            chapter.value.entries
                .single()
                .progressUnit,
        )
        assertEquals(
            9,
            chapter.value.entries
                .single()
                .progress,
        )
        assertEquals(
            100,
            chapter.value.entries
                .single()
                .total,
        )

        val neither =
            mapManga(mangaEntry(chaptersRead = 0, volumesRead = 0)) as ProviderResult.Success
        assertEquals(
            ProviderProgressUnit.CHAPTER,
            neither.value.entries
                .single()
                .progressUnit,
        )
        assertEquals(
            0,
            neither.value.entries
                .single()
                .progress,
        )
    }

    @Test
    fun anAbsentProgressCountIsZeroAndANegativeOneIsMalformed() {
        val absent = mapAnime(animeEntry(watched = null)) as ProviderResult.Success
        assertEquals(
            0,
            absent.value.entries
                .single()
                .progress,
        )

        assertEquals(ProviderFailure.MalformedResponse, failure(mapAnime(animeEntry(watched = -1))))
        assertEquals(ProviderFailure.MalformedResponse, failure(mapManga(mangaEntry(chaptersRead = -1))))
        assertEquals(ProviderFailure.MalformedResponse, failure(mapManga(mangaEntry(volumesRead = -1))))
    }

    @Test
    fun aTotalIsTakenOnlyWhenPositiveAndIsClearedWhenProgressExceedsIt() {
        val zero = mapAnime(animeEntry(numEpisodes = 0)) as ProviderResult.Success
        assertNull(
            zero.value.entries
                .single()
                .total,
        )
        assertNull(
            zero.value.media
                .single()
                .totals.episodes,
        )

        val negative = mapAnime(animeEntry(numEpisodes = -1)) as ProviderResult.Success
        assertNull(
            negative.value.entries
                .single()
                .total,
        )

        val beyond = mapAnime(animeEntry(numEpisodes = 12, watched = 20)) as ProviderResult.Success
        assertNull(
            beyond.value.entries
                .single()
                .total,
        )
        assertEquals(
            20,
            beyond.value.entries
                .single()
                .progress,
        )
        assertEquals(
            12,
            beyond.value.media
                .single()
                .totals.episodes,
        )
    }

    @Test
    fun everyAnimeMediaTypeMapsToItsFormat() {
        val expectations =
            mapOf(
                "tv" to ProviderMediaFormat.TV,
                "movie" to ProviderMediaFormat.MOVIE,
                "ova" to ProviderMediaFormat.OVA,
                "ona" to ProviderMediaFormat.ONA,
                "special" to ProviderMediaFormat.SPECIAL,
                // Observed live on 2026-08-19 but absent from both published specifications.
                "tv_special" to ProviderMediaFormat.SPECIAL,
                "music" to ProviderMediaFormat.MUSIC,
                "pv" to ProviderMediaFormat.OTHER,
                "cm" to ProviderMediaFormat.OTHER,
                "doujinshi" to ProviderMediaFormat.OTHER,
            )

        expectations.forEach { (raw, expected) ->
            val result = mapAnime(animeEntry(mediaType = raw)) as ProviderResult.Success
            assertEquals(
                raw,
                expected,
                result.value.media
                    .single()
                    .format,
            )
        }

        assertNull(
            (mapAnime(animeEntry(mediaType = "unknown")) as ProviderResult.Success)
                .value.media
                .single()
                .format,
        )
        assertNull(
            (mapAnime(animeEntry(mediaType = null)) as ProviderResult.Success)
                .value.media
                .single()
                .format,
        )
    }

    @Test
    fun everyMangaMediaTypeMapsToItsFormat() {
        val expectations =
            mapOf(
                "manga" to ProviderMediaFormat.MANGA,
                "novel" to ProviderMediaFormat.NOVEL,
                "light_novel" to ProviderMediaFormat.NOVEL,
                "one_shot" to ProviderMediaFormat.ONE_SHOT,
                "doujinshi" to ProviderMediaFormat.OTHER,
                "manhwa" to ProviderMediaFormat.OTHER,
                "manhua" to ProviderMediaFormat.OTHER,
                "oel" to ProviderMediaFormat.OTHER,
            )

        expectations.forEach { (raw, expected) ->
            val result = mapManga(mangaEntry(mediaType = raw)) as ProviderResult.Success
            assertEquals(
                raw,
                expected,
                result.value.media
                    .single()
                    .format,
            )
        }

        assertNull(
            (mapManga(mangaEntry(mediaType = "unknown")) as ProviderResult.Success)
                .value.media
                .single()
                .format,
        )
    }

    @Test
    fun everyPublicationStatusMapsToItsCanonicalValue() {
        val anime =
            mapOf(
                "finished_airing" to ProviderMediaPublicationStatus.FINISHED,
                "currently_airing" to ProviderMediaPublicationStatus.RELEASING,
                "not_yet_aired" to ProviderMediaPublicationStatus.UPCOMING,
                "who_knows" to ProviderMediaPublicationStatus.UNKNOWN,
            )
        anime.forEach { (raw, expected) ->
            val result = mapAnime(animeEntry(status = raw)) as ProviderResult.Success
            assertEquals(
                raw,
                expected,
                result.value.media
                    .single()
                    .publicationStatus,
            )
        }
        assertEquals(
            ProviderMediaPublicationStatus.UNKNOWN,
            (mapAnime(animeEntry(status = null)) as ProviderResult.Success)
                .value.media
                .single()
                .publicationStatus,
        )

        val manga =
            mapOf(
                "finished" to ProviderMediaPublicationStatus.FINISHED,
                "currently_publishing" to ProviderMediaPublicationStatus.RELEASING,
                "not_yet_published" to ProviderMediaPublicationStatus.UPCOMING,
                "on_hiatus" to ProviderMediaPublicationStatus.HIATUS,
                "discontinued" to ProviderMediaPublicationStatus.CANCELLED,
                "who_knows" to ProviderMediaPublicationStatus.UNKNOWN,
            )
        manga.forEach { (raw, expected) ->
            val result = mapManga(mangaEntry(status = raw)) as ProviderResult.Success
            assertEquals(
                raw,
                expected,
                result.value.media
                    .single()
                    .publicationStatus,
            )
        }
    }

    @Test
    fun anAbsentBlankOrUnparsableUpdatedAtIsUnsupportedMandatoryData() {
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(updated = null))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(updated = "   "))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(updated = "yesterday"))))
        assertEquals(ProviderFailure.UnsupportedMandatoryData, failure(mapAnime(animeEntry(updated = "2026-04-25"))))
    }

    @Test
    fun anOffsetTimestampParsesToTheEquivalentInstant() {
        val result = mapAnime(animeEntry(updated = "2026-04-25T15:59:05+09:00")) as ProviderResult.Success
        assertEquals(
            Instant.parse("2026-04-25T06:59:05Z"),
            result.value.entries
                .single()
                .updatedAt,
        )

        val utc = mapAnime(animeEntry(updated = "2026-04-25T06:59:05Z")) as ProviderResult.Success
        assertEquals(
            Instant.parse("2026-04-25T06:59:05Z"),
            utc.value.entries
                .single()
                .updatedAt,
        )
    }

    @Test
    fun blankOptionalTextAndPicturesBecomeNull() {
        val result =
            mapAnime(
                animeEntry(
                    synopsis = "   ",
                    picture = MyAnimeListPictureDto(medium = "  ", large = "  "),
                ),
            ) as ProviderResult.Success

        assertNull(
            result.value.media
                .single()
                .synopsis,
        )
        assertNull(
            result.value.media
                .single()
                .poster,
        )
        assertNull(
            result.value.media
                .single()
                .banner,
        )
    }

    @Test
    fun thePosterPrefersTheLargePictureAndFallsBackToMedium() {
        val large =
            mapAnime(
                animeEntry(
                    picture =
                        MyAnimeListPictureDto(
                            medium = "https://images.invalid/m.jpg",
                            large = "https://images.invalid/l.jpg",
                        ),
                ),
            ) as ProviderResult.Success
        assertEquals(
            ProviderImageReference("https://images.invalid/l.jpg"),
            large.value.media
                .single()
                .poster,
        )

        val medium =
            mapAnime(
                animeEntry(picture = MyAnimeListPictureDto(medium = "https://images.invalid/m.jpg", large = null)),
            ) as ProviderResult.Success
        assertEquals(
            ProviderImageReference("https://images.invalid/m.jpg"),
            medium.value.media
                .single()
                .poster,
        )
    }

    @Test
    fun genresAreDeduplicatedBlankFilteredAndOrderPreserved() {
        val result =
            mapAnime(
                animeEntry(
                    genres =
                        listOf(
                            MyAnimeListGenreDto(1, "Action"),
                            null,
                            MyAnimeListGenreDto(2, "  "),
                            MyAnimeListGenreDto(3, "Drama"),
                            MyAnimeListGenreDto(4, "Action"),
                            MyAnimeListGenreDto(5, null),
                        ),
                ),
            ) as ProviderResult.Success

        assertEquals(
            listOf("Action", "Drama"),
            result.value.media
                .single()
                .genres,
        )

        val absent = mapAnime(animeEntry(genres = null)) as ProviderResult.Success
        assertEquals(
            emptyList<String>(),
            absent.value.media
                .single()
                .genres,
        )
    }

    @Test
    fun noMyAnimeListMediaEverCarriesAReleaseOrRelationOrBanner() {
        val result =
            MyAnimeListMediaListMapper.toCompleteImport(
                accountId,
                listOf(animeEntry(id = 1), animeEntry(id = 2, status = "currently_airing")),
                listOf(mangaEntry(id = 1), mangaEntry(id = 2)),
            ) as ProviderResult.Success

        assertEquals(4, result.value.media.size)
        assertTrue(result.value.media.all { media -> media.releases.isEmpty() })
        assertTrue(result.value.media.all { media -> media.relatedMediaIds.isEmpty() })
        assertTrue(result.value.media.all { media -> media.banner == null })
    }

    @Test
    fun anEmptyImportIsValid() {
        val result = MyAnimeListMediaListMapper.toCompleteImport(accountId, emptyList(), emptyList())

        val success = result as ProviderResult.Success
        assertTrue(success.value.media.isEmpty())
        assertTrue(success.value.entries.isEmpty())
    }
}
