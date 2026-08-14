package io.morgan.mio.feature.today.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.collectAsState
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.common.di.AppScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal data class BrokenRouteUiState(val ready: Boolean)
internal sealed interface BrokenRouteUiEvent

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
internal class BrokenRouteViewModel : ViewModel() {
    internal val uiState: StateFlow<BrokenRouteUiState> = MutableStateFlow(BrokenRouteUiState(false))
    internal val events: SharedFlow<BrokenRouteUiEvent> = MutableSharedFlow()
}

@Composable
internal fun BrokenRoute(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<BrokenRouteViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { }
        }
    }
    BrokenScreen(uiState, modifier)
}

@Composable
internal fun BrokenScreen(
    state: BrokenRouteUiState,
    modifier: Modifier = Modifier,
) {
    state
    modifier
}
