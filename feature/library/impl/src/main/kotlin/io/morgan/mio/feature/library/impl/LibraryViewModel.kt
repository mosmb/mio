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
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.preferences.PreferencesRepository
import io.morgan.mio.domain.progress.ObserveProgressUndoUseCase
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressRules
import io.morgan.mio.domain.progress.ProgressWriteResult
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
import io.morgan.mio.domain.queue.ObserveReadyMediaIdsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@AssistedInject
internal class LibraryViewModel(
    private val accountId: AccountId,
    private val collectionRepository: CollectionRepository,
    private val mediaRepository: MediaRepository,
    private val observeReadyMediaIds: ObserveReadyMediaIdsUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val stepProgress: StepProgressUseCase,
    private val undoProgress: UndoProgressUseCase,
    observeUndoUseCase: ObserveProgressUndoUseCase,
    @Assisted private val initialReady: Boolean,
) : ViewModel() {
    private var query = ""
    private var mediaFilter = LibraryMediaFilter.ALL
    private var statusFilter = if (initialReady) LibraryStatusFilter.READY else LibraryStatusFilter.CURRENT
    private var mediaSelectionGeneration = 0L
    private var statusSelectionGeneration = 0L
    private var preferencesRestored = false
    private var latestObservation: LibraryObservation? = null
    private var latestUndo: ProgressAction? = null
    private var lastRequest: PendingProgressRequest? = null
    private val pendingRequests = MutableStateFlow<Map<OperationGroupId, PendingProgressRequest>>(emptyMap())
    private val touchedMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val latestHiddenUndoId = MutableStateFlow<String?>(null)
    private val routineHapticEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var observationJob: Job? = null
    private val mutableUiState =
        MutableStateFlow(
            LibraryUiState(
                mediaFilter = mediaFilter,
                statusFilter = statusFilter,
            ),
        )

    internal val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()
    internal val routineHaptics: SharedFlow<Unit> = routineHapticEvents

    init {
        observeInventory()
        observePreferences()
        viewModelScope.launch {
            combine(observeUndoUseCase(), latestHiddenUndoId) { action, hiddenId ->
                action?.takeIf { it.id.value != hiddenId }
            }.collect { action ->
                latestUndo = action
                publish()
            }
        }
    }

    internal fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.QueryChanged -> {
                query = action.query
                publish()
            }
            is LibraryAction.SelectMediaFilter -> {
                mediaSelectionGeneration++
                mediaFilter = action.filter
                persistMediaFilter(action.filter)
                publish()
            }
            is LibraryAction.SelectStatusFilter -> {
                statusSelectionGeneration++
                statusFilter = action.filter
                persistStatusFilter(action.filter)
                publish()
            }
            LibraryAction.ClearFilters -> {
                mediaSelectionGeneration++
                statusSelectionGeneration++
                mediaFilter = LibraryMediaFilter.ALL
                statusFilter = LibraryStatusFilter.ALL
                persistMediaFilter(mediaFilter)
                persistStatusFilter(statusFilter)
                publish()
            }
            is LibraryAction.Step -> startStep(action)
            is LibraryAction.Undo -> startUndo(action)
            is LibraryAction.InteractionChanged -> {
                touchedMediaIds.update { ids ->
                    if (action.pressed) ids + action.mediaId else ids - action.mediaId
                }
                publish()
            }
            LibraryAction.RetryProgress -> lastRequest?.let(::startRequest)
            LibraryAction.DismissProgressError -> {
                mutableUiState.value = mutableUiState.value.copy(progressError = null)
            }
            LibraryAction.RetryObservation -> observeInventory()
        }
    }

    private fun observePreferences() {
        val initialMediaGeneration = mediaSelectionGeneration
        val initialStatusGeneration = statusSelectionGeneration
        viewModelScope.launch {
            preferencesRepository.observeAccountPreferences(accountId).collect { preferences ->
                if (!preferencesRestored) {
                    if (!initialReady && mediaSelectionGeneration == initialMediaGeneration) {
                        mediaFilter = preferences.libraryMediaFilter.toUiFilter()
                    }
                    if (!initialReady && statusSelectionGeneration == initialStatusGeneration) {
                        statusFilter = preferences.libraryStatusFilter.toUiFilter()
                    }
                    preferencesRestored = true
                    publish()
                }
            }
        }
    }

    private fun persistMediaFilter(filter: LibraryMediaFilter) {
        viewModelScope.launch {
            try {
                preferencesRepository.setLibraryMediaFilter(accountId, filter.toDomainFilter())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The selected filter remains usable for this session.
            }
        }
    }

    private fun persistStatusFilter(filter: LibraryStatusFilter) {
        viewModelScope.launch {
            try {
                preferencesRepository.setLibraryStatusFilter(accountId, filter.toDomainFilter())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The selected filter remains usable for this session.
            }
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
        combine(observeCollectionRows(), observeReadyMediaIds(accountId)) { rows, readyIds ->
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

    private fun startStep(action: LibraryAction.Step) {
        val mediaId = runCatching { MediaId(action.mediaId) }.getOrNull() ?: return
        if (pendingRequests.value.values.any { it.mediaId == mediaId }) return
        startRequest(
            PendingProgressRequest(
                actionId = OperationGroupId(UUID.randomUUID().toString()),
                mediaId = mediaId,
                direction = action.direction,
                timeoutMillis = action.timeoutMillis,
            ),
        )
    }

    private fun startRequest(request: PendingProgressRequest) {
        if (pendingRequests.value.values.any { it.mediaId == request.mediaId }) return
        pendingRequests.update { it + (request.actionId to request) }
        mutableUiState.value = mutableUiState.value.copy(progressError = null)
        publish()
        viewModelScope.launch {
            val result =
                try {
                    stepProgress(
                        mediaId = request.mediaId,
                        direction = request.direction.toDomainDirection(),
                        actionId = request.actionId,
                        undoWindow = request.timeoutMillis.milliseconds,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    pendingRequests.update { it - request.actionId }
                    lastRequest = request
                    mutableUiState.value = mutableUiState.value.copy(progressError = LibraryProgressErrorUiModel(true))
                    publish()
                    return@launch
                }
            pendingRequests.update { it - request.actionId }
            when (result) {
                is ProgressWriteResult.Committed -> {
                    latestHiddenUndoId.value = null
                    routineHapticEvents.tryEmit(Unit)
                }
                is ProgressWriteResult.AlreadyHandled -> latestHiddenUndoId.value = null
                ProgressWriteResult.Stale -> {
                    lastRequest = request
                    mutableUiState.value = mutableUiState.value.copy(progressError = LibraryProgressErrorUiModel(true))
                }
                is ProgressWriteResult.Rejected -> {
                    mutableUiState.value = mutableUiState.value.copy(progressError = LibraryProgressErrorUiModel(false))
                }
            }
            publish()
        }
    }

    private fun startUndo(action: LibraryAction.Undo) {
        val groupId = runCatching { OperationGroupId(action.groupId) }.getOrNull() ?: return
        viewModelScope.launch {
            val result =
                try {
                    undoProgress(groupId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)
                }
            latestHiddenUndoId.value = groupId.value
            if (result !is ProgressWriteResult.Committed && result !is ProgressWriteResult.AlreadyHandled) {
                mutableUiState.value = mutableUiState.value.copy(progressError = LibraryProgressErrorUiModel(false))
            }
            publish()
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
                    query = query,
                    mediaFilter = mediaFilter,
                    statusFilter = statusFilter,
                    latestUndo = latestUndo?.toUiModel(),
                )
            return
        }

        val allRows = observation.rows.map(::mapRow)
        val availableStatuses =
            if (allRows.any { it.status == LibraryRowStatus.REPEATING }) {
                LibraryUiState.DEFAULT_STATUS_FILTERS + LibraryStatusFilter.REPEATING
            } else {
                LibraryUiState.DEFAULT_STATUS_FILTERS
            }
        val normalizedQuery = query.trim()
        val visibleRows =
            allRows
                .filter { row ->
                    row.matchesMediaFilter(mediaFilter) &&
                        row.matchesStatusFilter(statusFilter, observation.readyMediaIds) &&
                        (normalizedQuery.isEmpty() || row.title.contains(normalizedQuery, ignoreCase = true))
                }.toMutableList()
        val protectedIds =
            touchedMediaIds.value +
                pendingRequests.value.values.map { it.mediaId.value } +
                listOfNotNull(latestUndo?.mediaId?.value)
        val allRowsByMediaId = allRows.associateBy(LibraryRowUiModel::mediaId)
        // Pin against background updates, never against an explicit search/filter change.
        val sameSelection =
            query == mutableUiState.value.query &&
                mediaFilter == mutableUiState.value.mediaFilter &&
                statusFilter == mutableUiState.value.statusFilter
        mutableUiState.value.rows.forEachIndexed { index, previousRow ->
            if (sameSelection && previousRow.mediaId in protectedIds) {
                val currentRow = allRowsByMediaId[previousRow.mediaId] ?: return@forEachIndexed
                visibleRows.removeAll { it.mediaId == previousRow.mediaId }
                visibleRows.add(min(index, visibleRows.size), currentRow)
            }
        }
        mutableUiState.value =
            LibraryUiState(
                isLoading = isLoading,
                hasObservedCollection = true,
                observationFailed = observationFailed,
                collectionSize = allRows.size,
                rows = visibleRows,
                query = query,
                availableStatusFilters = availableStatuses,
                mediaFilter = mediaFilter,
                statusFilter = statusFilter,
                latestUndo = latestUndo?.toUiModel(),
                progressError = mutableUiState.value.progressError,
            )
    }

    private fun mapRow(observed: ObservedLibraryEntry): LibraryRowUiModel {
        val entry = observed.entry
        val pending = pendingRequests.value.values.any { it.mediaId == entry.mediaId }
        val controls =
            if (entry.status == CollectionStatus.CURRENT) {
                LibraryProgressControlsUiModel(
                    decreaseEnabled =
                        !pending &&
                            ProgressRules.decide(entry, ProgressDirection.DECREASE) is ProgressRules.Decision.Change,
                    increaseEnabled =
                        !pending &&
                            ProgressRules.decide(entry, ProgressDirection.INCREASE) is ProgressRules.Decision.Change,
                )
            } else {
                null
            }
        return LibraryRowUiModel(
            entryId = entry.id.value,
            mediaId = entry.mediaId.value,
            title = observed.media?.title?.value ?: "",
            posterReference =
                observed.media
                    ?.artwork
                    ?.poster
                    ?.value,
            mediaType = observed.media?.type?.toRowType(),
            status = entry.status.toRowStatus(),
            progress = entry.progress.value,
            progressUnit = entry.progressUnit.toRowUnit(),
            total = entry.total?.value,
            controls = controls,
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

    private fun ProgressAction.toUiModel(): LibraryUndoUiModel =
        LibraryUndoUiModel(id.value, mediaId.value, afterProgress.value, unit.toRowUnit())

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

    private fun LibraryProgressDirection.toDomainDirection(): ProgressDirection =
        when (this) {
            LibraryProgressDirection.INCREASE -> ProgressDirection.INCREASE
            LibraryProgressDirection.DECREASE -> ProgressDirection.DECREASE
        }

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(
            @Assisted initialReady: Boolean,
        ): LibraryViewModel
    }

    private data class PendingProgressRequest(
        val actionId: OperationGroupId,
        val mediaId: MediaId,
        val direction: LibraryProgressDirection,
        val timeoutMillis: Long,
    )

    private data class ObservedLibraryEntry(
        val entry: CollectionEntry,
        val media: Media?,
    )

    private data class LibraryObservation(
        val rows: List<ObservedLibraryEntry>,
        val readyMediaIds: Set<MediaId>,
    )
}

private fun LibraryMediaFilter.toDomainFilter(): io.morgan.mio.domain.preferences.LibraryMediaFilter =
    when (this) {
        LibraryMediaFilter.ALL -> io.morgan.mio.domain.preferences.LibraryMediaFilter.ALL
        LibraryMediaFilter.ANIME -> io.morgan.mio.domain.preferences.LibraryMediaFilter.ANIME
        LibraryMediaFilter.MANGA -> io.morgan.mio.domain.preferences.LibraryMediaFilter.MANGA
    }

private fun io.morgan.mio.domain.preferences.LibraryMediaFilter.toUiFilter(): LibraryMediaFilter =
    when (this) {
        io.morgan.mio.domain.preferences.LibraryMediaFilter.ALL -> LibraryMediaFilter.ALL
        io.morgan.mio.domain.preferences.LibraryMediaFilter.ANIME -> LibraryMediaFilter.ANIME
        io.morgan.mio.domain.preferences.LibraryMediaFilter.MANGA -> LibraryMediaFilter.MANGA
    }

private fun LibraryStatusFilter.toDomainFilter(): io.morgan.mio.domain.preferences.LibraryStatusFilter =
    when (this) {
        LibraryStatusFilter.ALL -> io.morgan.mio.domain.preferences.LibraryStatusFilter.ALL
        LibraryStatusFilter.READY -> io.morgan.mio.domain.preferences.LibraryStatusFilter.READY
        LibraryStatusFilter.CURRENT -> io.morgan.mio.domain.preferences.LibraryStatusFilter.CURRENT
        LibraryStatusFilter.PLANNED -> io.morgan.mio.domain.preferences.LibraryStatusFilter.PLANNED
        LibraryStatusFilter.PAUSED -> io.morgan.mio.domain.preferences.LibraryStatusFilter.PAUSED
        LibraryStatusFilter.COMPLETED -> io.morgan.mio.domain.preferences.LibraryStatusFilter.COMPLETED
        LibraryStatusFilter.DROPPED -> io.morgan.mio.domain.preferences.LibraryStatusFilter.DROPPED
        LibraryStatusFilter.REPEATING -> io.morgan.mio.domain.preferences.LibraryStatusFilter.REPEATING
    }

private fun io.morgan.mio.domain.preferences.LibraryStatusFilter.toUiFilter(): LibraryStatusFilter =
    when (this) {
        io.morgan.mio.domain.preferences.LibraryStatusFilter.ALL -> LibraryStatusFilter.ALL
        io.morgan.mio.domain.preferences.LibraryStatusFilter.READY -> LibraryStatusFilter.READY
        io.morgan.mio.domain.preferences.LibraryStatusFilter.CURRENT -> LibraryStatusFilter.CURRENT
        io.morgan.mio.domain.preferences.LibraryStatusFilter.PLANNED -> LibraryStatusFilter.PLANNED
        io.morgan.mio.domain.preferences.LibraryStatusFilter.PAUSED -> LibraryStatusFilter.PAUSED
        io.morgan.mio.domain.preferences.LibraryStatusFilter.COMPLETED -> LibraryStatusFilter.COMPLETED
        io.morgan.mio.domain.preferences.LibraryStatusFilter.DROPPED -> LibraryStatusFilter.DROPPED
        io.morgan.mio.domain.preferences.LibraryStatusFilter.REPEATING -> LibraryStatusFilter.REPEATING
    }
