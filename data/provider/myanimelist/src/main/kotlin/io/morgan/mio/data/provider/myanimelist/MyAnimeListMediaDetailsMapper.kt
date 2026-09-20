package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaDetails
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaTotals
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult

internal object MyAnimeListMediaDetailsMapper {
    internal fun map(
        requestedPath: String,
        response: MyAnimeListMediaDetailsResponseDto,
    ): ProviderResult<ProviderMediaDetails> {
        val match =
            Regex("^(anime|manga)/([1-9][0-9]*)$").matchEntire(requestedPath)
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val requestedType = match.groupValues[1]
        val requestedId =
            match.groupValues[2].toIntOrNull()
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        if (response.id != requestedId) return ProviderResult.Failure(ProviderFailure.MalformedResponse)

        return try {
            val root = mapMedia(response, requestedType, requestedPath)
            val seen = mutableSetOf(root.id)
            val related =
                response.relatedAnime
                    .orEmpty()
                    .asSequence()
                    .mapNotNull { related -> related?.node }
                    .mapNotNull { node -> mapOptional(node, "anime") }
                    .plus(
                        response.relatedManga
                            .orEmpty()
                            .asSequence()
                            .mapNotNull { related -> related?.node }
                            .mapNotNull { node -> mapOptional(node, "manga") },
                    ).filter { media -> seen.add(media.id) }
                    .toList()
            val canonicalRoot = root.withRelatedIds(related.map(ProviderMedia::id))
            ProviderResult.Success(ProviderMediaDetails(ProviderKey.MY_ANIME_LIST, canonicalRoot, related))
        } catch (_: IllegalArgumentException) {
            ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
    }

    private fun mapOptional(
        node: MyAnimeListMediaDetailsResponseDto,
        expectedType: String,
    ): ProviderMedia? =
        try {
            mapMedia(node, expectedType, "$expectedType/${node.id ?: return null}")
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun mapMedia(
        node: MyAnimeListMediaDetailsResponseDto,
        pathType: String,
        path: String,
    ): ProviderMedia {
        val id = node.id?.takeIf { value -> value > 0 } ?: throw IllegalArgumentException()
        val type =
            when (pathType) {
                "anime" -> ProviderMediaType.ANIME
                "manga" -> ProviderMediaType.MANGA
                else -> throw IllegalArgumentException()
            }
        if (!path.endsWith("/$id")) throw IllegalArgumentException()
        val title = node.title?.takeIf(String::isNotBlank) ?: throw IllegalArgumentException()
        val format =
            if (type == ProviderMediaType.ANIME) mapAnimeFormat(node.mediaType) else mapMangaFormat(node.mediaType)
        return ProviderMedia(
            id = ProviderMediaId("$pathType/$id"),
            type = type,
            title = title,
            titleEnglish = node.alternativeTitles?.en?.takeIf(String::isNotBlank),
            titleJapanese = node.alternativeTitles?.ja?.takeIf(String::isNotBlank),
            titleRomaji = title,
            format = format,
            publicationStatus = mapPublicationStatus(node.status, type == ProviderMediaType.ANIME),
            synopsis = node.synopsis?.takeIf(String::isNotBlank),
            poster =
                listOfNotNull(node.mainPicture?.large, node.mainPicture?.medium)
                    .firstOrNull(String::isNotBlank)
                    ?.let(::ProviderImageReference),
            banner = null,
            totals =
                ProviderMediaTotals(
                    episodes = if (type == ProviderMediaType.ANIME) node.numEpisodes?.positiveOrNull() else null,
                    chapters = if (type == ProviderMediaType.MANGA) node.numChapters?.positiveOrNull() else null,
                    volumes = if (type == ProviderMediaType.MANGA) node.numVolumes?.positiveOrNull() else null,
                ),
            genres =
                node.genres
                    .orEmpty()
                    .filterNotNull()
                    .mapNotNull { genre ->
                        genre.name?.takeIf(String::isNotBlank)
                    }.distinct(),
            relatedMediaIds = emptyList(),
            releases = emptyList(),
            releaseYear = node.startSeason?.year?.positiveYearOrNull() ?: node.startDate?.fourDigitYearOrNull(),
            scorePercent = node.mean?.toScorePercentOrNull(),
        )
    }

    private fun ProviderMedia.withRelatedIds(ids: List<ProviderMediaId>): ProviderMedia =
        ProviderMedia(
            id = id,
            type = type,
            title = title,
            titleEnglish = titleEnglish,
            titleJapanese = titleJapanese,
            titleRomaji = titleRomaji,
            format = format,
            publicationStatus = publicationStatus,
            synopsis = synopsis,
            poster = poster,
            banner = banner,
            totals = totals,
            genres = genres,
            relatedMediaIds = ids,
            releases = releases,
            releaseYear = releaseYear,
            scorePercent = scorePercent,
        )

    private fun mapAnimeFormat(value: String?): ProviderMediaFormat? =
        when (value?.lowercase()) {
            "tv" -> ProviderMediaFormat.TV
            "movie" -> ProviderMediaFormat.MOVIE
            "ova" -> ProviderMediaFormat.OVA
            "ona" -> ProviderMediaFormat.ONA
            "special", "tv_special" -> ProviderMediaFormat.SPECIAL
            "music" -> ProviderMediaFormat.MUSIC
            "unknown", null -> null
            else -> ProviderMediaFormat.OTHER
        }

    private fun mapMangaFormat(value: String?): ProviderMediaFormat? =
        when (value?.lowercase()) {
            "manga" -> ProviderMediaFormat.MANGA
            "novel", "light_novel" -> ProviderMediaFormat.NOVEL
            "one_shot" -> ProviderMediaFormat.ONE_SHOT
            "unknown", null -> null
            else -> ProviderMediaFormat.OTHER
        }

    private fun mapPublicationStatus(
        value: String?,
        anime: Boolean,
    ): ProviderMediaPublicationStatus =
        if (anime) {
            when (value) {
                "finished_airing" -> ProviderMediaPublicationStatus.FINISHED
                "currently_airing" -> ProviderMediaPublicationStatus.RELEASING
                "not_yet_aired" -> ProviderMediaPublicationStatus.UPCOMING
                else -> ProviderMediaPublicationStatus.UNKNOWN
            }
        } else {
            when (value) {
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
