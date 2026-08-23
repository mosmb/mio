package io.morgan.mio.feature.today.impl

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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@AssistedInject
internal class AssistedViewModel(
    @Assisted private val mediaId: MediaId,
) : ViewModel() {
    internal val uiState: StateFlow<Boolean> = MutableStateFlow(mediaId.value.isNotEmpty())

    @AssistedFactory
    @ContributesIntoMap(AccountScope::class)
    @ManualViewModelAssistedFactoryKey
    internal fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted mediaId: MediaId): AssistedViewModel
    }
}

@Composable
internal fun AssistedRoute(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<AssistedViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AssistedScreen(state = uiState, modifier = modifier)
}

@Composable
internal fun AssistedScreen(
    state: Boolean,
    modifier: Modifier = Modifier,
) {
    state
    modifier
}
