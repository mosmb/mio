package io.morgan.mio.feature.progress.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.core.ui.title.MediaTitleNames
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.sync.ProgressConflictChoice
import io.morgan.mio.domain.sync.ResolveProgressConflictUseCase
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncIssueKind
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@AssistedInject
internal class ProgressConflictViewModel(
    @Assisted private val operationId: OperationId,
    @Assisted private val entryId: CollectionEntryId,
    private val collectionRepository: CollectionRepository,
    private val mediaRepository: MediaRepository,
    private val syncRecoveryRepository: SyncRecoveryRepository,
    private val resolveProgressConflict: ResolveProgressConflictUseCase,
) : ViewModel() {
    private val selection = MutableStateFlow<SelectedChoice?>(null)
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<ProgressConflictError?>(null)
    private val resolvedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    internal val resolved = resolvedEvents

    private val activeIssue =
        syncRecoveryRepository.observeState().map { state ->
            state.entryIssues.firstOrNull { issue ->
                issue.operationId == operationId && issue.entryId == entryId && issue.isProgressConflict()
            }
        }
    private val source =
        combine(activeIssue, collectionRepository.observeEntry(entryId)) { issue, entry ->
            if (issue == null) null else (entry?.mediaId ?: issue.mediaId)?.let { issue to it }
        }.flatMapLatest { pair ->
            if (pair == null) {
                flowOf(null)
            } else {
                val (issue, mediaId) = pair
                mediaRepository.observeMedia(mediaId).map { media ->
                    ConflictSource(
                        issue = issue,
                        title = media?.title?.value ?: "Saved title",
                        titleNames =
                            MediaTitleNames(
                                media?.title?.english,
                                media?.title?.japanese,
                                media?.title?.romaji,
                            ),
                    )
                }
            }
        }

    internal val uiState: StateFlow<ProgressConflictUiState> =
        combine(source, selection, saving, error) { source, requestedSelection, isSaving, currentError ->
            if (source == null) return@combine ProgressConflictUiState.Unavailable
            val remote = source.issue.remote ?: return@combine ProgressConflictUiState.Unavailable
            if (source.issue.local.unit != remote.unit) return@combine ProgressConflictUiState.Unavailable
            ProgressConflictUiState.Content(
                issue = source.issue,
                title = source.title,
                titleNames = source.titleNames,
                deviceProgress = source.issue.local.progress.value,
                providerProgress = remote.progress.value,
                unit =
                    source.issue.local.unit
                        .toConflictUnit(),
                selected = requestedSelection?.takeIf { selected -> selected.issue == source.issue }?.choice,
                saving = isSaving,
                error = currentError,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgressConflictUiState.Loading,
        )

    internal fun onAction(action: ProgressConflictAction) {
        when (action) {
            is ProgressConflictAction.Select -> {
                val content = uiState.value as? ProgressConflictUiState.Content ?: return
                selection.value = SelectedChoice(action.selection, content.issue)
                error.value = null
            }
            ProgressConflictAction.Confirm -> resolve()
        }
    }

    private fun resolve() {
        val content = uiState.value as? ProgressConflictUiState.Content ?: return
        val selected = content.selected ?: return
        if (saving.value) return
        saving.value = true
        error.value = null
        viewModelScope.launch {
            val success =
                try {
                    resolveProgressConflict(
                        expectedIssue = content.issue,
                        choice = selected.toDomainChoice(),
                        groupId = OperationGroupId(UUID.randomUUID().toString()),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    saving.value = false
                    error.value = ProgressConflictError.SAVE_FAILED
                    return@launch
                }
            saving.value = false
            if (success) {
                resolvedEvents.tryEmit(Unit)
            } else {
                error.value = ProgressConflictError.CHANGED
                selection.value = null
            }
        }
    }

    private fun SyncIssue.isProgressConflict(): Boolean = kind == SyncIssueKind.CONFLICT && remote != null

    private fun ProgressUnit.toConflictUnit(): ProgressConflictUnit =
        when (this) {
            ProgressUnit.EPISODE -> ProgressConflictUnit.EPISODE
            ProgressUnit.CHAPTER -> ProgressConflictUnit.CHAPTER
            ProgressUnit.VOLUME -> ProgressConflictUnit.VOLUME
        }

    private fun ProgressConflictSelection.toDomainChoice(): ProgressConflictChoice =
        when (this) {
            ProgressConflictSelection.KEEP_DEVICE -> ProgressConflictChoice.KEEP_DEVICE
            ProgressConflictSelection.USE_PROVIDER -> ProgressConflictChoice.USE_PROVIDER
        }

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    public fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted operationId: OperationId,
            @Assisted entryId: CollectionEntryId,
        ): ProgressConflictViewModel
    }

    private data class ConflictSource(
        val issue: SyncIssue,
        val title: String,
        val titleNames: MediaTitleNames =
            io.morgan.mio.core.ui.title
                .MediaTitleNames(),
    )

    private data class SelectedChoice(
        val choice: ProgressConflictSelection,
        val issue: SyncIssue,
    )
}
