package io.morgan.mio.data.sync.mapper

import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderReleaseTime
import io.morgan.mio.domain.model.AccountId

internal data class RefreshPersistenceSnapshot(
    val media: List<MediaEntity>,
    val genres: List<MediaGenreEntity>,
    val relatedMedia: List<RelatedMediaEntity>,
    val releases: List<ReleaseEntity>,
    val mediaProviderIds: List<MediaProviderIdEntity>,
    val entries: List<CollectionEntryEntity>,
    val entryProviderIds: List<CollectionEntryProviderIdEntity>,
)

internal fun CompleteProviderImport.toRefreshPersistence(
    localAccountId: AccountId,
    existingMediaLocalIds: Map<String, String> = emptyMap(),
    existingEntryLocalIds: Map<String, String> = emptyMap(),
): RefreshPersistenceSnapshot {
    val storedProvider = provider.toStoredProvider()
    val remoteMediaIds = media.map(ProviderMedia::id).toSet()

    fun existingOrStableMediaId(remoteId: String): String =
        existingMediaLocalIds[remoteId] ?: localMediaId(storedProvider, remoteId)

    fun existingOrStableEntryId(remoteId: String): String =
        existingEntryLocalIds[remoteId] ?: localEntryId(storedProvider, remoteId)

    val mediaEntities = media.map { item -> item.toEntity(existingOrStableMediaId(item.id.value)) }
    val genres =
        media.flatMap { item ->
            val localId = existingOrStableMediaId(item.id.value)
            item.genres.mapIndexed { position, genre ->
                MediaGenreEntity(mediaId = localId, genre = genre, position = position)
            }
        }
    val relatedMedia =
        media.flatMap { item ->
            val localId = existingOrStableMediaId(item.id.value)
            item.relatedMediaIds
                .filter { relatedId -> relatedId in remoteMediaIds }
                .mapIndexed { position, relatedId ->
                    RelatedMediaEntity(
                        mediaId = localId,
                        relatedMediaId = existingOrStableMediaId(relatedId.value),
                        position = position,
                    )
                }
        }
    val releases =
        media.flatMap { item ->
            item.releases.map { release ->
                ReleaseEntity(
                    mediaId = existingOrStableMediaId(item.id.value),
                    progressUnit = release.unit.toStoredUnit(),
                    unitNumber = release.number,
                    timeKind = release.time.toStoredKind(),
                    releaseInstantUtcMs = release.time.toInstantOrNull(),
                )
            }
        }
    val mediaProviderIds =
        media.map { item ->
            MediaProviderIdEntity(
                provider = storedProvider,
                remoteMediaId = item.id.value,
                mediaId = existingOrStableMediaId(item.id.value),
            )
        }
    val entries =
        entries.map { entry ->
            entry.toEntity(
                localAccountId = localAccountId,
                localMediaId = existingOrStableMediaId(entry.mediaId.value),
                localEntryId = existingOrStableEntryId(entry.id.value),
            )
        }
    val entryProviderIds =
        this.entries.map { remoteEntry ->
            CollectionEntryProviderIdEntity(
                provider = storedProvider,
                remoteCollectionEntryId = remoteEntry.id.value,
                collectionEntryId = existingOrStableEntryId(remoteEntry.id.value),
            )
        }
    return RefreshPersistenceSnapshot(
        media = mediaEntities,
        genres = genres,
        relatedMedia = relatedMedia,
        releases = releases,
        mediaProviderIds = mediaProviderIds,
        entries = entries,
        entryProviderIds = entryProviderIds,
    )
}

private fun ProviderMedia.toEntity(localId: String): MediaEntity =
    MediaEntity(
        mediaId = localId,
        mediaType = type.toStoredType(),
        title = title,
        format = format?.toStoredFormat(),
        publicationStatus = publicationStatus.toStoredPublicationStatus(),
        synopsis = synopsis,
        posterReference = poster?.value,
        bannerReference = banner?.value,
        episodeTotal = totals.episodes,
        chapterTotal = totals.chapters,
        volumeTotal = totals.volumes,
        releaseYear = releaseYear,
        scorePercent = scorePercent,
    )

private fun ProviderCollectionEntry.toEntity(
    localAccountId: AccountId,
    localMediaId: String,
    localEntryId: String,
): CollectionEntryEntity {
    val storedStatus = status.toStoredStatus()
    val storedUnit = progressUnit.toStoredUnit()
    return CollectionEntryEntity(
        collectionEntryId = localEntryId,
        accountId = localAccountId.value,
        mediaId = localMediaId,
        progressUnit = storedUnit,
        remoteStatus = storedStatus,
        remoteProgress = progress,
        remoteTotal = total,
        remoteUpdatedAtUtcMs = updatedAt,
        effectiveStatus = storedStatus,
        effectiveProgress = progress,
        effectiveTotal = total,
        effectiveUpdatedAtUtcMs = updatedAt,
    )
}

private fun localMediaId(
    provider: StoredConnectedProvider,
    remoteId: String,
): String = stableId(provider, "media", remoteId)

private fun localEntryId(
    provider: StoredConnectedProvider,
    remoteId: String,
): String = stableId(provider, "collection-entry", remoteId)

private fun stableId(
    provider: StoredConnectedProvider,
    kind: String,
    remoteId: String,
): String {
    require(remoteId.isNotBlank())
    return StableOpaqueIdFactory.create(provider.serializedValue, kind, remoteId)
}

private fun ProviderKey.toStoredProvider(): StoredConnectedProvider =
    when (this) {
        ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
        ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
    }

private fun ProviderMediaType.toStoredType(): StoredMediaType =
    when (this) {
        ProviderMediaType.ANIME -> StoredMediaType.ANIME
        ProviderMediaType.MANGA -> StoredMediaType.MANGA
    }

private fun ProviderMediaFormat.toStoredFormat(): StoredMediaFormat =
    when (this) {
        ProviderMediaFormat.TV -> StoredMediaFormat.TV
        ProviderMediaFormat.MOVIE -> StoredMediaFormat.MOVIE
        ProviderMediaFormat.OVA -> StoredMediaFormat.OVA
        ProviderMediaFormat.ONA -> StoredMediaFormat.ONA
        ProviderMediaFormat.SPECIAL -> StoredMediaFormat.SPECIAL
        ProviderMediaFormat.MUSIC -> StoredMediaFormat.MUSIC
        ProviderMediaFormat.MANGA -> StoredMediaFormat.MANGA
        ProviderMediaFormat.NOVEL -> StoredMediaFormat.NOVEL
        ProviderMediaFormat.ONE_SHOT -> StoredMediaFormat.ONE_SHOT
        ProviderMediaFormat.OTHER -> StoredMediaFormat.OTHER
    }

private fun ProviderMediaPublicationStatus.toStoredPublicationStatus(): StoredMediaPublicationStatus =
    when (this) {
        ProviderMediaPublicationStatus.UPCOMING -> StoredMediaPublicationStatus.UPCOMING
        ProviderMediaPublicationStatus.RELEASING -> StoredMediaPublicationStatus.RELEASING
        ProviderMediaPublicationStatus.FINISHED -> StoredMediaPublicationStatus.FINISHED
        ProviderMediaPublicationStatus.CANCELLED -> StoredMediaPublicationStatus.CANCELLED
        ProviderMediaPublicationStatus.HIATUS -> StoredMediaPublicationStatus.HIATUS
        ProviderMediaPublicationStatus.UNKNOWN -> StoredMediaPublicationStatus.UNKNOWN
    }

private fun ProviderProgressUnit.toStoredUnit(): StoredProgressUnit =
    when (this) {
        ProviderProgressUnit.EPISODE -> StoredProgressUnit.EPISODE
        ProviderProgressUnit.CHAPTER -> StoredProgressUnit.CHAPTER
        ProviderProgressUnit.VOLUME -> StoredProgressUnit.VOLUME
    }

private fun ProviderCollectionStatus.toStoredStatus(): StoredCollectionStatus =
    when (this) {
        ProviderCollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
        ProviderCollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
        ProviderCollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
        ProviderCollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
        ProviderCollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
        ProviderCollectionStatus.REPEATING -> StoredCollectionStatus.REPEATING
    }

private fun ProviderReleaseTime.toStoredKind(): StoredReleaseTimeKind =
    when (this) {
        ProviderReleaseTime.Unknown -> StoredReleaseTimeKind.UNKNOWN
        is ProviderReleaseTime.Trusted -> StoredReleaseTimeKind.TRUSTED
        is ProviderReleaseTime.Untrusted -> StoredReleaseTimeKind.UNTRUSTED
    }

private fun ProviderReleaseTime.toInstantOrNull() =
    when (this) {
        ProviderReleaseTime.Unknown -> null
        is ProviderReleaseTime.Trusted -> instant
        is ProviderReleaseTime.Untrusted -> instant
    }
