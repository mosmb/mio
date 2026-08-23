package io.morgan.mio.data.provider.anilist

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
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderRelease
import io.morgan.mio.data.provider.api.ProviderReleaseTime
import io.morgan.mio.data.provider.api.ProviderResult
import kotlin.time.Instant

internal object AniListMediaDetailsMapper {
    internal fun map(
        requestedMediaId: Int,
        node: AniListMediaDetailsNodeDto,
    ): ProviderResult<ProviderMediaDetails> {
        if (node.id != requestedMediaId) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }

        return try {
            val root = mapMedia(node, includeReleases = true)
            val seenIds = mutableSetOf(root.id)
            val related =
                node.relations
                    ?.nodes
                    .orEmpty()
                    .filterNotNull()
                    .mapNotNull { relatedNode ->
                        val relatedId = relatedNode.id?.takeIf { value -> value > 0 } ?: return@mapNotNull null
                        if (!seenIds.add(ProviderMediaId(relatedId.toString()))) return@mapNotNull null
                        try {
                            mapMedia(relatedNode, includeReleases = false)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
            val canonicalRoot =
                root.withRelatedIds(related.map(ProviderMedia::id))
            ProviderResult.Success(ProviderMediaDetails(ProviderKey.ANILIST, canonicalRoot, related))
        } catch (_: IllegalArgumentException) {
            ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
    }

    private fun mapMedia(
        node: AniListMediaDetailsNodeDto,
        includeReleases: Boolean,
    ): ProviderMedia {
        val remoteId = node.id?.takeIf { value -> value > 0 } ?: throw IllegalArgumentException()
        val type =
            when (node.type) {
                "ANIME" -> ProviderMediaType.ANIME
                "MANGA" -> ProviderMediaType.MANGA
                else -> throw IllegalArgumentException()
            }
        val title =
            listOfNotNull(
                node.title?.userPreferred,
                node.title?.romaji,
                node.title?.english,
                node.title?.native,
            ).firstOrNull(String::isNotBlank) ?: throw IllegalArgumentException()
        val mediaId = ProviderMediaId(remoteId.toString())
        val releases =
            if (includeReleases && type == ProviderMediaType.ANIME) {
                val seen = mutableSetOf<Pair<ProviderProgressUnit, Int>>()
                node.airingSchedule
                    ?.nodes
                    .orEmpty()
                    .filterNotNull()
                    .mapNotNull { airing ->
                        val episode = airing.episode?.takeIf { value -> value > 0 } ?: return@mapNotNull null
                        val airingAt = airing.airingAt?.takeIf { value -> value > 0 } ?: return@mapNotNull null
                        if (!seen.add(ProviderProgressUnit.EPISODE to episode)) return@mapNotNull null
                        ProviderRelease(
                            mediaId = mediaId,
                            unit = ProviderProgressUnit.EPISODE,
                            number = episode,
                            time = ProviderReleaseTime.Trusted(Instant.fromEpochSeconds(airingAt)),
                        )
                    }
            } else {
                emptyList()
            }

        return ProviderMedia(
            id = mediaId,
            type = type,
            title = title,
            format = mapFormat(node.format),
            publicationStatus = mapPublicationStatus(node.status),
            synopsis = node.description?.takeIf(String::isNotBlank),
            poster =
                listOfNotNull(
                    node.coverImage?.extraLarge,
                    node.coverImage?.large,
                    node.coverImage?.medium,
                ).firstOrNull(String::isNotBlank)?.let(::ProviderImageReference),
            banner = node.bannerImage?.takeIf(String::isNotBlank)?.let(::ProviderImageReference),
            totals =
                ProviderMediaTotals(
                    episodes = node.episodes?.takeIf { value -> value > 0 },
                    chapters = node.chapters?.takeIf { value -> value > 0 },
                    volumes = node.volumes?.takeIf { value -> value > 0 },
                ),
            genres =
                node.genres
                    .orEmpty()
                    .filterNotNull()
                    .filter(String::isNotBlank)
                    .distinct(),
            relatedMediaIds = emptyList(),
            releases = releases,
            releaseYear = node.seasonYear?.takeIf { value -> value > 0 },
            scorePercent = node.averageScore?.takeIf { value -> value in 1..100 },
        )
    }

    private fun ProviderMedia.withRelatedIds(ids: List<ProviderMediaId>): ProviderMedia =
        ProviderMedia(
            id = id,
            type = type,
            title = title,
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

    private fun mapFormat(value: String?): ProviderMediaFormat? =
        when (value) {
            "TV", "TV_SHORT" -> ProviderMediaFormat.TV
            "MOVIE" -> ProviderMediaFormat.MOVIE
            "OVA" -> ProviderMediaFormat.OVA
            "ONA" -> ProviderMediaFormat.ONA
            "SPECIAL" -> ProviderMediaFormat.SPECIAL
            "MUSIC" -> ProviderMediaFormat.MUSIC
            "MANGA" -> ProviderMediaFormat.MANGA
            "NOVEL" -> ProviderMediaFormat.NOVEL
            "ONE_SHOT" -> ProviderMediaFormat.ONE_SHOT
            null -> null
            else -> ProviderMediaFormat.OTHER
        }

    private fun mapPublicationStatus(value: String?): ProviderMediaPublicationStatus =
        when (value) {
            "FINISHED" -> ProviderMediaPublicationStatus.FINISHED
            "RELEASING" -> ProviderMediaPublicationStatus.RELEASING
            "NOT_YET_RELEASED" -> ProviderMediaPublicationStatus.UPCOMING
            "CANCELLED" -> ProviderMediaPublicationStatus.CANCELLED
            "HIATUS" -> ProviderMediaPublicationStatus.HIATUS
            else -> ProviderMediaPublicationStatus.UNKNOWN
        }
}
