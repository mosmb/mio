package io.morgan.mio.feature.media.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.progress.ObserveProgressUndoUseCase
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressWriteResult
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@AssistedInject
internal class MediaDetailViewModel(
    @Assisted private val mediaId: MediaId,
    private val accountId: AccountId,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val stepProgress: StepProgressUseCase,
    private val undoProgress: UndoProgressUseCase,
    observeUndoUseCase: ObserveProgressUndoUseCase,
    private val syncRecoveryRepository: SyncRecoveryRepository,
    private val retrySyncIssue: RetrySyncIssueUseCase,
) : ViewModel() {
    private val requestPhase = MutableStateFlow(RequestPhase.IDLE)
    private val progressError = MutableStateFlow<MediaDetailProgressErrorUiModel?>(null)
    private val latestHiddenUndoId = MutableStateFlow<String?>(null)
    private val pendingRequests = MutableStateFlow<Map<OperationGroupId, PendingProgressRequest>>(emptyMap())
    private val routineHapticEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val interactionMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val motionGenerations = mutableMapOf<String, Long>()
    private var lastRequest: PendingProgressRequest? = null

    internal val routineHaptics: SharedFlow<Unit> = routineHapticEvents

    private val latestUndo: Flow<ProgressAction?> =
        combine(observeUndoUseCase(), latestHiddenUndoId) { action, hiddenId ->
            action?.takeIf { value -> value.id.value != hiddenId }
        }

    private val observedContent: Flow<ObservedContent> =
        combine(
            mediaRepository.observeMedia(mediaId),
            mediaRepository.observeRelatedMedia(mediaId),
            collectionRepository.observeEntryForMedia(accountId, mediaId),
        ) { media, related, collection ->
            ObservedContent(media = media, related = related, collection = collection)
        }

    internal val uiState: StateFlow<MediaDetailUiState> =
        combine(
            observedContent,
            syncRecoveryRepository.observeState(),
            requestPhase,
            latestUndo,
            progressError,
        ) { content, recovery, phase, undo, error ->
            val recoveryUi =
                MediaDetailUiMapper.mapRecovery(
                    recovery = recovery,
                    collection = content.collection,
                    title = content.media?.title?.value ?: "Saved title",
                )
            when {
                content.media != null ->
                    MediaDetailUiMapper.mapContent(
                        media = content.media,
                        related = content.related,
                        collection = content.collection,
                        latestUndo = undo,
                        progressError = error,
                        syncIssues = recoveryUi.syncIssues,
                        accountIssue = recoveryUi.accountIssue,
                    )
                phase == RequestPhase.FAILED ->
                    MediaDetailUiState.Error(
                        latestUndo = undo?.let(MediaDetailUiMapper::mapUndo),
                        progressError = error,
                        syncIssues = recoveryUi.syncIssues,
                        accountIssue = recoveryUi.accountIssue,
                    )
                else ->
                    MediaDetailUiState.Loading(
                        latestUndo = undo?.let(MediaDetailUiMapper::mapUndo),
                        progressError = error,
                        syncIssues = recoveryUi.syncIssues,
                        accountIssue = recoveryUi.accountIssue,
                    )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MediaDetailUiState.Loading(),
        )

    init {
        refresh()
    }

    internal fun onAction(action: MediaDetailAction) {
        when (action) {
            MediaDetailAction.Retry ->
                if (uiState.value is MediaDetailUiState.Error) {
                    refresh()
                }
            is MediaDetailAction.Step -> startStep(action)
            is MediaDetailAction.Undo -> startUndo(action)
            is MediaDetailAction.RetrySync -> startSyncRetry(action)
            MediaDetailAction.RetryProgress -> lastRequest?.let(::startRequest)
            is MediaDetailAction.InteractionChanged ->
                interactionMediaIds.update { ids ->
                    if (action.pressed) ids + action.mediaId else ids - action.mediaId
                }
            is MediaDetailAction.MotionChanged ->
                if (action.active) {
                    val current = motionGenerations[action.mediaId] ?: Long.MIN_VALUE
                    if (action.generation >= current) motionGenerations[action.mediaId] = action.generation
                } else if (motionGenerations[action.mediaId] == action.generation) {
                    motionGenerations.remove(action.mediaId)
                }
            is MediaDetailAction.VisibilityChanged -> Unit
            MediaDetailAction.DismissError -> progressError.value = null
        }
    }

    private fun startStep(action: MediaDetailAction.Step) {
        val requestedMediaId = runCatching { MediaId(action.mediaId) }.getOrNull() ?: return
        if (requestedMediaId != mediaId) return
        val request =
            PendingProgressRequest(
                actionId = OperationGroupId(UUID.randomUUID().toString()),
                mediaId = requestedMediaId,
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
                    progressError.value = MediaDetailProgressErrorUiModel(retryable = true)
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
                    progressError.value = MediaDetailProgressErrorUiModel(retryable = true)
                }
                is ProgressWriteResult.Rejected -> {
                    progressError.value = MediaDetailProgressErrorUiModel(retryable = false)
                }
            }
        }
    }

    private fun startSyncRetry(action: MediaDetailAction.RetrySync) {
        val operationId = runCatching { OperationId(action.operationId) }.getOrNull() ?: return
        viewModelScope.launch {
            try {
                retrySyncIssue(operationId)
            } catch (_: io.morgan.mio.domain.sync.SyncInfrastructureException) {
                progressError.value = MediaDetailProgressErrorUiModel(retryable = false)
            }
        }
    }

    private fun startUndo(action: MediaDetailAction.Undo) {
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
                is ProgressWriteResult.Committed,
                is ProgressWriteResult.AlreadyHandled,
                -> latestHiddenUndoId.value = groupId.value
                else -> {
                    latestHiddenUndoId.value = groupId.value
                    progressError.value = MediaDetailProgressErrorUiModel(retryable = false)
                }
            }
        }
    }

    private fun refresh() {
        requestPhase.value = RequestPhase.RUNNING
        viewModelScope.launch {
            try {
                mediaRepository.refreshMedia(mediaId)
                requestPhase.value = RequestPhase.SUCCEEDED
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                requestPhase.value = RequestPhase.FAILED
            }
        }
    }

    override fun onCleared() {
        interactionMediaIds.value = emptySet()
        motionGenerations.clear()
    }

    private fun MediaDetailProgressDirection.toDomainDirection(): ProgressDirection =
        when (this) {
            MediaDetailProgressDirection.INCREASE -> ProgressDirection.INCREASE
            MediaDetailProgressDirection.DECREASE -> ProgressDirection.DECREASE
        }

    private data class PendingProgressRequest(
        val actionId: OperationGroupId,
        val mediaId: MediaId,
        val direction: MediaDetailProgressDirection,
        val timeoutMillis: Long,
    )

    private data class ObservedContent(
        val media: io.morgan.mio.domain.media.Media?,
        val related: List<io.morgan.mio.domain.media.Media>,
        val collection: io.morgan.mio.domain.collection.CollectionEntry?,
    )

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    public fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted mediaId: MediaId,
        ): MediaDetailViewModel
    }

    private enum class RequestPhase {
        IDLE,
        RUNNING,
        SUCCEEDED,
        FAILED,
    }
}
