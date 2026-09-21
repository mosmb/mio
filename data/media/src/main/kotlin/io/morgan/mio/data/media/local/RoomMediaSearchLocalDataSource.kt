package io.morgan.mio.data.media.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.MediaSearchQueryEntity
import io.morgan.mio.core.database.entity.MediaSearchResultEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.media.mapper.MediaPersistenceSnapshot
import io.morgan.mio.data.media.mapper.toDomain
import io.morgan.mio.data.media.mapper.toEntity
import io.morgan.mio.data.media.mapper.toGenreEntities
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.media.MediaSearchFilter
import io.morgan.mio.domain.media.MediaSearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Inject
public class RoomMediaSearchLocalDataSource(
    private val database: MioDatabase,
) {
    public fun observeSearch(
        accountId: String,
        query: String,
        filter: MediaSearchFilter,
    ): Flow<MediaSearchResults?> {
        require(accountId.isNotBlank())
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return flowOf(null)
        val storedFilter = filter.toStoredValue()
        return database.invalidationTracker
            .createFlow(
                "media_search_query",
                "media_search_result",
                "media",
                "media_genre",
                "related_media",
            ).map {
                database.inTransaction {
                    val dao = database.mediaSearchDao()
                    val header = dao.getQuery(accountId, storedFilter)
                    if (header?.normalizedQuery != normalizedQuery) {
                        null
                    } else {
                        MediaSearchResults(
                            media = dao.getResults(accountId, storedFilter, normalizedQuery).map { it.toDomain() },
                            hasMore = header.animeCursor != null || header.mangaCursor != null,
                        )
                    }
                }
            }
    }

    internal suspend fun getSearchQuery(
        accountId: String,
        query: String,
        filter: MediaSearchFilter,
        provider: ProviderKey,
    ): MediaSearchQueryEntity? =
        database
            .mediaSearchDao()
            .getQuery(accountId, filter.toStoredValue())
            ?.takeIf { header ->
                header.normalizedQuery == query.trim() && header.provider == provider.toStoredValue()
            }

    internal suspend fun replaceFirstPage(
        accountId: String,
        query: String,
        filter: MediaSearchFilter,
        provider: ProviderKey,
        animeCursor: String?,
        mangaCursor: String?,
        snapshots: List<MediaPersistenceSnapshot>,
        isCurrentRequest: () -> Boolean,
    ): Boolean {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty())
        requireSnapshots(provider, snapshots)
        val storedFilter = filter.toStoredValue()
        val storedProvider = provider.toStoredValue()

        return database.inTransaction {
            if (!isCurrentRequest() || !database.selectedAccountDao().isSelectedAccount(accountId, storedProvider)) {
                return@inTransaction false
            }
            insertSearchSummaries(snapshots)
            database.mediaSearchDao().upsertQuery(
                MediaSearchQueryEntity(
                    accountId = accountId,
                    filter = storedFilter,
                    provider = storedProvider,
                    normalizedQuery = normalizedQuery,
                    animeCursor = animeCursor,
                    mangaCursor = mangaCursor,
                ),
            )
            database.mediaSearchDao().deleteResults(accountId, storedFilter)
            val rows =
                snapshots.mapIndexed { position, snapshot ->
                    MediaSearchResultEntity(
                        accountId = accountId,
                        filter = storedFilter,
                        mediaId = snapshot.media.id.value,
                        position = position,
                    )
                }
            if (rows.isNotEmpty()) database.mediaSearchDao().upsertResults(rows)
            true
        }
    }

    internal suspend fun appendPage(
        accountId: String,
        query: String,
        filter: MediaSearchFilter,
        provider: ProviderKey,
        expectedAnimeCursor: String?,
        expectedMangaCursor: String?,
        animeCursor: String?,
        mangaCursor: String?,
        snapshots: List<MediaPersistenceSnapshot>,
        isCurrentRequest: () -> Boolean,
    ): Boolean {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty())
        requireSnapshots(provider, snapshots)
        val storedFilter = filter.toStoredValue()
        val storedProvider = provider.toStoredValue()

        return database.inTransaction {
            if (!isCurrentRequest() || !database.selectedAccountDao().isSelectedAccount(accountId, storedProvider)) {
                return@inTransaction false
            }
            val dao = database.mediaSearchDao()
            val header = dao.getQuery(accountId, storedFilter) ?: return@inTransaction false
            if (
                header.normalizedQuery != normalizedQuery ||
                header.provider != storedProvider ||
                header.animeCursor != expectedAnimeCursor ||
                header.mangaCursor != expectedMangaCursor
            ) {
                return@inTransaction false
            }

            insertSearchSummaries(snapshots)
            val existingIds = dao.getResultMediaIds(accountId, storedFilter).toHashSet()
            val newSnapshots =
                snapshots
                    .distinctBy { snapshot -> snapshot.media.id.value }
                    .filterNot { snapshot -> snapshot.media.id.value in existingIds }
            val startPosition = existingIds.size
            val rows =
                newSnapshots.mapIndexed { offset, snapshot ->
                    MediaSearchResultEntity(
                        accountId = accountId,
                        filter = storedFilter,
                        mediaId = snapshot.media.id.value,
                        position = startPosition + offset,
                    )
                }
            if (rows.isNotEmpty()) dao.upsertResults(rows)
            dao.upsertQuery(
                header.copy(
                    animeCursor = animeCursor,
                    mangaCursor = mangaCursor,
                ),
            )
            true
        }
    }

    internal suspend fun insertSearchSummaries(snapshots: List<MediaPersistenceSnapshot>) {
        if (snapshots.isEmpty()) return
        val mediaDao = database.mediaDao()
        val mediaIds = snapshots.map { snapshot -> snapshot.media.id.value }.distinct()
        val existing = mediaDao.getMediaIds(mediaIds).toHashSet()
        snapshots.filter { it.media.id.value in existing }.forEach { snapshot ->
            val title = snapshot.media.title
            if (title.english != null || title.japanese != null || title.romaji != null) {
                mediaDao.enrichTitleNames(snapshot.media.id.value, title.english, title.japanese, title.romaji)
            }
        }
        val newSnapshots = snapshots.filterNot { snapshot -> snapshot.media.id.value in existing }
        mediaDao.insertMediaIfAbsent(snapshots.map { snapshot -> snapshot.media.toEntity() })

        val newGenres = newSnapshots.flatMap { snapshot -> snapshot.media.toGenreEntities() }
        if (newGenres.isNotEmpty()) mediaDao.upsertGenres(newGenres)

        val providerMappings =
            snapshots
                .flatMap { snapshot -> snapshot.providerIds }
                .map { mapping ->
                    MediaProviderIdEntity(
                        provider = mapping.provider,
                        remoteMediaId = mapping.remoteId,
                        mediaId = mapping.mediaId.value,
                    )
                }
        if (providerMappings.isNotEmpty()) database.providerIdDao().upsertMediaProviderIds(providerMappings)
    }

    private fun requireSnapshots(
        provider: ProviderKey,
        snapshots: List<MediaPersistenceSnapshot>,
    ) {
        val storedProvider = provider.toStoredValue()
        require(snapshots.map { snapshot -> snapshot.media.id }.distinct().size == snapshots.size)
        snapshots.forEach { snapshot ->
            require(snapshot.releases.isEmpty())
            require(snapshot.media.relatedMediaIds.isEmpty())
            require(snapshot.providerIds.size == 1)
            require(snapshot.providerIds.single().mediaId == snapshot.media.id)
            require(snapshot.providerIds.single().provider == storedProvider)
        }
    }

    private fun MediaSearchFilter.toStoredValue(): String = name

    private fun ProviderKey.toStoredValue(): StoredConnectedProvider =
        when (this) {
            ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
            ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
        }
}
