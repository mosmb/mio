package io.morgan.mio.data.provider.anilist

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
import io.morgan.mio.data.provider.api.ProviderRelease
import io.morgan.mio.data.provider.api.ProviderReleaseTime
import io.morgan.mio.data.provider.api.ProviderResult
import kotlin.time.Instant

internal object AniListMediaListMapper {
    internal fun toCompleteImport(
        accountId: ProviderAccountId,
        entries: List<AniListMediaListEntryDto>,
    ): ProviderResult<CompleteProviderImport> {
        val mediaByRemoteId = LinkedHashMap<Int, ProviderMedia>()
        val collectionEntries = mutableListOf<ProviderCollectionEntry>()

        for (entryDto in entries) {
            val remoteEntryId =
                entryDto.id?.takeIf { value -> value > 0 }
                    ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            val mediaDto = entryDto.media ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            val remoteMediaId =
                mediaDto.id?.takeIf { value -> value > 0 }
                    ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)

            val type =
                when (mediaDto.type) {
                    "ANIME" -> ProviderMediaType.ANIME
                    "MANGA" -> ProviderMediaType.MANGA
                    else -> return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
                }

            val title =
                listOfNotNull(
                    mediaDto.title?.userPreferred,
                    mediaDto.title?.romaji,
                    mediaDto.title?.english,
                    mediaDto.title?.native,
                ).firstOrNull(String::isNotBlank)
                    ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)

            val status =
                when (entryDto.status) {
                    "CURRENT" -> ProviderCollectionStatus.CURRENT
                    "PLANNING" -> ProviderCollectionStatus.PLANNED
                    "PAUSED" -> ProviderCollectionStatus.PAUSED
                    "COMPLETED" -> ProviderCollectionStatus.COMPLETED
                    "DROPPED" -> ProviderCollectionStatus.DROPPED
                    "REPEATING" -> ProviderCollectionStatus.REPEATING
                    else -> return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
                }

            val rawProgress = entryDto.progress
            val rawProgressVolumes = entryDto.progressVolumes
            if ((rawProgress != null && rawProgress < 0) || (rawProgressVolumes != null && rawProgressVolumes < 0)) {
                return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            }

            val progressUnit: ProviderProgressUnit
            val progress: Int
            val rawTotal: Int?
            when {
                type == ProviderMediaType.ANIME -> {
                    progressUnit = ProviderProgressUnit.EPISODE
                    progress = rawProgress ?: 0
                    rawTotal = mediaDto.episodes
                }

                (rawProgressVolumes ?: 0) > 0 && (rawProgress ?: 0) == 0 -> {
                    progressUnit = ProviderProgressUnit.VOLUME
                    progress = rawProgressVolumes ?: 0
                    rawTotal = mediaDto.volumes
                }

                else -> {
                    progressUnit = ProviderProgressUnit.CHAPTER
                    progress = rawProgress ?: 0
                    rawTotal = mediaDto.chapters
                }
            }
            var total = rawTotal?.takeIf { value -> value > 0 }
            if (total != null && progress > total) {
                total = null
            }

            val updatedAtSeconds =
                entryDto.updatedAt?.takeIf { value -> value > 0 }
                    ?: entryDto.createdAt?.takeIf { value -> value > 0 }
                    ?: return ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData)
            val updatedAt = Instant.fromEpochSeconds(updatedAtSeconds)

            val format =
                when (mediaDto.format) {
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

            val publicationStatus =
                when (mediaDto.status) {
                    "FINISHED" -> ProviderMediaPublicationStatus.FINISHED
                    "RELEASING" -> ProviderMediaPublicationStatus.RELEASING
                    "NOT_YET_RELEASED" -> ProviderMediaPublicationStatus.UPCOMING
                    "CANCELLED" -> ProviderMediaPublicationStatus.CANCELLED
                    "HIATUS" -> ProviderMediaPublicationStatus.HIATUS
                    else -> ProviderMediaPublicationStatus.UNKNOWN
                }

            val synopsis = mediaDto.description?.takeIf(String::isNotBlank)
            val poster =
                listOfNotNull(
                    mediaDto.coverImage?.extraLarge,
                    mediaDto.coverImage?.large,
                    mediaDto.coverImage?.medium,
                ).firstOrNull(String::isNotBlank)?.let(::ProviderImageReference)
            val banner = mediaDto.bannerImage?.takeIf(String::isNotBlank)?.let(::ProviderImageReference)

            val totals =
                ProviderMediaTotals(
                    episodes = mediaDto.episodes?.takeIf { value -> value > 0 },
                    chapters = mediaDto.chapters?.takeIf { value -> value > 0 },
                    volumes = mediaDto.volumes?.takeIf { value -> value > 0 },
                )

            val genres =
                mediaDto.genres
                    .orEmpty()
                    .filterNotNull()
                    .filter(String::isNotBlank)
                    .distinct()

            val mediaId = ProviderMediaId(remoteMediaId.toString())

            val releases =
                if (type == ProviderMediaType.ANIME) {
                    val seen = mutableSetOf<Pair<ProviderProgressUnit, Int>>()
                    mediaDto.airingSchedule
                        ?.nodes
                        .orEmpty()
                        .filterNotNull()
                        .mapNotNull { node ->
                            val episode = node.episode?.takeIf { value -> value > 0 } ?: return@mapNotNull null
                            val airingAt = node.airingAt?.takeIf { value -> value > 0 } ?: return@mapNotNull null
                            val key = ProviderProgressUnit.EPISODE to episode
                            if (!seen.add(key)) return@mapNotNull null
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

            if (!mediaByRemoteId.containsKey(remoteMediaId)) {
                mediaByRemoteId[remoteMediaId] =
                    ProviderMedia(
                        id = mediaId,
                        type = type,
                        title = title,
                        format = format,
                        publicationStatus = publicationStatus,
                        synopsis = synopsis,
                        poster = poster,
                        banner = banner,
                        totals = totals,
                        genres = genres,
                        relatedMediaIds = emptyList(),
                        releases = releases,
                    )
            }

            collectionEntries +=
                ProviderCollectionEntry(
                    id = ProviderCollectionEntryId(remoteEntryId.toString()),
                    accountId = accountId,
                    mediaId = mediaId,
                    status = status,
                    progress = progress,
                    progressUnit = progressUnit,
                    total = total,
                    updatedAt = updatedAt,
                )
        }

        return try {
            ProviderResult.Success(
                CompleteProviderImport(
                    provider = ProviderKey.ANILIST,
                    accountId = accountId,
                    media = mediaByRemoteId.values.toList(),
                    entries = collectionEntries,
                ),
            )
        } catch (invalid: IllegalArgumentException) {
            ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
    }
}
