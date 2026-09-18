package io.morgan.mio.feature.discover.impl

import androidx.lifecycle.ViewModelStore
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaSearchCommandOutcome
import io.morgan.mio.domain.media.MediaSearchFilter
import io.morgan.mio.domain.media.MediaSearchRepository
import io.morgan.mio.domain.media.MediaSearchResults
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    @Test
    fun typingIsDebouncedFilterChangesSubmitImmediatelyAndOldRequestsAreCancelled() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeMediaSearchRepository()
            val firstSearchEntered = CompletableDeferred<Unit>()
            val releaseFirstSearch = CompletableDeferred<Unit>()
            repository.refreshBehavior = { query, _ ->
                if (query == "first") {
                    firstSearchEntered.complete(Unit)
                    releaseFirstSearch.await()
                }
                MediaSearchCommandOutcome.SUCCEEDED
            }
            val viewModel =
                DiscoverViewModel(
                    accountId = AccountId("discover-account"),
                    mediaSearchRepository = repository,
                    collectionRepository = EmptyCollectionRepository,
                )
            val store = ViewModelStore().also { it.put("discover", viewModel) }

            try {
                viewModel.onAction(DiscoverAction.QueryChanged("first"))
                advanceTimeBy(299)
                runCurrent()
                assertTrue(repository.refreshCalls.isEmpty())

                advanceTimeBy(1)
                runCurrent()
                firstSearchEntered.await()
                assertEquals(listOf("first" to MediaSearchFilter.ALL), repository.refreshCalls)

                viewModel.onAction(DiscoverAction.QueryChanged("second"))
                runCurrent()
                advanceTimeBy(299)
                runCurrent()
                assertEquals(1, repository.refreshCalls.size)

                advanceTimeBy(1)
                runCurrent()
                assertEquals(
                    listOf(
                        "first" to MediaSearchFilter.ALL,
                        "second" to MediaSearchFilter.ALL,
                    ),
                    repository.refreshCalls,
                )
                assertEquals("second", viewModel.uiState.value.query)

                viewModel.onAction(DiscoverAction.SelectFilter(DiscoverMediaFilter.MANGA))
                runCurrent()
                assertEquals(
                    "second" to MediaSearchFilter.MANGA,
                    repository.refreshCalls.last(),
                )
                assertEquals(DiscoverMediaFilter.MANGA, viewModel.uiState.value.filter)

                releaseFirstSearch.complete(Unit)
                runCurrent()
                assertFalse(viewModel.uiState.value.refreshFailed)

                viewModel.onAction(DiscoverAction.QueryChanged(""))
                runCurrent()
                advanceTimeBy(400)
                runCurrent()
                assertEquals("", viewModel.uiState.value.query)
                assertEquals(3, repository.refreshCalls.size)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun refreshRetryDoesNotRetryPaginationWhenBothRequestsHaveFailed() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeMediaSearchRepository().apply {
                    observations["cached" to MediaSearchFilter.ALL] =
                        MutableStateFlow(MediaSearchResults(emptyList(), true))
                    refreshBehavior = { _, _ -> MediaSearchCommandOutcome.FAILED }
                }
            val viewModel = DiscoverViewModel(AccountId("discover-account"), repository, EmptyCollectionRepository)
            val store = ViewModelStore().also { it.put("discover", viewModel) }
            try {
                viewModel.onAction(DiscoverAction.QueryChanged("cached"))
                advanceTimeBy(300)
                runCurrent()
                viewModel.onAction(DiscoverAction.LoadMore)
                runCurrent()
                assertTrue(viewModel.uiState.value.refreshFailed)
                assertTrue(viewModel.uiState.value.loadMoreFailed)
                viewModel.onAction(DiscoverAction.Retry)
                runCurrent()
                assertEquals(2, repository.refreshCalls.size)
                assertEquals(1, repository.loadMoreCalls)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    private class FakeMediaSearchRepository : MediaSearchRepository {
        val refreshCalls = mutableListOf<Pair<String, MediaSearchFilter>>()
        var refreshBehavior: suspend (String, MediaSearchFilter) -> MediaSearchCommandOutcome = { _, _ ->
            MediaSearchCommandOutcome.SUCCEEDED
        }
        var loadMoreCalls = 0
        val observations =
            mutableMapOf<Pair<String, MediaSearchFilter>, MutableStateFlow<MediaSearchResults?>>()

        override fun observeSearch(
            query: String,
            filter: MediaSearchFilter,
        ): Flow<MediaSearchResults?> = observations.getOrPut(query to filter) { MutableStateFlow(null) }

        override suspend fun refreshSearch(
            query: String,
            filter: MediaSearchFilter,
        ): MediaSearchCommandOutcome {
            refreshCalls += query to filter
            return refreshBehavior(query, filter)
        }

        override suspend fun loadMore(
            query: String,
            filter: MediaSearchFilter,
        ): MediaSearchCommandOutcome {
            loadMoreCalls += 1
            return MediaSearchCommandOutcome.FAILED
        }
    }

    private object EmptyCollectionRepository : CollectionRepository {
        override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> = flowOf(emptyList())

        override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> = flowOf(null)

        override fun observeEntryForMedia(
            accountId: AccountId,
            mediaId: MediaId,
        ): Flow<CollectionEntry?> = flowOf(null)

        override suspend fun refreshCollection(accountId: AccountId) = Unit
    }
}
