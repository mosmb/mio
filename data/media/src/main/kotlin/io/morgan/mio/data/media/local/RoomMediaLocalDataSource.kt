package io.morgan.mio.data.media.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.media.mapper.MediaDetailsPersistenceSnapshot
import io.morgan.mio.data.media.mapper.MediaPersistenceSnapshot
import io.morgan.mio.data.media.mapper.toDomain
import io.morgan.mio.data.media.mapper.toEntity
import io.morgan.mio.data.media.mapper.toGenreEntities
import io.morgan.mio.data.media.mapper.toRelatedMediaEntities
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.morgan.mio.data.media.mapper.toEntity as providerIdToEntity
import io.morgan.mio.data.media.mapper.toEntity as releaseToEntity

@Inject
public class RoomMediaLocalDataSource(
    private val database: MioDatabase,
) {
    public fun observeMedia(mediaId: MediaId): Flow<Media?> =
        database.mediaDao().observeMedia(mediaId.value).map { aggregate -> aggregate?.toDomain() }

    public fun observeRelatedMedia(mediaId: MediaId): Flow<List<Media>> =
        database.mediaDao().observeRelatedMedia(mediaId.value).map { aggregates ->
            aggregates.map { aggregate -> aggregate.toDomain() }.toList()
        }

    public fun observeReleases(mediaId: MediaId): Flow<List<Release>> =
        database.releaseDao().observeReleases(mediaId.value).map { releases ->
            releases.map { release -> release.toDomain() }.toList()
        }

    internal suspend fun findMediaProviderId(
        mediaId: MediaId,
        provider: StoredConnectedProvider,
    ): String? =
        database
            .providerIdDao()
            .findMediaProviderId(mediaId.value, provider)
            ?.remoteMediaId

    internal suspend fun replaceInitialMediaSnapshot(snapshot: MediaPersistenceSnapshot) {
        replaceInitialMediaSnapshots(listOf(snapshot))
    }

    internal suspend fun replaceInitialMediaSnapshots(input: List<MediaPersistenceSnapshot>) {
        val snapshots = input.toList()
        val mediaIds = snapshots.map { snapshot -> snapshot.media.id }.distinct()
        require(mediaIds.size == snapshots.size)
        snapshots.forEach { snapshot ->
            require(snapshot.releases.all { release -> release.mediaId == snapshot.media.id })
            require(snapshot.providerIds.all { mapping -> mapping.mediaId == snapshot.media.id })
        }
        val mediaEntities = snapshots.map { snapshot -> snapshot.media.toEntity() }
        val genres = snapshots.flatMap { snapshot -> snapshot.media.toGenreEntities() }
        val relatedMedia = snapshots.flatMap { snapshot -> snapshot.media.toRelatedMediaEntities() }
        val releases = snapshots.flatMap { snapshot -> snapshot.releases.toList().map { it.releaseToEntity() } }
        val providerMappings =
            snapshots
                .flatMap { snapshot -> snapshot.providerIds.toList() }
                .map { mapping -> mapping.providerIdToEntity() }

        database.inTransaction {
            database.mediaDao().upsertMedia(mediaEntities)
            mediaIds.forEach { mediaId ->
                database.mediaDao().deleteGenres(mediaId.value)
                database.mediaDao().deleteRelatedMedia(mediaId.value)
                database.releaseDao().deleteReleases(mediaId.value)
                database.providerIdDao().deleteMediaProviderIds(mediaId.value)
            }
            if (genres.isNotEmpty()) database.mediaDao().upsertGenres(genres)
            if (relatedMedia.isNotEmpty()) database.mediaDao().upsertRelatedMedia(relatedMedia)
            if (releases.isNotEmpty()) database.releaseDao().upsertReleases(releases)
            if (providerMappings.isNotEmpty()) database.providerIdDao().upsertMediaProviderIds(providerMappings)
        }
    }

    internal suspend fun replaceMediaDetailsSnapshot(snapshot: MediaDetailsPersistenceSnapshot) {
        val owner = snapshot.owner
        val related = snapshot.related
        val ownerMediaId = owner.media.id.value
        val media = listOf(owner.media) + related.map { item -> item.media }
        val genres = media.flatMap { item -> item.toGenreEntities() }
        val relations = owner.media.toRelatedMediaEntities()
        val releases = owner.releases.map { release -> release.releaseToEntity() }
        val provider = requireNotNull(owner.providerIds.singleOrNull()?.provider)
        val mappings =
            (listOf(owner) + related).map { item ->
                require(item.providerIds.size == 1)
                require(item.providerIds.single().provider == provider)
                item.providerIds.single().providerIdToEntity()
            }

        database.inTransaction {
            database.mediaDao().upsertMedia(
                media.map { item ->
                    val incoming = item.toEntity()
                    val existing = database.mediaDao().getMedia(item.id.value)
                    incoming.copy(
                        titleEnglish = incoming.titleEnglish ?: existing?.titleEnglish,
                        titleJapanese = incoming.titleJapanese ?: existing?.titleJapanese,
                        titleRomaji = incoming.titleRomaji ?: existing?.titleRomaji,
                    )
                },
            )
            database.mediaDao().deleteGenres(ownerMediaId)
            related.forEach { item -> database.mediaDao().deleteGenres(item.media.id.value) }
            database.mediaDao().deleteRelatedMedia(ownerMediaId)
            database.releaseDao().deleteReleases(ownerMediaId)
            database.providerIdDao().deleteMediaProviderId(ownerMediaId, provider)
            related.forEach { item -> database.providerIdDao().deleteMediaProviderId(item.media.id.value, provider) }
            if (genres.isNotEmpty()) database.mediaDao().upsertGenres(genres)
            if (relations.isNotEmpty()) database.mediaDao().upsertRelatedMedia(relations)
            if (releases.isNotEmpty()) database.releaseDao().upsertReleases(releases)
            database.providerIdDao().upsertMediaProviderIds(mappings)
        }
    }
}
