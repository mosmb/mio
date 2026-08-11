package io.morgan.mio.data.media.local

import io.morgan.mio.core.database.MioDatabase
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

public class RoomMediaLocalDataSource(
    private val database: MioDatabase,
) {
    public fun observeMedia(mediaId: MediaId): Flow<Media?> =
        database.mediaDao().observeMedia(mediaId.value).map { aggregate -> aggregate?.toDomain() }

    public fun observeReleases(mediaId: MediaId): Flow<List<Release>> =
        database.releaseDao().observeReleases(mediaId.value).map { releases ->
            releases.map { release -> release.toDomain() }.toList()
        }

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
}
