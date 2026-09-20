package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaTotals
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import kotlin.time.Instant

internal object MyAnimeListMediaListMapper {
    internal fun toCompleteImport(
        accountId: ProviderAccountId,
        animeEntries: List<MyAnimeListAnimeListEntryDto>,
        mangaEntries: List<MyAnimeListMangaListEntryDto>,
    ): ProviderResult<CompleteProviderImport> {
        val mediaByRemoteId = LinkedHashMap<String, ProviderMedia>()
        val collectionEntries = mutableListOf<ProviderCollectionEntry>()

        for (entryDto in animeEntries) {
            val result = mapAnimeEntry(accountId, entryDto)
            if (result is ProviderResult.Failure) {
                return result
            }
            val (media, entry, remoteId) = (result as ProviderResult.Success).value
            if (remoteId !in mediaByRemoteId) {
                mediaByRemoteId[remoteId] = media
                collectionEntries.add(entry)
            }
        }

        for (entryDto in mangaEntries) {
            val result = mapMangaEntry(accountId, entryDto)
            if (result is ProviderResult.Failure) {
                return result
            }
            val (media, entry, remoteId) = (result as ProviderResult.Success).value
            if (remoteId !in mediaByRemoteId) {
                mediaByRemoteId[remoteId] = media
                collectionEntries.add(entry)
            }
        }

        return try {
            val import =
                CompleteProviderImport(
                    ProviderKey.MY_ANIME_LIST,
                    accountId,
                    mediaByRemoteId.values.toList(),
                    collectionEntries,
                )
            ProviderResult.Success(import)
        } catch (invalid: IllegalArgumentException) {
            ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
    }

    private fun mapAnimeEntry(
        accountId: ProviderAccountId,
        entryDto: MyAnimeListAnimeListEntryDto,
    ): ProviderResult<Triple<ProviderMedia, ProviderCollectionEntry, String>> {
        val node = entryDto.node ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
        val listStatus = entryDto.listStatus ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)

        val remoteMediaId =
            node.id?.takeIf { it > 0 } ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
        val remoteIdString = "anime/$remoteMediaId"

        val title =
            node.title?.takeIf(String::isNotBlank)
                ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)

        val status =
            if (listStatus.isRewatching == true) {
                ProviderCollectionStatus.REPEATING
            } else {
                when (listStatus.status) {
                    "watching" -> ProviderCollectionStatus.CURRENT
                    "plan_to_watch" -> ProviderCollectionStatus.PLANNED
                    "on_hold" -> ProviderCollectionStatus.PAUSED
                    "completed" -> ProviderCollectionStatus.COMPLETED
                    "dropped" -> ProviderCollectionStatus.DROPPED
                    else -> return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
                }
            }

        val rawProgress = listStatus.numEpisodesWatched
        if (rawProgress != null && rawProgress < 0) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }

        val progressUnit = ProviderProgressUnit.EPISODE
        val progress = rawProgress ?: 0
        val rawTotal = node.numEpisodes
        val total = rawTotal?.takeIf { it > 0 }

        val updatedAt =
            try {
                listStatus.updatedAt?.takeIf(String::isNotBlank)?.let { Instant.parse(it) }
                    ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            } catch (invalid: IllegalArgumentException) {
                return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            }

        val format = mapAnimeFormat(node.mediaType)
        val publicationStatus = mapPublicationStatus(node.status, isAnime = true)
        val synopsis = node.synopsis?.takeIf(String::isNotBlank)
        val poster =
            listOfNotNull(
                node.mainPicture?.large,
                node.mainPicture?.medium,
            ).firstOrNull(String::isNotBlank)?.let(::ProviderImageReference)
        val totals = ProviderMediaTotals(episodes = node.numEpisodes?.positiveOrNull(), chapters = null, volumes = null)
        val genres =
            node.genres
                .orEmpty()
                .filterNotNull()
                .mapNotNull { genre ->
                    genre.name?.takeIf(String::isNotBlank)
                }.distinct()

        val media =
            ProviderMedia(
                id = ProviderMediaId(remoteIdString),
                type = ProviderMediaType.ANIME,
                title = title,
                titleEnglish = node.alternativeTitles?.en?.takeIf(String::isNotBlank),
                titleJapanese = node.alternativeTitles?.ja?.takeIf(String::isNotBlank),
                titleRomaji = title,
                format = format,
                publicationStatus = publicationStatus,
                synopsis = synopsis,
                poster = poster,
                banner = null,
                totals = totals,
                genres = genres,
                relatedMediaIds = emptyList(),
                releases = emptyList(),
                releaseYear = node.startSeason?.year?.positiveYearOrNull() ?: node.startDate?.fourDigitYearOrNull(),
                scorePercent = node.mean?.toScorePercentOrNull(),
            )

        val entry =
            ProviderCollectionEntry(
                id = ProviderCollectionEntryId(remoteIdString),
                accountId = accountId,
                mediaId = media.id,
                status = status,
                progress = progress,
                progressUnit = progressUnit,
                total = total?.takeIf { progress <= it },
                updatedAt = updatedAt,
            )

        return ProviderResult.Success(Triple(media, entry, remoteIdString))
    }

    private fun mapMangaEntry(
        accountId: ProviderAccountId,
        entryDto: MyAnimeListMangaListEntryDto,
    ): ProviderResult<Triple<ProviderMedia, ProviderCollectionEntry, String>> {
        val node = entryDto.node ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
        val listStatus = entryDto.listStatus ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)

        val remoteMediaId =
            node.id?.takeIf { it > 0 } ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
        val remoteIdString = "manga/$remoteMediaId"

        val title =
            node.title?.takeIf(String::isNotBlank)
                ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)

        val status =
            if (listStatus.isRereading == true) {
                ProviderCollectionStatus.REPEATING
            } else {
                when (listStatus.status) {
                    "reading" -> ProviderCollectionStatus.CURRENT
                    "plan_to_read" -> ProviderCollectionStatus.PLANNED
                    "on_hold" -> ProviderCollectionStatus.PAUSED
                    "completed" -> ProviderCollectionStatus.COMPLETED
                    "dropped" -> ProviderCollectionStatus.DROPPED
                    else -> return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
                }
            }

        val numChaptersRead = listStatus.numChaptersRead
        val numVolumesRead = listStatus.numVolumesRead
        if ((numChaptersRead != null && numChaptersRead < 0) || (numVolumesRead != null && numVolumesRead < 0)) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }

        val progressUnit: ProviderProgressUnit
        val progress: Int
        val rawTotal: Int?
        when {
            numVolumesRead != null && numVolumesRead > 0 && (numChaptersRead ?: 0) == 0 -> {
                progressUnit = ProviderProgressUnit.VOLUME
                progress = numVolumesRead
                rawTotal = node.numVolumes
            }
            else -> {
                progressUnit = ProviderProgressUnit.CHAPTER
                progress = numChaptersRead ?: 0
                rawTotal = node.numChapters
            }
        }
        val total = rawTotal?.takeIf { it > 0 }

        val updatedAt =
            try {
                listStatus.updatedAt?.takeIf(String::isNotBlank)?.let { Instant.parse(it) }
                    ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            } catch (invalid: IllegalArgumentException) {
                return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            }

        val format = mapMangaFormat(node.mediaType)
        val publicationStatus = mapPublicationStatus(node.status, isAnime = false)
        val synopsis = node.synopsis?.takeIf(String::isNotBlank)
        val poster =
            listOfNotNull(
                node.mainPicture?.large,
                node.mainPicture?.medium,
            ).firstOrNull(String::isNotBlank)?.let(::ProviderImageReference)
        val totals =
            ProviderMediaTotals(
                episodes = null,
                chapters = node.numChapters?.positiveOrNull(),
                volumes = node.numVolumes?.positiveOrNull(),
            )
        val genres =
            node.genres
                .orEmpty()
                .filterNotNull()
                .mapNotNull { genre ->
                    genre.name?.takeIf(String::isNotBlank)
                }.distinct()

        val media =
            ProviderMedia(
                id = ProviderMediaId(remoteIdString),
                type = ProviderMediaType.MANGA,
                title = title,
                titleEnglish = node.alternativeTitles?.en?.takeIf(String::isNotBlank),
                titleJapanese = node.alternativeTitles?.ja?.takeIf(String::isNotBlank),
                titleRomaji = title,
                format = format,
                publicationStatus = publicationStatus,
                synopsis = synopsis,
                poster = poster,
                banner = null,
                totals = totals,
                genres = genres,
                relatedMediaIds = emptyList(),
                releases = emptyList(),
                releaseYear = node.startSeason?.year?.positiveYearOrNull() ?: node.startDate?.fourDigitYearOrNull(),
                scorePercent = node.mean?.toScorePercentOrNull(),
            )

        val entry =
            ProviderCollectionEntry(
                id = ProviderCollectionEntryId(remoteIdString),
                accountId = accountId,
                mediaId = media.id,
                status = status,
                progress = progress,
                progressUnit = progressUnit,
                total = total?.takeIf { progress <= it },
                updatedAt = updatedAt,
            )

        return ProviderResult.Success(Triple(media, entry, remoteIdString))
    }

    private fun mapAnimeFormat(mediaType: String?): ProviderMediaFormat? =
        when (mediaType?.lowercase()) {
            "tv" -> ProviderMediaFormat.TV
            "movie" -> ProviderMediaFormat.MOVIE
            "ova" -> ProviderMediaFormat.OVA
            "ona" -> ProviderMediaFormat.ONA
            // MyAnimeList splits a televised special out from `special`. Both are the same canonical
            // format; live sampling on 2026-08-19 found `tv_special` on ~5% of ranked anime, so
            // letting it fall through to OTHER would mislabel a common format.
            "special", "tv_special" -> ProviderMediaFormat.SPECIAL
            "music" -> ProviderMediaFormat.MUSIC
            "unknown", null -> null
            // `pv` (promotional video) and `cm` (commercial) are real MyAnimeList values with no
            // canonical equivalent, and are the reason this fallback must stay.
            else -> ProviderMediaFormat.OTHER
        }

    private fun mapMangaFormat(mediaType: String?): ProviderMediaFormat? =
        when (mediaType?.lowercase()) {
            "manga" -> ProviderMediaFormat.MANGA
            "novel", "light_novel" -> ProviderMediaFormat.NOVEL
            "one_shot" -> ProviderMediaFormat.ONE_SHOT
            "unknown", null -> null
            else -> ProviderMediaFormat.OTHER
        }

    private fun mapPublicationStatus(
        status: String?,
        isAnime: Boolean,
    ): ProviderMediaPublicationStatus =
        if (isAnime) {
            when (status) {
                "finished_airing" -> ProviderMediaPublicationStatus.FINISHED
                "currently_airing" -> ProviderMediaPublicationStatus.RELEASING
                "not_yet_aired" -> ProviderMediaPublicationStatus.UPCOMING
                else -> ProviderMediaPublicationStatus.UNKNOWN
            }
        } else {
            when (status) {
                "finished" -> ProviderMediaPublicationStatus.FINISHED
                "currently_publishing" -> ProviderMediaPublicationStatus.RELEASING
                "not_yet_published" -> ProviderMediaPublicationStatus.UPCOMING
                "on_hiatus" -> ProviderMediaPublicationStatus.HIATUS
                "discontinued" -> ProviderMediaPublicationStatus.CANCELLED
                else -> ProviderMediaPublicationStatus.UNKNOWN
            }
        }

    private fun Int?.positiveOrNull(): Int? = this?.takeIf { value -> value > 0 }

    private fun Int.positiveYearOrNull(): Int? = takeIf { value -> value in 1..9999 }

    private fun String.fourDigitYearOrNull(): Int? =
        Regex("(?:^|[^0-9])(\\d{4})(?:[^0-9]|$)")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.positiveYearOrNull()

    private fun Double.toScorePercentOrNull(): Int? =
        if (isFinite() && this in 0.1..10.0) {
            kotlin.math
                .floor(this * 10.0 + 0.5)
                .toInt()
                .takeIf { value -> value in 1..100 }
        } else {
            null
        }
}
