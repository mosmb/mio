package io.morgan.mio.data.media

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.MediaSearchQueryEntity
import io.morgan.mio.core.database.entity.MediaSearchResultEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.media.local.RoomMediaSearchLocalDataSource
import io.morgan.mio.data.media.mapper.ProviderMediaDetailsPersistenceMapper
import io.morgan.mio.data.media.mapper.toDomain
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBrowseCategory
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaBrowseRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaBrowseCategory
import io.morgan.mio.domain.media.MediaBrowseRepository
import io.morgan.mio.domain.media.MediaSearchCommandOutcome
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultMediaBrowseRepository(
    private val accountId: AccountId,
    private val providerBinding: ProviderBinding,
    private val credentialStore: ProviderCredentialStore,
    private val database: MioDatabase,
    private val summaries: RoomMediaSearchLocalDataSource,
    private val clock: Clock,
) : MediaBrowseRepository {
    private val generations = ConcurrentHashMap<String, AtomicLong>()

    override fun categories(type: MediaType): List<MediaBrowseCategory> =
        providerBinding.mediaBrowseDataSource
            ?.categories(ProviderMediaType.valueOf(type.name))
            .orEmpty()
            .map { MediaBrowseCategory.valueOf(it.name) }

    override fun observe(
        type: MediaType,
        category: MediaBrowseCategory,
    ): Flow<List<Media>?> {
        val key = cacheKey(type, category)
        return database.invalidationTracker
            .createFlow(
                "media_search_query",
                "media_search_result",
                "media",
                "media_genre",
                "related_media",
            ).map {
                val identity = identity(type, category)
                database.inTransaction {
                    val dao = database.mediaSearchDao()
                    if (dao.getQuery(accountId.value, key)?.normalizedQuery != identity) {
                        null
                    } else {
                        dao.getResults(accountId.value, key, identity).map { it.toDomain() }
                    }
                }
            }
    }

    override suspend fun refresh(
        type: MediaType,
        category: MediaBrowseCategory,
    ): MediaSearchCommandOutcome {
        val source = providerBinding.mediaBrowseDataSource ?: return MediaSearchCommandOutcome.FAILED
        if (category !in categories(type)) return MediaSearchCommandOutcome.FAILED
        val key = cacheKey(type, category)
        val counter = generations.getOrPut(key) { AtomicLong() }
        val generation = counter.incrementAndGet()
        val request = request(type, category)
        val identity = requestIdentity(request)
        return try {
            val credentials = credentialStore.read(providerBinding.provider) ?: return MediaSearchCommandOutcome.FAILED
            var result = source.browse(credentials, request)
            val failure = (result as? ProviderResult.Failure)?.failure
            if (failure is ProviderFailure.RateLimited) {
                // One bounded retry, honoring the provider's delay while Room remains visible.
                delay(failure.retryAfter ?: 60.seconds)
                if (counter.get() != generation ||
                    !database.selectedAccountDao().isSelectedAccount(
                        accountId.value,
                        StoredConnectedProvider.valueOf(providerBinding.provider.name),
                    )
                ) {
                    return MediaSearchCommandOutcome.SUPERSEDED
                }
                result = source.browse(credentials, request)
            }
            val items =
                when (result) {
                    is ProviderResult.Success -> result.value
                    is ProviderResult.Failure -> return MediaSearchCommandOutcome.FAILED
                }
            if (items.size > request.limit ||
                items.any { it.type != request.type }
            ) {
                return MediaSearchCommandOutcome.FAILED
            }
            val snapshots =
                items
                    .map {
                        ProviderMediaDetailsPersistenceMapper.mapSearchResult(providerBinding.provider, it)
                    }.distinctBy { it.media.id }
            val provider = StoredConnectedProvider.valueOf(providerBinding.provider.name)
            val committed =
                database.inTransaction {
                    if (counter.get() != generation ||
                        !database.selectedAccountDao().isSelectedAccount(accountId.value, provider)
                    ) {
                        return@inTransaction false
                    }
                    summaries.insertSearchSummaries(snapshots)
                    val dao = database.mediaSearchDao()
                    dao.upsertQuery(MediaSearchQueryEntity(accountId.value, key, provider, identity, null, null))
                    dao.deleteResults(accountId.value, key)
                    if (snapshots.isNotEmpty()) {
                        dao.upsertResults(
                            snapshots.mapIndexed {
                                index,
                                snapshot,
                                ->
                                MediaSearchResultEntity(accountId.value, key, snapshot.media.id.value, index)
                            },
                        )
                    }
                    true
                }
            if (committed) MediaSearchCommandOutcome.SUCCEEDED else MediaSearchCommandOutcome.SUPERSEDED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            MediaSearchCommandOutcome.FAILED
        }
    }

    private fun cacheKey(
        type: MediaType,
        category: MediaBrowseCategory,
    ): String = "BROWSE:${type.name}:${category.name}"

    private fun identity(
        type: MediaType,
        category: MediaBrowseCategory,
    ): String = requestIdentity(request(type, category))

    private fun requestIdentity(request: ProviderMediaBrowseRequest): String =
        if (request.category in
            listOf(ProviderBrowseCategory.THIS_SEASON, ProviderBrowseCategory.NEXT_SEASON)
        ) {
            "${request.year}:${request.season}"
        } else {
            "browse-v1"
        }

    private fun request(
        type: MediaType,
        category: MediaBrowseCategory,
    ): ProviderMediaBrowseRequest {
        // UTC calendar quarters match provider season definitions; no provider date types escape data.
        val date = clock.now().toString()
        var year = date.substring(0, 4).toInt()
        var quarter = (date.substring(5, 7).toInt() - 1) / 3
        if (category == MediaBrowseCategory.NEXT_SEASON) {
            quarter += 1
            if (quarter == 4) {
                quarter = 0
                year += 1
            }
        }
        return ProviderMediaBrowseRequest(
            ProviderMediaType.valueOf(type.name),
            ProviderBrowseCategory.valueOf(category.name),
            year,
            listOf("WINTER", "SPRING", "SUMMER", "FALL")[quarter],
        )
    }
}
