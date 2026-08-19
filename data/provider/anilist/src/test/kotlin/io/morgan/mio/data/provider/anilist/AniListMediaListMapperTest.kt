package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderReleaseTime
import io.morgan.mio.data.provider.api.ProviderResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListMediaListMapperTest {
    private val accountId = ProviderAccountId("42")

    private fun titleDto(preferred: String? = "Title") =
        AniListMediaTitleDto(userPreferred = preferred, romaji = null, english = null, native = null)

    private fun mediaDto(
        id: Int = 1,
        type: String? = "ANIME",
        format: String? = "TV",
        status: String? = "RELEASING",
        episodes: Int? = 12,
        chapters: Int? = null,
        volumes: Int? = null,
        description: String? = null,
        bannerImage: String? = null,
        genres: List<String?>? = null,
        title: AniListMediaTitleDto? = titleDto(),
        coverImage: AniListCoverImageDto? = null,
        airingSchedule: AniListAiringScheduleConnectionDto? = null,
    ) = AniListMediaDto(
        id = id,
        type = type,
        format = format,
        status = status,
        episodes = episodes,
        chapters = chapters,
        volumes = volumes,
        description = description,
        bannerImage = bannerImage,
        genres = genres,
        title = title,
        coverImage = coverImage,
        airingSchedule = airingSchedule,
    )

    private fun entryDto(
        id: Int = 1,
        status: String? = "CURRENT",
        progress: Int? = null,
        progressVolumes: Int? = null,
        updatedAt: Long? = 1_700_000_000,
        createdAt: Long? = 1_690_000_000,
        media: AniListMediaDto? = mediaDto(),
    ) = AniListMediaListEntryDto(
        id = id,
        status = status,
        progress = progress,
        progressVolumes = progressVolumes,
        updatedAt = updatedAt,
        createdAt = createdAt,
        media = media,
    )

    private fun mapOne(entry: AniListMediaListEntryDto) =
        AniListMediaListMapper.toCompleteImport(accountId, listOf(entry))

    @Test
    fun everyListStatusMapsToItsCanonicalStatusIncludingRepeating() {
        val mapping =
            mapOf(
                "CURRENT" to ProviderCollectionStatus.CURRENT,
                "PLANNING" to ProviderCollectionStatus.PLANNED,
                "PAUSED" to ProviderCollectionStatus.PAUSED,
                "COMPLETED" to ProviderCollectionStatus.COMPLETED,
                "DROPPED" to ProviderCollectionStatus.DROPPED,
                "REPEATING" to ProviderCollectionStatus.REPEATING,
            )

        mapping.forEach { (raw, expected) ->
            val result = mapOne(entryDto(status = raw)) as ProviderResult.Success
            assertEquals(
                expected,
                result.value.entries
                    .single()
                    .status,
            )
        }
    }

    @Test
    fun unknownAndNullListStatusProduceUnsupportedMandatoryData() {
        assertEquals(
            ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
            mapOne(entryDto(status = "SOMETHING_ELSE")),
        )
        assertEquals(
            ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
            mapOne(entryDto(status = null)),
        )
    }

    @Test
    fun animeUsesEpisodeProgress() {
        val result =
            mapOne(entryDto(progress = 5, media = mediaDto(type = "ANIME", episodes = 12))) as ProviderResult.Success

        val entry = result.value.entries.single()
        assertEquals(ProviderProgressUnit.EPISODE, entry.progressUnit)
        assertEquals(5, entry.progress)
        assertEquals(12, entry.total)
    }

    @Test
    fun mangaDefaultsToChapterProgress() {
        val result =
            mapOne(
                entryDto(progress = 10, media = mediaDto(type = "MANGA", chapters = 50, volumes = 5)),
            ) as ProviderResult.Success

        val entry = result.value.entries.single()
        assertEquals(ProviderProgressUnit.CHAPTER, entry.progressUnit)
        assertEquals(10, entry.progress)
        assertEquals(50, entry.total)
    }

    @Test
    fun mangaUsesVolumeProgressOnlyUnderTheExactCondition() {
        val result =
            mapOne(
                entryDto(
                    progress = 0,
                    progressVolumes = 3,
                    media = mediaDto(type = "MANGA", chapters = 50, volumes = 8),
                ),
            ) as ProviderResult.Success

        val entry = result.value.entries.single()
        assertEquals(ProviderProgressUnit.VOLUME, entry.progressUnit)
        assertEquals(3, entry.progress)
        assertEquals(8, entry.total)
    }

    @Test
    fun mangaWithNonZeroChapterProgressAndVolumesIsStillChapterTracked() {
        val result =
            mapOne(
                entryDto(
                    progress = 4,
                    progressVolumes = 1,
                    media = mediaDto(type = "MANGA", chapters = 50, volumes = 8),
                ),
            ) as ProviderResult.Success

        assertEquals(
            ProviderProgressUnit.CHAPTER,
            result.value.entries
                .single()
                .progressUnit,
        )
    }

    @Test
    fun negativeProgressOrProgressVolumesIsMalformedResponse() {
        assertEquals(
            ProviderResult.Failure(ProviderFailure.MalformedResponse),
            mapOne(entryDto(progress = -1)),
        )
        assertEquals(
            ProviderResult.Failure(ProviderFailure.MalformedResponse),
            mapOne(entryDto(progressVolumes = -1, media = mediaDto(type = "MANGA"))),
        )
    }

    @Test
    fun progressExceedingTotalClampsTotalToNull() {
        val result =
            mapOne(
                entryDto(progress = 20, media = mediaDto(type = "ANIME", episodes = 12)),
            ) as ProviderResult.Success

        val entry = result.value.entries.single()
        assertEquals(20, entry.progress)
        assertNull(entry.total)
    }

    @Test
    fun updatedAtFallsBackToCreatedAtAndBothAbsentFails() {
        val fallback =
            mapOne(entryDto(updatedAt = null, createdAt = 1_690_000_000)) as ProviderResult.Success
        assertEquals(
            1_690_000_000,
            fallback.value.entries
                .single()
                .updatedAt.epochSeconds,
        )

        assertEquals(
            ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
            mapOne(entryDto(updatedAt = null, createdAt = null)),
        )
        assertEquals(
            ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
            mapOne(entryDto(updatedAt = 0, createdAt = null)),
        )
    }

    @Test
    fun everyFormatAndPublicationStatusMapping() {
        val formatMapping =
            mapOf(
                "TV" to ProviderMediaFormat.TV,
                "TV_SHORT" to ProviderMediaFormat.TV,
                "MOVIE" to ProviderMediaFormat.MOVIE,
                "OVA" to ProviderMediaFormat.OVA,
                "ONA" to ProviderMediaFormat.ONA,
                "SPECIAL" to ProviderMediaFormat.SPECIAL,
                "MUSIC" to ProviderMediaFormat.MUSIC,
                "MANGA" to ProviderMediaFormat.MANGA,
                "NOVEL" to ProviderMediaFormat.NOVEL,
                "ONE_SHOT" to ProviderMediaFormat.ONE_SHOT,
            )
        formatMapping.forEach { (raw, expected) ->
            val result = mapOne(entryDto(media = mediaDto(format = raw))) as ProviderResult.Success
            assertEquals(
                expected,
                result.value.media
                    .single()
                    .format,
            )
        }
        assertEquals(
            null,
            (mapOne(entryDto(media = mediaDto(format = null))) as ProviderResult.Success)
                .value.media
                .single()
                .format,
        )
        assertEquals(
            ProviderMediaFormat.OTHER,
            (mapOne(entryDto(media = mediaDto(format = "UNRECOGNISED"))) as ProviderResult.Success)
                .value.media
                .single()
                .format,
        )

        val publicationMapping =
            mapOf(
                "FINISHED" to ProviderMediaPublicationStatus.FINISHED,
                "RELEASING" to ProviderMediaPublicationStatus.RELEASING,
                "NOT_YET_RELEASED" to ProviderMediaPublicationStatus.UPCOMING,
                "CANCELLED" to ProviderMediaPublicationStatus.CANCELLED,
                "HIATUS" to ProviderMediaPublicationStatus.HIATUS,
            )
        publicationMapping.forEach { (raw, expected) ->
            val result = mapOne(entryDto(media = mediaDto(status = raw))) as ProviderResult.Success
            assertEquals(
                expected,
                result.value.media
                    .single()
                    .publicationStatus,
            )
        }
        assertEquals(
            ProviderMediaPublicationStatus.UNKNOWN,
            (mapOne(entryDto(media = mediaDto(status = null))) as ProviderResult.Success)
                .value.media
                .single()
                .publicationStatus,
        )
        assertEquals(
            ProviderMediaPublicationStatus.UNKNOWN,
            (mapOne(entryDto(media = mediaDto(status = "UNRECOGNISED"))) as ProviderResult.Success)
                .value.media
                .single()
                .publicationStatus,
        )
    }

    @Test
    fun totalsOfZeroOrNegativeBecomeNull() {
        val result =
            mapOne(entryDto(media = mediaDto(episodes = 0, chapters = -1, volumes = null))) as ProviderResult.Success

        val totals =
            result.value.media
                .single()
                .totals
        assertNull(totals.episodes)
        assertNull(totals.chapters)
        assertNull(totals.volumes)
    }

    @Test
    fun blankDescriptionCoverAndBannerBecomeNull() {
        val result =
            mapOne(
                entryDto(
                    media =
                        mediaDto(
                            description = "  ",
                            bannerImage = "  ",
                            coverImage = AniListCoverImageDto(extraLarge = "  ", large = "  ", medium = "  "),
                        ),
                ),
            ) as ProviderResult.Success

        val media = result.value.media.single()
        assertNull(media.synopsis)
        assertNull(media.banner)
        assertNull(media.poster)
    }

    @Test
    fun genresAreDeduplicatedAndBlankFilteredInOrder() {
        val result =
            mapOne(
                entryDto(media = mediaDto(genres = listOf("Action", null, "  ", "Action", "Drama"))),
            ) as ProviderResult.Success

        assertEquals(
            listOf("Action", "Drama"),
            result.value.media
                .single()
                .genres,
        )
    }

    @Test
    fun relatedMediaIdsAreAlwaysEmpty() {
        val result = mapOne(entryDto()) as ProviderResult.Success

        assertTrue(
            result.value.media
                .single()
                .relatedMediaIds
                .isEmpty(),
        )
    }

    @Test
    fun animeAiringNodesBecomeTrustedReleasesWithDuplicatesCollapsedAndInvalidValuesDropped() {
        val airingSchedule =
            AniListAiringScheduleConnectionDto(
                nodes =
                    listOf(
                        AniListAiringScheduleNodeDto(episode = 5, airingAt = 1_700_000_000),
                        AniListAiringScheduleNodeDto(episode = 5, airingAt = 1_700_000_001),
                        AniListAiringScheduleNodeDto(episode = 0, airingAt = 1_700_000_002),
                        AniListAiringScheduleNodeDto(episode = 6, airingAt = 0),
                        null,
                    ),
            )
        val result =
            mapOne(
                entryDto(media = mediaDto(type = "ANIME", airingSchedule = airingSchedule)),
            ) as ProviderResult.Success

        val releases =
            result.value.media
                .single()
                .releases
        assertEquals(1, releases.size)
        assertEquals(5, releases.single().number)
        assertEquals(ProviderProgressUnit.EPISODE, releases.single().unit)
        assertTrue(releases.single().time is ProviderReleaseTime.Trusted)
    }

    @Test
    fun mangaProducesNoReleases() {
        val airingSchedule =
            AniListAiringScheduleConnectionDto(nodes = listOf(AniListAiringScheduleNodeDto(1, 1_700_000_000)))
        val result =
            mapOne(
                entryDto(media = mediaDto(type = "MANGA", airingSchedule = airingSchedule)),
            ) as ProviderResult.Success

        assertTrue(
            result.value.media
                .single()
                .releases
                .isEmpty(),
        )
    }

    @Test
    fun twoEntriesForTheSameMediaProduceMalformedResponse() {
        val media = mediaDto(id = 1)
        val entries =
            listOf(
                entryDto(id = 1, media = media),
                entryDto(id = 2, media = media),
            )

        val result = AniListMediaListMapper.toCompleteImport(accountId, entries)

        assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), result)
    }

    @Test
    fun mediaWithoutAMandatoryTitleFails() {
        assertEquals(
            ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
            mapOne(entryDto(media = mediaDto(title = titleDto(preferred = null)))),
        )
        assertEquals(
            ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
            mapOne(entryDto(media = mediaDto(title = null))),
        )
    }

    @Test
    fun unrecognisedMediaTypeFails() {
        assertEquals(
            ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
            mapOne(entryDto(media = mediaDto(type = "NOVEL"))),
        )
    }

    @Test
    fun animeTypeIsMappedToCanonicalType() {
        val result = mapOne(entryDto(media = mediaDto(type = "ANIME"))) as ProviderResult.Success
        assertEquals(
            ProviderMediaType.ANIME,
            result.value.media
                .single()
                .type,
        )
    }
}
