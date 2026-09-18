package io.morgan.mio.data.media

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.media.local.RoomMediaSearchLocalDataSource
import io.morgan.mio.data.media.mapper.MediaPersistenceSnapshot
import io.morgan.mio.data.media.mapper.ProviderMediaDetailsPersistenceMapper
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaSearchPage
import io.morgan.mio.data.provider.api.ProviderMediaSearchRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.api.ProviderSearchCursor
import io.morgan.mio.domain.media.MediaSearchCommandOutcome
import io.morgan.mio.domain.media.MediaSearchFilter
import io.morgan.mio.domain.media.MediaSearchRepository
import io.morgan.mio.domain.media.MediaSearchResults
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultMediaSearchRepository(
    private val accountId: AccountId,
    private val providerBinding: ProviderBinding,
    private val credentialStore: ProviderCredentialStore,
    private val localDataSource: RoomMediaSearchLocalDataSource,
) : MediaSearchRepository {
    private val requestGeneration = AtomicLong()
    private val loadMoreMutex = Mutex()

    override fun observeSearch(
        query: String,
        filter: MediaSearchFilter,
    ): Flow<MediaSearchResults?> = localDataSource.observeSearch(accountId.value, query, filter)

    override suspend fun refreshSearch(
        query: String,
        filter: MediaSearchFilter,
    ): MediaSearchCommandOutcome {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return MediaSearchCommandOutcome.SUCCEEDED
        val generation = requestGeneration.incrementAndGet()
        val credentials = readCredentials() ?: return currentOrSuperseded(generation, MediaSearchCommandOutcome.FAILED)
        val requests = initialPageRequests(filter)
        val pages =
            fetchPages(normalizedQuery, requests, credentials)
                ?: return currentOrSuperseded(generation, MediaSearchCommandOutcome.FAILED)
        if (requestGeneration.get() != generation) return MediaSearchCommandOutcome.SUPERSEDED

        val snapshots = mapPage(filter, pages) ?: return MediaSearchCommandOutcome.FAILED
        val cursors = cursorsFrom(pages)
        return try {
            val committed =
                localDataSource.replaceFirstPage(
                    accountId = accountId.value,
                    query = normalizedQuery,
                    filter = filter,
                    provider = providerBinding.provider,
                    animeCursor = cursors.anime,
                    mangaCursor = cursors.manga,
                    snapshots = snapshots,
                    isCurrentRequest = { requestGeneration.get() == generation },
                )
            if (committed) MediaSearchCommandOutcome.SUCCEEDED else MediaSearchCommandOutcome.SUPERSEDED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            currentOrSuperseded(generation, MediaSearchCommandOutcome.FAILED)
        }
    }

    override suspend fun loadMore(
        query: String,
        filter: MediaSearchFilter,
    ): MediaSearchCommandOutcome {
        if (!loadMoreMutex.tryLock()) return MediaSearchCommandOutcome.SUPERSEDED
        try {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) return MediaSearchCommandOutcome.SUCCEEDED
            val generation = requestGeneration.incrementAndGet()
            val header =
                try {
                    localDataSource.getSearchQuery(accountId.value, normalizedQuery, filter, providerBinding.provider)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: SQLiteException) {
                    return MediaSearchCommandOutcome.FAILED
                } ?: return MediaSearchCommandOutcome.SUCCEEDED
            val requests = continuationRequests(filter, header.animeCursor, header.mangaCursor)
            if (requests.isEmpty()) return MediaSearchCommandOutcome.SUCCEEDED

            val credentials =
                readCredentials() ?: return currentOrSuperseded(generation, MediaSearchCommandOutcome.FAILED)
            val pages =
                fetchPages(normalizedQuery, requests, credentials)
                    ?: return currentOrSuperseded(generation, MediaSearchCommandOutcome.FAILED)
            if (requestGeneration.get() != generation) return MediaSearchCommandOutcome.SUPERSEDED

            val snapshots = mapPage(filter, pages) ?: return MediaSearchCommandOutcome.FAILED
            val pageCursors = cursorsFrom(pages)
            val nextAnimeCursor =
                if (pages.any {
                        it.type == ProviderMediaType.ANIME
                    }
                ) {
                    pageCursors.anime
                } else {
                    header.animeCursor
                }
            val nextMangaCursor =
                if (pages.any {
                        it.type == ProviderMediaType.MANGA
                    }
                ) {
                    pageCursors.manga
                } else {
                    header.mangaCursor
                }
            if (
                (pages.any { it.type == ProviderMediaType.ANIME } && nextAnimeCursor == header.animeCursor) ||
                (pages.any { it.type == ProviderMediaType.MANGA } && nextMangaCursor == header.mangaCursor)
            ) {
                return MediaSearchCommandOutcome.FAILED
            }

            return try {
                val committed =
                    localDataSource.appendPage(
                        accountId = accountId.value,
                        query = normalizedQuery,
                        filter = filter,
                        provider = providerBinding.provider,
                        expectedAnimeCursor = header.animeCursor,
                        expectedMangaCursor = header.mangaCursor,
                        animeCursor = nextAnimeCursor,
                        mangaCursor = nextMangaCursor,
                        snapshots = snapshots,
                        isCurrentRequest = { requestGeneration.get() == generation },
                    )
                if (committed) MediaSearchCommandOutcome.SUCCEEDED else MediaSearchCommandOutcome.SUPERSEDED
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: SQLiteException) {
                currentOrSuperseded(generation, MediaSearchCommandOutcome.FAILED)
            }
        } finally {
            loadMoreMutex.unlock()
        }
    }

    private suspend fun readCredentials(): ProviderCredentials? =
        try {
            credentialStore.read(providerBinding.provider)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: ProviderCredentialStorageException) {
            null
        }

    private suspend fun fetchPages(
        query: String,
        requests: List<PageRequest>,
        credentials: ProviderCredentials,
    ): List<TypePage>? =
        coroutineScope {
            val replies =
                requests
                    .map { request ->
                        async {
                            request to
                                try {
                                    providerBinding.mediaSearchDataSource.search(
                                        credentials = credentials,
                                        request =
                                            ProviderMediaSearchRequest(
                                                query = query,
                                                type = request.type,
                                                limit = request.limit,
                                                cursor = request.cursor,
                                            ),
                                    )
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (_: Exception) {
                                    ProviderResult.Failure(ProviderFailure.Unexpected)
                                }
                        }
                    }.awaitAll()
            val pages = mutableListOf<TypePage>()
            replies.forEach { (request, result) ->
                when (result) {
                    is ProviderResult.Failure -> return@coroutineScope null
                    is ProviderResult.Success -> {
                        val page = result.value
                        if (
                            page.items.size > request.limit ||
                            page.items.any { item -> item.type != request.type }
                        ) {
                            return@coroutineScope null
                        }
                        pages += TypePage(request.type, page)
                    }
                }
            }
            pages
        }

    private fun mapPage(
        filter: MediaSearchFilter,
        pages: List<TypePage>,
    ): List<MediaPersistenceSnapshot>? =
        try {
            val orderedItems =
                when (filter) {
                    MediaSearchFilter.ALL -> interleave(pages)
                    MediaSearchFilter.ANIME, MediaSearchFilter.MANGA ->
                        pages
                            .singleOrNull()
                            ?.page
                            ?.items
                            .orEmpty()
                }
            orderedItems
                .map { media -> ProviderMediaDetailsPersistenceMapper.mapSearchResult(providerBinding.provider, media) }
                .distinctBy { snapshot -> snapshot.media.id }
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun interleave(pages: List<TypePage>): List<ProviderMedia> {
        val anime =
            pages
                .singleOrNull { page -> page.type == ProviderMediaType.ANIME }
                ?.page
                ?.items
                .orEmpty()
        val manga =
            pages
                .singleOrNull { page -> page.type == ProviderMediaType.MANGA }
                ?.page
                ?.items
                .orEmpty()
        val results = ArrayList<ProviderMedia>(anime.size + manga.size)
        repeat(maxOf(anime.size, manga.size)) { index ->
            anime.getOrNull(index)?.let(results::add)
            manga.getOrNull(index)?.let(results::add)
        }
        return results
    }

    private fun cursorsFrom(pages: List<TypePage>): PageCursors =
        PageCursors(
            anime =
                pages
                    .singleOrNull { page -> page.type == ProviderMediaType.ANIME }
                    ?.page
                    ?.nextCursor
                    ?.value,
            manga =
                pages
                    .singleOrNull { page -> page.type == ProviderMediaType.MANGA }
                    ?.page
                    ?.nextCursor
                    ?.value,
        )

    private fun initialPageRequests(filter: MediaSearchFilter): List<PageRequest> =
        when (filter) {
            MediaSearchFilter.ALL ->
                listOf(
                    PageRequest(ProviderMediaType.ANIME, ALL_TYPE_PAGE_SIZE, null),
                    PageRequest(ProviderMediaType.MANGA, ALL_TYPE_PAGE_SIZE, null),
                )
            MediaSearchFilter.ANIME -> listOf(PageRequest(ProviderMediaType.ANIME, PAGE_SIZE, null))
            MediaSearchFilter.MANGA -> listOf(PageRequest(ProviderMediaType.MANGA, PAGE_SIZE, null))
        }

    private fun continuationRequests(
        filter: MediaSearchFilter,
        animeCursor: String?,
        mangaCursor: String?,
    ): List<PageRequest> =
        when (filter) {
            MediaSearchFilter.ALL ->
                buildList {
                    animeCursor?.let { cursor ->
                        add(PageRequest(ProviderMediaType.ANIME, ALL_TYPE_PAGE_SIZE, ProviderSearchCursor(cursor)))
                    }
                    mangaCursor?.let { cursor ->
                        add(PageRequest(ProviderMediaType.MANGA, ALL_TYPE_PAGE_SIZE, ProviderSearchCursor(cursor)))
                    }
                }
            MediaSearchFilter.ANIME ->
                animeCursor
                    ?.let { cursor ->
                        listOf(PageRequest(ProviderMediaType.ANIME, PAGE_SIZE, ProviderSearchCursor(cursor)))
                    }.orEmpty()
            MediaSearchFilter.MANGA ->
                mangaCursor
                    ?.let { cursor ->
                        listOf(PageRequest(ProviderMediaType.MANGA, PAGE_SIZE, ProviderSearchCursor(cursor)))
                    }.orEmpty()
        }

    private fun currentOrSuperseded(
        generation: Long,
        current: MediaSearchCommandOutcome,
    ): MediaSearchCommandOutcome =
        if (requestGeneration.get() == generation) current else MediaSearchCommandOutcome.SUPERSEDED

    private data class PageRequest(
        val type: ProviderMediaType,
        val limit: Int,
        val cursor: ProviderSearchCursor?,
    )

    private data class TypePage(
        val type: ProviderMediaType,
        val page: ProviderMediaSearchPage,
    )

    private data class PageCursors(
        val anime: String?,
        val manga: String?,
    )

    private companion object {
        const val PAGE_SIZE: Int = 20
        const val ALL_TYPE_PAGE_SIZE: Int = PAGE_SIZE / 2
    }
}
