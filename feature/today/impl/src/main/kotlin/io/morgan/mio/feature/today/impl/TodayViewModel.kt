package io.morgan.mio.feature.today.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.progress.ObserveProgressUndoUseCase
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressRules
import io.morgan.mio.domain.progress.ProgressWriteResult
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
import io.morgan.mio.domain.queue.ObserveTodayQueueUseCase
import io.morgan.mio.domain.sync.RetrySyncIssueUseCase
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AccountScope::class)
@ViewModelKey
@Inject
internal class TodayViewModel(
    private val accountId: AccountId,
    observeTodayQueue: ObserveTodayQueueUseCase,
    private val collectionRepository: CollectionRepository,
    private val mediaRepository: MediaRepository,
    private val stepProgress: StepProgressUseCase,
    private val undoProgress: UndoProgressUseCase,
    observeUndoUseCase: ObserveProgressUndoUseCase,
    private val syncRecoveryRepository: SyncRecoveryRepository,
    private val retrySyncIssue: RetrySyncIssueUseCase,
) : ViewModel() {
    private val progressError = MutableStateFlow<TodayProgressErrorUiModel?>(null)
    private val latestHiddenUndoId = MutableStateFlow<String?>(null)
    private val touchedMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val settlingMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingRequests = MutableStateFlow<Map<OperationGroupId, PendingProgressRequest>>(emptyMap())
    private val visible = MutableStateFlow(true)
    private val routineHapticEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var lastRequest: PendingProgressRequest? = null
    private var previousState: TodayUiState? = null
    private val motionGenerations = mutableMapOf<String, Long>()

    internal val routineHaptics: SharedFlow<Unit> = routineHapticEvents

    private val latestUndo: Flow<ProgressAction?> =
        combine(observeUndoUseCase(), latestHiddenUndoId) { action, hiddenId ->
            action?.takeIf { value -> value.id.value != hiddenId }
        }

    private val recoveryPresentation: Flow<TodayRecoveryUiModel> =
        combine(
            syncRecoveryRepository.observeState(),
            collectionRepository.observeCollection(accountId),
        ) { recovery, collection ->
            RecoveryInput(recovery = recovery, collection = collection)
        }.distinctUntilChanged()
            .flatMapLatest { input ->
                val affectedEntries = input.recovery.entryIssues.mapTo(hashSetOf()) { it.entryId }
                val mediaIds =
                    input.collection
                        .filter { it.id in affectedEntries }
                        .map { it.mediaId }
                        .distinct()
                val mediaFlows = mediaIds.map { mediaId -> mediaRepository.observeMedia(mediaId) }
                if (mediaFlows.isEmpty()) {
                    flowOf(input.toRecoveryUi(emptyMap()))
                } else {
                    combine(mediaFlows) { mediaValues ->
                        mediaIds
                            .zip(mediaValues.toList())
                            .associate { (mediaId, media) -> mediaId.value to media?.title?.value }
                    }.map(input::toRecoveryUi)
                }
            }

    private val baseState: Flow<TodayUiState> =
        combine(
            observeTodayQueue(accountId),
            collectionRepository.observeCollection(accountId),
            latestUndo,
            progressError,
            recoveryPresentation,
        ) { queue, collection, undo, error, recovery ->
            TodayUiMapper.map(
                queue = queue,
                collection = collection,
                latestUndo = undo,
                progressError = error,
                syncIssues = recovery.syncIssues,
                accountIssue = recovery.accountIssue,
            )
        }

    private val presentationProtection: Flow<PresentationProtection> =
        combine(touchedMediaIds, pendingRequests, settlingMediaIds, visible) { touched, pending, settling, isVisible ->
            PresentationProtection(
                touchedMediaIds = touched,
                pendingMediaIds = pending.values.map { request -> request.mediaId.value }.toSet(),
                settlingMediaIds = settling,
                visible = isVisible,
            )
        }

    internal val uiState: StateFlow<TodayUiState> =
        combine(
            baseState,
            collectionRepository.observeCollection(accountId),
            presentationProtection,
        ) { base, collection, protection ->
            val protectedIds =
                if (protection.visible) {
                    protection.touchedMediaIds +
                        protection.pendingMediaIds +
                        protection.settlingMediaIds +
                        listOfNotNull(base.latestUndoMediaId())
                } else {
                    emptySet()
                }
            preserveProtectedState(base, collection, protectedIds)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState.Loading(),
        )

    internal fun onAction(action: TodayAction) {
        when (action) {
            is TodayAction.Step -> startStep(action)
            is TodayAction.Undo -> startUndo(action)
            is TodayAction.RetrySync -> startSyncRetry(action)
            TodayAction.RetryProgress -> lastRequest?.let(::startRequest)
            is TodayAction.InteractionChanged ->
                touchedMediaIds.update { ids ->
                    if (action.pressed) ids + action.mediaId else ids - action.mediaId
                }
            is TodayAction.MotionChanged ->
                if (action.active) {
                    val current = motionGenerations[action.mediaId] ?: Long.MIN_VALUE
                    if (action.generation >= current) {
                        motionGenerations[action.mediaId] = action.generation
                        settlingMediaIds.update { ids -> ids + action.mediaId }
                    }
                } else if (motionGenerations[action.mediaId] == action.generation) {
                    motionGenerations.remove(action.mediaId)
                    settlingMediaIds.update { ids -> ids - action.mediaId }
                }
            is TodayAction.VisibilityChanged -> {
                visible.value = action.visible
                if (!action.visible) {
                    touchedMediaIds.value = emptySet()
                    pendingRequests.value = emptyMap()
                    settlingMediaIds.value = emptySet()
                    motionGenerations.clear()
                }
            }
            TodayAction.DismissError -> progressError.value = null
        }
    }

    private fun startStep(action: TodayAction.Step) {
        val mediaId = runCatching { MediaId(action.mediaId) }.getOrNull() ?: return
        val request =
            PendingProgressRequest(
                actionId = OperationGroupId(UUID.randomUUID().toString()),
                mediaId = mediaId,
                direction = action.direction,
                timeoutMillis = action.timeoutMillis,
            )
        startRequest(request)
    }

    private fun startRequest(request: PendingProgressRequest) {
        pendingRequests.update { requests -> requests + (request.actionId to request) }
        progressError.value = null
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
                    pendingRequests.update { requests -> requests - request.actionId }
                    lastRequest = request
                    progressError.value = TodayProgressErrorUiModel(retryable = true)
                    return@launch
                }
            pendingRequests.update { requests -> requests - request.actionId }
            when (result) {
                is ProgressWriteResult.Committed -> {
                    latestHiddenUndoId.value = null
                    routineHapticEvents.tryEmit(Unit)
                }
                is ProgressWriteResult.AlreadyHandled -> latestHiddenUndoId.value = null
                ProgressWriteResult.Stale -> {
                    lastRequest = request
                    progressError.value = TodayProgressErrorUiModel(retryable = true)
                }
                is ProgressWriteResult.Rejected -> {
                    progressError.value = TodayProgressErrorUiModel(retryable = false)
                }
            }
        }
    }

    private fun startSyncRetry(action: TodayAction.RetrySync) {
        val operationId = runCatching { OperationId(action.operationId) }.getOrNull() ?: return
        viewModelScope.launch {
            try {
                retrySyncIssue(operationId)
            } catch (_: io.morgan.mio.domain.sync.SyncInfrastructureException) {
                progressError.value = TodayProgressErrorUiModel(retryable = false)
            }
        }
    }

    private fun startUndo(action: TodayAction.Undo) {
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
            when (result) {
                is ProgressWriteResult.Committed -> {
                    latestHiddenUndoId.value = groupId.value
                }
                is ProgressWriteResult.AlreadyHandled -> latestHiddenUndoId.value = groupId.value
                else -> {
                    latestHiddenUndoId.value = groupId.value
                    progressError.value = TodayProgressErrorUiModel(retryable = false)
                }
            }
        }
    }

    override fun onCleared() {
        motionGenerations.clear()
    }

    private fun preserveProtectedState(
        base: TodayUiState,
        collection: List<CollectionEntry>,
        protectedIds: Set<String>,
    ): TodayUiState {
        val prior = previousState as? TodayUiState.Populated
        if (prior == null || protectedIds.isEmpty() || collection.isEmpty()) {
            previousState = base
            return base
        }
        val current = base as? TodayUiState.Populated
        val currentNew = current?.newRecommendations.orEmpty().associateBy { it.mediaId }
        val entries = collection.associateBy { it.mediaId.value }
        val now =
            (current?.now?.takeIf { it.mediaId == prior.now.mediaId } ?: prior.now)
                .withCollection(entries[prior.now.mediaId], current?.now?.mediaId == prior.now.mediaId)
        val rows =
            prior.newRecommendations.map { item ->
                (currentNew[item.mediaId] ?: item).withCollection(entries[item.mediaId], item.mediaId in currentNew)
            }
        val result =
            TodayUiState.Populated(
                now = now,
                newRecommendations = rows,
                remainingReadyCount = current?.remainingReadyCount ?: 0,
                latestUndo =
                    when (base) {
                        is TodayUiState.Populated -> base.latestUndo
                        is TodayUiState.CaughtUp -> base.latestUndo
                        is TodayUiState.Loading -> base.latestUndo
                    },
                progressError =
                    when (base) {
                        is TodayUiState.Populated -> base.progressError
                        is TodayUiState.CaughtUp -> base.progressError
                        is TodayUiState.Loading -> base.progressError
                    },
                syncIssues = base.syncIssues(),
                accountIssue = base.accountIssue(),
            )
        previousState = result
        return result
    }

    private fun TodayProgressDirection.toDomainDirection(): ProgressDirection =
        when (this) {
            TodayProgressDirection.INCREASE -> ProgressDirection.INCREASE
            TodayProgressDirection.DECREASE -> ProgressDirection.DECREASE
        }

    private data class PendingProgressRequest(
        val actionId: OperationGroupId,
        val mediaId: MediaId,
        val direction: TodayProgressDirection,
        val timeoutMillis: Long,
    )

    private data class PresentationProtection(
        val touchedMediaIds: Set<String>,
        val pendingMediaIds: Set<String>,
        val settlingMediaIds: Set<String>,
        val visible: Boolean,
    )

    private data class RecoveryInput(
        val recovery: io.morgan.mio.domain.sync.SyncRecoveryState,
        val collection: List<CollectionEntry>,
    ) {
        fun toRecoveryUi(titlesByMediaId: Map<String, String?>): TodayRecoveryUiModel {
            val titleByEntryId =
                collection.associate { entry ->
                    entry.id.value to titlesByMediaId[entry.mediaId.value]
                }
            val mediaIdByEntryId = collection.associate { entry -> entry.id.value to entry.mediaId.value }
            return TodayUiMapper.mapRecovery(recovery, titleByEntryId, mediaIdByEntryId)
        }
    }
}

private fun TodayUiState.latestUndoMediaId(): String? =
    when (this) {
        is TodayUiState.Loading -> null
        is TodayUiState.Populated -> latestUndo?.mediaId
        is TodayUiState.CaughtUp -> latestUndo?.mediaId
    }

private fun TodayUiState.syncIssues(): List<TodayUiState.SyncIssueUiModel> =
    when (this) {
        is TodayUiState.Loading -> syncIssues
        is TodayUiState.Populated -> syncIssues
        is TodayUiState.CaughtUp -> syncIssues
    }

private fun TodayUiState.accountIssue(): TodayUiState.AccountIssueUiModel? =
    when (this) {
        is TodayUiState.Loading -> accountIssue
        is TodayUiState.Populated -> accountIssue
        is TodayUiState.CaughtUp -> accountIssue
    }

private fun TodayNowUiModel.withCollection(
    entry: CollectionEntry?,
    queueClaims: Boolean,
): TodayNowUiModel =
    if (entry == null) {
        copy(controls = null, nextUnit = null, availableUnitCount = null, undo = null)
    } else {
        copy(
            progress = entry.progress.value,
            progressUnit = entry.progressUnit.toTodayUnit(),
            total = entry.total?.value,
            status = entry.status.toTodayStatus(),
            controls = entry.progressControls(),
            nextUnit =
                nextUnit.takeIf {
                    queueClaims &&
                        progress == entry.progress.value &&
                        status == entry.status.toTodayStatus()
                },
            availableUnitCount =
                availableUnitCount.takeIf {
                    queueClaims &&
                        progress == entry.progress.value &&
                        status == entry.status.toTodayStatus()
                },
        )
    }

private fun TodayNewUiModel.withCollection(
    entry: CollectionEntry?,
    queueClaims: Boolean,
): TodayNewUiModel =
    if (entry == null) {
        copy(controls = null, nextUnit = null, availableUnitCount = null, releaseAge = null, undo = null)
    } else {
        copy(
            progress = entry.progress.value,
            progressUnit = entry.progressUnit.toTodayUnit(),
            total = entry.total?.value,
            status = entry.status.toTodayStatus(),
            controls = entry.progressControls(),
            nextUnit =
                nextUnit.takeIf {
                    queueClaims &&
                        progress == entry.progress.value &&
                        status == entry.status.toTodayStatus()
                },
            availableUnitCount =
                availableUnitCount.takeIf {
                    queueClaims &&
                        progress == entry.progress.value &&
                        status == entry.status.toTodayStatus()
                },
            releaseAge =
                releaseAge.takeIf {
                    queueClaims &&
                        progress == entry.progress.value &&
                        status == entry.status.toTodayStatus()
                },
        )
    }

private fun CollectionEntry.progressControls(): TodayProgressControlsUiModel? =
    if (status != CollectionStatus.CURRENT) {
        null
    } else {
        TodayProgressControlsUiModel(
            decreaseEnabled = ProgressRules.decide(this, ProgressDirection.DECREASE) is ProgressRules.Decision.Change,
            increaseEnabled = ProgressRules.decide(this, ProgressDirection.INCREASE) is ProgressRules.Decision.Change,
        )
    }

private fun CollectionStatus.toTodayStatus(): TodayCollectionStatus =
    when (this) {
        CollectionStatus.CURRENT -> TodayCollectionStatus.CURRENT
        CollectionStatus.PLANNED -> TodayCollectionStatus.PLANNED
        CollectionStatus.PAUSED -> TodayCollectionStatus.PAUSED
        CollectionStatus.COMPLETED -> TodayCollectionStatus.COMPLETED
        CollectionStatus.DROPPED -> TodayCollectionStatus.DROPPED
        CollectionStatus.REPEATING -> TodayCollectionStatus.REPEATING
    }

private fun io.morgan.mio.domain.model.ProgressUnit.toTodayUnit(): TodayProgressUnit =
    when (this) {
        io.morgan.mio.domain.model.ProgressUnit.EPISODE -> TodayProgressUnit.EPISODE
        io.morgan.mio.domain.model.ProgressUnit.CHAPTER -> TodayProgressUnit.CHAPTER
        io.morgan.mio.domain.model.ProgressUnit.VOLUME -> TodayProgressUnit.VOLUME
    }
