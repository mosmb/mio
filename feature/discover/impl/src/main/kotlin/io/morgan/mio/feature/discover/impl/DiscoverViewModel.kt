package io.morgan.mio.feature.discover.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaSearchCommandOutcome
import io.morgan.mio.domain.media.MediaSearchFilter
import io.morgan.mio.domain.media.MediaSearchRepository
import io.morgan.mio.domain.media.MediaSearchResults
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ContributesIntoMap(AccountScope::class)
@ViewModelKey
@Inject
internal class DiscoverViewModel(
    private val accountId: AccountId,
    private val mediaSearchRepository: MediaSearchRepository,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DiscoverUiState())
    internal val uiState: StateFlow<DiscoverUiState> = mutableUiState.asStateFlow()

    private var query = ""
    private var filter = DiscoverMediaFilter.ALL
    private var resultSnapshot: MediaSearchResults? = null
    private var collection: List<CollectionEntry> = emptyList()
    private var inputGeneration = 0L
    private var requestGeneration = 0L
    private var observationFailed = false
    private var observationJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        observeCollection()
    }

    internal fun onAction(action: DiscoverAction) {
        when (action) {
            is DiscoverAction.QueryChanged ->
                updateInput(
                    query = action.query,
                    filter = filter,
                    debounceMillis = SEARCH_DEBOUNCE_MILLIS,
                )
            DiscoverAction.SubmitQuery -> startRefresh(debounceMillis = 0)
            is DiscoverAction.SelectFilter ->
                if (action.filter != filter) {
                    updateInput(query = query, filter = action.filter, debounceMillis = 0)
                }
            DiscoverAction.Retry -> startRefresh(debounceMillis = 0)
            DiscoverAction.LoadMore -> startLoadMore()
        }
    }

    private fun updateInput(
        query: String,
        filter: DiscoverMediaFilter,
        debounceMillis: Long,
    ) {
        if (query == this.query && filter == this.filter) return
        inputGeneration += 1
        requestGeneration += 1
        observationJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        this.query = query
        this.filter = filter
        resultSnapshot = null
        observationFailed = false
        mutableUiState.value =
            DiscoverUiState(
                query = query,
                filter = filter,
                isSearching = query.trim().isNotEmpty(),
            )
        observeSearch()
        if (query.trim().isNotEmpty()) startRefresh(debounceMillis)
    }

    private fun observeSearch() {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return
        val observedInputGeneration = inputGeneration
        val observedFilter = filter.toDomain()
        observationFailed = false
        observationJob?.cancel()
        observationJob =
            viewModelScope.launch {
                try {
                    mediaSearchRepository
                        .observeSearch(normalizedQuery, observedFilter)
                        .catch { failure ->
                            if (failure is CancellationException) throw failure
                            if (observedInputGeneration == inputGeneration) {
                                observationFailed = true
                                mutableUiState.update { state ->
                                    state.copy(isSearching = false, refreshFailed = true)
                                }
                            }
                        }.collect { results ->
                            if (observedInputGeneration != inputGeneration) return@collect
                            resultSnapshot = results
                            publishResults()
                        }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                }
            }
    }

    private fun observeCollection() {
        viewModelScope.launch {
            collectionRepository
                .observeCollection(accountId)
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                }.collect { entries ->
                    collection = entries
                    publishResults()
                }
        }
    }

    private fun publishResults() {
        val snapshot = resultSnapshot
        mutableUiState.update { state ->
            state.copy(
                results = DiscoverUiMapper.mapResults(snapshot, collection),
                hasObservedResults = snapshot != null,
                hasMore = snapshot?.hasMore == true,
            )
        }
    }

    private fun startRefresh(debounceMillis: Long) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            mutableUiState.update { state -> state.copy(isSearching = false) }
            return
        }
        val observedInputGeneration = inputGeneration
        val observedFilter = filter.toDomain()
        val currentRequestGeneration = ++requestGeneration
        if (observationFailed) observeSearch()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                isSearching = true,
                isLoadingMore = false,
                refreshFailed = false,
                loadMoreFailed = false,
            )
        }
        searchJob =
            viewModelScope.launch {
                if (debounceMillis > 0) delay(debounceMillis)
                val outcome =
                    try {
                        mediaSearchRepository.refreshSearch(normalizedQuery, observedFilter)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        MediaSearchCommandOutcome.FAILED
                    }
                if (
                    observedInputGeneration != inputGeneration ||
                    currentRequestGeneration != requestGeneration
                ) {
                    return@launch
                }
                mutableUiState.update { state ->
                    state.copy(
                        isSearching = false,
                        refreshFailed = observationFailed || outcome == MediaSearchCommandOutcome.FAILED,
                    )
                }
            }
    }

    private fun startLoadMore() {
        val currentState = mutableUiState.value
        val normalizedQuery = query.trim()
        if (
            normalizedQuery.isEmpty() ||
            !currentState.hasMore ||
            currentState.isSearching ||
            currentState.isLoadingMore
        ) {
            return
        }
        val observedInputGeneration = inputGeneration
        val observedFilter = filter.toDomain()
        val currentRequestGeneration = ++requestGeneration
        searchJob?.cancel()
        loadMoreJob?.cancel()
        mutableUiState.update { state -> state.copy(isLoadingMore = true, loadMoreFailed = false) }
        loadMoreJob =
            viewModelScope.launch {
                val outcome =
                    try {
                        mediaSearchRepository.loadMore(normalizedQuery, observedFilter)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        MediaSearchCommandOutcome.FAILED
                    }
                if (
                    observedInputGeneration != inputGeneration ||
                    currentRequestGeneration != requestGeneration
                ) {
                    return@launch
                }
                mutableUiState.update { state ->
                    state.copy(
                        isLoadingMore = false,
                        loadMoreFailed = outcome == MediaSearchCommandOutcome.FAILED,
                    )
                }
            }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300
    }
}

private fun DiscoverMediaFilter.toDomain(): MediaSearchFilter =
    when (this) {
        DiscoverMediaFilter.ALL -> MediaSearchFilter.ALL
        DiscoverMediaFilter.ANIME -> MediaSearchFilter.ANIME
        DiscoverMediaFilter.MANGA -> MediaSearchFilter.MANGA
    }
