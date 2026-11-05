package io.morgan.mio.feature.discover.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaBrowseCategory
import io.morgan.mio.domain.media.MediaBrowseRepository
import io.morgan.mio.domain.media.MediaSearchCommandOutcome
import io.morgan.mio.domain.media.MediaSearchResults
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

@ContributesIntoMap(AccountScope::class)
@ViewModelKey
@Inject
internal class DiscoverCategoryViewModel(
    private val repository: MediaBrowseRepository,
    collectionRepository: CollectionRepository,
    accountId: AccountId,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiscoverBrowseUiState(season = DiscoverBrowseSeason.at(clock.now())))
    val uiState: StateFlow<DiscoverBrowseUiState> = mutableState.asStateFlow()
    private var selection: Pair<MediaType, MediaBrowseCategory?>? = null
    private var generation = 0
    private var collection: List<CollectionEntry> = emptyList()
    private val snapshots = mutableMapOf<MediaBrowseCategory, List<Media>>()
    private val observations = mutableMapOf<MediaBrowseCategory, Job>()
    private val refreshes = mutableMapOf<MediaBrowseCategory, Job>()
    private val observationFailures = mutableSetOf<MediaBrowseCategory>()

    init {
        viewModelScope.launch {
            collectionRepository
                .observeCollection(
                    accountId,
                ).catch { if (it is CancellationException) throw it }
                .collect {
                    collection = it
                    publish()
                }
        }
    }

    fun open(
        type: MediaType,
        category: MediaBrowseCategory? = null,
    ) {
        if (selection == type to category) return
        selection = type to category
        generation++
        observations.values.forEach { it.cancel() }
        refreshes.values.forEach { it.cancel() }
        observations.clear()
        refreshes.clear()
        snapshots.clear()
        observationFailures.clear()
        val categories = repository.categories(type)
        mutableState.value =
            DiscoverBrowseUiState(type = type, categories = categories, season = DiscoverBrowseSeason.at(clock.now()))
        val visible = if (category == null) categories.take(2) else listOf(category)
        visible.forEach { retry(it) }
    }

    fun retry(category: MediaBrowseCategory) {
        val type = mutableState.value.type
        val current = generation
        observations.remove(category)?.cancel()
        observationFailures.remove(category)
        observations[category] =
            viewModelScope.launch {
                repository
                    .observe(type, category)
                    .catch {
                        if (it is CancellationException) throw it
                        if (current == generation) {
                            observationFailures += category
                            mutableState.update { state -> state.copy(failed = state.failed + category) }
                        }
                    }.collect { media ->
                        if (current != generation) return@collect
                        if (media == null) snapshots.remove(category) else snapshots[category] = media
                        publish()
                    }
            }
        refreshes.remove(category)?.cancel()
        mutableState.update { it.copy(loading = it.loading + category, failed = it.failed - category) }
        refreshes[category] =
            viewModelScope.launch {
                val outcome =
                    try {
                        repository.refresh(type, category)
                    } catch (
                        c: CancellationException,
                    ) {
                        throw c
                    } catch (_: Exception) {
                        MediaSearchCommandOutcome.FAILED
                    }
                if (current != generation) return@launch
                mutableState.update {
                    it.copy(
                        loading = it.loading - category,
                        failed =
                            if (outcome == MediaSearchCommandOutcome.FAILED ||
                                category in observationFailures
                            ) {
                                it.failed + category
                            } else {
                                it.failed - category
                            },
                    )
                }
            }
    }

    private fun publish() {
        mutableState.update { state ->
            state.copy(
                items =
                    snapshots.mapValues { (_, media) ->
                        DiscoverUiMapper.mapResults(MediaSearchResults(media, false), collection)
                    },
            )
        }
    }
}
