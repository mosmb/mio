package io.morgan.mio.data.media

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.FakeProviderMediaSearchDataSource
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerCredentialsFixture
import io.morgan.mio.core.testing.provider.providerMediaDetailsFixture
import io.morgan.mio.core.testing.provider.providerMediaFixture
import io.morgan.mio.data.media.local.RoomMediaLocalDataSource
import io.morgan.mio.data.media.local.RoomMediaSearchLocalDataSource
import io.morgan.mio.data.media.mapper.ProviderMediaDetailsPersistenceMapper
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMedia
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderMediaSearchPage
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.api.ProviderSearchCursor
import io.morgan.mio.domain.media.MediaSearchCommandOutcome
import io.morgan.mio.domain.media.MediaSearchFilter
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DefaultMediaSearchRepositoryTest {
    private lateinit var database: MioDatabase
    private lateinit var local: RoomMediaLocalDataSource
    private lateinit var searchSource: FakeProviderMediaSearchDataSource
    private lateinit var credentials: FakeProviderCredentialStore
    private lateinit var repository: DefaultMediaSearchRepository

    private val accountId = AccountId("search-account")
    private var provider = ProviderKey.ANILIST

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        local = RoomMediaLocalDataSource(database)
        configureProvider()
    }

    private fun configureProvider() {
        searchSource = FakeProviderMediaSearchDataSource(provider)
        credentials =
            FakeProviderCredentialStore().also { store ->
                kotlinx.coroutines.runBlocking { store.write(providerCredentialsFixture(provider)) }
            }
        val template = providerBindingFixture(provider)
        val binding =
            ProviderBinding(
                provider = provider,
                authenticator = template.authenticator,
                identityDataSource = template.identityDataSource,
                initialImportDataSource = template.initialImportDataSource,
                mediaDetailDataSource = template.mediaDetailDataSource,
                mediaSearchDataSource = searchSource,
                syncDataSource = template.syncDataSource,
            )
        repository =
            DefaultMediaSearchRepository(
                accountId = accountId,
                providerBinding = binding,
                credentialStore = credentials,
                localDataSource = RoomMediaSearchLocalDataSource(database),
            )
        selectAccount()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun trimsSearchInputAndPersistsSuccessfulEmptyResultsSeparatelyFromUncachedQueries() =
        runTest {
            searchSource.enqueue { _, request ->
                assertEquals("quiet place", request.query)
                assertEquals(ProviderMediaType.ANIME, request.type)
                ProviderResult.Success(searchPage(searchMedia("1", title = "Quiet Place")))
            }
            assertEquals(
                MediaSearchCommandOutcome.SUCCEEDED,
                repository.refreshSearch("  quiet place  ", MediaSearchFilter.ANIME),
            )

            val found = repository.observeSearch("quiet place", MediaSearchFilter.ANIME).first()
            assertTrue(found != null)
            assertEquals(
                "Quiet Place",
                found
                    ?.media
                    ?.single()
                    ?.title
                    ?.value,
            )
            assertEquals(null, repository.observeSearch("quiet place", MediaSearchFilter.MANGA).first())

            searchSource.enqueue { _, request ->
                assertEquals(ProviderMediaType.MANGA, request.type)
                ProviderResult.Success(searchPage())
            }
            assertEquals(
                MediaSearchCommandOutcome.SUCCEEDED,
                repository.refreshSearch("no title", MediaSearchFilter.MANGA),
            )
            val empty = repository.observeSearch("no title", MediaSearchFilter.MANGA).first()
            assertTrue(empty != null)
            assertTrue(empty!!.media.isEmpty())
            assertFalse(empty.hasMore)
            assertNull(repository.observeSearch("other title", MediaSearchFilter.MANGA).first())
        }

    @Test
    fun failedSubrequestForAllDoesNotCommitEitherHalfOfThePage() =
        runTest {
            searchSource.enqueue { _, request ->
                assertEquals(ProviderMediaType.ANIME, request.type)
                ProviderResult.Success(
                    searchPage(
                        searchMedia("anime-1", type = ProviderMediaType.ANIME),
                        nextCursor = "2",
                    ),
                )
            }
            searchSource.enqueue { _, request ->
                assertEquals(ProviderMediaType.MANGA, request.type)
                ProviderResult.Failure(ProviderFailure.Timeout)
            }

            assertEquals(
                MediaSearchCommandOutcome.FAILED,
                repository.refreshSearch("mixed", MediaSearchFilter.ALL),
            )
            assertNull(repository.observeSearch("mixed", MediaSearchFilter.ALL).first())
            assertTrue(searchSource.calls.all { call -> call.request.cursor == null })
        }

    @Test
    fun failedRefreshRetainsMatchingCacheAndDoesNotLeakItIntoAnotherQuery() =
        runTest {
            searchSource.enqueue { _, _ ->
                ProviderResult.Success(searchPage(searchMedia("1", title = "Cached Title")))
            }
            repository.refreshSearch("cached title", MediaSearchFilter.ANIME)

            searchSource.enqueue { _, _ -> ProviderResult.Failure(ProviderFailure.Timeout) }
            assertEquals(
                MediaSearchCommandOutcome.FAILED,
                repository.refreshSearch("cached title", MediaSearchFilter.ANIME),
            )
            assertEquals(
                "Cached Title",
                repository
                    .observeSearch(
                        "cached title",
                        MediaSearchFilter.ANIME,
                    ).first()
                    ?.media
                    ?.single()
                    ?.title
                    ?.value,
            )

            searchSource.enqueue { _, _ -> ProviderResult.Failure(ProviderFailure.Timeout) }
            assertEquals(
                MediaSearchCommandOutcome.FAILED,
                repository.refreshSearch("uncached title", MediaSearchFilter.ANIME),
            )
            assertNull(repository.observeSearch("uncached title", MediaSearchFilter.ANIME).first())
            assertEquals(
                "Cached Title",
                repository
                    .observeSearch(
                        "cached title",
                        MediaSearchFilter.ANIME,
                    ).first()
                    ?.media
                    ?.single()
                    ?.title
                    ?.value,
            )
        }

    @Test
    fun duplicatePageRetryKeepsMembershipUniqueAndDoesNotAdvanceCursorOnFailure() =
        runTest {
            searchSource.enqueue { _, _ ->
                ProviderResult.Success(
                    searchPage(
                        searchMedia("one", title = "One"),
                        nextCursor = "2",
                    ),
                )
            }
            repository.refreshSearch("series", MediaSearchFilter.ANIME)

            searchSource.enqueue { _, request ->
                assertEquals("2", request.cursor?.value)
                ProviderResult.Success(
                    searchPage(
                        searchMedia("one", title = "One again"),
                        searchMedia("two", title = "Two"),
                        nextCursor = "3",
                    ),
                )
            }
            assertEquals(MediaSearchCommandOutcome.SUCCEEDED, repository.loadMore("series", MediaSearchFilter.ANIME))
            var results = repository.observeSearch("series", MediaSearchFilter.ANIME).first()!!
            assertEquals(listOf("One", "Two"), results.media.map { media -> media.title.value })
            assertTrue(results.hasMore)

            searchSource.enqueue { _, request ->
                assertEquals("3", request.cursor?.value)
                ProviderResult.Failure(ProviderFailure.Timeout)
            }
            assertEquals(MediaSearchCommandOutcome.FAILED, repository.loadMore("series", MediaSearchFilter.ANIME))

            searchSource.enqueue { _, request ->
                assertEquals("3", request.cursor?.value)
                ProviderResult.Success(
                    searchPage(
                        searchMedia("two", title = "Two again"),
                        searchMedia("three", title = "Three"),
                        nextCursor = "4",
                    ),
                )
            }
            assertEquals(MediaSearchCommandOutcome.SUCCEEDED, repository.loadMore("series", MediaSearchFilter.ANIME))
            results = repository.observeSearch("series", MediaSearchFilter.ANIME).first()!!
            assertEquals(listOf("One", "Two", "Three"), results.media.map { media -> media.title.value })
            assertEquals(3, results.media.distinctBy { media -> media.id }.size)
            assertEquals(listOf(null, "2", "3", "3"), searchSource.calls.map { call -> call.request.cursor?.value })
        }

    @Test
    fun sameQueryRefreshSupersedesAnInFlightLoadMoreAtTheDatabaseBoundary() =
        runTest {
            searchSource.enqueue { _, _ ->
                ProviderResult.Success(searchPage(searchMedia("first", title = "First"), nextCursor = "2"))
            }
            repository.refreshSearch("series", MediaSearchFilter.ANIME)

            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            searchSource.enqueue { _, request ->
                assertEquals("2", request.cursor?.value)
                entered.complete(Unit)
                release.await()
                ProviderResult.Success(searchPage(searchMedia("stale", title = "Stale"), nextCursor = "3"))
            }
            val loadMore = async { repository.loadMore("series", MediaSearchFilter.ANIME) }
            entered.await()
            assertEquals(MediaSearchCommandOutcome.SUPERSEDED, repository.loadMore("series", MediaSearchFilter.ANIME))

            searchSource.enqueue { _, request ->
                assertEquals(null, request.cursor)
                ProviderResult.Success(searchPage(searchMedia("fresh", title = "Fresh"), nextCursor = "8"))
            }
            assertEquals(
                MediaSearchCommandOutcome.SUCCEEDED,
                repository.refreshSearch("series", MediaSearchFilter.ANIME),
            )
            release.complete(Unit)

            assertEquals(MediaSearchCommandOutcome.SUPERSEDED, loadMore.await())
            val results = repository.observeSearch("series", MediaSearchFilter.ANIME).first()!!
            assertEquals(listOf("Fresh"), results.media.map { media -> media.title.value })
            assertTrue(results.hasMore)
            assertEquals("8", database.mediaSearchDao().getQuery(accountId.value, "ANIME")?.animeCursor)
        }

    @Test
    fun observedRowsAndContinuationAlwaysBelongToTheSameCommittedPage() =
        runTest {
            val emissions = Channel<io.morgan.mio.domain.media.MediaSearchResults?>(Channel.UNLIMITED)
            backgroundScope.launch {
                repository.observeSearch("series", MediaSearchFilter.ANIME).collect { emissions.send(it) }
            }
            assertNull(emissions.receive())
            repeat(6) { index ->
                val hasMore = index % 2 == 0
                searchSource.enqueue { _, _ ->
                    ProviderResult.Success(
                        searchPage(searchMedia("version-$index"), nextCursor = if (hasMore) "2" else null),
                    )
                }
                assertEquals(
                    MediaSearchCommandOutcome.SUCCEEDED,
                    repository.refreshSearch("series", MediaSearchFilter.ANIME),
                )
                do {
                    val observed = requireNotNull(emissions.receive())
                    val observedVersion =
                        observed.media
                            .single()
                            .title.value
                            .substringAfterLast('-')
                            .toInt()
                    assertEquals(observedVersion % 2 == 0, observed.hasMore)
                } while (observedVersion != index)
            }
        }

    @Test
    fun aLateQueryCannotEvictTheNewerQueryCache() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            searchSource.enqueue { _, _ ->
                entered.complete(Unit)
                release.await()
                ProviderResult.Success(searchPage(searchMedia("old")))
            }
            val old = async { repository.refreshSearch("old", MediaSearchFilter.ANIME) }
            entered.await()
            searchSource.enqueue { _, _ -> ProviderResult.Success(searchPage(searchMedia("new"))) }
            assertEquals(MediaSearchCommandOutcome.SUCCEEDED, repository.refreshSearch("new", MediaSearchFilter.ANIME))
            release.complete(Unit)
            assertEquals(MediaSearchCommandOutcome.SUPERSEDED, old.await())
            assertNull(repository.observeSearch("old", MediaSearchFilter.ANIME).first())
            assertEquals(
                "Title new",
                repository
                    .observeSearch("new", MediaSearchFilter.ANIME)
                    .first()!!
                    .media
                    .single()
                    .title.value,
            )
        }

    @Test
    fun teardownRejectsLateSearchResponseAndDoesNotRestoreSearchOwnedMedia() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            searchSource.enqueue { _, _ ->
                entered.complete(Unit)
                release.await()
                ProviderResult.Success(searchPage(searchMedia("late", title = "Late result")))
            }
            val pending = async { repository.refreshSearch("late", MediaSearchFilter.ANIME) }
            entered.await()

            database.selectedAccountDao().deleteAccount(accountId.value)
            release.complete(Unit)

            assertEquals(MediaSearchCommandOutcome.SUPERSEDED, pending.await())
            assertNull(database.mediaSearchDao().getQuery(accountId.value, "ANIME"))
            val lateId = MediaId(StableOpaqueIdFactory.create("anilist", "media", "late"))
            assertTrue(database.mediaDao().getMediaIds(listOf(lateId.value)).isEmpty())
        }

    @Test
    fun searchSummaryDoesNotOverwriteDetailRelationshipsReleasesProgressOrOutbox() =
        runTest {
            val detailedRoot =
                providerMediaFixture(
                    id = "501",
                    relatedMediaIds = listOf("502"),
                    releaseYear = 2023,
                    scorePercent = 91,
                )
            val details =
                ProviderMediaDetailsPersistenceMapper.map(
                    providerMediaDetailsFixture(
                        root = detailedRoot,
                        related = listOf(providerMediaFixture(id = "502", releases = emptyList())),
                    ),
                )
            local.replaceMediaDetailsSnapshot(details)
            val rootId = MediaId(StableOpaqueIdFactory.create("anilist", "media", "501"))
            val relatedId = MediaId(StableOpaqueIdFactory.create("anilist", "media", "502"))
            val now = Instant.parse("2026-09-18T10:00:00Z")
            database.collectionEntryDao().upsertEntries(
                listOf(
                    CollectionEntryEntity(
                        collectionEntryId = "entry-501",
                        accountId = accountId.value,
                        mediaId = rootId.value,
                        progressUnit = StoredProgressUnit.EPISODE,
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        remoteProgress = 3,
                        remoteTotal = 12,
                        remoteUpdatedAtUtcMs = now,
                        effectiveStatus = StoredCollectionStatus.CURRENT,
                        effectiveProgress = 7,
                        effectiveTotal = 12,
                        effectiveUpdatedAtUtcMs = now,
                        localProgressRevision = 1,
                    ),
                ),
            )
            database.pendingOperationDao().insertGroup(
                PendingOperationGroupEntity(
                    operationGroupId = "search-preservation-group",
                    accountId = accountId.value,
                    createdAtUtcMs = now,
                    compensatesGroupId = null,
                ),
            )
            database.pendingOperationDao().insertOperations(
                listOf(
                    PendingOperationEntity(
                        operationId = "search-preservation-operation",
                        operationGroupId = "search-preservation-group",
                        accountId = accountId.value,
                        collectionEntryId = "entry-501",
                        positionInGroup = 0,
                        kind = StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                        baseProgress = 7,
                        targetProgress = 8,
                        baseStatus = null,
                        targetStatus = null,
                        lifecycleState = StoredPendingOperationState.ENQUEUED,
                        attemptCount = 0,
                        lastAttemptAtUtcMs = null,
                        retryNotBeforeUtcMs = null,
                    ),
                ),
            )

            searchSource.enqueue { _, _ ->
                ProviderResult.Success(searchPage(searchMedia("501", title = "Search summary")))
            }
            repository.refreshSearch("detailed title", MediaSearchFilter.ANIME)

            val cached =
                repository
                    .observeSearch("detailed title", MediaSearchFilter.ANIME)
                    .first()!!
                    .media
                    .single()
            assertEquals("Fixture Media", cached.title.value)
            assertEquals(listOf("Adventure", "Drama"), cached.genres)
            assertEquals(listOf(relatedId), cached.relatedMediaIds)
            assertEquals(2023, cached.releaseYear)
            assertEquals(91, cached.score?.percent)
            assertEquals(1, local.observeReleases(rootId).first().size)
            val progress = database.collectionEntryDao().getEntry("entry-501")
            assertEquals(7, progress?.effectiveProgress)
            assertEquals(1L, progress?.localProgressRevision)
            assertEquals(
                "search-preservation-operation",
                database.pendingOperationDao().getOperation("search-preservation-operation")?.operationId,
            )
        }

    @Test
    fun myAnimeListAllKeepsNamespacedIdentitiesAndContinuesOnlyTheUnfinishedType() =
        runTest {
            provider = ProviderKey.MY_ANIME_LIST
            configureProvider()
            searchSource.enqueue { _, request ->
                assertEquals(ProviderMediaType.ANIME, request.type)
                ProviderResult.Success(searchPage(searchMedia("anime/1")))
            }
            searchSource.enqueue { _, request ->
                assertEquals(ProviderMediaType.MANGA, request.type)
                ProviderResult.Success(
                    searchPage(searchMedia("manga/1", ProviderMediaType.MANGA), nextCursor = "10"),
                )
            }
            assertEquals(MediaSearchCommandOutcome.SUCCEEDED, repository.refreshSearch("mixed", MediaSearchFilter.ALL))
            val first = checkNotNull(repository.observeSearch("mixed", MediaSearchFilter.ALL).first())
            assertEquals(
                2,
                first.media
                    .map { it.id }
                    .distinct()
                    .size,
            )
            assertTrue(first.hasMore)
            searchSource.enqueue { _, request ->
                assertEquals(ProviderMediaType.MANGA, request.type)
                assertEquals("10", request.cursor?.value)
                ProviderResult.Failure(ProviderFailure.NetworkUnavailable)
            }
            assertEquals(MediaSearchCommandOutcome.FAILED, repository.loadMore("mixed", MediaSearchFilter.ALL))
            assertEquals(first, repository.observeSearch("mixed", MediaSearchFilter.ALL).first())
            searchSource.enqueue { _, request ->
                assertEquals(ProviderMediaType.MANGA, request.type)
                assertEquals("10", request.cursor?.value)
                ProviderResult.Success(
                    searchPage(
                        searchMedia("manga/1", ProviderMediaType.MANGA),
                        searchMedia("manga/2", ProviderMediaType.MANGA),
                    ),
                )
            }
            assertEquals(MediaSearchCommandOutcome.SUCCEEDED, repository.loadMore("mixed", MediaSearchFilter.ALL))
            val final = checkNotNull(repository.observeSearch("mixed", MediaSearchFilter.ALL).first())
            assertEquals(3, final.media.size)
            assertEquals(first.media, final.media.take(2))
            assertFalse(final.hasMore)
            assertNull(repository.observeSearch("other", MediaSearchFilter.ALL).first())
        }

    private fun storedProvider(): StoredConnectedProvider =
        if (provider == ProviderKey.ANILIST) StoredConnectedProvider.ANILIST else StoredConnectedProvider.MY_ANIME_LIST

    private fun selectAccount() =
        kotlinx.coroutines.runBlocking {
            database.selectedAccountDao().replaceSelectedAccount(
                account =
                    SelectedAccountEntity(
                        selectionKey = SELECTED_ACCOUNT_KEY,
                        accountId = accountId.value,
                        connectedProvider = storedProvider(),
                        displayName = "Search Fixture",
                        avatarReference = null,
                    ),
                providerId =
                    AccountProviderIdEntity(
                        accountId = accountId.value,
                        provider = storedProvider(),
                        remoteAccountId = "remote-search-account",
                    ),
            )
        }

    private fun searchPage(
        vararg items: ProviderMedia,
        nextCursor: String? = null,
    ): ProviderMediaSearchPage =
        ProviderMediaSearchPage(
            items = items.toList(),
            nextCursor = nextCursor?.let(::ProviderSearchCursor),
        )

    private fun searchMedia(
        id: String,
        type: ProviderMediaType = ProviderMediaType.ANIME,
        title: String = "Title $id",
    ): ProviderMedia =
        ProviderMedia(
            id = ProviderMediaId(id),
            type = type,
            title = title,
            format = null,
            publicationStatus = io.morgan.mio.data.provider.api.ProviderMediaPublicationStatus.UNKNOWN,
            synopsis = null,
            poster = null,
            banner = null,
            totals =
                io.morgan.mio.data.provider.api
                    .ProviderMediaTotals(null, null, null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
            releases = emptyList(),
        )
}
