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
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.progress.CorrectProgressUseCase
import io.morgan.mio.domain.progress.ProgressWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@AssistedInject
internal class ProgressEditorViewModel(
    @Assisted private val mediaId: MediaId,
    private val accountId: AccountId,
    mediaRepository: MediaRepository,
    collectionRepository: CollectionRepository,
    private val correctProgress: CorrectProgressUseCase,
) : ViewModel() {
    private val targetText = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<ProgressEditorError?>(null)
    private val savedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    internal val saved = savedEvents

    private val source =
        combine(
            mediaRepository.observeMedia(mediaId),
            collectionRepository.observeEntryForMedia(accountId, mediaId),
        ) { media, entry ->
            if (media == null || entry == null) {
                null
            } else {
                EditorSource(title = media.title.value, entry = entry)
            }
        }

    internal val uiState: StateFlow<ProgressEditorUiState> =
        combine(source, targetText, saving, error) { content, requestedText, isSaving, currentError ->
            if (content == null) return@combine ProgressEditorUiState.Missing
            val target =
                requestedText ?: content.entry.progress.value
                    .toString()
            val parsed = target.toIntOrNull()
            val canEdit = content.entry.status in setOf(CollectionStatus.CURRENT, CollectionStatus.COMPLETED)
            val validationError =
                when {
                    (target.isBlank() || parsed == null || parsed < 0) && requestedText != null ->
                        ProgressEditorError.INVALID_VALUE
                    parsed != null &&
                        parsed >= 0 &&
                        content.entry.total
                            ?.value
                            ?.let { total -> parsed > total } == true ->
                        ProgressEditorError.ABOVE_TOTAL
                    parsed == content.entry.progress.value && requestedText != null -> ProgressEditorError.UNCHANGED
                    !canEdit -> ProgressEditorError.INELIGIBLE
                    else -> null
                }
            ProgressEditorUiState.Content(
                title = content.title,
                unit = content.entry.progressUnit.toEditorUnit(),
                currentProgress = content.entry.progress.value,
                total = content.entry.total?.value,
                targetText = target,
                canSave =
                    parsed != null &&
                        parsed >= 0 &&
                        content.entry.total
                            ?.value
                            ?.let { total -> parsed <= total } != false &&
                        parsed != content.entry.progress.value &&
                        canEdit &&
                        !isSaving,
                saving = isSaving,
                error = currentError ?: validationError,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgressEditorUiState.Loading,
        )

    internal fun onAction(action: ProgressEditorAction) {
        when (action) {
            is ProgressEditorAction.TargetChanged -> {
                targetText.value = action.value.take(MAX_INPUT_LENGTH)
                error.value = null
            }
            ProgressEditorAction.Save -> save()
        }
    }

    private fun save() {
        val content = uiState.value as? ProgressEditorUiState.Content ?: return
        if (!content.canSave || saving.value) return
        val target = content.targetText.toIntOrNull() ?: return
        saving.value = true
        error.value = null
        viewModelScope.launch {
            val result =
                try {
                    correctProgress(
                        mediaId = mediaId,
                        targetProgress = target,
                        actionId = OperationGroupId(UUID.randomUUID().toString()),
                        undoWindow = UNDO_WINDOW,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    saving.value = false
                    error.value = ProgressEditorError.SAVE_FAILED
                    return@launch
                }
            saving.value = false
            when (result) {
                is ProgressWriteResult.Committed,
                is ProgressWriteResult.AlreadyHandled,
                -> savedEvents.tryEmit(Unit)
                ProgressWriteResult.Stale -> error.value = ProgressEditorError.STALE
                is ProgressWriteResult.Rejected ->
                    error.value =
                        when (result.reason) {
                            ProgressWriteResult.Rejection.AT_BOUNDARY -> ProgressEditorError.ABOVE_TOTAL
                            ProgressWriteResult.Rejection.INELIGIBLE -> ProgressEditorError.INELIGIBLE
                            else -> ProgressEditorError.SAVE_FAILED
                        }
            }
        }
    }

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    public fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted mediaId: MediaId,
        ): ProgressEditorViewModel
    }

    private data class EditorSource(
        val title: String,
        val entry: CollectionEntry,
    )

    private fun io.morgan.mio.domain.model.ProgressUnit.toEditorUnit(): ProgressEditorUnit =
        when (this) {
            io.morgan.mio.domain.model.ProgressUnit.EPISODE -> ProgressEditorUnit.EPISODE
            io.morgan.mio.domain.model.ProgressUnit.CHAPTER -> ProgressEditorUnit.CHAPTER
            io.morgan.mio.domain.model.ProgressUnit.VOLUME -> ProgressEditorUnit.VOLUME
        }

    private companion object {
        const val MAX_INPUT_LENGTH = 10
        val UNDO_WINDOW = 6.seconds
    }
}
