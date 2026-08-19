package io.morgan.mio.data.account.mapper

import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaFormat
import io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderReleaseTime

internal fun CompleteProviderImport.toInitialImportPersistence(
    identity: ProviderIdentity,
): InitialImportPersistenceSnapshot {
    require(identity.provider == provider)
    require(identity.accountId == accountId)

    val storedProvider = provider.toStoredValue()
    val localAccountId = ImportedIdentityFactory.accountId(storedProvider, identity.accountId.value)

    val account =
        SelectedAccountEntity(
            selectionKey = SELECTED_ACCOUNT_KEY,
            accountId = localAccountId.value,
            connectedProvider = storedProvider,
            displayName = identity.displayName,
            avatarReference = identity.avatar?.value,
        )
    val accountProviderId =
        AccountProviderIdEntity(
            accountId = localAccountId.value,
            provider = storedProvider,
            remoteAccountId = identity.accountId.value,
        )

    val remoteMediaIds = media.map(ProviderMedia::id).map { id -> id.value }.toSet()

    val mediaEntities = mutableListOf<MediaEntity>()
    val genreEntities = mutableListOf<MediaGenreEntity>()
    val relatedMediaEntities = mutableListOf<RelatedMediaEntity>()
    val releaseEntities = mutableListOf<ReleaseEntity>()
    val mediaProviderIdEntities = mutableListOf<MediaProviderIdEntity>()

    for (item in media) {
        val localMediaId = ImportedIdentityFactory.mediaId(storedProvider, item.id.value)

        mediaEntities +=
            MediaEntity(
                mediaId = localMediaId.value,
                mediaType = item.type.toStoredValue(),
                title = item.title,
                format = item.format?.toStoredValue(),
                publicationStatus = item.publicationStatus.toStoredValue(),
                synopsis = item.synopsis,
                posterReference = item.poster?.value,
                bannerReference = item.banner?.value,
                episodeTotal = item.totals.episodes,
                chapterTotal = item.totals.chapters,
                volumeTotal = item.totals.volumes,
            )

        item.genres.forEachIndexed { position, genre ->
            genreEntities += MediaGenreEntity(mediaId = localMediaId.value, genre = genre, position = position)
        }

        item.relatedMediaIds
            .map { relatedId -> relatedId.value }
            .filter { relatedRemoteId -> relatedRemoteId in remoteMediaIds }
            .forEachIndexed { position, relatedRemoteId ->
                relatedMediaEntities +=
                    RelatedMediaEntity(
                        mediaId = localMediaId.value,
                        relatedMediaId = ImportedIdentityFactory.mediaId(storedProvider, relatedRemoteId).value,
                        position = position,
                    )
            }

        item.releases.forEach { release ->
            releaseEntities +=
                ReleaseEntity(
                    mediaId = localMediaId.value,
                    progressUnit = release.unit.toStoredValue(),
                    unitNumber = release.number,
                    timeKind = release.time.toStoredTimeKind(),
                    releaseInstantUtcMs = release.time.instantOrNull(),
                )
        }

        mediaProviderIdEntities +=
            MediaProviderIdEntity(
                provider = storedProvider,
                remoteMediaId = item.id.value,
                mediaId = localMediaId.value,
            )
    }

    val entryEntities = mutableListOf<CollectionEntryEntity>()
    val entryProviderIdEntities = mutableListOf<CollectionEntryProviderIdEntity>()

    for (item in entries) {
        val localEntryId = ImportedIdentityFactory.collectionEntryId(storedProvider, item.id.value)
        val localMediaId = ImportedIdentityFactory.mediaId(storedProvider, item.mediaId.value)
        val status = item.status.toStoredValue()
        val progressUnit = item.progressUnit.toStoredValue()

        entryEntities +=
            CollectionEntryEntity(
                collectionEntryId = localEntryId.value,
                accountId = localAccountId.value,
                mediaId = localMediaId.value,
                progressUnit = progressUnit,
                remoteStatus = status,
                remoteProgress = item.progress,
                remoteTotal = item.total,
                remoteUpdatedAtUtcMs = item.updatedAt,
                effectiveStatus = status,
                effectiveProgress = item.progress,
                effectiveTotal = item.total,
                effectiveUpdatedAtUtcMs = item.updatedAt,
            )
        entryProviderIdEntities +=
            CollectionEntryProviderIdEntity(
                provider = storedProvider,
                remoteCollectionEntryId = item.id.value,
                collectionEntryId = localEntryId.value,
            )
    }

    return InitialImportPersistenceSnapshot(
        accountId = localAccountId,
        account = account,
        accountProviderId = accountProviderId,
        media = mediaEntities,
        genres = genreEntities,
        relatedMedia = relatedMediaEntities,
        releases = releaseEntities,
        mediaProviderIds = mediaProviderIdEntities,
        entries = entryEntities,
        entryProviderIds = entryProviderIdEntities,
    )
}

private fun ProviderKey.toStoredValue(): StoredConnectedProvider =
    when (this) {
        ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
        ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
    }

private fun ProviderMediaType.toStoredValue(): StoredMediaType =
    when (this) {
        ProviderMediaType.ANIME -> StoredMediaType.ANIME
        ProviderMediaType.MANGA -> StoredMediaType.MANGA
    }

private fun ProviderMediaFormat.toStoredValue(): StoredMediaFormat =
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

private fun ProviderMediaPublicationStatus.toStoredValue(): StoredMediaPublicationStatus =
    when (this) {
        ProviderMediaPublicationStatus.UPCOMING -> StoredMediaPublicationStatus.UPCOMING
        ProviderMediaPublicationStatus.RELEASING -> StoredMediaPublicationStatus.RELEASING
        ProviderMediaPublicationStatus.FINISHED -> StoredMediaPublicationStatus.FINISHED
        ProviderMediaPublicationStatus.CANCELLED -> StoredMediaPublicationStatus.CANCELLED
        ProviderMediaPublicationStatus.HIATUS -> StoredMediaPublicationStatus.HIATUS
        ProviderMediaPublicationStatus.UNKNOWN -> StoredMediaPublicationStatus.UNKNOWN
    }

private fun ProviderProgressUnit.toStoredValue(): StoredProgressUnit =
    when (this) {
        ProviderProgressUnit.EPISODE -> StoredProgressUnit.EPISODE
        ProviderProgressUnit.CHAPTER -> StoredProgressUnit.CHAPTER
        ProviderProgressUnit.VOLUME -> StoredProgressUnit.VOLUME
    }

private fun ProviderCollectionStatus.toStoredValue(): StoredCollectionStatus =
    when (this) {
        ProviderCollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
        ProviderCollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
        ProviderCollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
        ProviderCollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
        ProviderCollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
        ProviderCollectionStatus.REPEATING -> StoredCollectionStatus.REPEATING
    }

private fun ProviderReleaseTime.toStoredTimeKind(): StoredReleaseTimeKind =
    when (this) {
        is ProviderReleaseTime.Unknown -> StoredReleaseTimeKind.UNKNOWN
        is ProviderReleaseTime.Trusted -> StoredReleaseTimeKind.TRUSTED
        is ProviderReleaseTime.Untrusted -> StoredReleaseTimeKind.UNTRUSTED
    }

private fun ProviderReleaseTime.instantOrNull() =
    when (this) {
        is ProviderReleaseTime.Unknown -> null
        is ProviderReleaseTime.Trusted -> instant
        is ProviderReleaseTime.Untrusted -> instant
    }
