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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@AssistedInject
internal class MediaDetailViewModel(
    @Assisted private val mediaId: MediaId,
    private val accountId: AccountId,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {
    private val requestPhase = MutableStateFlow(RequestPhase.IDLE)

    internal val uiState: StateFlow<MediaDetailUiState> =
        combine(
            mediaRepository.observeMedia(mediaId),
            mediaRepository.observeRelatedMedia(mediaId),
            collectionRepository.observeEntryForMedia(accountId, mediaId),
            requestPhase,
        ) { media, related, collection, phase ->
            when {
                media != null -> MediaDetailUiMapper.mapContent(media, related, collection)
                phase == RequestPhase.FAILED -> MediaDetailUiState.Error
                else -> MediaDetailUiState.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MediaDetailUiState.Loading,
        )

    init {
        refresh()
    }

    internal fun onAction(action: MediaDetailAction) {
        if (action == MediaDetailAction.Retry && uiState.value is MediaDetailUiState.Error) {
            refresh()
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

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    internal fun interface Factory : ManualViewModelAssistedFactory {
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
