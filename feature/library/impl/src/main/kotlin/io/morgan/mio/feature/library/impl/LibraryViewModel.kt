package io.morgan.mio.feature.library.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.collection.orderLibraryEntries
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.queue.ObserveReadyMediaIdsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AssistedInject
internal class LibraryViewModel(
    private val accountId: AccountId,
    private val collectionRepository: CollectionRepository,
    private val mediaRepository: MediaRepository,
    private val observeReadyMediaIds: ObserveReadyMediaIdsUseCase,
    @Assisted initialReady: Boolean,
) : ViewModel() {
    private var mediaFilter = LibraryMediaFilter.ALL
    private var statusFilter = if (initialReady) LibraryStatusFilter.READY else LibraryStatusFilter.CURRENT
    private val mutableUiState =
        MutableStateFlow(
            LibraryUiState(
                mediaFilter = mediaFilter,
                statusFilter = statusFilter,
            ),
        )
    private var latestObservation: LibraryObservation? = null
    private var observationJob: Job? = null

    internal val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    init {
        observeInventory()
    }

    internal fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.SelectMediaFilter -> {
                mediaFilter = action.filter
                publish()
            }
            is LibraryAction.SelectStatusFilter -> {
                statusFilter = action.filter
                publish()
            }
            LibraryAction.ClearFilters -> {
                mediaFilter = LibraryMediaFilter.ALL
                statusFilter = LibraryStatusFilter.ALL
                publish()
            }
            LibraryAction.RetryObservation -> observeInventory()
        }
    }

    private fun observeInventory() {
        observationJob?.cancel()
        if (latestObservation == null) {
            mutableUiState.value = mutableUiState.value.copy(isLoading = true, observationFailed = false)
        } else {
            mutableUiState.value = mutableUiState.value.copy(observationFailed = false)
        }

        observationJob =
            viewModelScope.launch {
                observeLibrary()
                    .catch { failure ->
                        if (
                            failure is CancellationException ||
                            failure is IllegalArgumentException ||
                            failure is IllegalStateException ||
                            failure !is Exception
                        ) {
                            throw failure
                        }
                        publish(isLoading = false, observationFailed = true)
                    }.collect { observation ->
                        latestObservation = observation
                        publish(isLoading = false, observationFailed = false)
                    }
            }
    }

    private fun observeLibrary(): Flow<LibraryObservation> =
        combine(
            observeCollectionRows(),
            observeReadyMediaIds(accountId),
        ) { rows, readyIds ->
            LibraryObservation(rows = rows, readyMediaIds = readyIds)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCollectionRows(): Flow<List<ObservedLibraryEntry>> =
        collectionRepository.observeCollection(accountId).flatMapLatest { entries ->
            val orderedEntries = orderLibraryEntries(entries)
            if (orderedEntries.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    orderedEntries.map { entry ->
                        mediaRepository.observeMedia(entry.mediaId).map { media ->
                            ObservedLibraryEntry(entry = entry, media = media)
                        }
                    },
                ) { observed -> observed.toList() }
            }
        }

    private fun publish(
        isLoading: Boolean = mutableUiState.value.isLoading,
        observationFailed: Boolean = mutableUiState.value.observationFailed,
    ) {
        val observation = latestObservation
        if (observation == null) {
            mutableUiState.value =
                mutableUiState.value.copy(
                    isLoading = isLoading,
                    observationFailed = observationFailed,
                    mediaFilter = mediaFilter,
                    statusFilter = statusFilter,
                )
            return
        }

        val allRows = observation.rows.map(::mapRow)
        val availableStatuses =
            if (allRows.any { row -> row.status == LibraryRowStatus.REPEATING }) {
                LibraryUiState.DEFAULT_STATUS_FILTERS + LibraryStatusFilter.REPEATING
            } else {
                LibraryUiState.DEFAULT_STATUS_FILTERS
            }
        val visibleRows =
            allRows.filter { row ->
                row.matchesMediaFilter(mediaFilter) &&
                    row.matchesStatusFilter(statusFilter, observation.readyMediaIds)
            }
        mutableUiState.value =
            LibraryUiState(
                isLoading = isLoading,
                hasObservedCollection = true,
                observationFailed = observationFailed,
                collectionSize = allRows.size,
                rows = visibleRows,
                availableStatusFilters = availableStatuses,
                mediaFilter = mediaFilter,
                statusFilter = statusFilter,
            )
    }

    private fun mapRow(observed: ObservedLibraryEntry): LibraryRowUiModel {
        val entry = observed.entry
        val media = observed.media
        return LibraryRowUiModel(
            entryId = entry.id.value,
            mediaId = entry.mediaId.value,
            title = media?.title?.value ?: "",
            posterReference = media?.artwork?.poster?.value,
            mediaType = media?.type?.toRowType(),
            status = entry.status.toRowStatus(),
            progress = entry.progress.value,
            progressUnit = entry.progressUnit.toRowUnit(),
            total = entry.total?.value,
        )
    }

    private fun LibraryRowUiModel.matchesMediaFilter(filter: LibraryMediaFilter): Boolean =
        when (filter) {
            LibraryMediaFilter.ALL -> true
            LibraryMediaFilter.ANIME -> mediaType == LibraryRowMediaType.ANIME
            LibraryMediaFilter.MANGA -> mediaType == LibraryRowMediaType.MANGA
        }

    private fun LibraryRowUiModel.matchesStatusFilter(
        filter: LibraryStatusFilter,
        readyMediaIds: Set<MediaId>,
    ): Boolean =
        when (filter) {
            LibraryStatusFilter.ALL -> true
            LibraryStatusFilter.READY -> MediaId(mediaId) in readyMediaIds
            LibraryStatusFilter.CURRENT -> status == LibraryRowStatus.CURRENT
            LibraryStatusFilter.PLANNED -> status == LibraryRowStatus.PLANNED
            LibraryStatusFilter.PAUSED -> status == LibraryRowStatus.PAUSED
            LibraryStatusFilter.COMPLETED -> status == LibraryRowStatus.COMPLETED
            LibraryStatusFilter.DROPPED -> status == LibraryRowStatus.DROPPED
            LibraryStatusFilter.REPEATING -> status == LibraryRowStatus.REPEATING
        }

    private fun CollectionStatus.toRowStatus(): LibraryRowStatus =
        when (this) {
            CollectionStatus.CURRENT -> LibraryRowStatus.CURRENT
            CollectionStatus.PLANNED -> LibraryRowStatus.PLANNED
            CollectionStatus.PAUSED -> LibraryRowStatus.PAUSED
            CollectionStatus.COMPLETED -> LibraryRowStatus.COMPLETED
            CollectionStatus.DROPPED -> LibraryRowStatus.DROPPED
            CollectionStatus.REPEATING -> LibraryRowStatus.REPEATING
        }

    private fun io.morgan.mio.domain.model.MediaType.toRowType(): LibraryRowMediaType =
        when (this) {
            io.morgan.mio.domain.model.MediaType.ANIME -> LibraryRowMediaType.ANIME
            io.morgan.mio.domain.model.MediaType.MANGA -> LibraryRowMediaType.MANGA
        }

    private fun io.morgan.mio.domain.model.ProgressUnit.toRowUnit(): LibraryProgressUnit =
        when (this) {
            io.morgan.mio.domain.model.ProgressUnit.EPISODE -> LibraryProgressUnit.EPISODE
            io.morgan.mio.domain.model.ProgressUnit.CHAPTER -> LibraryProgressUnit.CHAPTER
            io.morgan.mio.domain.model.ProgressUnit.VOLUME -> LibraryProgressUnit.VOLUME
        }

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(
            @Assisted initialReady: Boolean,
        ): LibraryViewModel
    }

    private data class ObservedLibraryEntry(
        val entry: CollectionEntry,
        val media: Media?,
    )

    private data class LibraryObservation(
        val rows: List<ObservedLibraryEntry>,
        val readyMediaIds: Set<MediaId>,
    )
}
