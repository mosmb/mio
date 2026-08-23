package io.morgan.mio.data.media.mapper

import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaDetails
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderRelease
import io.morgan.mio.data.provider.api.ProviderReleaseTime
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaScore
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal

internal object ProviderMediaDetailsPersistenceMapper {
    internal fun map(details: ProviderMediaDetails): MediaDetailsPersistenceSnapshot {
        val storedProvider = details.provider.toStoredValue()
        val root = details.root.toDomain(details.provider)
        val related = details.related.map { media -> media.toDomain(details.provider) }
        val relatedSnapshots =
            details.related.zip(related).map { (providerMedia, media) ->
                MediaPersistenceSnapshot(
                    media = media,
                    providerIds =
                        listOf(
                            MediaProviderIdInput(
                                mediaId = media.id,
                                provider = storedProvider,
                                remoteId = providerMedia.id.value,
                            ),
                        ),
                )
            }
        val rootRemoteId = details.root.id.value
        return MediaDetailsPersistenceSnapshot(
            owner =
                MediaPersistenceSnapshot(
                    media = root,
                    releases = details.root.releases(details.provider),
                    providerIds =
                        listOf(
                            MediaProviderIdInput(
                                mediaId = root.id,
                                provider = storedProvider,
                                remoteId = rootRemoteId,
                            ),
                        ),
                ),
            related = relatedSnapshots,
        )
    }

    private fun ProviderMedia.toDomain(provider: ProviderKey): Media {
        val localId = MediaId(localId(provider, id.value))
        return Media(
            id = localId,
            type = type.toDomain(),
            title = MediaTitle(title),
            format = format?.toDomain(),
            publicationStatus = publicationStatus.toDomain(),
            synopsis = synopsis,
            artwork =
                Artwork(
                    poster = poster?.toDomain(),
                    banner = banner?.toDomain(),
                ),
            totals =
                MediaTotals(
                    episodes = totals.episodes?.let(::UnitTotal),
                    chapters = totals.chapters?.let(::UnitTotal),
                    volumes = totals.volumes?.let(::UnitTotal),
                ),
            genres = genres,
            relatedMediaIds = relatedMediaIds.map { relatedId -> MediaId(localId(provider, relatedId.value)) },
            releaseYear = releaseYear,
            score = scorePercent?.let(::MediaScore),
        )
    }

    private fun ProviderMedia.releases(provider: ProviderKey): List<Release> =
        releases.map { release -> release.toDomain(provider, id.value) }

    private fun ProviderRelease.toDomain(
        provider: ProviderKey,
        rootRemoteId: String,
    ): Release {
        require(mediaId.value == rootRemoteId)
        return Release(
            mediaId = MediaId(localId(provider, rootRemoteId)),
            unit = unit.toDomain(),
            number = UnitNumber(number),
            time = time.toDomain(),
        )
    }

    private fun ProviderReleaseTime.toDomain(): ReleaseTime =
        when (this) {
            ProviderReleaseTime.Unknown -> ReleaseTime.Unknown
            is ProviderReleaseTime.Trusted -> ReleaseTime.Trusted(instant)
            is ProviderReleaseTime.Untrusted -> ReleaseTime.Untrusted(instant)
        }

    private fun ProviderProgressUnit.toDomain(): ProgressUnit =
        when (this) {
            ProviderProgressUnit.EPISODE -> ProgressUnit.EPISODE
            ProviderProgressUnit.CHAPTER -> ProgressUnit.CHAPTER
            ProviderProgressUnit.VOLUME -> ProgressUnit.VOLUME
        }

    private fun ProviderImageReference.toDomain(): ImageReference = ImageReference(value)

    private fun ProviderMediaType.toDomain(): MediaType =
        when (this) {
            ProviderMediaType.ANIME -> MediaType.ANIME
            ProviderMediaType.MANGA -> MediaType.MANGA
        }

    private fun ProviderMediaFormat.toDomain(): MediaFormat =
        when (this) {
            ProviderMediaFormat.TV -> MediaFormat.TV
            ProviderMediaFormat.MOVIE -> MediaFormat.MOVIE
            ProviderMediaFormat.OVA -> MediaFormat.OVA
            ProviderMediaFormat.ONA -> MediaFormat.ONA
            ProviderMediaFormat.SPECIAL -> MediaFormat.SPECIAL
            ProviderMediaFormat.MUSIC -> MediaFormat.MUSIC
            ProviderMediaFormat.MANGA -> MediaFormat.MANGA
            ProviderMediaFormat.NOVEL -> MediaFormat.NOVEL
            ProviderMediaFormat.ONE_SHOT -> MediaFormat.ONE_SHOT
            ProviderMediaFormat.OTHER -> MediaFormat.OTHER
        }

    private fun ProviderMediaPublicationStatus.toDomain(): MediaPublicationStatus =
        when (this) {
            ProviderMediaPublicationStatus.UPCOMING -> MediaPublicationStatus.UPCOMING
            ProviderMediaPublicationStatus.RELEASING -> MediaPublicationStatus.RELEASING
            ProviderMediaPublicationStatus.FINISHED -> MediaPublicationStatus.FINISHED
            ProviderMediaPublicationStatus.CANCELLED -> MediaPublicationStatus.CANCELLED
            ProviderMediaPublicationStatus.HIATUS -> MediaPublicationStatus.HIATUS
            ProviderMediaPublicationStatus.UNKNOWN -> MediaPublicationStatus.UNKNOWN
        }

    private fun ProviderKey.toStoredValue(): StoredConnectedProvider =
        when (this) {
            ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
            ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
        }

    private fun localId(
        provider: ProviderKey,
        remoteId: String,
    ): String =
        StableOpaqueIdFactory.create(
            namespace =
                when (provider) {
                    ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST.serializedValue
                    ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST.serializedValue
                },
            kind = "media",
            remoteId = remoteId,
        )
}
