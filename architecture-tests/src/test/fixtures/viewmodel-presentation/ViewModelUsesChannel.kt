package io.morgan.mio.feature.today.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.common.di.AppScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal data class ChannelUiState(val ready: Boolean)
internal sealed interface ChannelUiEvent

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
internal class ChannelViewModel : ViewModel() {
    internal val uiState: StateFlow<ChannelUiState> = MutableStateFlow(ChannelUiState(false))
    internal val events: SharedFlow<ChannelUiEvent> = MutableSharedFlow()
    private val channel = Channel<ChannelUiEvent>(Channel.BUFFERED)
}

@Composable
internal fun ChannelRoute(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<ChannelViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { }
        }
    }
    ChannelScreen(uiState, modifier)
}

@Composable
internal fun ChannelScreen(
    state: ChannelUiState,
    modifier: Modifier = Modifier,
) {
    state
    modifier
}
