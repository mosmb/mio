package io.morgan.mio.feature.media.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@AssistedInject
internal class ValidAccountAssistedViewModel(
    @Assisted private val mediaId: MediaId,
) : ViewModel() {
    internal val uiState: StateFlow<Boolean> = MutableStateFlow(mediaId.value.isNotEmpty())

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    internal fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted mediaId: MediaId): ValidAccountAssistedViewModel
    }
}

@Composable
internal fun ValidAccountAssistedRoute(modifier: Modifier = Modifier) {
    val viewModel =
        assistedMetroViewModel<ValidAccountAssistedViewModel, ValidAccountAssistedViewModel.Factory> {
            create(MediaId("fixture-media"))
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ValidAccountAssistedScreen(state = uiState, modifier = modifier)
}

@Composable
internal fun ValidAccountAssistedScreen(
    state: Boolean,
    modifier: Modifier = Modifier,
) {
    state
    modifier
}
